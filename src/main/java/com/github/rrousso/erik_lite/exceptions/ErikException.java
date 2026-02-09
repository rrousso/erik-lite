package com.github.rrousso.erik_lite.exceptions;

/**
 * Base exception for all Erik-specific errors.
 * 
 * All Erik exceptions extend RuntimeException (unchecked) following Spring best practices.
 * This allows cleaner code without forcing exception handling everywhere while still
 * providing specific exception types for targeted error handling where needed.
 */
public class ErikException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ErikException(String message) {
        super(message);
    }

    public ErikException(String message, Throwable cause) {
        super(message, cause);
    }

    public ErikException(Throwable cause) {
        super(cause);
    }
}