package com.github.rrousso.erik_lite.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "erik")
public class ErikProperties {

    private NarrativeConfig narrative = new NarrativeConfig();
    private AnalyticalConfig analytical = new AnalyticalConfig();
    private DebugConfig debug = new DebugConfig();

    private String apiKey;
    private int roundWindowSize = 6;
    private int roundThresholdSize = 18;

    /**
     * API key resolution: property first, then environment variable.
     */
    public String getApiKey() {
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("${OPENROUTER_API_KEY}")) {
            return System.getenv("OPENROUTER_API_KEY");
        }
        return apiKey;
    }

    @Data
    public static class NarrativeConfig {
        private String model = "google/gemini-2.5-pro";
        private double temperature = 0.4;
        private int maxTokens = 3000;
    }

    @Data
    public static class AnalyticalConfig {
        private String model = "google/gemini-2.5-flash";
        private double temperature = 0.3;
        private int maxTokens = 6000;
    }

    @Data
    public static class DebugConfig {
        private boolean enabled = false;
        private String outputDir = "user_data/debug";
    }
}