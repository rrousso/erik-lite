package com.github.rrousso.erik_lite.services.orchestration;

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
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.services.llm.FlagDetectorService;
import com.github.rrousso.erik_lite.services.orchestration.strategies.FlowStrategy;
import com.github.rrousso.erik_lite.services.orchestration.strategies.FlowStrategyFactory;

/**
 * Unit tests for SessionFlowService.
 *
 * Tests routing logic only — all business logic is in strategies.
 * Verifies: input validation, flag→strategy dispatch, mode→strategy dispatch, error propagation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionFlowService Tests")
public class SessionFlowServiceTest {

    @Mock
    private FlowStrategyFactory strategyFactory;

    @Mock
    private FlagDetectorService flagDetector;

    @Mock
    private FlowStrategy mockStrategy;

    private SessionFlowService sessionFlowService;
    private SessionState state;

    @BeforeEach
    void setUp() {
        sessionFlowService = new SessionFlowService(strategyFactory, flagDetector);
        state = new SessionState();
    }

    // ========================================
    // INPUT VALIDATION TESTS
    // ========================================

    @Test
    @DisplayName("Should throw NullPointerException when userInput is null")
    void shouldThrowExceptionWhenUserInputIsNull() {
        assertThrows(NullPointerException.class, () -> {
            sessionFlowService.handleUserInput(null, state);
        });
    }

    @Test
    @DisplayName("Should throw NullPointerException when state is null")
    void shouldThrowExceptionWhenStateIsNull() {
        assertThrows(NullPointerException.class, () -> {
            sessionFlowService.handleUserInput("Hello", null);
        });
    }

    @Test
    @DisplayName("Should return empty string when userInput is blank")
    void shouldReturnEmptyStringWhenInputIsBlank() {
        String result = sessionFlowService.handleUserInput("   ", state);
        assertEquals("", result);

        verifyNoInteractions(flagDetector);
        verifyNoInteractions(strategyFactory);
    }

    @Test
    @DisplayName("Should return empty string when userInput is empty")
    void shouldReturnEmptyStringWhenInputIsEmpty() {
        String result = sessionFlowService.handleUserInput("", state);
        assertEquals("", result);

        verifyNoInteractions(flagDetector);
        verifyNoInteractions(strategyFactory);
    }

    // ========================================
    // FLAG-BASED ROUTING TESTS
    // ========================================

    @Test
    @DisplayName("Should route to START strategy when START flag detected")
    void shouldRouteToStartStrategyWhenStartFlagDetected() {
        String userInput = "Yes, let's begin!";

        mockValidFlag(userInput, Flag.START_STANZA);
        when(strategyFactory.getStrategyForFlag(Flag.START_STANZA)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state)).thenReturn("[System] Starting stanza...");

        String result = sessionFlowService.handleUserInput(userInput, state);

        assertEquals("[System] Starting stanza...", result);

        verify(flagDetector).detect(userInput, state);
        verify(strategyFactory).getStrategyForFlag(Flag.START_STANZA);
        verify(mockStrategy).execute(userInput, state);
        verify(strategyFactory, never()).getStrategyForConversation(any());
    }

    @Test
    @DisplayName("Should route to PAUSE strategy when PAUSE flag detected")
    void shouldRouteToPauseStrategyWhenPauseFlagDetected() {
        String userInput = "((pause))";

        mockValidFlag(userInput, Flag.PAUSE_STANZA);
        when(strategyFactory.getStrategyForFlag(Flag.PAUSE_STANZA)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state)).thenReturn("[Erik] Sure, let's take a break.");

        String result = sessionFlowService.handleUserInput(userInput, state);

        assertEquals("[Erik] Sure, let's take a break.", result);

        verify(flagDetector).detect(userInput, state);
        verify(strategyFactory).getStrategyForFlag(Flag.PAUSE_STANZA);
        verify(mockStrategy).execute(userInput, state);
    }

    @Test
    @DisplayName("Should route to ABANDON strategy when ABANDON flag detected")
    void shouldRouteToAbandonStrategyWhenAbandonFlagDetected() {
        String userInput = "((abandon))";

        mockValidFlag(userInput, Flag.ABANDON_STANZA);
        when(strategyFactory.getStrategyForFlag(Flag.ABANDON_STANZA)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state)).thenReturn("[System] Stanza abandoned.");

        String result = sessionFlowService.handleUserInput(userInput, state);

        assertEquals("[System] Stanza abandoned.", result);

        verify(flagDetector).detect(userInput, state);
        verify(strategyFactory).getStrategyForFlag(Flag.ABANDON_STANZA);
        verify(mockStrategy).execute(userInput, state);
    }

    // ========================================
    // MODE-BASED ROUTING TESTS (No Flag)
    // ========================================

    @Test
    @DisplayName("Should route to conversation strategy when no flag detected")
    void shouldRouteToConversationStrategyWhenNoFlagDetected() {
        String userInput = "Hello Erik!";

        when(flagDetector.detect(userInput, state)).thenReturn(Flag.NONE);
        when(strategyFactory.getStrategyForConversation(state)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state)).thenReturn("[Erik] Hi! How can I help?");

        String result = sessionFlowService.handleUserInput(userInput, state);

        assertEquals("[Erik] Hi! How can I help?", result);

        verify(flagDetector).detect(userInput, state);
        verify(strategyFactory).getStrategyForConversation(state);
        verify(mockStrategy).execute(userInput, state);
        verify(strategyFactory, never()).getStrategyForFlag(any());
    }

    @Test
    @DisplayName("Should route to conversation strategy for normal user message")
    void shouldRouteToConversationStrategyForNormalMessage() {
        String userInput = "I want to create a story about vampires";

        when(flagDetector.detect(userInput, state)).thenReturn(Flag.NONE);
        when(strategyFactory.getStrategyForConversation(state)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state))
            .thenReturn("[Erik] Vampires! Great choice. Tell me more...");

        String result = sessionFlowService.handleUserInput(userInput, state);

        assertEquals("[Erik] Vampires! Great choice. Tell me more...", result);

        verify(flagDetector).detect(userInput, state);
        verify(strategyFactory).getStrategyForConversation(state);
        verify(mockStrategy).execute(userInput, state);
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    @DisplayName("Should propagate exception from flag detector")
    void shouldPropagateExceptionFromFlagDetector() {
        String userInput = "Test input";

        when(flagDetector.detect(userInput, state))
            .thenThrow(new RuntimeException("Flag detection failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            sessionFlowService.handleUserInput(userInput, state);
        });

        assertEquals("Flag detection failed", exception.getMessage());

        verify(flagDetector).detect(userInput, state);
        verifyNoInteractions(strategyFactory);
    }

    @Test
    @DisplayName("Should propagate exception from strategy execution")
    void shouldPropagateExceptionFromStrategyExecution() {
        String userInput = "Hello";

        when(flagDetector.detect(userInput, state)).thenReturn(Flag.NONE);
        when(strategyFactory.getStrategyForConversation(state)).thenReturn(mockStrategy);
        when(mockStrategy.execute(userInput, state))
            .thenThrow(new RuntimeException("Strategy execution failed"));

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            sessionFlowService.handleUserInput(userInput, state);
        });

        assertEquals("Strategy execution failed", exception.getMessage());
    }

    @Test
    @DisplayName("Should propagate exception from strategy factory")
    void shouldPropagateExceptionFromStrategyFactory() {
        String userInput = "start";

        when(flagDetector.detect(userInput, state)).thenReturn(Flag.START_STANZA);
        when(flagDetector.isValidFlagForStatus(eq(Flag.START_STANZA), any())).thenReturn(true);
        when(strategyFactory.getStrategyForFlag(Flag.START_STANZA))
            .thenThrow(new IllegalStateException("No strategy registered for flag"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            sessionFlowService.handleUserInput(userInput, state);
        });

        assertEquals("No strategy registered for flag", exception.getMessage());
    }

    // ========================================
    // MULTIPLE CONSECUTIVE CALLS
    // ========================================

    @Test
    @DisplayName("Should handle multiple consecutive calls correctly")
    void shouldHandleMultipleConsecutiveCallsCorrectly() {
        // First call — no flag
        when(flagDetector.detect("Hello", state)).thenReturn(Flag.NONE);
        when(strategyFactory.getStrategyForConversation(state)).thenReturn(mockStrategy);
        when(mockStrategy.execute("Hello", state)).thenReturn("[Erik] Hi!");

        String result1 = sessionFlowService.handleUserInput("Hello", state);
        assertEquals("[Erik] Hi!", result1);

        // Second call — flag detected
        when(flagDetector.detect("start", state)).thenReturn(Flag.START_STANZA);
        when(flagDetector.isValidFlagForStatus(eq(Flag.START_STANZA), any())).thenReturn(true);
        when(strategyFactory.getStrategyForFlag(Flag.START_STANZA)).thenReturn(mockStrategy);
        when(mockStrategy.execute("start", state)).thenReturn("[System] Starting...");

        String result2 = sessionFlowService.handleUserInput("start", state);
        assertEquals("[System] Starting...", result2);

        // Third call — different flag
        when(flagDetector.detect("pause", state)).thenReturn(Flag.PAUSE_STANZA);
        when(flagDetector.isValidFlagForStatus(eq(Flag.PAUSE_STANZA), any())).thenReturn(true);
        when(strategyFactory.getStrategyForFlag(Flag.PAUSE_STANZA)).thenReturn(mockStrategy);
        when(mockStrategy.execute("pause", state)).thenReturn("[Erik] Pausing...");

        String result3 = sessionFlowService.handleUserInput("pause", state);
        assertEquals("[Erik] Pausing...", result3);

        verify(flagDetector, times(3)).detect(anyString(), eq(state));
        verify(strategyFactory, times(1)).getStrategyForConversation(state);
        verify(strategyFactory, times(1)).getStrategyForFlag(Flag.START_STANZA);
        verify(strategyFactory, times(1)).getStrategyForFlag(Flag.PAUSE_STANZA);
    }
    
    /**
     * Helper: mock flag detection returning a valid flag.
     * Since FlagDetectorService is a mock, isValidFlagForStatus defaults to false.
     * Tests that expect a flag to be routed must also stub the validity check.
     */
    private void mockValidFlag(String userInput, Flag flag) {
        when(flagDetector.detect(userInput, state)).thenReturn(flag);
        when(flagDetector.isValidFlagForStatus(eq(flag), any())).thenReturn(true);
    }
}