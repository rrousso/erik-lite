package com.github.rrousso.erik_lite.services.command;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.repositories.StanzaRepository;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Unit tests for CommandService.
 *
 * Tests slash command parsing and execution.
 * erik-lite changes: /debug shows characters + events + beats (no facts/tensions/knowledge).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Command Service Tests")
public class CommandServiceTest {

    @Mock
    private StanzaRepository stanzaRepository;

    @Mock
    private StanzaPersistenceService persistenceService;

    private CommandService commandService;
    private SessionState state;

    @BeforeEach
    void setUp() {
        commandService = new CommandService(stanzaRepository, persistenceService);
        state = new SessionState();
    }

    // ========== NON-COMMAND INPUT ==========

    @Test
    @DisplayName("Should return notACommand for regular input")
    void shouldReturnNotACommandForRegularInput() {
        CommandResult result = commandService.processCommand("Hello Erik!", state);

        assertFalse(result.wasHandled());
        assertEquals("", result.getResponse());
    }

    @Test
    @DisplayName("Should return notACommand for null input")
    void shouldReturnNotACommandForNullInput() {
        CommandResult result = commandService.processCommand(null, state);

        assertFalse(result.wasHandled());
    }

    @Test
    @DisplayName("Should return notACommand for input without prefix")
    void shouldReturnNotACommandForInputWithoutPrefix() {
        CommandResult result = commandService.processCommand("list stanzas", state);

        assertFalse(result.wasHandled());
    }

    // ========== HELP COMMAND ==========

    @Test
    @DisplayName("Should handle /help command")
    void shouldHandleHelpCommand() {
        CommandResult result = commandService.processCommand("/help", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("ERIK COMMANDS"));
        assertTrue(result.getResponse().contains("/list"));
        assertTrue(result.getResponse().contains("/search"));
        assertTrue(result.getResponse().contains("/load"));
    }

    // ========== LIST COMMAND ==========

    @Test
    @DisplayName("Should handle /list command with no stanzas")
    void shouldHandleListCommandWithNoStanzas() {
        when(stanzaRepository.findAll()).thenReturn(Collections.emptyList());

        CommandResult result = commandService.processCommand("/list", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas saved yet"));
    }

    @Test
    @DisplayName("Should handle /list command with stanzas")
    void shouldHandleListCommandWithStanzas() {
        Stanza stanza = createTestStanza(1L, "Haunted mansion", "Ghost investigation");
        when(stanzaRepository.findAll()).thenReturn(Arrays.asList(stanza));

        CommandResult result = commandService.processCommand("/list", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("SAVED STANZAS"));
        assertTrue(result.getResponse().contains("Haunted mansion"));
        assertTrue(result.getResponse().contains("Ghost investigation"));
    }

    // ========== SEARCH COMMAND ==========

    @Test
    @DisplayName("Should handle /search command with no keywords")
    void shouldHandleSearchCommandWithNoKeywords() {
        CommandResult result = commandService.processCommand("/search", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Usage: /search"));
    }

    @Test
    @DisplayName("Should handle /search command with no matches")
    void shouldHandleSearchCommandWithNoMatches() {
        CommandResult result = commandService.processCommand("/search vampire", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("No stanzas found matching"));
    }

    @Test
    @DisplayName("Should handle /search command with matches")
    void shouldHandleSearchCommandWithMatches() {
        Stanza stanza = createTestStanza(2L, "Vampire castle", "Romance with vampire");
        when(stanzaRepository.fullTextSearch("vampire")).thenReturn(Arrays.asList(stanza));

        CommandResult result = commandService.processCommand("/search vampire", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("SEARCH RESULTS"));
        assertTrue(result.getResponse().contains("Vampire castle"));
    }

    // ========== UNKNOWN COMMAND ==========

    @Test
    @DisplayName("Should handle unknown command")
    void shouldHandleUnknownCommand() {
        CommandResult result = commandService.processCommand("/foobar", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Unknown command"));
    }

    // ========== EMPTY COMMAND ==========

    @Test
    @DisplayName("Should handle empty command after prefix")
    void shouldHandleEmptyCommandAfterPrefix() {
        CommandResult result = commandService.processCommand("/", state);

        assertTrue(result.wasHandled());
        assertTrue(result.getResponse().contains("Empty command"));
    }

    // ========== HELPER METHODS ==========

    private Stanza createTestStanza(Long id, String setting, String quickSynopsis) {
        Stanza stanza = new Stanza();
        stanza.setId(id);
        stanza.setSetting(setting);
        stanza.setQuickSynopsis(quickSynopsis);
        stanza.setStatus("completed");
        stanza.setWorldIdentifier("test_world");

        Persona persona = new Persona("TestUser", "they/them", "", "");
        stanza.setPersona(persona);

        return stanza;
    }
}