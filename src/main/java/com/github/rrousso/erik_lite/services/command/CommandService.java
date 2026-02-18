package com.github.rrousso.erik_lite.services.command;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_lite.domain.valueobjects.LoadedStanzaMemory;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.persistence.repositories.StanzaRepository;
import com.github.rrousso.erik_lite.services.stanza.StanzaPersistenceService;

/**
 * Handles slash commands. Bypasses LLM entirely.
 */
@Service
public class CommandService {

    private static final Logger log = LoggerFactory.getLogger(CommandService.class);
    private static final String COMMAND_PREFIX = "/";

    private final StanzaRepository stanzaRepository;
    private final StanzaPersistenceService persistenceService;

    public CommandService(StanzaRepository stanzaRepository, StanzaPersistenceService persistenceService) {
        this.stanzaRepository = stanzaRepository;
        this.persistenceService = persistenceService;
    }

    public CommandResult processCommand(String userInput, SessionState state) {
        if (userInput == null || !userInput.startsWith(COMMAND_PREFIX)) {
            return CommandResult.notACommand();
        }

        String commandLine = userInput.substring(COMMAND_PREFIX.length()).trim();

        if (commandLine.isEmpty()) {
            return CommandResult.handled("[System] Empty command. Type /help for available commands.");
        }

        String[] parts = commandLine.split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1].trim() : "";

        log.info("Processing command: {} with args: {}", command, args);

