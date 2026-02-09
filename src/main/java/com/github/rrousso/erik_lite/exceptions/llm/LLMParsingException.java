package com.github.rrousso.erik_lite.exceptions.llm;

/**
 * Thrown when LLM response cannot be parsed.
 * 
 * Examples:
 * - Invalid JSON
 * - Unexpected response format
 * - Missing required fields
 */
public class LLMParsingException extends LLMException {

    private static final long serialVersionUID = 1L;
    private final String responseBody;

    public LLMParsingException(String message, String responseBody, Throwable cause) {
        super(String.format("%s (response length: %d chars)", message,
            responseBody != null ? responseBody.length() : 0), cause);
        this.responseBody = responseBody;
    }

    public LLMParsingException(String message, Throwable cause) {
        super(message, cause);
        this.responseBody = null;
    }

    public String getResponseBody() {
        return responseBody;
    }
}