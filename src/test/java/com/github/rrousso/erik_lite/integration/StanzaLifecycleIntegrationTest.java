package com.github.rrousso.erik_lite.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.orchestration.SessionFlowService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration test for the full stanza lifecycle.
 *
 * Boots the full Spring context with H2 database.
 * Only the LLMClientService is mocked (no real API calls).
 * Everything else — strategies, persistence, extraction, Flyway — runs for real.
 *
 * Tests the flows:
 * - VOID planning → START → narration → END
 * - VOID planning → START → narration → PAUSE → CONTINUE → END
 *
 * This catches wiring issues between PauseStanzaStrategy, ContinueStanzaStrategy,
 * SynopsisGeneratorService, and persistence that unit tests cannot detect.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("Stanza Lifecycle Integration Tests")
public class StanzaLifecycleIntegrationTest {

    @Autowired
    private SessionFlowService sessionFlowService;
    
    @Autowired
    private StanzaPersistenceService persistenceService;

    @MockitoBean
    private LLMClientService llmClient;

    private SessionState state;

    // === JSON fixtures ===

    /**
     * Minimal valid InitializedStanza JSON that the analytical LLM would return.
     * Matches erik-lite schema (no facts, no tensions, no knows).
     */
    private static final String INITIALIZATION_JSON = """
            {
              "worldIdentifier": "cinderella_test",
              "userCharacter": {
                "publicRole": "A mysterious young man at the ball",
                "privateBackstory": "Actually a servant who dreams of more",
                "publiclyVisibleTraits": ["tall", "well-dressed", "nervous"],
                "currentEmotionalState": "excited and anxious"
              },
              "explicitCharacters": [
                {
                  "name": "Fairy Godfather",
                  "canonRole": "Magical mentor",
                  "currentEmotionalState": "warmly amused",
                  "relationshipToUser": "Guardian and guide",
                  "presentInFirstScene": true,
                  "blueprint": {
                    "tier1_essentials": "Wise old mentor with dry humor and a silver cane",
                    "tier2_motivators": "Wants to see the user succeed; fears failing another charge",
                    "tier3_anchors": ["silver-tipped cane", "knowing smile", "faint shimmer around hands"]
                  }
                }
              ],
              "likelyCharacters": [],
              "backgroundCharacters": [
                {
                  "name": "Palace Guard",
                  "canonRole": "Minor NPC",
                  "threatOrAlly": "neutral",
                  "relevanceToStanza": "Blocks unauthorized entry"
                }
              ],
              "worldContext": {
                "timeContext": "Evening of the grand ball",
                "currentWorldState": "The kingdom is celebrating the prince's return",
                "tone": "romantic fantasy with humor",
                "supernaturalRules": ["Magic fades at midnight"],
                "relevantLocations": [
                  {
                    "name": "The Grand Ballroom",
                    "description": "A vast hall with crystal chandeliers and marble floors"
                  }
                ]
              }
            }
            """;

    /**
     * Minimal extraction result - empty changes (nothing happened yet).
     */
    private static final String EXTRACTION_JSON = """
            {
              "events": [],
              "characterAppearances": [],
              "emergentCharacters": [],
              "charactersStateChanges": []
            }
            """;

    /**
     * Quick synopsis for stanza end.
     */
    private static final String QUICK_SYNOPSIS_JSON = """
            {
              "quickSynopsis": "A young man attended the grand ball with help from his fairy godfather."
            }
            """;

    /**
     * Pause changes summary (what user discussed during pause).
     */
    private static final String PAUSE_CHANGES_JSON = """
            The user wants to make the prince more charming and add romantic tension.
            """;

    @BeforeEach
    void setUp() {
        state = new SessionState();

        // Simulate some planning conversation so void history isn't empty
        state.getVoidHistory().addUserMessage("I want a Cinderella story but I'm the male lead");
        state.getVoidHistory().addAssistantMessage("Great idea! A male Cinderella at the ball. Should we add a fairy godfather?");
        state.getVoidHistory().addUserMessage("Yes! Let's start");
    }

    // ========================================
    // FULL LIFECYCLE: START → NARRATE → END
    // ========================================

