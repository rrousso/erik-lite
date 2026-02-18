package com.github.rrousso.erik_lite.services.orchestration.strategies;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.LoadedStanzaMemory;
import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.orchestration.ConversationService;
import com.github.rrousso.erik_lite.services.stanza.StanzaInitializationService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Strategy for handling the START_STANZA flag.
 * 
 * This strategy:
 * 1. Validates we're not already in a stanza
 * 2. Gets Erik's confirmation response
 * 3. Extracts stanza setup from planning conversation
 * 4. Persists the stanza to database
 * 5. Gets opening narration from the Narrator
 * 
 * Transitions the session from VOID mode to STANZA mode.
 */
@Component
public class StartStanzaStrategy implements FlowStrategy {
    
    private static final Logger log = LoggerFactory.getLogger(StartStanzaStrategy.class);
    
    private final ConversationService conversationService;
    private final StanzaInitializationService initializationService;
    private final StanzaPersistenceService persistenceService;
    private final PersonaService personaService;
    
    public StartStanzaStrategy(
            ConversationService conversationService,
            StanzaInitializationService initializationService,
            StanzaPersistenceService persistenceService,
            PersonaService personaService) {
        this.conversationService = conversationService;
        this.initializationService = initializationService;
        this.persistenceService = persistenceService;
        this.personaService = personaService;
    }

    @Override
    public String execute(String userInput, SessionState state) {
        if (state.isInStanzaMode()) {
            log.warn("Attempt to start stanza while already in stanza mode");
            return "[System] Already in stanza mode.\n";
        }
        
        if (state.getStanzaStatus() == StanzaStatus.COMPLETED) {
            log.info("Attempt to start stanza after completion");
            return "\n[System] You've already completed a stanza this session.\n"
                    + " [System] Take some time to reflect and process the experience. "
                    + "\n[System] Use 'exit' when you're ready to close the app.\n";
        }
        
        log.info("Starting new stanza");
        StringBuilder builder = new StringBuilder();

        try {
            state.setCompletedStanza(null);
            
            state.enterStanzaMode();
            state.setStanzaStatus(StanzaStatus.ACTIVE);
            
            String erikResponse = conversationService.converseWithErik(state, userInput);
            builder.append("\n[Erik] ").append(erikResponse);
            builder.append("\n\n");
            
            log.debug("Extracting stanza details from planning conversation...");

            // Load parent stanza entity if user loaded one via /load
            Stanza parentStanza = null;
            LoadedStanzaMemory loadedMemory = state.getLoadedStanzaMemory();
            if (loadedMemory != null && loadedMemory.hasSourceStanzaId()) {
                try {
                    parentStanza = persistenceService.loadStanzaWithRelationships(
                        loadedMemory.getSourceStanzaId());
                    log.info("Loaded parent stanza {} for continuation", loadedMemory.getSourceStanzaId());
                } catch (Exception e) {
                    log.warn("Failed to load parent stanza {} - proceeding without continuation context",
                        loadedMemory.getSourceStanzaId(), e);
                }
            }

            InitializedStanza initialized = initializationService.initializeFromPlanning(
                state.getVoidHistory(),
                parentStanza
            );
            
            state.setInitializedStanza(initialized);
            
            // Persist to database
            try {
                Persona persona = personaService.getCurrentPersona();
                
                Long parentId = (parentStanza != null) ? parentStanza.getId() : null;
                Stanza savedStanza = persistenceService.saveInitializedStanza(initialized, persona, parentId);
                
                // Set parent stanza lineage
                if (parentStanza != null) {
                    log.info("New stanza {} created as continuation of parent stanza {}", 
                        savedStanza.getId(), parentStanza.getId());
                }
                
                Long stanzaId = savedStanza.getId();
                if (stanzaId == null) {
                    log.error("Stanza was saved but ID is null - this shouldn't happen");
                } else {
                    state.setActiveStanzaId(stanzaId);
                    log.info("Stanza persisted to database with ID: {}", stanzaId);
                }
            } catch (Exception e) {
                log.error("Failed to persist stanza to database - continuing without persistence", e);
            }
            
            builder.append("[STANZA START]\n");
            
            String opening = conversationService.converseWithNarrator(state, "Begin the scene.");
            builder.append("\n[Opening Narration] ");
            builder.append(opening);
            
            // Clear loaded memory — it's been consumed by initialization
            state.setLoadedStanzaMemory(null);
            
            log.info("Stanza started successfully");
            return builder.toString();
            
        } catch (Exception e) {
            log.error("Failed to start stanza", e);
            
            state.enterVoidMode();
            state.setStanzaStatus(StanzaStatus.NONE);
            state.setActiveStanzaId(null);
            state.setInitializedStanza(null);
            
            return "[System] Failed to start stanza. Remaining in void mode.\n";
        }
    }
}