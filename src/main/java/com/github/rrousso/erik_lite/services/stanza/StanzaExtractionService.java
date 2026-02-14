package com.github.rrousso.erik_lite.services.stanza;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.config.ExtractionConfig;
import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.dto.extraction.CharacterAppearance;
import com.github.rrousso.erik_lite.dto.extraction.EmergentCharacter;
import com.github.rrousso.erik_lite.dto.extraction.EventExtraction;
import com.github.rrousso.erik_lite.dto.extraction.ExtractionResult;
import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.util.JsonCleanupUtil;

/**
 * Extracts state changes from narrative exchanges and updates the database.
 * 
 * erik-lite simplified from erik-core:
 * - Extracts events + character appearances + emergent characters only
 * - No facts, knowledge transfers, secrets, tensions, or blueprint updates
 * - No ExtractionApplierRegistry — apply logic inlined
 * - No ExtractionPromptBuilder — prompt built inline (template in Step 6)
 * 
 * Process:
 * 1. Check if extraction should happen (based on ExtractionConfig)
 * 2. Build extraction prompt with conversation context + current state
 * 3. Call analytical LLM to analyze recent exchanges
 * 4. Parse JSON response into ExtractionResult
 * 5. Apply events, character appearances, and emergent characters to Stanza
 */
@Service
public class StanzaExtractionService {

    private static final Logger log = LoggerFactory.getLogger(StanzaExtractionService.class);

    private static final int MAX_NAME_LENGTH = 100;

    private final LLMClientService llmClient;
    private final ExtractionConfig extractionConfig;

    public StanzaExtractionService(LLMClientService llmClient, ExtractionConfig extractionConfig) {
        this.llmClient = llmClient;
        this.extractionConfig = extractionConfig;
    }

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    /**
     * Process extraction based on configured frequency and flags.
     * Main entry point for regular extraction during stanza flow.
     *
     * @return true if extraction was performed, false if skipped
     */
    public boolean processExtraction(
            Stanza stanza,
            ConversationHistory history,
            int exchangeNumber,
            boolean isFirstExchange,
            boolean isFinalExchange) {

        boolean shouldExtract = extractionConfig.shouldExtract(exchangeNumber, isFirstExchange, isFinalExchange);

        if (!shouldExtract) {
            log.debug("[Extraction] Skipping extraction for exchange {} (frequency: {})",
                exchangeNumber, extractionConfig.getFrequency());
            return false;
        }

        log.info("[Extraction] Processing extraction for exchange {} (frequency: {}, history size: {})",
            exchangeNumber, extractionConfig.getFrequency(), history.getCurrentHistorySize());

        return performExtraction(stanza, history);
    }

    /**
     * Force extraction regardless of frequency configuration.
     * Used for critical moments like beat boundaries.
     */
    public boolean forceExtraction(Stanza stanza, ConversationHistory history) {
        int exchangeNumber = stanza.getCurrentExchange();

        log.info("[Extraction] FORCED extraction for exchange {} (history size: {})",
            exchangeNumber, history.getCurrentHistorySize());

        return performExtraction(stanza, history);
    }

    // =========================================================================
    // EXTRACTION CORE
    // =========================================================================

    private boolean performExtraction(Stanza stanza, ConversationHistory history) {
        try {
            // 1. Build prompt
            String prompt = buildExtractionPrompt(stanza, history);

            // 2. Call analytical LLM
            log.debug("[Extraction] Calling analytical model");
            String jsonResponse = llmClient.call(ModelType.ANALYTICAL, prompt, "Extract state changes from the narrative.");

            // 3. Parse JSON
            ExtractionResult result = JsonCleanupUtil.parseJson(jsonResponse, ExtractionResult.class);

            // 4. Log
            if (result.hasAnyChanges()) {
                log.info("[Extraction] Extracted {} total changes: {}", result.getTotalChangeCount(), result);
            } else {
                log.debug("[Extraction] No changes extracted from these exchanges");
                return true;
            }

            // 5. Apply changes (order matters: emergent characters before appearances)
            applyEmergentCharacters(stanza, result.getEmergentCharacters());
            applyCharacterAppearances(stanza, result.getCharacterAppearances());
            applyEvents(stanza, result.getEvents());

            log.info("[Extraction] Successfully applied all changes");
            return true;

        } catch (Exception e) {
            log.error("[Extraction] Failed to extract/apply changes for stanza {}", stanza.getId(), e);
            return false;
        }
    }

