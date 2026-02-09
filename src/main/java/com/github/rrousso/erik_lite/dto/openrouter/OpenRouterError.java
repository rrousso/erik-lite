package com.github.rrousso.erik_lite.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for OpenRouter API error response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenRouterError {

    private ErrorDetail error;

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrorDetail {
        private String message;
        private String type;
        private String code;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        @Override
        public String toString() {
            return String.format("OpenRouter API Error [%s]: %s (code: %s)",
                type != null ? type : "unknown",
                message != null ? message : "no message",
                code != null ? code : "none");
        }
    }

    @Override
    public String toString() {
        return error != null ? error.toString() : "OpenRouter API Error (no details)";
    }
}