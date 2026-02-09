package com.github.rrousso.erik_lite.exceptions.configuration;

/**
 * Thrown when required configuration is missing.
 */
public class MissingConfigException extends ConfigurationException {

    private static final long serialVersionUID = 1L;
    private final String configKey;
    private final String howToFix;

    public MissingConfigException(String configKey, String howToFix) {
        super(String.format("Missing required configuration: %s%nHow to fix: %s",
            configKey, howToFix));
        this.configKey = configKey;
        this.howToFix = howToFix;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getHowToFix() {
        return howToFix;
    }
}