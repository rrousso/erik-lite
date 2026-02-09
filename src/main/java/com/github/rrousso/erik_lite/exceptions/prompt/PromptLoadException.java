package com.github.rrousso.erik_lite.exceptions.prompt;

/**
 * Thrown when prompt file exists but cannot be loaded.
 */
public class PromptLoadException extends PromptException {

    private static final long serialVersionUID = 1L;

    public PromptLoadException(String promptPath, Throwable cause) {
        super(String.format("Failed to load prompt: prompts/%s", promptPath), cause);
    }
}