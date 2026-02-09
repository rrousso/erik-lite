package com.github.rrousso.erik_lite.exceptions.stanza;

import com.github.rrousso.erik_lite.exceptions.ErikException;

/**
 * Base exception for stanza-related errors.
 */
public class StanzaException extends ErikException {

    private static final long serialVersionUID = 1L;

    public StanzaException(String message) {
        super(message);
    }

    public StanzaException(String message, Throwable cause) {
        super(message, cause);
    }
}