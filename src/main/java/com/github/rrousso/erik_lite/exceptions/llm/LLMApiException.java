package com.github.rrousso.erik_lite.exceptions.llm;

/**
 * Thrown when LLM API call fails (network, timeout, HTTP error).
 * 
 * Examples:
 * - Network connection failed
 * - Request timeout
 * - HTTP 500 error
 * - API returned error response
 */
public class LLMApiException extends LLMException {

    private static final long serialVersionUID = 1L;
    private final int statusCode;
    private final String apiErrorMessage;

    public LLMApiException(String message, int statusCode) {
        super(String.format("%s (HTTP %d)", message, statusCode));
        this.statusCode = statusCode;
        this.apiErrorMessage = null;
    }

    public LLMApiException(String message, String apiErrorMessage) {
        super(String.format("%s: %s", message, apiErrorMessage));
        this.statusCode = 0;
        this.apiErrorMessage = apiErrorMessage;
    }

    public LLMApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.apiErrorMessage = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getApiErrorMessage() {
        return apiErrorMessage;
    }
}