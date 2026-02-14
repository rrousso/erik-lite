package com.github.rrousso.erik_lite.services.stanza;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.exceptions.llm.LLMException;
import com.github.rrousso.erik_lite.exceptions.stanza.StanzaException;
import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;

/**
 * Handles beat transitions in stanzas.
 *
 * 3-step process:
 * 1. Process any regular text (close current beat with final narration)
 * 2. Close beat, generate summary, start new beat
 * 3. Generate opening narration for new beat
 */
@Service
public class BeatTransitionService {

    private static final Logger log = LoggerFactory.getLogger(BeatTransitionService.class);

    private final ConversationService conversationService;
    private final StanzaPersistenceService persistenceService;
    private final BeatSummaryService beatSummaryService;
    private final StanzaExtractionService extractionService;

    private static final Pattern BEAT_WITH_TEXT = Pattern.compile(
        "^(.*?)\\(\\((?:next|new|start)\\s+beat:\\s*(.+?)\\)\\)\\s*$",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern BEAT_ONLY = Pattern.compile(
        "^\\(\\((?:next|new|start)\\s+beat:\\s*(.+?)\\)\\)\\s*$",
        Pattern.CASE_INSENSITIVE);

    public BeatTransitionService(
            ConversationService conversationService,
            StanzaPersistenceService persistenceService,
            BeatSummaryService beatSummaryService,
            StanzaExtractionService extractionService) {
        this.conversationService = conversationService;
        this.persistenceService = persistenceService;
        this.beatSummaryService = beatSummaryService;
        this.extractionService = extractionService;
    }

    @Transactional
    public String transitionToNextBeat(String userInput, SessionState state) {
        Long stanzaId = state.getActiveStanzaId();

        if (stanzaId == null) {
            throw new StanzaException("No stanza ID found in session state.");
        }

        Stanza stanza = persistenceService.loadStanzaWithRelationships(stanzaId);

        if (stanza == null) {
            throw new StanzaException("No stanza found for ID: " + stanzaId);
        }

        BeatTransition transition = parseInput(userInput);

        StringBuilder output = new StringBuilder();

        // STEP 1: Process regular text
        if (transition.hasRegularText()) {
            String closingNarration = processClosingNarration(stanza, state, transition.getRegularText());
            output.append("\n[Narration] ").append(closingNarration).append("\n\n");
        }

        // STEP 2: Close and start
        BeatTransitionResult result = closeAndStartBeat(stanza, state, transition.getTransitionContext());

        output.append("[System] Beat ")
              .append(result.oldBeatNumber)
              .append(" ended. Beat ")
              .append(result.newBeatNumber)
              .append(" started.\n\n");

        // STEP 3: Generate opening narration
        String openingNarration = generateOpeningNarration(stanza, state, transition.getTransitionContext());
        output.append("[Narration] ").append(openingNarration).append("\n");

        return output.toString();
    }

    private String processClosingNarration(Stanza stanza, SessionState state, String regularText) {
        log.debug("[BeatTransition] Processing closing narration");

        String narration;
        try {
            narration = conversationService.converseWithNarrator(state, regularText);
        } catch (Exception e) {
            throw new LLMException("Failed to generate closing narration", e);
        }

        stanza.incrementExchange();
        ConversationHistory history = state.getStanzaHistory();
        boolean extracted = extractionService.forceExtraction(stanza, history);

        if (!extracted) {
            log.warn("[BeatTransition] Failed to extract state at beat boundary");
        }
        persistenceService.save(stanza);

        return narration;
    }

    private BeatTransitionResult closeAndStartBeat(Stanza stanza, SessionState state, String transitionContext) {
        log.debug("[BeatTransition] Closing current beat and starting new beat");

        Beat closedBeat = stanza.closeCurrentBeat();
        if (closedBeat == null) {
            throw new StanzaException("No active beat found to close");
        }

        int oldBeatNum = closedBeat.getBeatNumber();

        log.debug("[BeatTransition] Beat {} closed at exchange {}",
            oldBeatNum, closedBeat.getEndExchange());

        String summary = beatSummaryService.generateBeatSummary(
            closedBeat, stanza, state.getStanzaHistory());

        stanza.finalizeBeatAndStartNew(closedBeat, summary, transitionContext);

        state.getStanzaHistory().clearHistory();

        persistenceService.save(stanza);

        Beat newBeat = stanza.getCurrentBeat();
        int newBeatNum = newBeat != null ? newBeat.getBeatNumber() : oldBeatNum + 1;

        log.info("[BeatTransition] Beat transition complete: {} -> {}", oldBeatNum, newBeatNum);

        return new BeatTransitionResult(oldBeatNum, newBeatNum);
    }

    private String generateOpeningNarration(Stanza stanza, SessionState state, String transitionContext) {
        log.debug("[BeatTransition] Generating opening narration");

        String narration;
        try {
            narration = conversationService.converseWithNarrator(
                state, "((The scene transitions: " + transitionContext + "))");
        } catch (Exception e) {
            throw new LLMException("Failed to generate opening narration", e);
        }

        ConversationHistory history = state.getStanzaHistory();
        boolean extracted = extractionService.forceExtraction(stanza, history);

        if (!extracted) {
            log.warn("[BeatTransition] Failed to extract state at beat boundary");
        }
        persistenceService.save(stanza);

        return narration;
    }

    private BeatTransition parseInput(String input) {
        if (input == null || input.trim().isEmpty()) {
            return null;
        }

        Matcher withText = BEAT_WITH_TEXT.matcher(input.trim());
        if (withText.matches()) {
            String regularText = withText.group(1).trim();
            String transitionContext = withText.group(2).trim();
            if (!transitionContext.isEmpty()) {
                return new BeatTransition(regularText, transitionContext);
            }
        }

        Matcher beatOnly = BEAT_ONLY.matcher(input.trim());
        if (beatOnly.matches()) {
            String transitionContext = beatOnly.group(1).trim();
            if (!transitionContext.isEmpty()) {
                return new BeatTransition("", transitionContext);
            }
        }

        return null;
    }

    // ========== INNER CLASSES ==========

    private static class BeatTransition {
        private final String regularText;
        private final String transitionContext;

        BeatTransition(String regularText, String transitionContext) {
            this.regularText = regularText != null ? regularText : "";
            this.transitionContext = transitionContext;
        }

        boolean hasRegularText() { return !regularText.isEmpty(); }
        String getRegularText() { return regularText; }
        String getTransitionContext() { return transitionContext; }
    }

    private record BeatTransitionResult(int oldBeatNumber, int newBeatNumber) {}
}