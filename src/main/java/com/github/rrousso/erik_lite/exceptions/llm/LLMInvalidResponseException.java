package com.github.rrousso.erik_lite.exceptions.llm;

/**
 * Thrown when LLM response is valid but content is empty/invalid.
 * 
 * Examples:
 * - Empty content field
 * - No choices returned
 * - Content doesn't match expected format
 */
public class LLMInvalidResponseException extends LLMException {

    private static final long serialVersionUID = 1L;
    private final String reason;

    public LLMInvalidResponseException(String reason) {
        super("Invalid LLM response: " + reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}