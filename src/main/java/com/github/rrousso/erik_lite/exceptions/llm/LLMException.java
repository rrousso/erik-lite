package com.github.rrousso.erik_lite.exceptions.llm;

import com.github.rrousso.erik_lite.exceptions.ErikException;

/**
 * Base exception for all LLM-related errors.
 */
public class LLMException extends ErikException {

    private static final long serialVersionUID = 1L;

    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}