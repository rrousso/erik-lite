package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;
import com.github.rrousso.erik_lite.services.session.SynopsisGeneratorService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the CONTINUE_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're currently in void mode with a paused stanza
 * 2. Transitions from VOID mode back to STANZA mode
 * 3. Updates database status to "active"
 * 4. Gets Narrator's continuation with any changes requested during the pause
 */
@Component
public class ContinueStanzaStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(ContinueStanzaStrategy.class);

    private final ConversationService conversationService;
    private final SynopsisGeneratorService synopsisGenerator;
    private final StanzaPersistenceService persistenceService;

    public ContinueStanzaStrategy(
            ConversationService conversationService,
            SynopsisGeneratorService synopsisGenerator,
            StanzaPersistenceService persistenceService) {
        this.conversationService = conversationService;
        this.synopsisGenerator = synopsisGenerator;
        this.persistenceService = persistenceService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        if (state.isInStanzaMode()) {
            log.warn("Attempt to continue stanza while already in stanza mode");
            return "[System] Already in stanza mode.\n";
        }
        
        if (state.getInitializedStanza() == null) {
            log.warn("Attempt to continue stanza with no current stanza");
            return "[System] No stanza to continue. Use 'start stanza' to begin a new one.\n";
        }

        log.info("Continuing paused stanza");
        
        state.enterStanzaMode();
        state.setStanzaStatus(StanzaStatus.ACTIVE);
        
        Long stanzaId = state.getActiveStanzaId();
        if (stanzaId != null) {
            try {
                persistenceService.updateStatus(stanzaId, "active");
            } catch (Exception e) {
                log.warn("Failed to update stanza status in database", e);
            }
        }
        
        try {
            String pauseChanges = synopsisGenerator.generatePauseChanges(state.getVoidHistory());
            
            String continuation = conversationService.converseWithNarrator(state, 
                "Continue the scene implementing the following changes: " + pauseChanges);
            
            log.info("Stanza continued successfully");
            return "\n[Narration] " + continuation;
            
        } catch (Exception e) {
            log.error("Failed to continue stanza", e);
            
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.PAUSED);
            
            return "[System] Failed to continue stanza.\n";
        }
    }
}