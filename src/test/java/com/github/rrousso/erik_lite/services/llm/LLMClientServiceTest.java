package com.github.rrousso.erik_lite.services.llm;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_lite.dto.openrouter.Choice;
import com.github.rrousso.erik_lite.dto.openrouter.Message;
import com.github.rrousso.erik_lite.dto.openrouter.OpenRouterError;
import com.github.rrousso.erik_lite.dto.openrouter.OpenRouterResponse;

/**
 * Unit tests for OpenRouter API response DTOs and Jackson parsing.
 */
@DisplayName("OpenRouter DTO Tests")
public class LLMClientServiceTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should parse successful response with content")
    void shouldParseSuccessfulResponse() throws Exception {
        String jsonResponse = """
        {
          "id": "gen-123",
          "model": "anthropic/claude-sonnet-4.5",
          "created": 1234567890,
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Hello, I am Claude!"
              },
              "finish_reason": "stop"
            }
          ]
        }
        """;

        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);

        assertNotNull(response);
        assertEquals("gen-123", response.getId());
        assertEquals("anthropic/claude-sonnet-4.5", response.getModel());

        assertNotNull(response.getChoices());
        assertEquals(1, response.getChoices().size());

        Choice choice = response.getChoices().get(0);
        assertEquals(0, choice.getIndex());
        assertEquals("stop", choice.getFinishReason());

        Message message = choice.getMessage();
        assertNotNull(message);
        assertEquals("assistant", message.getRole());
        assertEquals("Hello, I am Claude!", message.getContent());

        assertEquals("Hello, I am Claude!", response.getContent());
    }

    @Test
    @DisplayName("Should parse error response")
    void shouldParseErrorResponse() throws Exception {
        String jsonError = """
        {
          "error": {
            "message": "Rate limit exceeded",
            "type": "rate_limit_error",
            "code": 429
          }
        }
        """;

        OpenRouterError error = objectMapper.readValue(jsonError, OpenRouterError.class);

        assertNotNull(error);
        assertNotNull(error.getError());
        assertEquals("Rate limit exceeded", error.getError().getMessage());
    }

    @Test
    @DisplayName("Should handle response with empty choices")
    void shouldHandleResponseWithEmptyChoices() throws Exception {
        String jsonResponse = """
        {
          "id": "gen-456",
          "model": "test-model",
          "choices": []
        }
        """;

        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);

        assertNotNull(response);
        assertTrue(response.getChoices().isEmpty());
        assertNull(response.getContent());
    }

    @Test
    @DisplayName("Should ignore unknown fields in response")
    void shouldIgnoreUnknownFields() throws Exception {
        String jsonResponse = """
        {
          "id": "gen-789",
          "model": "test-model",
          "unknown_field": "should be ignored",
          "choices": [
            {
              "index": 0,
              "message": {
                "role": "assistant",
                "content": "Test"
              },
              "finish_reason": "stop",
              "extra_field": true
            }
          ]
        }
        """;

        OpenRouterResponse response = objectMapper.readValue(jsonResponse, OpenRouterResponse.class);

        assertNotNull(response);
        assertEquals("Test", response.getContent());
    }
}