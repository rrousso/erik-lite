package com.github.rrousso.erik_lite.exceptions.prompt;

/**
 * Thrown when prompt file cannot be found.
 */
public class PromptNotFoundException extends PromptException {

    private static final long serialVersionUID = 1L;
    private final String promptPath;

    public PromptNotFoundException(String promptPath) {
        super(String.format("Prompt file not found: prompts/%s", promptPath));
        this.promptPath = promptPath;
    }

    public String getPromptPath() {
        return promptPath;
    }
}