package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;
import com.github.rrousso.erik_lite.services.stanza.EventCompressionService;
import com.github.rrousso.erik_lite.services.stanza.StanzaExtractionService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling narration in STANZA mode (talking to the Narrator).
 *
 * After each exchange:
 * 1. Increment exchange counter
 * 2. Run extraction (events + character appearances) based on config frequency
 * 3. Compress old events if threshold reached
 * 4. Save all changes in one transaction
 */
@Component
public class StanzaModeStrategy implements FlowStrategy {

    private static final Logger log = LoggerFactory.getLogger(StanzaModeStrategy.class);

    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;
    private final StanzaExtractionService extractionService;
    private final EventCompressionService compressionService;

    public StanzaModeStrategy(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            StanzaExtractionService extractionService,
            EventCompressionService compressionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.extractionService = extractionService;
        this.compressionService = compressionService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        try {
            String narration = conversationService.converseWithNarrator(state, userInput);

            updateStanzaState(state, userInput, narration);

            return "\n[Narration] " + narration;

        } catch (Exception e) {
            log.error("Error in stanza mode", e);
            return "\n[System] An error occurred. Please try again.\n";
        }
    }

    private void updateStanzaState(SessionState state, String userInput, String narration) {
        Long stanzaId = state.getActiveStanzaId();

        if (stanzaId == null) {
            return;
        }

        try {
            Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);

            stanza.incrementExchange();
            int exchangeNumber = stanza.getCurrentExchange();

            // Process extraction — service decides whether to extract based on config
            ConversationHistory history = state.getStanzaHistory();
            boolean isFirstExchange = (exchangeNumber == 1);
            boolean isFinalExchange = false;

            boolean extracted = extractionService.processExtraction(
                stanza, history, exchangeNumber, isFirstExchange, isFinalExchange);

            if (extracted) {
                log.debug("[StanzaModeStrategy] Extraction completed for exchange {}", exchangeNumber);
            }

            // Check if we should compress events
            if (compressionService.shouldCompress(exchangeNumber)) {
                log.debug("[StanzaModeStrategy] Compressing events (exchange {})", exchangeNumber);
                int compressed = compressionService.compressEvents(stanza);
                if (compressed > 0) {
                    log.info("[StanzaModeStrategy] Compressed {} events", compressed);
                }
            }

            persistenceService.save(stanza);

        } catch (Exception e) {
            log.warn("Failed to update stanza state in database", e);
        }
    }
}