package com.github.rrousso.erik_lite.util;

import java.util.Deque;
import java.util.ArrayDeque;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility for cleaning and parsing JSON responses from LLMs.
 *
 * LLMs sometimes wrap JSON in markdown code fences (```json ... ```).
 * This utility handles cleanup + parsing in one step.
 */
public class JsonCleanupUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        return repairJson(cleaned.trim());
    }

    /**
     * Attempt to repair common LLM JSON structural errors:
     * - Truncated responses (unclosed arrays/objects)
     * - Mismatched close markers (} where ] expected, or vice versa)
     *
     * Strategy: walk the string tracking open brackets; if we hit a close
     * marker that doesn't match the top of the stack, replace it with the
     * correct one; then close any still-open brackets at the end.
     */
    static String repairJson(String json) {
        if (json == null || json.isEmpty()) return json;

        StringBuilder result = new StringBuilder();
        Deque<Character> stack = new ArrayDeque<>();

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"') {
                result.append(c);
                i++;
                while (i < json.length()) {
                    char sc = json.charAt(i);
                    result.append(sc);
                    if (sc == '\\') {
                        i++; // skip escaped char
                        if (i < json.length()) result.append(json.charAt(i));
                    } else if (sc == '"') {
                        break;
                    }
                    i++;
                }
                continue;
            }

            if (c == '{' || c == '[') {
                stack.push(c);
                result.append(c);
            } else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) {
                    char expected = stack.peek() == '{' ? '}' : ']';
                    if (c != expected) {
                        result.append(expected);
                        stack.pop();
                    } else {
                        result.append(c);
                        stack.pop();
                    }
                }
            } else {
                result.append(c);
            }
        }

        while (!stack.isEmpty()) {
            char open = stack.pop();
            result.append(open == '{' ? '}' : ']');
        }

        return result.toString();
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