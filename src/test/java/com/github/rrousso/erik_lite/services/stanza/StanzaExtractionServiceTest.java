package com.github.rrousso.erik_lite.services.stanza;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_lite.config.ExtractionConfig;
import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;

/**
 * Unit tests for StanzaExtractionService (erik-lite simplified).
 *
 * Tests:
 * - Config-based extraction skipping
 * - Successful extraction with events, character appearances, emergent characters
 * - Partial results (some categories empty)
 * - Error handling (LLM failure, JSON parse failure)
 * - Force extraction bypass
 *
 * Dropped from erik-core tests: factDiscoveries, secretRevelations, tensionChanges,
 * blueprintUpdates, ExtractionPromptBuilder mock, ExtractionApplierRegistry mock.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StanzaExtractionService Tests (erik-lite)")
public class StanzaExtractionServiceTest {

    @Mock
    private LLMClientService llmClient;

    @Mock
    private ExtractionConfig extractionConfig;

    @Mock
    private SystemPromptBuilderService promptBuilder;

    private StanzaExtractionService service;
    private Stanza mockStanza;
    private ConversationHistory mockHistory;

    @BeforeEach
    void setUp() {
        service = new StanzaExtractionService(llmClient, extractionConfig, promptBuilder);

        // Set up a basic stanza with characters and events lists
        mockStanza = new Stanza();
        mockStanza.setId(1L);
        mockStanza.setCurrentExchange(5);
        mockStanza.setCurrentBeat(1);
        mockStanza.setCharacters(new ArrayList<>());
        mockStanza.setEvents(new ArrayList<>());

        // Add a test character
        StanzaCharacter testChar = new StanzaCharacter();
        testChar.setName("Scott McCall");
        testChar.setPresenceStatus("present");
        testChar.setUser(false);
        testChar.setStanza(mockStanza);
        mockStanza.getCharacters().add(testChar);

        // Set up history
        mockHistory = new ConversationHistory();
        mockHistory.addUserMessage("I walk into the room");
        mockHistory.addAssistantMessage("The room is dark and cold.");

        // Default mock for template
        lenient().when(promptBuilder.buildExtractionPrompt()).thenReturn(
            "CHARACTERS:\n{characters}\n\nRECENT EVENTS:\n{recent_events}\n\nCONVERSATION:\n{conversation_context}");
        lenient().when(extractionConfig.getFrequency()).thenReturn(1);
    }

    // ========================================
    // CONFIG-BASED SKIPPING TESTS
    // ========================================

    @Test
    @DisplayName("Should skip extraction when config says not to extract")
    void shouldSkipExtractionWhenConfigSaysNo() {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(false);

        boolean result = service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertFalse(result);
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("Should extract when config says to extract")
    void shouldExtractWhenConfigSaysYes() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createEventsOnlyJson());

        boolean result = service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertTrue(result);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
    }

    // ========================================
    // SUCCESSFUL EXTRACTION TESTS
    // ========================================

    @Test
    @DisplayName("Should apply extracted events to stanza")
    void shouldApplyExtractedEventsToStanza() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createEventsOnlyJson());

        int eventsBefore = mockStanza.getEvents().size();
        service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertTrue(mockStanza.getEvents().size() > eventsBefore);
    }

    @Test
    @DisplayName("Should apply character appearances to stanza")
    void shouldApplyCharacterAppearancesToStanza() throws Exception {
        // Add a potential character
        StanzaCharacter derek = new StanzaCharacter();
        derek.setName("Derek Hale");
        derek.setPresenceStatus("potential");
        derek.setStanza(mockStanza);
        mockStanza.getCharacters().add(derek);

        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createAppearanceJson());

        service.processExtraction(mockStanza, mockHistory, 5, false, false);

        // Derek should now be "present"
        StanzaCharacter updatedDerek = mockStanza.getCharacters().stream()
            .filter(c -> "Derek Hale".equals(c.getName()))
            .findFirst().orElseThrow();
        assertEquals("present", updatedDerek.getPresenceStatus());
    }

    @Test
    @DisplayName("Should create emergent characters")
    void shouldCreateEmergentCharacters() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createEmergentCharacterJson());

        int charsBefore = mockStanza.getCharacters().size();
        service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertTrue(mockStanza.getCharacters().size() > charsBefore);

        // Find the emergent character
        StanzaCharacter newChar = mockStanza.getCharacters().stream()
            .filter(c -> "Mystery Stranger".equals(c.getName()))
            .findFirst().orElseThrow();
        assertEquals("present", newChar.getPresenceStatus());
        assertTrue(newChar.getCanonRole().contains("EMERGENT"));
    }

    @Test
    @DisplayName("Should handle full extraction with all change types")
    void shouldHandleFullExtractionWithAllTypes() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createFullExtractionJson());

        boolean result = service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertTrue(result);
        assertFalse(mockStanza.getEvents().isEmpty());
    }

    // ========================================
    // EMPTY RESULTS TESTS
    // ========================================

    @Test
    @DisplayName("Should handle extraction with no changes")
    void shouldHandleExtractionWithNoChanges() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createEmptyExtractionJson());

        boolean result = service.processExtraction(mockStanza, mockHistory, 5, false, false);

        assertTrue(result);
        // No events should have been added
        assertTrue(mockStanza.getEvents().isEmpty());
    }

    // ========================================
    // FORCE EXTRACTION TESTS
    // ========================================

    @Test
    @DisplayName("Should force extraction regardless of config")
    void shouldForceExtractionRegardlessOfConfig() throws Exception {
        // Config would say no, but force overrides
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn(createEventsOnlyJson());

        boolean result = service.forceExtraction(mockStanza, mockHistory);

        assertTrue(result);
        verify(llmClient).call(eq(ModelType.ANALYTICAL), anyString(), anyString());
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    @DisplayName("Should not throw when LLM call fails")
    void shouldNotThrowWhenLLMCallFails() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenThrow(new RuntimeException("LLM error"));

        assertDoesNotThrow(() -> {
            service.processExtraction(mockStanza, mockHistory, 5, false, false);
        });
    }

    @Test
    @DisplayName("Should not throw when JSON parsing fails")
    void shouldNotThrowWhenJsonParsingFails() throws Exception {
        when(extractionConfig.shouldExtract(anyInt(), anyBoolean(), anyBoolean())).thenReturn(true);
        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("invalid json {{{");

        assertDoesNotThrow(() -> {
            service.processExtraction(mockStanza, mockHistory, 5, false, false);
        });
    }

    // ========================================
    // HELPER METHODS — JSON test fixtures
    // ========================================

    private String createEventsOnlyJson() {
        return """
            {
              "events": [
                {
                  "description": "User entered the dark room",
                  "significance": "MINOR",
                  "charactersInvolved": ["User"]
                },
                {
                  "description": "Scott noticed a strange smell",
                  "significance": "MAJOR",
                  "charactersInvolved": ["Scott McCall"]
                }
              ],
              "characterAppearances": [],
              "emergentCharacters": []
            }
            """;
    }

    private String createAppearanceJson() {
        return """
            {
              "events": [],
              "characterAppearances": [
                {
                  "characterName": "Derek Hale",
                  "changeType": "APPEARED",
                  "context": "Derek stepped out of the shadows"
                }
              ],
              "emergentCharacters": []
            }
            """;
    }

    private String createEmergentCharacterJson() {
        return """
            {
              "events": [],
              "characterAppearances": [
                {
                  "characterName": "Mystery Stranger",
                  "changeType": "APPEARED",
                  "context": "A hooded figure appeared in the doorway"
                }
              ],
              "emergentCharacters": [
                {
                  "characterName": "Mystery Stranger",
                  "canonRole": "original",
                  "currentEmotionalState": "guarded",
                  "relationshipToUser": "stranger",
                  "hiddenBackstory": "A hunter tracking supernatural activity",
                  "physicalDescription": "Tall, hooded figure with a scar across the jaw"
                }
              ]
            }
            """;
    }

    private String createFullExtractionJson() {
        return """
            {
              "events": [
                {
                  "description": "User entered the classroom",
                  "significance": "MINOR",
                  "charactersInvolved": ["User"]
                }
              ],
              "characterAppearances": [
                {
                  "characterName": "Scott McCall",
                  "changeType": "APPEARED",
                  "context": "Scott was already seated"
                }
              ],
              "emergentCharacters": []
            }
            """;
    }

    private String createEmptyExtractionJson() {
        return """
            {
              "events": [],
              "characterAppearances": [],
              "emergentCharacters": []
            }
            """;
    }
}