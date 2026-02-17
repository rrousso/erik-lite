package com.github.rrousso.erik_lite.services.llm;

import com.github.rrousso.erik_lite.domain.enums.Flag;
import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Detects system flags from user input using a lightweight analytical model.
 *
 * This pre-filter determines if the user is issuing a command before calling
 * the main narrative models. Uses conversation context (Erik's last message)
 * to distinguish between:
 * - Descriptive "start": "I want to start at the dance scene"
 * - Command "start": "yeah" after Erik asks "Ready to begin?"
 */
@Service
public class FlagDetectorService {

    private static final Logger log = LoggerFactory.getLogger(FlagDetectorService.class);

    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;

    public FlagDetectorService(LLMClientService llmClient, SystemPromptBuilderService promptBuilder) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        log.info("FlagDetectorService initialized");
    }

    /**
     * Detect flag from user input with conversation context.
     */
    public Flag detect(String userInput, SessionState state) {
        Objects.requireNonNull(userInput, "userInput cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        if (userInput.isBlank()) {
            log.warn("Empty user input provided to flag detector");
            return Flag.NONE;
        }

        try {
            StanzaStatus currentStatus = state.getStanzaStatus();
            String conversationContext = buildConversationContext(state);
            String prompt = buildFlagDetectionPrompt(userInput, currentStatus, conversationContext);

            String response = llmClient.call(ModelType.ANALYTICAL, "", prompt);
            Flag flag = parseResponse(response.trim());

            log.debug("Flag detection - Input: \"{}\", Status: {}, Context: \"{}\", Response: \"{}\", Flag: {}",
                    userInput, currentStatus, conversationContext, response.trim(), flag);

            return flag;
        } catch (Exception e) {
            log.error("Error detecting flag from input: \"{}\"", userInput, e);
            return Flag.NONE;
        }
    }

    /**
     * Build conversation context from SessionState.
     *
     * Only extracts context for START detection in VOID mode.
     * In STANZA mode, PAUSE/END/ABANDON don't need conversational cues.
     */
    private String buildConversationContext(SessionState state) {
        if (!state.isInVoidMode()) {
            return "";
        }

        if (state.getStanzaStatus() != StanzaStatus.NONE && state.getStanzaStatus() != StanzaStatus.ABANDONED) {
            return "";
        }

        ConversationHistory voidHistory = state.getVoidHistory();
        List<ConversationHistory.Message> messages = voidHistory.getAllMessages();

        if (messages.isEmpty()) {
            return "";
        }

        // Find the last assistant (Erik) message
        for (int i = messages.size() - 1; i >= 0; i--) {
            ConversationHistory.Message msg = messages.get(i);
            if ("assistant".equals(msg.getRole())) {
                return msg.getContent();
            }
        }

        return "";
    }

    /**
     * Build the prompt for flag detection with conversation context.
     */
    private String buildFlagDetectionPrompt(String userInput, StanzaStatus currentStatus, String conversationContext) {
        String template = promptBuilder.buildFlagDetectionPrompt();
        String availableFlags = getAvailableFlags(currentStatus);

        return template
                .replace("{STATUS}", currentStatus.name())
                .replace("{AVAILABLE_FLAGS}", availableFlags)
                .replace("{USER_INPUT}", userInput)
                .replace("{CONVERSATION_CONTEXT}", conversationContext != null ? conversationContext : "");
    }

    /**
     * Get available flags based on current status.
     */
    private String getAvailableFlags(StanzaStatus currentStatus) {
        return switch (currentStatus) {
            case NONE -> "START";
            case ACTIVE -> "PAUSE, END, ABANDON, NEXT_BEAT";
            case PAUSED -> "CONTINUE";
            case ABANDONED -> "START";
            case COMPLETED -> "NONE";
        };
    }

    private Flag parseResponse(String response) {
        String cleanResponse = response.toUpperCase().trim();

        // Check NEXT_BEAT first (most specific, avoids partial match on "END")
        if (cleanResponse.contains("NEXT_BEAT") || cleanResponse.contains("NEXT BEAT")) {
            return Flag.NEXT_BEAT;
        } else if (cleanResponse.contains("START")) {
            return Flag.START_STANZA;
        } else if (cleanResponse.contains("PAUSE")) {
            return Flag.PAUSE_STANZA;
        } else if (cleanResponse.contains("CONTINUE")) {
            return Flag.CONTINUE_STANZA;
        } else if (cleanResponse.contains("END")) {
            return Flag.END_STANZA;
        } else if (cleanResponse.contains("ABANDON")) {
            return Flag.ABANDON_STANZA;
        }
        return Flag.NONE;
    }

    /**
     * Check if a flag is valid for the given status.
     * Public so SessionFlowService can validate after detection.
     */
    public boolean isValidFlagForStatus(Flag flag, StanzaStatus status) {
        return switch (status) {
            case NONE -> flag == Flag.START_STANZA || flag == Flag.NONE;
            case ACTIVE -> flag == Flag.PAUSE_STANZA || flag == Flag.END_STANZA ||
                           flag == Flag.ABANDON_STANZA || flag == Flag.NEXT_BEAT || flag == Flag.NONE;
            case PAUSED -> flag == Flag.CONTINUE_STANZA || flag == Flag.NONE;
            case ABANDONED -> flag == Flag.START_STANZA || flag == Flag.NONE;
            case COMPLETED -> flag == Flag.NONE;
        };
    }
}