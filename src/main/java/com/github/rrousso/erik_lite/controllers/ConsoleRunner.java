package com.github.rrousso.erik_lite.controllers;

import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_lite.persistence.entities.Chat;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.services.chat.ChatPersistenceService;
import com.github.rrousso.erik_lite.services.command.CommandService;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.orchestration.SessionFlowService;

/**
 * Main console interface for Erik.
 *
 * Input processing order:
 * 1. Check for "exit"
 * 2. Check for "/" commands via CommandService
 * 3. Pass to SessionFlowService for LLM-based processing
 */
@Component
public class ConsoleRunner {

	private final SessionFlowService sessionFlow;
    private final CommandService commandService;
    private final ChatPersistenceService chatPersistence;
    private final PersonaService personaService;
    private final SessionState state = new SessionState();

    public ConsoleRunner(
            SessionFlowService sessionFlow,
            CommandService commandService,
            ChatPersistenceService chatPersistence,
            PersonaService personaService) {
        this.sessionFlow = sessionFlow;
        this.commandService = commandService;
        this.chatPersistence = chatPersistence;
        this.personaService = personaService;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

     // Create chat for this session and seed Erik's greeting
        String erikGreeting = "The white infinity of the Void steadies as I come into focus beside you.\n\n" +
            "\"Hey,\" I say gently. \"I'm glad you're here. " +
            "No rush. We can sit for a bit, or if you have something in mind, " +
            "tell me what you want to make and I'll help you shape it.\"";

        try {
            Persona persona = personaService.getCurrentPersona();
            Chat chat = chatPersistence.createChat(persona);
            state.setChatId(chat.getId());
        } catch (Exception e) {
            System.out.println("[System] Warning: Failed to create chat session. Messages will not be persisted.");
        }

        // Seed greeting into void history so Erik knows he said it
        state.getVoidHistory().addAssistantMessage(erikGreeting);

        // Persist the greeting
        if (state.getChatId() != null) {
            try {
                chatPersistence.saveMessage(state.getChatId(), "VOID", "assistant", erikGreeting, null);
            } catch (Exception e) {
                // Non-critical, continue
            }
        }

        System.out.println("\n=== ERIK - CREATIVE ASSISTANT ===");
        System.out.println("Commands:");
        System.out.println("  Natural language works! Try 'let's begin', 'pause', 'continue', etc.");
        System.out.println("  In stanza: use ((pause)), ((end)), etc. for out-of-character commands");
        System.out.println("  Type /help for system commands");
        System.out.println("  'exit' - close the app\n");

        System.out.println(erikGreeting + "\n");

        while (true) {
            System.out.print("> ");
            String userInput = scanner.nextLine().trim();

            if (userInput.equalsIgnoreCase("exit")) {
                System.out.println("\n[System] Exiting The Void.\n");
                break;
            }

            if (userInput.isEmpty()) continue;

            // Check for slash commands first
            CommandResult cmdResult = commandService.processCommand(userInput, state);
            if (cmdResult.wasHandled()) {
                System.out.println(cmdResult.getResponse());
                continue;
            }

            // Check if this looks like a command without the slash
            CommandResult fuzzyCheck = commandService.checkForMissingSlash(userInput, state);
            if (fuzzyCheck.wasHandled()) {
                System.out.println(fuzzyCheck.getResponse());
                continue;
            }

            // LLM-based processing
            String response = sessionFlow.handleUserInput(userInput, state);
            System.out.println(response);
        }

        scanner.close();
    }
}