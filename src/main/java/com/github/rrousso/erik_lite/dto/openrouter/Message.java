package com.github.rrousso.erik_lite.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for a message in OpenRouter API response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    private String role;
    private String content;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}