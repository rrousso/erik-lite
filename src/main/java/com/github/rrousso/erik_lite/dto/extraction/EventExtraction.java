package com.github.rrousso.erik_lite.dto.extraction;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing an extracted event from the narrative.
 * 
 * JSON structure from LLM:
 * {
 *   "description": "brief description of what happened",
 *   "significance": "MAJOR" | "MINOR",
 *   "charactersInvolved": ["character name", ...]
 * }
 */
@Data
@NoArgsConstructor
public class EventExtraction {

    private String description;
    private String significance;
    private List<String> charactersInvolved = new ArrayList<>();

    public EventExtraction(String description, String significance) {
        this.description = description;
        this.significance = significance;
    }

    public void setCharactersInvolved(List<String> charactersInvolved) {
        this.charactersInvolved = charactersInvolved != null ? charactersInvolved : new ArrayList<>();
    }

    public boolean isMajor() {
        return "MAJOR".equalsIgnoreCase(significance);
    }

    public boolean isMinor() {
        return "MINOR".equalsIgnoreCase(significance);
    }

    @Override
    public String toString() {
        return String.format("EventExtraction[%s: %s]", significance, description);
    }
}