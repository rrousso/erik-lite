package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;

/**
 * Strategy for handling regular conversation in VOID mode (talking to Erik).
 * 
 * This is the default mode when no special flag is detected.
 * Erik responds to the user's input using the void mode prompt.
 */
@Component
public class VoidModeStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(VoidModeStrategy.class);
    
    private final ConversationService conversationService;
    
    public VoidModeStrategy(ConversationService conversationService) {
        this.conversationService = conversationService;
    }
    
    @Override
    public String execute(String userInput, SessionState state) {
        try {
            String response = conversationService.converseWithErik(state, userInput);
            return "\n[Erik] " + response;
            
        } catch (Exception e) {
            log.error("Error in void mode", e);
            return "\n[System] An error occurred. Please try again.\n";
        }
    }
}