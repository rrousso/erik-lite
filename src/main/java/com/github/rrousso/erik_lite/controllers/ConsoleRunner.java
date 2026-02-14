package com.github.rrousso.erik_lite.controllers;

import java.util.Scanner;

import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.domain.valueobjects.CommandResult;
import com.github.rrousso.erik_lite.services.command.CommandService;
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
    private final SessionState state = new SessionState();

    public ConsoleRunner(
            SessionFlowService sessionFlow,
            CommandService commandService) {
        this.sessionFlow = sessionFlow;
        this.commandService = commandService;
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n=== ERIK - CREATIVE ASSISTANT ===");
        System.out.println("Commands:");
        System.out.println("  Natural language works! Try 'let's begin', 'pause', 'continue', etc.");
        System.out.println("  In stanza: use ((pause)), ((end)), etc. for out-of-character commands");
        System.out.println("  Type /help for system commands");
        System.out.println("  'exit' - close the app\n");

        System.out.println(
            "The white infinity of the Void steadies as I come into focus beside you.\n\n" +
            "\"Hey,\" I say gently. \"I'm glad you're here. " +
            "No rush. We can sit for a bit, or if you have something in mind, " +
            "tell me what you want to make and I'll help you shape it.\"\n");

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

            // LLM-based processing
            String response = sessionFlow.handleUserInput(userInput, state);
            System.out.println(response);
        }

        scanner.close();
    }
}