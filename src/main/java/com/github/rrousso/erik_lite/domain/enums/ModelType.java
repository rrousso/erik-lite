package com.github.rrousso.erik_lite.domain.enums;

/**
 * Enum representing different types of LLM models used in the system.
 * Each type is optimized for specific tasks.
 */
public enum ModelType {
    /**
     * High-quality creative model for narrative generation.
     * Used for: Erik (void mode), Narrator (stanza mode), detailed synopsis
     * Model: Claude Sonnet
     */
    NARRATIVE("narrative"),

    /**
     * Fast, efficient model for analytical tasks.
     * Used for: Flag detection, stanza extraction, quick synopsis, change detection
     * Model: Gemini Flash
     */
    ANALYTICAL("analytical");

    private final String configKey;

    ModelType(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }
}