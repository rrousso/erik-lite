package com.github.rrousso.erik_lite.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for cleaning and parsing JSON responses from LLMs.
 *
 * LLMs sometimes wrap JSON in markdown code fences (```json ... ```).
 * This utility handles cleanup + parsing in one step.
 */
public class JsonCleanupUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Clean markdown code fences from JSON string.
     */
    public static String cleanJsonResponse(String jsonResponse) {
        if (jsonResponse == null) {
            return null;
        }

        String cleaned = jsonResponse.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Clean and parse JSON response into target type.
     */
    public static <T> T parseJson(String jsonResponse, Class<T> targetClass) throws Exception {
        String cleaned = cleanJsonResponse(jsonResponse);
        return MAPPER.readValue(cleaned, targetClass);
    }

    /**
     * Clean and parse with a custom ObjectMapper.
     */
    public static <T> T parseJson(String jsonResponse, Class<T> targetClass, ObjectMapper mapper) throws Exception {
        String cleaned = cleanJsonResponse(jsonResponse);
        return mapper.readValue(cleaned, targetClass);
    }

    /**
     * Validate that a string contains valid JSON (after cleanup).
     */
    public static boolean isValidJson(String jsonResponse) {
        try {
            String cleaned = cleanJsonResponse(jsonResponse);
            if (cleaned == null || cleaned.isEmpty()) {
                return false;
            }
            MAPPER.readTree(cleaned);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}