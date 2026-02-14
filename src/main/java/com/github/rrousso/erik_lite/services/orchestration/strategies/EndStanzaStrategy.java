package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;
import com.github.rrousso.erik_lite.services.orchestration.StanzaCompletionService;
import com.github.rrousso.erik_lite.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the END_STANZA flag.
 *
 * 1. Get Narrator's closing narration
 * 2. Final extraction (captures closing events)
 * 3. Create CompletedStanza with quick synopsis
 * 4. Update database status to "completed"
 * 5. Get Erik's reflection response
 */
@Component
public class EndStanzaStrategy implements FlowStrategy {

    private static final Logger log = LoggerFactory.getLogger(EndStanzaStrategy.class);

    private final ConversationService conversationService;
    private final StanzaCompletionService completionService;
    private final StanzaPersistenceService persistenceService;
    private final StanzaExtractionService extractionService;

    public EndStanzaStrategy(
            ConversationService conversationService,
            StanzaCompletionService completionService,
            StanzaExtractionService extractionService,
            StanzaPersistenceService persistenceService) {
        this.conversationService = conversationService;
        this.completionService = completionService;
        this.persistenceService = persistenceService;
        this.extractionService = extractionService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        if (state.isInVoidMode()) {
            log.warn("Attempt to end stanza while in void mode");
            return "[System] No active stanza to end.\n";
        }

        log.info("Ending stanza");
        StringBuilder builder = new StringBuilder();

        try {
            // Closing narration
            String closure = conversationService.converseWithNarrator(state,
                userInput + " ((Bring the scene to a natural closing moment. NARRATION ONLY - no meta-commentary, no epilogue notes, no analysis. Just the final narrative moment.))");
            builder.append("\n[Narration - Closing] ");
            builder.append(closure);

            Long stanzaId = state.getActiveStanzaId();

            if (stanzaId != null) {
                try {
                    Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);
                    stanza.incrementExchange();

                    // Final extraction BEFORE synopsis (history still has messages)
                    ConversationHistory history = state.getStanzaHistory();
                    int exchangeNumber = stanza.getCurrentExchange();
                    extractionService.processExtraction(
                        stanza, history, exchangeNumber, false, true);

                    // Synopsis + completion (clears history as side effect)
                    state.enterVoidMode();
                    CompletedStanza completed = completionService.createCompletedStanza(state);
                    state.setCompletedStanza(completed);
                    state.setStanzaStatus(StanzaStatus.COMPLETED);

                    // Single database write
                    stanza.setStatus("completed");
                    stanza.setQuickSynopsis(completed.getQuickSynopsis());
                    persistenceService.save(stanza);

                    builder.append("\n\n[STANZA END]\n");
                    builder.append("\n[System] Here's the quick synopsis:\n");
                    builder.append(completed.getQuickSynopsis());
                    builder.append("\n");

                } catch (Exception e) {
                    log.warn("Failed to update completed stanza in database", e);
                }
            }

            // Erik's reflection
            try {
                String erikReflection = conversationService.converseWithErik(state,
                    "Stanza finished! What did you think about it?");
                builder.append("\n[Erik] ").append(erikReflection);
            } catch (Exception e) {
                log.warn("Failed to get Erik's reflection", e);
            }

        } catch (Exception e) {
            log.error("Error ending stanza", e);
            builder.append("\n[System] Error ending stanza: ").append(e.getMessage()).append("\n");
        }

        return builder.toString();
    }
}