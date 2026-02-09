package com.github.rrousso.erik_lite.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the user's character in the stanza.
 * Separates public (observable) information from private (narrator-only) backstory.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCharacter {

    @JsonProperty("publicRole")
    private String publicRole;

    @JsonProperty("privateBackstory")
    private String privateBackstory;

    @JsonProperty("currentLocation")
    private String currentLocation;

    @JsonProperty("currentGoals")
    private List<String> currentGoals = new ArrayList<>();

    @JsonProperty("publiclyVisibleTraits")
    private List<String> publiclyVisibleTraits = new ArrayList<>();

    // === FORMAT FOR NARRATOR ===

    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("**PUBLIC ROLE (What characters can observe):**\n");
        if (hasValue(publicRole)) {
            sb.append(publicRole).append("\n");
        }
        sb.append("\n");

        if (!publiclyVisibleTraits.isEmpty()) {
            sb.append("**Visible Traits:**\n");
            for (String trait : publiclyVisibleTraits) {
                sb.append("- ").append(trait).append("\n");
            }
            sb.append("\n");
        }

        if (hasValue(currentLocation)) {
            sb.append("**Current Location:** ").append(currentLocation).append("\n\n");
        }

        if (!currentGoals.isEmpty()) {
            sb.append("**Current Goals:**\n");
            for (String goal : currentGoals) {
                sb.append("- ").append(goal).append("\n");
            }
            sb.append("\n");
        }

        if (hasValue(privateBackstory)) {
            sb.append("**PRIVATE BACKSTORY (Narrator-only, characters do NOT know this):**\n");
            sb.append(privateBackstory).append("\n");
            sb.append("\nCRITICAL: This information is SECRET. ");
            sb.append("Characters cannot know, sense, or infer ");
            sb.append("any of this unless the user explicitly reveals it through dialogue or actions.\n");
        }

        return sb.toString();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}