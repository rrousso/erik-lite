package com.github.rrousso.erik_lite.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a character in the initialization phase.
 * This is the DTO deserialized from the architect LLM's JSON output.
 *
 * Not to be confused with the StanzaCharacter entity (persistence layer).
 *
 * Erik-lite: No 'knows' field — fact tempId references removed.
 * The narrator sees all character info without knowledge filtering.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StanzaCharacter {

    @JsonProperty("name")
    private String name;

    @JsonProperty("canonRole")
    private String canonRole;

    @JsonProperty("currentEmotionalState")
    private String currentEmotionalState;

    @JsonProperty("relationshipToUser")
    private String relationshipToUser;

    @JsonProperty("presentInFirstScene")
    private Boolean presentInFirstScene;

    @JsonProperty("blueprint")
    private CharacterBlueprint blueprint;

    // === INNER CLASS: BLUEPRINT ===

    /**
     * Three-tiered character definition structure.
     * Gives the narrator essential character info in a compact format.
     */
    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CharacterBlueprint {

        @JsonProperty("tier1_essentials")
        private String tier1Essentials;  // Archetype & Speech Pattern

        @JsonProperty("tier2_motivators")
        private String tier2Motivators;  // Primary Goal & Major Fear

        @JsonProperty("tier3_anchors")
        private List<String> tier3Anchors = new ArrayList<>();  // 3 visual details

        public String toNarratorContext() {
            StringBuilder sb = new StringBuilder();

            if (hasValue(tier1Essentials)) {
                sb.append("Essentials: ").append(tier1Essentials).append("\n");
            }
            if (hasValue(tier2Motivators)) {
                sb.append("Motivators: ").append(tier2Motivators).append("\n");
            }
            if (tier3Anchors != null && !tier3Anchors.isEmpty()) {
                sb.append("Visual Anchors: ").append(String.join(", ", tier3Anchors)).append("\n");
            }

            return sb.toString();
        }

        private boolean hasValue(String value) {
            return value != null && !value.isEmpty();
        }
    }

    // === CONVENIENCE METHODS ===

    /**
     * Check if this character should be in the opening scene.
     */
    public boolean isPresentInFirstScene() {
        return presentInFirstScene != null && presentInFirstScene;
    }

    // === FORMAT FOR NARRATOR ===

    /**
     * Full detail format for characters in the active scene.
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("**").append(name.toUpperCase()).append("**\n");

        if (hasValue(canonRole)) {
            sb.append("Role: ").append(canonRole).append("\n");
        }

        if (hasValue(relationshipToUser)) {
            sb.append("Relationship to User: ").append(relationshipToUser).append("\n");
        }

        if (hasValue(currentEmotionalState)) {
            sb.append("Current State: ").append(currentEmotionalState).append("\n");
        }

        if (blueprint != null) {
            sb.append("\n").append(blueprint.toNarratorContext());
        }

        return sb.toString();
    }

    /**
     * Compact format for characters who might appear but aren't in the scene yet.
     */
    public String toPotentialContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(name);
        if (hasValue(canonRole)) {
            sb.append(" (").append(canonRole).append(")");
        }
        return sb.toString();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}