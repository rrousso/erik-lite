package com.github.rrousso.erik_lite.services.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionContext;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_lite.domain.valueobjects.LoadedStanzaMemory;
import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Unit tests for SessionAssemblerService.
 *
 * Tests context assembly for VOID and STANZA modes,
 * including persona, synopsis, exchanges, loaded stanza memory, and completed stanza.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SessionAssemblerService Tests")
public class SessionAssemblerServiceTest {

    @Mock
    private PersonaService configService;

    @Mock
    private StanzaPersistenceService persistenceService;

    private SessionAssemblerService service;

    @BeforeEach
    void setUp() {
        service = new SessionAssemblerService(configService, persistenceService);

        when(configService.getUserPersona()).thenReturn("User: Test User\nPronouns: they/them");
    }

    // ========================================
    // VOID MODE TESTS
    // ========================================

    @Test
    @DisplayName("Should assemble basic VOID mode context")
    void shouldAssembleBasicVoidModeContext() {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.NONE);

        SessionContext context = service.assembleForVoid(state);

        assertNotNull(context);
        assertEquals(SessionState.Mode.VOID, context.getMode());
        assertEquals(StanzaStatus.NONE, context.getStanzaStatus());
        assertNotNull(context.getUserPersona());
        assertFalse(context.hasSynopsis());
        assertFalse(context.hasRecentExchanges());
        assertFalse(context.hasInitializedStanza());
        assertFalse(context.hasCompletedStanza());
        assertFalse(context.hasLoadedStanzaMemory());
    }

    @Test
    @DisplayName("Should include user persona in VOID context")
    void shouldIncludeUserPersonaInVoidContext() {
        SessionState state = new SessionState();
        String userPersona = "User: Jane Doe\nPronouns: she/her\nDescription: tall";
        when(configService.getUserPersona()).thenReturn(userPersona);

        SessionContext context = service.assembleForVoid(state);

        assertEquals(userPersona, context.getUserPersona());
        verify(configService).getUserPersona();
    }

    @Test
    @DisplayName("Should include recent exchanges in VOID context")
    void shouldIncludeRecentExchangesInVoidContext() {
        SessionState state = new SessionState();
        state.getVoidHistory().addUserMessage("Hello Erik");
        state.getVoidHistory().addAssistantMessage("Hi! How can I help?");

        SessionContext context = service.assembleForVoid(state);

        assertTrue(context.hasRecentExchanges());
        assertTrue(context.getRecentExchanges().contains("Hello Erik"));
        assertTrue(context.getRecentExchanges().contains("Hi! How can I help?"));
    }

    @Test
    @DisplayName("Should include loaded stanza memory in VOID context")
    void shouldIncludeLoadedStanzaMemoryInVoidContext() {
        SessionState state = new SessionState();
        LoadedStanzaMemory memory = new LoadedStanzaMemory(
            "narrator context here", "quick synopsis here", "cinderella");
        state.setLoadedStanzaMemory(memory);

        SessionContext context = service.assembleForVoid(state);

        assertTrue(context.hasLoadedStanzaMemory());
        assertEquals(memory, context.getLoadedStanzaMemory());
    }

    @Test
    @DisplayName("Should include completed stanza in VOID context")
    void shouldIncludeCompletedStanzaInVoidContext() {
        SessionState state = new SessionState();
        state.setStanzaStatus(StanzaStatus.COMPLETED);

        InitializedStanza initStanza = new InitializedStanza();
        CompletedStanza completed = new CompletedStanza("A brief synopsis", initStanza);
        state.setCompletedStanza(completed);

        SessionContext context = service.assembleForVoid(state);

        assertTrue(context.hasCompletedStanza());
        assertEquals(completed, context.getCompletedStanza());
    }

    // ========================================
    // STANZA MODE TESTS
    // ========================================

    @Test
    @DisplayName("Should assemble STANZA mode context with DB narrator context")
    void shouldAssembleStanzaModeContextWithDbContext() {
        SessionState state = new SessionState();
        state.setActiveStanzaId(1L);

        Stanza stanza = mock(Stanza.class);
        when(stanza.toNarratorContext()).thenReturn("Full narrator context from DB");
        when(persistenceService.loadStanzaWithRelationships(1L)).thenReturn(stanza);

        SessionContext context = service.assembleForStanza(state);

        assertNotNull(context);
        assertEquals(SessionState.Mode.STANZA, context.getMode());
        assertTrue(context.hasNarratorContextFromDB());
        assertTrue(context.getNarratorContext().contains("Full narrator context from DB"));
    }

    @Test
    @DisplayName("Should fall back to InitializedStanza when no active stanza ID")
    void shouldFallBackToInitializedStanzaWhenNoActiveStanzaId() {
        SessionState state = new SessionState();
        // No activeStanzaId set

        InitializedStanza initStanza = new InitializedStanza();
        initStanza.setWorldIdentifier("teen_wolf");
        state.setInitializedStanza(initStanza);

        SessionContext context = service.assembleForStanza(state);

        assertNotNull(context);
        assertTrue(context.hasInitializedStanza());
    }
}