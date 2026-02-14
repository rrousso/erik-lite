package com.github.rrousso.erik_lite.services.orchestration;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.domain.enums.Flag;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.llm.FlagDetectorService;
import com.github.rrousso.erik_lite.services.orchestration.strategies.FlowStrategyFactory;

/**
 * Main orchestrator for the Erik application flow.
 *
 * Routes user input to the appropriate strategy:
 * 1. Validate input
 * 2. Detect if input contains a flag (START, PAUSE, END, etc.)
 * 3. Get appropriate strategy from factory
 * 4. Execute strategy and return result
 */
@Service
public class SessionFlowService {

    private static final Logger log = LoggerFactory.getLogger(SessionFlowService.class);

    private final FlowStrategyFactory flowStrategyFactory;
    private final FlagDetectorService flagDetector;

    public SessionFlowService(
            FlowStrategyFactory flowStrategyFactory,
            FlagDetectorService flagDetector) {
        this.flowStrategyFactory = flowStrategyFactory;
        this.flagDetector = flagDetector;

        log.info("SessionFlowService initialized with Strategy pattern");
    }

    public String handleUserInput(String userInput, SessionState state) {
        Objects.requireNonNull(userInput, "userInput cannot be null");
        Objects.requireNonNull(state, "state cannot be null");

        if (userInput.isBlank()) {
            log.warn("Empty user input received");
            return "";
        }

        Flag flag = flagDetector.detect(userInput, state);

        if (flag != Flag.NONE) {
            log.debug("Flag detected: {} - routing to flag strategy", flag);
            return flowStrategyFactory.getStrategyForFlag(flag).execute(userInput, state);
        }

        log.debug("No flag detected - routing to mode-based strategy");
        return flowStrategyFactory.getStrategyForConversation(state).execute(userInput, state);
    }
}