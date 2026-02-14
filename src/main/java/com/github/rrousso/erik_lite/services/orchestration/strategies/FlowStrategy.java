package com.github.rrousso.erik_lite.services.orchestration.strategies;

import com.github.rrousso.erik_lite.domain.models.SessionState;

/**
 * Strategy interface for handling different flow operations in SessionFlowService.
 * 
 * Each implementation handles a specific operation (start stanza, pause, etc.)
 * or mode (void mode, stanza mode).
 */
public interface FlowStrategy {
    
    /**
     * Execute this strategy's operation.
     * 
     * @param userInput The user's input text
     * @param state The current session state
     * @return The message to display to the user
     */
    String execute(String userInput, SessionState state);
}