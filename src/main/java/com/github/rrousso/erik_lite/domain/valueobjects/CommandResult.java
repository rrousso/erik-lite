package com.github.rrousso.erik_lite.domain.valueobjects;

/**
 * Result of a command execution.
 * Commands are deterministic operations that bypass LLM processing.
 */
public class CommandResult {

    private final boolean handled;
    private final String response;

    private CommandResult(boolean handled, String response) {
        this.handled = handled;
        this.response = response;
    }

    public static CommandResult handled(String response) {
        return new CommandResult(true, response);
    }

    public static CommandResult notACommand() {
        return new CommandResult(false, "");
    }

    public boolean wasHandled() {
        return handled;
    }

    public String getResponse() {
        return response;
    }
}