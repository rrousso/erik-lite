package com.github.rrousso.erik_lite.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents the complete initialized state of a stanza.
 * This is the parsed output from the Initialization Architect call.
 *
 * Contains:
 * - Tiered character presence (explicit, likely, background)
 * - User character setup
 * - World context (tone, rules, locations)
 * - Clarifications needed before starting
 *
 * Erik-lite: No facts or tensions. The narrator sees all character info
 * and is trusted to write consistently.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class InitializedStanza {

    @JsonProperty("worldIdentifier")
    private String worldIdentifier;

    @JsonProperty("userCharacter")
    private UserCharacter userCharacter;

    @JsonProperty("explicitCharacters")
    private List<StanzaCharacter> explicitCharacters = new ArrayList<>();

    @JsonProperty("likelyCharacters")
    private List<StanzaCharacter> likelyCharacters = new ArrayList<>();

    @JsonProperty("backgroundCharacters")
    private List<BackgroundCharacter> backgroundCharacters = new ArrayList<>();

    @JsonProperty("worldContext")
    private WorldContext worldContext;

    // === CONVENIENCE METHODS ===

    /**
     * Get all characters who should be present in the first scene.
     * Checks both explicit and likely tiers.
     */
    public List<StanzaCharacter> getFirstSceneCharacters() {
        return Stream.concat(explicitCharacters.stream(), likelyCharacters.stream())
                .filter(StanzaCharacter::isPresentInFirstScene)
                .toList();
    }

    /**
     * Get characters who could potentially appear (not present but relevant).
     * Checks both explicit and likely tiers.
     */
    public List<StanzaCharacter> getPotentialCharacters() {
        return Stream.concat(explicitCharacters.stream(), likelyCharacters.stream())
                .filter(c -> !c.isPresentInFirstScene())
                .toList();
    }

    /**
     * Find a character by name across explicit and likely tiers.
     * Returns null if not found.
     */
    public StanzaCharacter findCharacterByName(String name) {
        return Stream.concat(explicitCharacters.stream(), likelyCharacters.stream())
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if this is a known IP or original world.
     */
    public boolean isKnownIP() {
        return worldIdentifier != null && !worldIdentifier.equalsIgnoreCase("original");
    }

    // === FORMAT FOR NARRATOR ===

    /**
     * Convert to a narrator-friendly context string.
     * This is what gets injected into the narrator's system prompt
     * when starting from InitializedStanza (before DB persistence).
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== STANZA INITIALIZATION ===\n\n");

        // World identifier
        if (hasValue(worldIdentifier)) {
            sb.append("World: ").append(worldIdentifier.toUpperCase()).append("\n\n");
        }

        // User character
        if (userCharacter != null) {
            sb.append("=== USER CHARACTER ===\n\n");
            sb.append(userCharacter.toNarratorContext());
            sb.append("\n");
        }

        // Present characters (full context)
        List<StanzaCharacter> present = getFirstSceneCharacters();
        if (!present.isEmpty()) {
            sb.append("=== CHARACTERS IN SCENE (Full Context) ===\n\n");
            for (StanzaCharacter c : present) {
                sb.append(c.toNarratorContext());
                sb.append("\n---\n\n");
            }
        }

        // Potential characters (limited context)
        List<StanzaCharacter> potential = getPotentialCharacters();
        if (!potential.isEmpty()) {
            sb.append("=== CHARACTERS WHO MIGHT APPEAR ===\n");
            sb.append("(You MAY introduce these if narratively appropriate)\n\n");
            for (StanzaCharacter c : potential) {
                sb.append(c.toPotentialContext());
                sb.append("\n");
            }
            sb.append("\n");
        }

        // Background characters (reference only)
        if (!backgroundCharacters.isEmpty()) {
            sb.append("=== BACKGROUND CHARACTERS (Reference Only) ===\n");
            sb.append("(May be mentioned in dialogue, should NOT appear without setup)\n\n");
            for (BackgroundCharacter c : backgroundCharacters) {
                sb.append("- ").append(c.getName());
                sb.append(" (").append(c.getCanonRole()).append(")");
                sb.append(" - ").append(c.getThreatOrAlly()).append("\n");
            }
            sb.append("\n");
        }

        // World context
        if (worldContext != null) {
            sb.append("=== WORLD CONTEXT ===\n\n");
            sb.append(worldContext.toNarratorContext());
        }

        return sb.toString();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}