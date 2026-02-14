package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.stanza.BeatTransitionService;

/**
 * Strategy for handling NEXT_BEAT flag.
 * Delegates all beat transition logic to BeatTransitionService.
 */
@Component
public class NextBeatStrategy implements FlowStrategy {

    private static final Logger log = LoggerFactory.getLogger(NextBeatStrategy.class);

    private final BeatTransitionService beatTransitionService;

    public NextBeatStrategy(BeatTransitionService beatTransitionService) {
        this.beatTransitionService = beatTransitionService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        if (!state.isInStanzaMode()) {
            log.warn("Attempt to create new beat while in void mode");
            return "[System] Beat transitions only work during active stanzas.\n";
        }

        Long stanzaId = state.getActiveStanzaId();
        if (stanzaId == null) {
            log.error("[NextBeat] No active stanza ID in state");
            return "[System] No active stanza found.\n";
        }

        try {
            return beatTransitionService.transitionToNextBeat(userInput, state);

        } catch (IllegalArgumentException e) {
            log.warn("[NextBeat] Invalid input format: {}", e.getMessage());
            return "[System] Invalid beat format. Use: ((next beat: context))\n";

        } catch (IllegalStateException e) {
            log.error("[NextBeat] State error: {}", e.getMessage());
            return "[System] Error: " + e.getMessage() + "\n";

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("[NextBeat] Database constraint violation", e);
            return "[System] Database error: Beat may already exist. Please report this issue.\n";

        } catch (Exception e) {
            log.error("[NextBeat] Failed to transition beats", e);
            return "[System] Error transitioning beats. Please try again.\n";
        }
    }
}