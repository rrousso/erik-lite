package com.github.rrousso.erik_lite.exceptions.configuration;

import com.github.rrousso.erik_lite.exceptions.ErikException;

/**
 * Base exception for configuration-related errors.
 */
public class ConfigurationException extends ErikException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}