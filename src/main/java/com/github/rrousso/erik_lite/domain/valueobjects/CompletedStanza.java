package com.github.rrousso.erik_lite.domain.valueobjects;

import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Immutable value object representing a completed or abandoned stanza.
 *
 * Holds the quick synopsis (~150 words, narrative style) and the
 * original initialization data. Used by VOID mode to give Erik
 * context about the stanza that just ended.
 */
@Getter
@AllArgsConstructor
public class CompletedStanza {

    private final String quickSynopsis;
    private final InitializedStanza initializedStanza;
}