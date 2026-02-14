package com.github.rrousso.erik_lite.services.stanza;

import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.PromptLoaderService;
import com.github.rrousso.erik_lite.util.JsonCleanupUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Initializes a stanza from the planning conversation.
 *
 * One-time-per-stanza call that:
 * 1. Takes the planning conversation with Erik
 * 2. Calls the analytical LLM with the initialization prompt
 * 3. Parses the JSON response into InitializedStanza
 * 4. Returns the fully populated stanza state
 */
@Service
public class StanzaInitializationService {

    private static final Logger log = LoggerFactory.getLogger(StanzaInitializationService.class);
    private static final String DEBUG_FILE = "user_data/initialization_result.txt";

    private final LLMClientService llmClient;
    private final PromptLoaderService promptLoader;
    private final PersonaService configService;

    public StanzaInitializationService(
            LLMClientService llmClient,
            PromptLoaderService promptLoader,
            PersonaService configService) {
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
        this.configService = configService;
        log.info("StanzaInitializationService initialized");
    }

    /**
     * Initialize a stanza from the planning conversation.
     *
     * @param voidHistory  The conversation history from planning with Erik
     * @param loadedStanza Optional stanza loaded via /load command (for continuation)
     * @return InitializedStanza with full character roster and world context
     * @throws Exception if LLM call or parsing fails
     */
    public InitializedStanza initializeFromPlanning(ConversationHistory voidHistory, Stanza loadedStanza) throws Exception {
        log.info("[Initialization] Starting stanza initialization from planning conversation");

        String planningContext = buildPlanningContext(voidHistory, loadedStanza);
        String userPersona = configService.getUserPersona();
        String initPrompt = promptLoader.load("analytical/initialization_prompt.txt");
        String fullPrompt = buildFullPrompt(initPrompt, userPersona, planningContext);

        log.info("[Initialization] Calling analytical model for initialization...");
        log.debug("[Initialization] Prompt length: {} chars", fullPrompt.length());

        String response = llmClient.call(
                ModelType.ANALYTICAL,
                "You are a stanza initialization architect. Output ONLY valid JSON.",
                fullPrompt);

        log.info("[Initialization] Received response ({} chars)", response.length());

        InitializedStanza stanza = JsonCleanupUtil.parseJson(response, InitializedStanza.class);

        String cleanedJson = JsonCleanupUtil.cleanJsonResponse(response);
        saveToDebugFile(cleanedJson);

        log.info("[Initialization] Successfully parsed initialization:");
        log.info("  - World: {}", stanza.getWorldIdentifier());
        log.info("  - Explicit characters: {}", stanza.getExplicitCharacters().size());
        log.info("  - Likely characters: {}", stanza.getLikelyCharacters().size());
        log.info("  - Background characters: {}", stanza.getBackgroundCharacters().size()); 

        return stanza;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String buildPlanningContext(ConversationHistory history, Stanza loadedStanza) {
        StringBuilder sb = new StringBuilder();

        if (loadedStanza != null) {
            sb.append("=== LOADED STANZA CONTEXT (for continuation) ===\n\n");
            sb.append("CRITICAL INSTRUCTION:\n");
            sb.append("This is a CONTINUATION of an existing stanza.\n");
            sb.append("The character data and world context below should be PRESERVED.\n");
            sb.append("Only modify what the user explicitly requests in the planning conversation.\n");
            sb.append("Do NOT create new versions of existing characters - use their existing data.\n\n");
            sb.append(loadedStanza.toNarratorContext());

            String synopsis = loadedStanza.getQuickSynopsis();
            if (synopsis != null && !synopsis.isEmpty()) {
                sb.append("\n=== WHAT HAPPENED PREVIOUSLY ===\n\n");
                sb.append(synopsis).append("\n\n");
            }

            sb.append("=== END LOADED STANZA CONTEXT ===\n\n");
        }

        sb.append("=== PLANNING CONVERSATION ===\n\n");

        for (ConversationHistory.Message msg : history.getAllMessages()) {
            String role = "user".equals(msg.getRole()) ? "USER" : "ERIK";
            sb.append(role).append(": ").append(msg.getContent()).append("\n\n");
        }

        return sb.toString();
    }

    private String buildFullPrompt(String initPrompt, String userPersona, String planningContext) {
        return initPrompt + "\n\n" +
                "---\n\n" +
                "## ACTUAL INPUT FOR THIS STANZA\n\n" +
                "**User Persona:**\n" +
                userPersona + "\n\n" +
                "**Planning Conversation:**\n" +
                planningContext + "\n\n" +
                "Now output the initialization JSON:";
    }

    private void saveToDebugFile(String json) {
        try {
            Path filePath = Paths.get(DEBUG_FILE);
            Files.createDirectories(filePath.getParent());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String output = "// Initialization Result - " + timestamp + "\n" +
                    "// This file is for debugging - inspect the parsed JSON\n\n" +
                    json;

            Files.writeString(filePath, output,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("[Initialization] Saved result to {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("[Initialization] Failed to save debug file: {}", e.getMessage());
        }
    }

    /**
     * Validate an InitializedStanza has minimum required data.
     */
    public boolean isValid(InitializedStanza stanza) {
        if (stanza == null) return false;
        if (stanza.getUserCharacter() == null) return false;
        return stanza.getWorldIdentifier() != null && !stanza.getWorldIdentifier().isEmpty();
    }
}