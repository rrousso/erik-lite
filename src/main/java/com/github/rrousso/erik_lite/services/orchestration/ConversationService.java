package com.github.rrousso.erik_lite.services.orchestration;

import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionContext;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.chat.ChatPersistenceService;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;
import com.github.rrousso.erik_lite.services.session.SessionAssemblerService;
import com.github.rrousso.erik_lite.services.session.SynopsisGeneratorService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Unified service for handling LLM conversations in both VOID and STANZA modes.
 *
 * Encapsulates the common pattern:
 * 1. Assemble context (based on mode)
 * 2. Build system prompt (based on mode)
 * 3. Call LLM
 * 4. Update conversation history (based on mode)
 * 5. Generate synopsis (only for STANZA mode)
 *
 * Centralizes this logic to eliminate duplication across strategies.
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    public enum ConversationMode {
        VOID,
        STANZA
    }

    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final SessionAssemblerService sessionAssembler;
    private final SynopsisGeneratorService synopsisGenerator;
    private final StanzaPersistenceService persistenceService;
    private final ChatPersistenceService chatPersistence;

    public ConversationService(
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            SessionAssemblerService sessionAssembler,
            SynopsisGeneratorService synopsisGenerator,
            StanzaPersistenceService persistenceService,
            ChatPersistenceService chatPersistence) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.sessionAssembler = sessionAssembler;
        this.synopsisGenerator = synopsisGenerator;
        this.persistenceService = persistenceService;
        this.chatPersistence = chatPersistence;
    }

    /**
     * Conduct a conversation with the LLM in the specified mode.
     */
    public String converse(ConversationMode mode, SessionState state, String userInput) throws Exception {
        log.debug("Starting conversation in {} mode", mode);

        // 1. Assemble context based on mode
        SessionContext context = (mode == ConversationMode.VOID)
                ? sessionAssembler.assembleForVoid(state)
                : sessionAssembler.assembleForStanza(state);

        // 2. Build system prompt based on mode
        String systemPrompt = (mode == ConversationMode.VOID)
                ? promptBuilder.buildVoidPromptFromContext(context)
                : promptBuilder.buildStanzaPromptFromContext(context);

        // 3. Call LLM
        String response = llmClient.call(ModelType.NARRATIVE, systemPrompt, userInput);

        // 4. Get the appropriate history based on mode
        ConversationHistory history = (mode == ConversationMode.VOID)
                ? state.getVoidHistory()
                : state.getStanzaHistory();

        // 5. Update conversation history (in-memory)
        history.addUserMessage(userInput);
        history.addAssistantMessage(response);

        // 5b. Persist messages to database (dual-write, fail-safe)
        if (state.getChatId() != null) {
            try {
                String modeStr = (mode == ConversationMode.VOID) ? "VOID" : "STANZA";
                Integer exchangeNum = (mode == ConversationMode.STANZA && state.getActiveStanzaId() != null)
                        ? state.getStanzaHistory().getCurrentHistorySize() / 2
                        : null;
                chatPersistence.saveMessage(state.getChatId(), modeStr, "user", userInput, exchangeNum);
                chatPersistence.saveMessage(state.getChatId(), modeStr, "assistant", response, exchangeNum);
            } catch (Exception e) {
                log.warn("[Chat] Failed to persist messages to database", e);
            }
        }

        // 6. Generate synopsis (only for STANZA mode)
        if (mode == ConversationMode.STANZA) {
            try {
                if (synopsisGenerator.shouldGenerateSynopsis(state.getStanzaHistory())) {
                    Long stanzaId = state.getActiveStanzaId();
                    if (stanzaId != null) {
                        Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
                        if (stanza != null) {
                            synopsisGenerator.generateSynopsis(state.getStanzaHistory(), stanza);
                        } else {
                            log.warn("Could not load stanza {} for synopsis generation", stanzaId);
                        }
                    } else {
                        log.debug("No active stanza ID, skipping synopsis generation");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to generate synopsis", e);
            }
        }

        log.debug("Conversation completed successfully in {} mode", mode);
        return response;
    }

    /**
     * Convenience method for VOID mode conversations (talking to Erik).
     */
    public String converseWithErik(SessionState state, String userInput) throws Exception {
        return converse(ConversationMode.VOID, state, userInput);
    }

    /**
     * Convenience method for STANZA mode conversations (talking to Narrator).
     */
    public String converseWithNarrator(SessionState state, String userInput) throws Exception {
        return converse(ConversationMode.STANZA, state, userInput);
    }
}