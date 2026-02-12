package com.github.rrousso.erik_lite.domain.models;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_lite.domain.valueobjects.LoadedStanzaMemory;
import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import lombok.Data;

/**
 * Mutable holder for the current session state.
 *
 * Tracks:
 * - Current mode (VOID = planning with Erik, STANZA = narrated story)
 * - Separate conversation histories for each mode
 * - Stanza lifecycle status
 * - Active stanza references (in-memory DTO and database ID)
 * - Completed stanza data for post-stanza reflection
 * - Loaded stanza memory from /load command
 */
@Data
public class SessionState {

    public enum Mode {
        VOID,
        STANZA
    }

    private Mode mode = Mode.VOID;
    private ConversationHistory stanzaHistory;
    private ConversationHistory voidHistory;
    private StanzaStatus stanzaStatus = StanzaStatus.NONE;
    private CompletedStanza completedStanza = null;
    private InitializedStanza initializedStanza = null;
    private Long activeStanzaId = null;
    private LoadedStanzaMemory loadedStanzaMemory = null;

    public SessionState() {
        this.stanzaHistory = new ConversationHistory();
        this.voidHistory = new ConversationHistory();
    }

    // === MODE SWITCHING ===

    public void enterVoidMode() {
        this.mode = Mode.VOID;
    }

    public void enterStanzaMode() {
        this.mode = Mode.STANZA;
    }

    public boolean isInVoidMode() {
        return mode == Mode.VOID;
    }

    public boolean isInStanzaMode() {
        return mode == Mode.STANZA;
    }

    // === CONVENIENCE QUERIES ===

    public boolean hasLoadedStanzaMemory() {
        return loadedStanzaMemory != null;
    }

    public boolean hasActiveStanza() {
        return activeStanzaId != null;
    }
}