    @Test
    @DisplayName("Should start a stanza: VOID → START")
    void shouldStartStanza() throws Exception {
        // ANALYTICAL: flag detection (START), then initialization
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
                .thenReturn("START")
                .thenReturn(INITIALIZATION_JSON);

        // NARRATIVE: Erik confirmation, then opening narration
        when(llmClient.call(eq(ModelType.NARRATIVE), anyString(), anyString()))
                .thenReturn("Wonderful! Let me set the scene...")
                .thenReturn("The grand ballroom glittered with candles.");

        // --- ACT ---
        String startResult = sessionFlowService.handleUserInput("Yes! Let's start", state);

        // --- ASSERT ---
        assertNotNull(startResult, "Start result should not be null");
        assertTrue(state.isInStanzaMode(),
                "Should be in stanza mode after START. Result was: " + startResult);
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());
        assertNotNull(state.getActiveStanzaId(), "Should have a database stanza ID");

        // Verify stanza was persisted (use loadWithRelationships to avoid LazyInitializationException)
        Long stanzaId = state.getActiveStanzaId();
        Stanza dbStanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        assertEquals("active", dbStanza.getStatus());
        assertEquals("cinderella_test", dbStanza.getWorldIdentifier());
        assertTrue(dbStanza.getCharacters().size() >= 2, "Should have at least user + 1 NPC");
        assertFalse(dbStanza.getBeats().isEmpty(), "Should have at least 1 beat");
    }

    // ========================================
    // EDGE CASE: START WHILE ALREADY IN STANZA
    // ========================================

    @Test
    @DisplayName("Should give clear guidance when START detected but already in ACTIVE stanza")
    void shouldGiveClearMessageWhenStartDetectedButActive() throws Exception {
        state.enterStanzaMode();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
                .thenReturn("START");

        String result = sessionFlowService.handleUserInput("Let's start another stanza", state);

        assertNotNull(result);
        assertTrue(state.isInStanzaMode(), "Should remain in stanza mode");
        assertTrue(result.contains("already in a stanza"),
                "Expected clear guidance but got: " + result);
    }

    // ========================================
    // EDGE CASE: END WHILE IN VOID MODE
    // ========================================

    @Test
    @DisplayName("Should give clear guidance when END detected but no active stanza")
    void shouldGiveClearMessageWhenEndDetectedButNoStanza() throws Exception {
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
                .thenReturn("END");

        String result = sessionFlowService.handleUserInput("End the stanza", state);

        assertTrue(state.isInVoidMode(), "Should remain in void mode");
        assertEquals(StanzaStatus.NONE, state.getStanzaStatus());
        assertTrue(result.contains("Nothing to end"),
                "Expected clear guidance but got: " + result);
    }

    // ========================================
    // PAUSE/CONTINUE LIFECYCLE
    // ========================================

    @Test
    @DisplayName("Full lifecycle with pause: VOID → START → narration → PAUSE → CONTINUE → END")
    void testStanzaLifecycleWithPause() throws Exception {
        // --- SETUP: Mock all LLM responses ---
        
        // ANALYTICAL: Flags, initialization, extractions, synopsis
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
                .thenReturn("START")  // 1. Flag detection: start
                .thenReturn(INITIALIZATION_JSON)  // 2. Initialization
                .thenReturn("NONE")  // 3. Flag detection: first narration exchange
                .thenReturn(EXTRACTION_JSON)  // 4. Extraction after first exchange
                .thenReturn("PAUSE")  // 5. Flag detection: pause
                .thenReturn("NONE")  // 6. Flag detection: void mode exchange after pause
                .thenReturn("CONTINUE")  // 7. Flag detection: continue
                .thenReturn(PAUSE_CHANGES_JSON)  // 8. Pause changes summary
                .thenReturn("NONE")  // 9. Flag detection: narration after continue
                .thenReturn(EXTRACTION_JSON)  // 10. Extraction after continue
                .thenReturn("END")  // 11. Flag detection: end
                .thenReturn(EXTRACTION_JSON)  // 12. Final extraction
                .thenReturn(QUICK_SYNOPSIS_JSON);  // 13. Synopsis generation

        // NARRATIVE: Erik, narrations, reflection
        when(llmClient.call(eq(ModelType.NARRATIVE), anyString(), anyString()))
                .thenReturn("Wonderful! Let me set the scene...")  // 1. Erik confirmation
                .thenReturn("The grand ballroom glittered with candles.")  // 2. Opening narration
                .thenReturn("You approach the refreshment table.")  // 3. First action response
                .thenReturn("Sure, what would you like to adjust?")  // 4. Erik after pause
                .thenReturn("You notice the prince watching you.")  // 5. Continue narration
                .thenReturn("The clock strikes midnight. You flee.")  // 6. Closing narration
                .thenReturn("That was a wonderful story!");  // 7. Erik reflection

        // --- PHASE 1: START ---
        String startResult = sessionFlowService.handleUserInput("Yes! Let's start", state);
        
        assertNotNull(startResult, "Start result should not be null");
        assertTrue(state.isInStanzaMode(), "Should be in stanza mode after START");
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());
        assertNotNull(state.getActiveStanzaId(), "Should have a database stanza ID");
        
        Long stanzaId = state.getActiveStanzaId();
        Stanza dbStanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        assertEquals("active", dbStanza.getStatus());
        assertEquals("cinderella_test", dbStanza.getWorldIdentifier());

        // --- PHASE 2: FIRST NARRATION EXCHANGE ---
        String narration1 = sessionFlowService.handleUserInput("I walk to the refreshment table", state);
        
        assertNotNull(narration1, "First narration should not be null");
        assertTrue(state.isInStanzaMode(), "Should still be in stanza mode");
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());

        // --- PHASE 3: PAUSE ---
        String pauseResult = sessionFlowService.handleUserInput("((pause))", state);
        
        assertNotNull(pauseResult, "Pause result should not be null");
        assertTrue(state.isInVoidMode(), "Should be in void mode after pause");
        assertEquals(StanzaStatus.PAUSED, state.getStanzaStatus());
        assertTrue(pauseResult.contains("[Erik]"), "Should have Erik's response");
        
        // Verify database status
        dbStanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        assertEquals("paused", dbStanza.getStatus(), "Database should show paused status");

        // --- PHASE 4: VOID MODE EXCHANGE WHILE PAUSED ---
        String voidExchange = sessionFlowService.handleUserInput("Can we make the prince more charming?", state);
        
        assertNotNull(voidExchange, "Void exchange should not be null");
        assertTrue(state.isInVoidMode(), "Should remain in void mode");
        assertEquals(StanzaStatus.PAUSED, state.getStanzaStatus());
        assertTrue(voidExchange.contains("[Erik]"), "Should have Erik's response in void mode");

        // --- PHASE 5: CONTINUE ---
        String continueResult = sessionFlowService.handleUserInput("continue", state);
        
        assertNotNull(continueResult, "Continue result should not be null");
        assertTrue(state.isInStanzaMode(), "Should be back in stanza mode after continue");
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());
        assertTrue(continueResult.contains("[Narration]"), "Should have narrator's continuation");
        
        // Verify database status
        dbStanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        assertEquals("active", dbStanza.getStatus(), "Database should show active status");

        // --- PHASE 6: SECOND NARRATION EXCHANGE ---
        String narration2 = sessionFlowService.handleUserInput("I dance with the prince", state);
        
        assertNotNull(narration2, "Second narration should not be null");
        assertTrue(state.isInStanzaMode(), "Should still be in stanza mode");
        assertEquals(StanzaStatus.ACTIVE, state.getStanzaStatus());

        // --- PHASE 7: END ---
        String endResult = sessionFlowService.handleUserInput("((end))", state);
        
        assertNotNull(endResult, "End result should not be null");
        assertTrue(state.isInVoidMode(), "Should be in void mode after end");
        assertEquals(StanzaStatus.COMPLETED, state.getStanzaStatus());
        assertTrue(endResult.contains("[STANZA END]"), "Should have stanza end marker");
        assertTrue(endResult.contains("quick synopsis"), "Should have synopsis");
        assertTrue(endResult.contains("[Erik]"), "Should have Erik's reflection");
        
        // Verify final database status
        dbStanza = persistenceService.loadStanzaWithRelationships(stanzaId);
        assertEquals("completed", dbStanza.getStatus(), "Database should show completed status");
        assertNotNull(dbStanza.getQuickSynopsis(), "Should have quick synopsis in database");
        assertTrue(dbStanza.getCurrentExchange() > 0, "Should have tracked exchanges");
    }
}