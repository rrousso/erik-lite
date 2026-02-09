package com.github.rrousso.erik_lite.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the world context for a stanza.
 * Includes world rules, current state, tone, and relevant locations.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorldContext {

    @JsonProperty("supernaturalRules")
    private List<String> supernaturalRules = new ArrayList<>();

    @JsonProperty("currentWorldState")
    private String currentWorldState;

    @JsonProperty("relevantLocations")
    private List<RelevantLocation> relevantLocations = new ArrayList<>();

    @JsonProperty("timeContext")
    private String timeContext;

    @JsonProperty("tone")
    private String tone;

    // === INNER CLASS ===

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RelevantLocation {

        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;

        @JsonProperty("whoMightBeThere")
        private List<String> whoMightBeThere = new ArrayList<>();

        public String toNarratorContext() {
            StringBuilder sb = new StringBuilder();
            sb.append("- **").append(name).append("**: ").append(description);
            if (!whoMightBeThere.isEmpty()) {
                sb.append(" (Might find: ").append(String.join(", ", whoMightBeThere)).append(")");
            }
            return sb.toString();
        }
    }

    // === FORMAT FOR NARRATOR ===

    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();

        if (hasValue(tone)) {
            sb.append("**TONE:** ").append(tone).append("\n\n");
        }

        if (hasValue(timeContext)) {
            sb.append("**When:** ").append(timeContext).append("\n\n");
        }

        if (hasValue(currentWorldState)) {
            sb.append("**Current World State:**\n").append(currentWorldState).append("\n\n");
        }

        if (!supernaturalRules.isEmpty()) {
            sb.append("**World Rules:**\n");
            for (String rule : supernaturalRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }

        if (!relevantLocations.isEmpty()) {
            sb.append("**Known Locations:**\n");
            for (RelevantLocation loc : relevantLocations) {
                sb.append(loc.toNarratorContext()).append("\n");
            }
        }

        return sb.toString();
    }

    // === CONVENIENCE METHODS ===

    public RelevantLocation findLocation(String name) {
        return relevantLocations.stream()
            .filter(l -> l.getName().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    public List<String> getLocationNames() {
        return relevantLocations.stream()
            .map(RelevantLocation::getName)
            .toList();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}