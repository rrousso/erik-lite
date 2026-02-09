package com.github.rrousso.erik_lite.exceptions.configuration;

/**
 * Thrown when configuration value is invalid.
 */
public class InvalidConfigException extends ConfigurationException {

    private static final long serialVersionUID = 1L;

    public InvalidConfigException(String configKey, String invalidValue, String expectedFormat) {
        super(String.format("Invalid configuration for %s: '%s' (expected: %s)",
            configKey, invalidValue, expectedFormat));
    }
}