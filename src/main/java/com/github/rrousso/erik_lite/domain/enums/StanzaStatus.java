package com.github.rrousso.erik_lite.domain.enums;

import lombok.Getter;

/**
 * Represents the lifecycle state of a Stanza.
 * 
 * NONE → ACTIVE → PAUSED → ACTIVE → COMPLETED
 *                                  → ABANDONED
 */
@Getter
public enum StanzaStatus {

    NONE("None", "No stanza is ongoing"),
    ACTIVE("Active", "Stanza is currently running"),
    PAUSED("Paused", "Stanza is paused, can be continued"),
    COMPLETED("Completed", "Stanza has concluded, can't be restarted"),
    ABANDONED("Abandoned", "Stanza was dropped by the user, can't be restarted");

    private final String label;
    private final String description;

    StanzaStatus(String label, String description) {
        this.label = label;
        this.description = description;
    }
}