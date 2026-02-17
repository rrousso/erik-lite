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
import com.github.rrousso.erik_lite.dto.extraction.CharacterBlueprint;
import com.github.rrousso.erik_lite.dto.extraction.CharacterStateChange;
import com.github.rrousso.erik_lite.dto.extraction.EmergentCharacter;
import com.github.rrousso.erik_lite.dto.extraction.EventExtraction;
import com.github.rrousso.erik_lite.dto.extraction.ExtractionResult;
import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;
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
    private final SystemPromptBuilderService promptBuilder;

    public StanzaExtractionService(LLMClientService llmClient, ExtractionConfig extractionConfig,
                                   SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.extractionConfig = extractionConfig;
        this.promptBuilder = promptBuilder;
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
            applyCharacterStateChanges(stanza, result.getCharactersStateChanges());

            log.info("[Extraction] Successfully applied all changes");
            return true;

        } catch (Exception e) {
            log.error("[Extraction] Failed to extract/apply changes for stanza {}", stanza.getId(), e);
            return false;
        }
    }

    // =========================================================================
    // PROMPT BUILDING 
    // =========================================================================

    private String buildExtractionPrompt(Stanza stanza, ConversationHistory history) {
        int frequency = extractionConfig.getFrequency();
        String conversationContext = history.getLastNExchangesForExtraction(Math.max(frequency, 1));

        // Format characters
        StringBuilder characters = new StringBuilder();
        for (StanzaCharacter c : stanza.getCharacters()) {
            characters.append("- ").append(c.getName())
                      .append(" (").append(c.getPresenceStatus()).append(")")
                      .append(c.isUser() ? " [USER CHARACTER]" : "")
                      .append("\n");
        }

        // Format recent events (last 5)
        StringBuilder recentEvents = new StringBuilder();
        List<StanzaEvent> events = stanza.getEvents();
        int startIdx = Math.max(0, events.size() - 5);
        for (int i = startIdx; i < events.size(); i++) {
            StanzaEvent e = events.get(i);
            recentEvents.append("- [Exchange ").append(e.getExchangeNumber()).append("] ")
                        .append(e.getDescription())
                        .append(" (").append(e.isMajor() ? "MAJOR" : "MINOR").append(")\n");
        }
        if (events.isEmpty()) {
            recentEvents.append("[No events recorded yet]\n");
        }

        // Fill template placeholders
        return promptBuilder.buildExtractionPrompt()
                .replace("{characters}", characters.toString())
                .replace("{recent_events}", recentEvents.toString())
                .replace("{conversation_context}", conversationContext);
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
            
         // Handle blueprint (may be null if LLM didn't provide it)
            CharacterBlueprint blueprint = emergent.getBlueprint();
            if (blueprint != null) {
                character.setBlueprintTier1Essentials(
                    blueprint.getTier1Essentials() != null ? blueprint.getTier1Essentials() : "Unknown");
                character.setBlueprintTier2Motivators(
                    blueprint.getTier2Motivators() != null ? blueprint.getTier2Motivators() : "Unknown");
                character.setBlueprintTier3Anchors(
                    blueprint.getTier3Anchors() != null ? blueprint.getTier3Anchors() : new String[0]);
            } else {
                log.warn("[Extraction] Emergent character '{}' missing blueprint — using defaults", emergent.getCharacterName());
                character.setBlueprintTier1Essentials("Unknown");
                character.setBlueprintTier2Motivators("Unknown");
                character.setBlueprintTier3Anchors(new String[0]);
            }

            stanza.getCharacters().add(character);

            log.info("[Extraction] Created emergent character: {} ({})", emergent.getCharacterName(), character.getCanonRole());
        }
               
    }
    
    private void applyCharacterStateChanges(Stanza stanza, List<CharacterStateChange> stateChanges) {
        if (stateChanges == null || stateChanges.isEmpty()) return;

        log.info("[Extraction] Applying {} character state changes", stateChanges.size());

        for (CharacterStateChange change : stateChanges) {
            if (change.getCharacterCurrentName() == null || change.getCharacterCurrentName().isBlank()) {
                log.warn("[Extraction] Skipping state change with empty current name");
                continue;
            }

            // Find the character by current name
            Optional<StanzaCharacter> characterOpt = stanza.findCharacterByName(change.getCharacterCurrentName());
            
            if (characterOpt.isEmpty()) {
                log.warn("[Extraction] Character '{}' not found for state change — skipping", 
                    change.getCharacterCurrentName());
                continue;
            }

            StanzaCharacter character = characterOpt.get();
            boolean updated = false;

            // Update name if provided (character was previously a placeholder)
            if (change.getCharacterNewName() != null && !change.getCharacterNewName().isBlank()) {
                String oldName = character.getName();
                character.setName(change.getCharacterNewName());
                log.info("[Extraction] Updated character name: '{}' -> '{}'", oldName, change.getCharacterNewName());
                updated = true;
            }

            // Update emotional state if provided
            if (change.getNewEmotionalState() != null && !change.getNewEmotionalState().isBlank()) {
                String oldState = character.getEmotionalState();
                character.setEmotionalState(change.getNewEmotionalState());
                log.info("[Extraction] Updated emotional state for '{}': '{}' -> '{}'", 
                    character.getName(), oldState, change.getNewEmotionalState());
                updated = true;
            }

            // Update relationship if provided
            if (change.getUpdatedRelationshipToUser() != null && !change.getUpdatedRelationshipToUser().isBlank()) {
                String oldRelationship = character.getRelationshipToUser();
                character.setRelationshipToUser(change.getUpdatedRelationshipToUser());
                log.info("[Extraction] Updated relationship for '{}': '{}' -> '{}'", 
                    character.getName(), oldRelationship, change.getUpdatedRelationshipToUser());
                updated = true;
            }

            if (!updated) {
                log.warn("[Extraction] State change for '{}' contained no actual updates", 
                    change.getCharacterCurrentName());
            }
        }
    }
}