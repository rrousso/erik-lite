package com.github.rrousso.erik_lite.dto.initialization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight representation of a background character.
 * These exist in the world but aren't expected to appear soon.
 * Used for reference in dialogue and world-building texture.
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class BackgroundCharacter {

    @JsonProperty("name")
    private String name;

    @JsonProperty("canonRole")
    private String canonRole;

    @JsonProperty("relevanceToStanza")
    private String relevanceToStanza;

    @JsonProperty("threatOrAlly")
    private String threatOrAlly; // THREAT | ALLY | NEUTRAL | UNKNOWN

    public BackgroundCharacter(String name, String canonRole, String relevanceToStanza, String threatOrAlly) {
        this.name = name;
        this.canonRole = canonRole;
        this.relevanceToStanza = relevanceToStanza;
        this.threatOrAlly = threatOrAlly;
    }

    // === CONVENIENCE METHODS ===

    public boolean isThreat() {
        return "THREAT".equalsIgnoreCase(threatOrAlly);
    }

    public boolean isAlly() {
        return "ALLY".equalsIgnoreCase(threatOrAlly);
    }

    public boolean isUnknown() {
        return "UNKNOWN".equalsIgnoreCase(threatOrAlly);
    }
}