    // =========================================================================
    // PROMPT BUILDING (inline — will move to template file in Step 6)
    // =========================================================================

    private String buildExtractionPrompt(Stanza stanza, ConversationHistory history) {
        int frequency = extractionConfig.getFrequency();
        String conversationContext = history.getLastNExchangesForExtraction(Math.max(frequency, 1));

        StringBuilder prompt = new StringBuilder();

        prompt.append("You are analyzing a narrative exchange for state changes.\n\n");

        // Current characters
        prompt.append("=== CURRENT CHARACTERS ===\n");
        for (StanzaCharacter c : stanza.getCharacters()) {
            prompt.append("- ").append(c.getName())
                  .append(" (").append(c.getPresenceStatus()).append(")")
                  .append(c.isUser() ? " [USER CHARACTER]" : "")
                  .append("\n");
        }
        prompt.append("\n");

        // Recent events
        prompt.append("=== RECENT EVENTS ===\n");
        List<StanzaEvent> events = stanza.getEvents();
        int startIdx = Math.max(0, events.size() - 5);
        for (int i = startIdx; i < events.size(); i++) {
            StanzaEvent e = events.get(i);
            prompt.append("- [Exchange ").append(e.getExchangeNumber()).append("] ")
                  .append(e.getDescription())
                  .append(" (").append(e.isMajor() ? "MAJOR" : "MINOR").append(")\n");
        }
        if (events.isEmpty()) {
            prompt.append("[No events recorded yet]\n");
        }
        prompt.append("\n");

        // Conversation context
        prompt.append("=== NARRATIVE EXCHANGES TO ANALYZE ===\n");
        prompt.append(conversationContext);
        prompt.append("\n\n");

        // Instructions
        prompt.append("Analyze the exchanges above and output a JSON object with these fields:\n");
        prompt.append("{\n");
        prompt.append("  \"events\": [{ \"description\": \"...\", \"significance\": \"MAJOR|MINOR\", \"charactersInvolved\": [\"name\", ...] }],\n");
        prompt.append("  \"characterAppearances\": [{ \"characterName\": \"...\", \"changeType\": \"APPEARED|LEFT|MENTIONED\", \"context\": \"...\" }],\n");
        prompt.append("  \"emergentCharacters\": [{ \"characterName\": \"...\", \"canonRole\": \"...\", \"currentEmotionalState\": \"...\", ");
        prompt.append("\"relationshipToUser\": \"...\", \"hiddenBackstory\": \"...\", \"physicalDescription\": \"...\" }]\n");
        prompt.append("}\n\n");
        prompt.append("Rules:\n");
        prompt.append("- Events: Record things that HAPPENED. Max 280 chars per description. Mark story-critical events as MAJOR.\n");
        prompt.append("- Character appearances: Only when characters ENTER, LEAVE, or are MENTIONED for the first time.\n");
        prompt.append("- Emergent characters: Only for NEW characters not in the current characters list above.\n");
        prompt.append("- If nothing changed, return empty arrays.\n");
        prompt.append("- Output ONLY valid JSON. No markdown, no explanation.\n");

        return prompt.toString();
    }

    // =========================================================================
    // APPLY METHODS (inlined from erik-core's applier classes)
    // =========================================================================

    private void applyEvents(Stanza stanza, List<EventExtraction> extractions) {
        if (extractions == null || extractions.isEmpty()) return;

        log.info("[Extraction] Applying {} events", extractions.size());

        for (EventExtraction extraction : extractions) {
            StanzaEvent event = new StanzaEvent();
            event.setStanza(stanza);
            event.setDescription(extraction.getDescription());
            event.setExchangeNumber(stanza.getCurrentExchange());
            event.setMajor(extraction.isMajor());

            // Link to current beat
            Beat currentBeat = stanza.getCurrentBeat();
            if (currentBeat != null) {
                event.setBeat(currentBeat);
            } else {
                event.setBeatNumber(stanza.getCurrentBeatNumber());
            }

            // Involved characters
            if (extraction.getCharactersInvolved() != null && !extraction.getCharactersInvolved().isEmpty()) {
                event.setInvolvedCharacters(String.join(",", extraction.getCharactersInvolved()));
            }

            stanza.getEvents().add(event);

            log.debug("[Extraction] Created event: {} (beat {}, exchange {}, {})",
                extraction.getDescription(),
                event.getBeatNumber(),
                stanza.getCurrentExchange(),
                extraction.getSignificance());
        }
    }

