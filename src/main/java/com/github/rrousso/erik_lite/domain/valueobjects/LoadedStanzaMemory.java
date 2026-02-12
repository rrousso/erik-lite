package com.github.rrousso.erik_lite.domain.valueobjects;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable snapshot of a previously completed stanza, loaded via /load command.
 *
 * Carries pre-formatted strings extracted from the Stanza entity at load time.
 * This keeps the domain layer free of persistence entity dependencies —
 * the service that loads the stanza calls toNarratorContext() and getQuickSynopsis()
 * and wraps the results here.
 */
@Getter
@AllArgsConstructor
public class LoadedStanzaMemory {

    /** The stanza's full narrator context (world, characters, beats, etc.) */
    private final String narratorContext;

    /** Short narrative synopsis (~150 words) of what happened */
    private final String quickSynopsis;

    /** The world identifier (e.g., "cinderella", "teen_wolf", "original") */
    private final String worldIdentifier;

    public boolean hasQuickSynopsis() {
        return quickSynopsis != null && !quickSynopsis.isEmpty();
    }
}