package com.github.rrousso.erik_lite.services.llm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_lite.domain.enums.Flag;
import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;

/**
 * Comprehensive tests for FlagDetectorService.
 *
 * Tests: input validation, flag detection per status, status-aware filtering,
 * conversation context usage, error handling.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlagDetectorService Tests")
public class FlagDetectorServiceTest {

    @Mock
    private LLMClientService llmClient;

    @Mock
    private SystemPromptBuilderService promptBuilder;

    private FlagDetectorService flagDetector;

    @BeforeEach
    void setUp() {
        flagDetector = new FlagDetectorService(llmClient, promptBuilder);

        lenient().when(promptBuilder.buildFlagDetectionPrompt())
            .thenReturn("Detect flag from: {USER_INPUT}\nContext: {CONVERSATION_CONTEXT}\nStatus: {STATUS}\nAvailable: {AVAILABLE_FLAGS}");
    }

    // ========================================
    // INPUT VALIDATION TESTS
    // ========================================

    @Test
    @DisplayName("Should throw NullPointerException when userInput is null")
    void shouldThrowExceptionWhenUserInputIsNull() {
        SessionState state = new SessionState();

        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect(null, state);
        });

        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("Should throw NullPointerException when state is null")
    void shouldThrowExceptionWhenStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            flagDetector.detect("test input", null);
        });

        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("Should return NONE when userInput is blank")
    void shouldReturnNoneWhenInputIsBlank() {
        SessionState state = new SessionState();

        Flag result = flagDetector.detect("   ", state);

        assertEquals(Flag.NONE, result);
        verifyNoInteractions(llmClient);
    }

    @Test
    @DisplayName("Should return NONE when userInput is empty")
    void shouldReturnNoneWhenInputIsEmpty() {
        SessionState state = new SessionState();

        Flag result = flagDetector.detect("", state);

        assertEquals(Flag.NONE, result);
        verifyNoInteractions(llmClient);
    }

    // ========================================
    // START FLAG DETECTION TESTS
    // ========================================

    @Test
    @DisplayName("Should return START when user confirms after Erik asks 'Ready to begin?'")
    void shouldReturnStartWhenConfirmingAfterReadyPrompt() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("Perfect setup! Ready to begin?");

        String userInput = "Yes!";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.START_STANZA, result);

        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("Perfect setup! Ready to begin?")
        );
    }

    @Test
    @DisplayName("Should return NONE when user describes WHERE to start (planning phase)")
    void shouldReturnNoneWhenDescribingStartLocation() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("What setting do you want?");

        String userInput = "I want to start at the dance scene";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("NONE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.NONE, result);
    }

    @Test
    @DisplayName("Should return NONE when trying to START but stanza already completed")
    void shouldReturnNoneWhenStartingButStanzaCompleted() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.COMPLETED);

        String userInput = "Let's start";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.NONE, result);
    }

    // ========================================
    // PAUSE FLAG DETECTION TESTS
    // ========================================

    @Test
    @DisplayName("Should return PAUSE when user says ((pause)) during active stanza")
    void shouldReturnPauseWhenUserSaysPauseDuringActiveStanza() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        String userInput = "((pause))";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.PAUSE_STANZA, result);
    }

    @Test
    @DisplayName("Should return PAUSE with natural language pause request")
    void shouldReturnPauseWithNaturalLanguage() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        String userInput = "Hold on, let's pause here";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.PAUSE_STANZA, result);
    }

    @Test
    @DisplayName("Should return NONE when trying to PAUSE but not in active stanza")
    void shouldReturnNoneWhenPausingButNotActive() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);

        String userInput = "pause";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("PAUSE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.NONE, result);
    }

    // ========================================
    // CONTINUE FLAG DETECTION TESTS
    // ========================================

    @Test
    @DisplayName("Should return CONTINUE when user wants to resume paused stanza")
    void shouldReturnContinueWhenResumingPausedStanza() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.PAUSED);

        String userInput = "Let's continue";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("CONTINUE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.CONTINUE_STANZA, result);
    }

    @Test
    @DisplayName("Should return NONE when trying to CONTINUE but not paused")
    void shouldReturnNoneWhenContinuingButNotPaused() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        String userInput = "continue";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("CONTINUE");

        Flag result = flagDetector.detect(userInput, state);

        assertEquals(Flag.NONE, result);
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    @DisplayName("Should return NONE when LLM call fails")
    void shouldReturnNoneWhenLlmCallFails() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenThrow(new RuntimeException("API Error"));

        Flag result = flagDetector.detect("pause", state);

        assertEquals(Flag.NONE, result);
    }

    @Test
    @DisplayName("Should return NONE for unrecognized LLM response")
    void shouldReturnNoneForUnrecognizedResponse() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("GARBAGE_XYZ_NOT_A_FLAG");

        Flag result = flagDetector.detect("some input", state);

        assertEquals(Flag.NONE, result);
    }

    @Test
    @DisplayName("Should return NONE for empty LLM response")
    void shouldReturnNoneForEmptyLlmResponse() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.ACTIVE);

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("");

        Flag result = flagDetector.detect("test", state);

        assertEquals(Flag.NONE, result);
    }

    // ========================================
    // CONVERSATION CONTEXT TESTS
    // ========================================

    @Test
    @DisplayName("Should include conversation context in LLM call")
    void shouldIncludeConversationContextInLlmCall() throws Exception {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);
        state.getVoidHistory().addAssistantMessage("What kind of story?");
        state.getVoidHistory().addUserMessage("A vampire romance");
        state.getVoidHistory().addAssistantMessage("Great! Ready to begin?");

        String userInput = "Yes!";

        when(llmClient.call(eq(ModelType.ANALYTICAL), anyString(), anyString()))
            .thenReturn("START");

        flagDetector.detect(userInput, state);

        verify(llmClient).call(
            eq(ModelType.ANALYTICAL),
            anyString(),
            contains("Ready to begin?")
        );
    }
}