    private void applyCharacterAppearances(Stanza stanza, List<CharacterAppearance> appearances) {
        if (appearances == null || appearances.isEmpty()) return;

        log.info("[Extraction] Applying {} character appearances", appearances.size());

        for (CharacterAppearance appearance : appearances) {
            if (appearance.getCharacterName() == null || appearance.getCharacterName().length() > MAX_NAME_LENGTH) {
                log.warn("[Extraction] Skipping appearance with invalid name");
                continue;
            }

            Optional<StanzaCharacter> charOpt = stanza.findCharacterByName(appearance.getCharacterName());

            if (charOpt.isEmpty()) {
                // Character not found — create minimal placeholder if APPEARED
                if (appearance.isAppearance()) {
                    StanzaCharacter newChar = new StanzaCharacter();
                    newChar.setStanza(stanza);
                    newChar.setName(appearance.getCharacterName());
                    newChar.setCanonRole("EMERGENT - unknown");
                    newChar.setPresenceStatus("present");
                    stanza.getCharacters().add(newChar);
                    log.info("[Extraction] Created placeholder character: {}", appearance.getCharacterName());
                } else {
                    log.debug("[Extraction] Ignoring appearance for unknown character: {}", appearance.getCharacterName());
                }
                continue;
            }

            StanzaCharacter character = charOpt.get();
            String oldStatus = character.getPresenceStatus();

            if (appearance.isAppearance()) {
                character.setPresenceStatus("present");
            } else if (appearance.isDeparture()) {
                character.setPresenceStatus("potential");
            } else if (appearance.isMention() && "background".equals(oldStatus)) {
                character.setPresenceStatus("potential");
            }

            log.debug("[Extraction] Character '{}': {} → {}", character.getName(), oldStatus, character.getPresenceStatus());
        }
    }

    private void applyEmergentCharacters(Stanza stanza, List<EmergentCharacter> emergentCharacters) {
        if (emergentCharacters == null || emergentCharacters.isEmpty()) return;

        log.info("[Extraction] Applying {} emergent characters", emergentCharacters.size());

        for (EmergentCharacter emergent : emergentCharacters) {
            if (emergent.getCharacterName() == null || emergent.getCharacterName().isBlank()) {
                log.warn("[Extraction] Skipping emergent character with empty name");
                continue;
            }

            if (emergent.getCharacterName().length() > MAX_NAME_LENGTH) {
                log.warn("[Extraction] Skipping emergent character with name exceeding {} chars", MAX_NAME_LENGTH);
                continue;
            }

            // Skip if already exists
            Optional<StanzaCharacter> existing = stanza.findCharacterByName(emergent.getCharacterName());
            if (existing.isPresent()) {
                log.info("[Extraction] Emergent character '{}' already exists — skipping", emergent.getCharacterName());
                continue;
            }

            StanzaCharacter character = new StanzaCharacter();
            character.setStanza(stanza);
            character.setName(emergent.getCharacterName());
            character.setPresenceStatus("present");

            String role = emergent.getCanonRole();
            character.setCanonRole(role != null && !role.isBlank() ? "EMERGENT - " + role : "EMERGENT - original");

            character.setEmotionalState(
                emergent.getCurrentEmotionalState() != null ? emergent.getCurrentEmotionalState() : "Unknown");
            character.setRelationshipToUser(
                emergent.getRelationshipToUser() != null ? emergent.getRelationshipToUser() : "Unknown");
            character.setPrivateBackstory(emergent.getHiddenBackstory());

            stanza.getCharacters().add(character);

            log.info("[Extraction] Created emergent character: {} ({})", emergent.getCharacterName(), character.getCanonRole());
        }
    }
}