        return switch (command) {
            case "help" -> handleHelp();
            case "list" -> handleList();
            case "search" -> handleSearch(args);
            case "load" -> handleLoad(args, state);
            case "clear" -> handleClear(state);
            case "debug" -> handleDebug(state);
            default -> CommandResult.handled("[System] Unknown command: /" + command + ". Type /help for available commands.");
        };
    }
    
    /**
     * Check if input looks like a command that's missing the slash prefix.
     * Returns a helpful suggestion if it matches a known command pattern.
     */
    public CommandResult checkForMissingSlash(String userInput, SessionState state) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return CommandResult.notACommand();
        }
        
        String trimmed = userInput.trim();
        String[] parts = trimmed.split("\\s+", 2);
        String firstWord = parts[0].toLowerCase();
        
        // Check if first word matches any known command
        boolean looksLikeCommand = switch (firstWord) {
            case "help", "list", "search", "load", "clear", "debug" -> true;
            default -> false;
        };
        
        if (!looksLikeCommand) {
            return CommandResult.notACommand();
        }
        
        // Special case: "load" with a number is very likely a forgotten slash
        if (firstWord.equals("load") && parts.length > 1) {
            try {
                Long.parseLong(parts[1].trim());
                return CommandResult.handled(
                    String.format("[System] Did you mean '/%s'? (Commands need a '/' prefix)\n", trimmed)
                );
            } catch (NumberFormatException e) {
                // Not a number, might be conversational
            }
        }
        
        // For other command-like words, give a gentle hint
        return CommandResult.handled(
            String.format("[System] '%s' looks like a command. Did you mean '/%s'?\n" +
                         "Type /help to see all commands.\n", trimmed, trimmed)
        );
    }

    // ========== COMMAND HANDLERS ==========

    private CommandResult handleHelp() {
        String help = """
            
            === ERIK COMMANDS ===
            
            /help                           - Show this help message
            /list                           - List all saved stanzas (ID + quick synopsis)
            /search [keywords]              - Search stanzas by keywords
            /search [section]: [keywords]   - Search on specific stanza sections
                                              Available sections: setting-premise-tone-character
            /load [id]                      - Load a stanza into Erik's memory for reference
            /clear                          - Clear loaded stanza memory
            /debug                          - Show current stanza state (characters, events, beats)
            
            Examples:
              /search vampire romance
              /load 5
              /debug
            """;
        return CommandResult.handled(help);
    }

    private CommandResult handleList() {
        List<Stanza> stanzas = stanzaRepository.findAll();

        if (stanzas.isEmpty()) {
            return CommandResult.handled("\n[System] No stanzas saved yet.\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== SAVED STANZAS ===\n\n");

        for (Stanza stanza : stanzas) {
            sb.append(formatStanzaSummary(stanza));
            sb.append("\n---\n");
        }

        sb.append("\nUse /load [id] to load a stanza into Erik's memory.\n");

        return CommandResult.handled(sb.toString());
    }

    private CommandResult handleSearch(String args) {
        if (args.isEmpty()) {
            return CommandResult.handled("[System] Usage: /search [keywords] or /search:field [keywords]\nExample: /search vampire romance\nExample: /search:character Kael");
        }

        String[] searchParts = args.split(":", 2);

        String searchType;
        String rawKeywords;

        if (searchParts.length == 1) {
            searchType = "full";
            rawKeywords = searchParts[0].trim();
        } else {
            searchType = searchParts[0].trim().toLowerCase();
            rawKeywords = searchParts[1].trim();
        }

        if (rawKeywords.isEmpty()) {
            return CommandResult.handled("[System] Please provide search keywords.");
        }

        List<Stanza> matches;

        switch (searchType) {
            case "full" -> {
                String[] words = rawKeywords.split("\\s+");
                String query = String.join(" & ", words);
                matches = stanzaRepository.fullTextSearch(query);
            }
            case "setting" -> matches = stanzaRepository.searchBySetting(rawKeywords);
            case "premise" -> matches = stanzaRepository.searchByPremise(rawKeywords);
            case "tone" -> matches = stanzaRepository.searchByTone(rawKeywords);
            case "character" -> matches = stanzaRepository.searchByCharacter(rawKeywords);
            default -> {
                return CommandResult.handled("\n[System] '" + searchType + "' is not a valid search type.\nValid types: setting, premise, tone, character\n");
            }
        }

        if (matches.isEmpty()) {
            return CommandResult.handled("\n[System] No stanzas found matching: " + rawKeywords + "\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== SEARCH RESULTS FOR: ").append(rawKeywords).append(" ===\n\n");
        sb.append("Found ").append(matches.size()).append(" stanza(s):\n\n");

        for (Stanza stanza : matches) {
            sb.append(formatStanzaSummary(stanza));
            sb.append("\n---\n");
        }

        sb.append("\nUse /load [id] to load a stanza into Erik's memory.\n");

        return CommandResult.handled(sb.toString());
    }

    private CommandResult handleLoad(String idArg, SessionState state) {
        if (idArg.isEmpty()) {
            return CommandResult.handled("[System] Usage: /load [id]\nExample: /load 5");
        }

        Long id;
        try {
            id = Long.parseLong(idArg);
        } catch (NumberFormatException e) {
            return CommandResult.handled("[System] Invalid ID: " + idArg + ". Must be a number.");
        }

        Stanza stanza;
        try {
            stanza = persistenceService.loadStanzaWithRelationships(id);
        } catch (Exception e) {
            return CommandResult.handled("[System] No stanza found with ID: " + id);
        }

        // Convert entity to value object (domain layer separation)
        LoadedStanzaMemory memory = new LoadedStanzaMemory(
            stanza.toNarratorContext(),
            stanza.getQuickSynopsis(),
            stanza.getWorldIdentifier());

        state.setLoadedStanzaMemory(memory);

        StringBuilder sb = new StringBuilder();
        sb.append("\n[System] Loaded stanza #").append(id).append(" into Erik's memory.\n\n");
        sb.append("Setting: ").append(stanza.getSetting()).append("\n");
        sb.append("Premise: ").append(stanza.getPremise()).append("\n\n");
        sb.append("Erik can now reference this stanza in your conversation.\n");
        sb.append("Use /clear to remove it from memory.\n");

        log.info("Loaded stanza {} into session memory", id);

        return CommandResult.handled(sb.toString());
    }

    private CommandResult handleClear(SessionState state) {
        if (state.getLoadedStanzaMemory() == null) {
            return CommandResult.handled("[System] No stanza currently loaded in memory.");
        }

        state.setLoadedStanzaMemory(null);
        return CommandResult.handled("[System] Cleared stanza memory.");
    }

    private CommandResult handleDebug(SessionState state) {
        Long activeStanzaId = state.getActiveStanzaId();

        if (activeStanzaId == null) {
            return CommandResult.handled("\n[Debug] No active stanza. Start a stanza first with 'let's begin'.\n");
        }

        Stanza activeStanza;
        try {
            activeStanza = persistenceService.loadStanzaWithRelationships(activeStanzaId);
        } catch (Exception e) {
            return CommandResult.handled("\n[Debug] Error loading stanza: " + e.getMessage() + "\n");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n=== STANZA DEBUG - CURRENT STATE ===\n\n");

        sb.append("ID: ").append(activeStanza.getId()).append("\n");
        sb.append("Status: ").append(activeStanza.getStatus()).append("\n");
        sb.append("Current Beat: ").append(activeStanza.getCurrentBeatNumber()).append("\n");
        sb.append("Current Exchange: ").append(activeStanza.getCurrentExchange()).append("\n\n");

        // CHARACTERS
        sb.append("--- CHARACTERS ---\n\n");
        if (activeStanza.getCharacters().isEmpty()) {
            sb.append("  (No characters)\n\n");
        } else {
            for (StanzaCharacter character : activeStanza.getCharacters()) {
                sb.append("  ").append(character.getName());
                if (character.isUser()) sb.append(" (USER)");
                sb.append(" [").append(character.getPresenceStatus()).append("]");
                if (character.getEmotionalState() != null) {
                    sb.append(" - ").append(character.getEmotionalState());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // EVENTS (last 10)
        sb.append("--- RECENT EVENTS ---\n\n");
        List<StanzaEvent> events = activeStanza.getEvents();
        if (events.isEmpty()) {
            sb.append("  (No events recorded)\n\n");
        } else {
            int startIdx = Math.max(0, events.size() - 10);
            for (int i = startIdx; i < events.size(); i++) {
                StanzaEvent event = events.get(i);
                sb.append("  [Exchange ").append(event.getExchangeNumber()).append("] ");
                sb.append(event.isMajor() ? "MAJOR" : "minor").append(": ");
                sb.append(event.getDescription()).append("\n");
            }
            sb.append("\n  Total events: ").append(events.size()).append("\n\n");
        }

        // BEATS
        sb.append("--- BEATS ---\n\n");
        activeStanza.getBeats().forEach(beat -> {
            sb.append("  Beat ").append(beat.getBeatNumber());
            sb.append(beat.isActive() ? " (ACTIVE)" : " (completed)");
            sb.append(" [Exchange ").append(beat.getStartExchange());
            if (beat.getEndExchange() != null) {
                sb.append("-").append(beat.getEndExchange());
            }
            sb.append("]\n");
        });

        return CommandResult.handled(sb.toString());
    }

    // ========== HELPERS ==========

    private String formatStanzaSummary(Stanza stanza) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID: ").append(stanza.getId()).append("\n");

        if (stanza.getSetting() != null && !stanza.getSetting().isEmpty())
            sb.append("Setting: ").append(stanza.getSetting()).append("\n");
        if (stanza.getPremise() != null && !stanza.getPremise().isEmpty())
            sb.append("Premise: ").append(stanza.getPremise()).append("\n");
        if (stanza.getTone() != null && !stanza.getTone().isEmpty())
            sb.append("Tone: ").append(stanza.getTone()).append("\n");
        if (stanza.getCreatedAt() != null)
            sb.append("Created: ").append(stanza.getCreatedAt().toLocalDate()).append("\n");
        if (stanza.getQuickSynopsis() != null && !stanza.getQuickSynopsis().isEmpty())
            sb.append("\nSynopsis:\n").append(truncate(stanza.getQuickSynopsis(), 200)).append("\n");

        return sb.toString();
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}