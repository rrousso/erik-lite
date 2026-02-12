package com.github.rrousso.erik_lite.services.session;

import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionContext;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Assembles SessionContext snapshots from SessionState and other sources.
 *
 * Answers: "Who is Erik right now, and what does he know?"
 *
 * Does NOT control flow, render prompts, or talk to the LLM.
 * Only assembles truth into a structured snapshot.
 *
 * Loads stanza data from database when activeStanzaId is present,
 * falling back to InitializedStanza for backward compatibility.
 */
@Service
public class SessionAssemblerService {

    private static final Logger log = LoggerFactory.getLogger(SessionAssemblerService.class);

    private final PersonaService configService;
    private final StanzaPersistenceService persistenceService;

    public SessionAssemblerService(PersonaService configService, StanzaPersistenceService persistenceService) {
        this.configService = configService;
        this.persistenceService = persistenceService;
    }

    /**
     * Assemble context for VOID mode (talking to Erik).
     *
     * Uses void history for recent exchanges.
     * Uses stanza history for synopsis (carries over from narration).
     * May include paused stanza setup, completed stanza for reflection,
     * and loaded stanza memory from /load command.
     */
    public SessionContext assembleForVoid(SessionState state) {
        log.debug("Assembling context for VOID mode, status: {}", state.getStanzaStatus());

        ConversationHistory voidHistory = state.getVoidHistory();
        ConversationHistory stanzaHistory = state.getStanzaHistory();

        SessionContext context = SessionContext.builder()
                .userPersona(configService.getUserPersona())
                .mode(SessionState.Mode.VOID)
                .stanzaStatus(state.getStanzaStatus())
                .initializedStanza(state.getInitializedStanza())
                .synopsis(stanzaHistory.getSynopsis())
                .recentExchanges(voidHistory.getRecentExchangesForSystemPrompt())
                .completedStanza(state.getCompletedStanza())
                .loadedStanzaMemory(state.getLoadedStanzaMemory())
                .build();

        log.debug("Assembled VOID context - hasSynopsis: {}, hasRecentExchanges: {}, hasStanzaSetup: {}, hasCompletedStanza: {}, hasLoadedMemory: {}",
                context.hasSynopsis(),
                context.hasRecentExchanges(),
                context.hasInitializedStanza(),
                context.hasCompletedStanza(),
                context.hasLoadedStanzaMemory());

        return context;
    }

    /**
     * Assemble context for STANZA mode (narrator).
     *
     * Uses stanza history for synopsis and recent exchanges.
     * Tries to load narrator context from DB first, falls back to InitializedStanza.
     */
    public SessionContext assembleForStanza(SessionState state) {
        log.debug("Assembling context for STANZA mode");

        ConversationHistory stanzaHistory = state.getStanzaHistory();

        // Try to load narrator context from database first
        String narratorContext = null;
        Long stanzaId = state.getActiveStanzaId();

        if (state.hasActiveStanza() && stanzaId != null) {
            narratorContext = loadNarratorContextFromDB(stanzaId);
        }

        // Build context — use DB context if available, otherwise fall back to InitializedStanza
        SessionContext.Builder builder = SessionContext.builder()
                .userPersona(configService.getUserPersona())
                .mode(SessionState.Mode.STANZA)
                .stanzaStatus(state.getStanzaStatus())
                .synopsis(stanzaHistory.getSynopsis())
                .recentExchanges(stanzaHistory.getRecentExchangesForSystemPrompt());

        if (narratorContext != null) {
            builder.narratorContextFromDB(narratorContext);
            log.debug("Assembled STANZA context from DATABASE");
        } else if (state.getInitializedStanza() != null) {
            builder.initializedStanza(state.getInitializedStanza());
            log.debug("Assembled STANZA context from InitializedStanza (fallback)");
        } else {
            throw new IllegalStateException("Cannot assemble stanza context: no DB record or InitializedStanza");
        }

        SessionContext context = builder.build();

        log.debug("Assembled STANZA context - hasSynopsis: {}, hasRecentExchanges: {}, hasNarratorContextFromDB: {}",
                context.hasSynopsis(),
                context.hasRecentExchanges(),
                context.hasNarratorContextFromDB());

        return context;
    }

    /**
     * Load narrator context string from the database.
     * Returns null if loading fails (allows fallback to InitializedStanza).
     */
    private String loadNarratorContextFromDB(Long stanzaId) {
        try {
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
            String context = stanza.toNarratorContext();
            log.debug("Loaded narrator context from DB for stanza ID: {}", stanzaId);
            return context;
        } catch (Exception e) {
            log.warn("Failed to load narrator context from DB for stanza ID: {} - will use fallback", stanzaId, e);
            return null;
        }
    }
}