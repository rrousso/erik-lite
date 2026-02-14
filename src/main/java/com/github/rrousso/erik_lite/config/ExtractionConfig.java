package com.github.rrousso.erik_lite.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for state extraction behavior.
 * 
 * Controls how frequently the system extracts state changes from narrative exchanges.
 * 
 * erik-lite simplified: extracts events + character appearances only
 * (no facts, knowledge transfers, secrets, or tensions).
 */
@Configuration
@ConfigurationProperties(prefix = "erik.extraction")
@Getter @Setter
public class ExtractionConfig {

    /**
     * How often to run extraction (in exchanges).
     * 1 = every exchange, 2 = every other, 0 = disabled.
     */
    private int frequency = 1;

    /**
     * Whether extraction is enabled at all.
     */
    private boolean enabled = true;

    /**
     * Whether to always extract on stanza end, regardless of frequency.
     */
    private boolean alwaysExtractOnEnd = true;

    /**
     * Whether to always extract on stanza start, regardless of frequency.
     */
    private boolean alwaysExtractOnStart = true;

    /**
     * Check if extraction should occur for a given exchange number.
     */
    public boolean shouldExtract(int exchangeNumber, boolean isFirstExchange, boolean isFinalExchange) {
        if (!enabled) {
            return false;
        }

        if (isFirstExchange && alwaysExtractOnStart) {
            return true;
        }

        if (isFinalExchange && alwaysExtractOnEnd) {
            return true;
        }

        if (frequency == 0) {
            return false;
        }

        return exchangeNumber % frequency == 0;
    }
}