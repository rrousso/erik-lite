package com.github.rrousso.erik_lite.services.orchestration;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.session.SynopsisGeneratorService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates CompletedStanza objects when a stanza ends or is abandoned.
 *
 * Centralizes:
 * - Generating a quick synopsis of the stanza
 * - Creating the CompletedStanza value object
 * - Clearing the stanza history
 *
 * Used by both EndStanzaStrategy and AbandonStanzaStrategy.
 */
@Service
public class StanzaCompletionService {

    private static final Logger log = LoggerFactory.getLogger(StanzaCompletionService.class);

    private final SynopsisGeneratorService synopsisGenerator;
    private final StanzaPersistenceService stanzaPersistenceService;

    public StanzaCompletionService(
            SynopsisGeneratorService synopsisGenerator,
            StanzaPersistenceService stanzaPersistenceService) {
        this.synopsisGenerator = synopsisGenerator;
        this.stanzaPersistenceService = stanzaPersistenceService;
    }

    /**
     * Create a CompletedStanza from the current session state.
     *
     * 1. Generates a quick synopsis from the stanza history
     * 2. Creates a CompletedStanza with the synopsis and initialized stanza
     * 3. Clears the stanza history (ready for next stanza)
     */
    public CompletedStanza createCompletedStanza(SessionState state) {
        String quickSynopsis = "";

        try {
            Long stanzaId = state.getActiveStanzaId();
            if (stanzaId != null) {
                Stanza stanza = stanzaPersistenceService.loadStanzaWithRelationships(stanzaId);
                if (stanza != null) {
                    quickSynopsis = synopsisGenerator.generateQuickSynopsis(state.getStanzaHistory(), stanza);
                } else {
                    log.warn("[StanzaCompletion] Could not load stanza {} for quick synopsis", stanzaId);
                }
            } else {
                log.warn("[StanzaCompletion] No active stanza ID for quick synopsis");
            }
        } catch (Exception e) {
            log.error("Failed to generate quick synopsis", e);
        }

        // Truncate if too long
        if (quickSynopsis.length() > 2000) {
            log.warn("[StanzaCompletion] Quick synopsis exceeded 2000 characters ({}), truncating", quickSynopsis.length());
            quickSynopsis = quickSynopsis.substring(0, 2000);
        }

        CompletedStanza completed = new CompletedStanza(
                quickSynopsis,
                state.getInitializedStanza());

        // Clear history to prepare for next stanza
        state.getStanzaHistory().clearHistory();

        return completed;
    }
}