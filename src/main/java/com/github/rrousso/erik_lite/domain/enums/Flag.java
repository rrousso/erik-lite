package com.github.rrousso.erik_lite.domain.enums;

/**
 * Flags detected in user input that trigger special behavior.
 * These are OOC (out of character) commands that affect the stanza lifecycle.
 */
public enum Flag {

    /** No special flag detected - normal narration */
    NONE,

    /** User wants to start the stanza (begin narration) */
    START_STANZA,

    /** User wants to pause the stanza to discuss changes with Erik */
    PAUSE_STANZA,

    /** User wants to resume a paused stanza */
    CONTINUE_STANZA,

    /** User wants to end the stanza (complete it) */
    END_STANZA,

    /** User wants to abandon the stanza (discard it) */
    ABANDON_STANZA,

    /**
     * User wants to transition to a new beat (scene change).
     * Examples:
     * - "I sit down." ((next beat: Let's see what the pack is doing))
     * - ((next beat: Time skip to evening))
     */
    NEXT_BEAT
}