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
 * Tests the flow: VOID planning → START → narration → END
 *
 * This would have caught the post-migration location bug and any
 * wiring issues between strategies and persistence.
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
}