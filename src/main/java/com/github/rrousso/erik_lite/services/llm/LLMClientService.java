package com.github.rrousso.erik_lite.services.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.dto.openrouter.OpenRouterError;
import com.github.rrousso.erik_lite.dto.openrouter.OpenRouterResponse;
import com.github.rrousso.erik_lite.exceptions.configuration.MissingConfigException;
import com.github.rrousso.erik_lite.exceptions.llm.LLMApiException;
import com.github.rrousso.erik_lite.exceptions.llm.LLMInvalidResponseException;
import com.github.rrousso.erik_lite.exceptions.llm.LLMParsingException;
import com.github.rrousso.erik_lite.services.config.LLMConfigService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Spring-managed LLM client service with support for multiple model types.
 * Routes calls to appropriate models based on task type.
 * 
 * Uses Jackson ObjectMapper for robust JSON parsing.
 */
@Service
public class LLMClientService {

    private static final Logger log = LoggerFactory.getLogger(LLMClientService.class);

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;

    private final HttpClient client;
    private final LLMConfigService configService;
    private final ObjectMapper objectMapper;

    public LLMClientService(LLMConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();

        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

        log.info("LLMClientService initialized with {}s connect timeout and {}s request timeout",
            CONNECT_TIMEOUT_SECONDS, REQUEST_TIMEOUT_SECONDS);
    }

    /**
     * Simple system + user prompt call (legacy method - defaults to NARRATIVE)
     */
    public String callNarrator(String systemPrompt, String userPrompt) {
        return call(ModelType.NARRATIVE, systemPrompt, userPrompt);
    }

    /**
     * Simple system + user prompt call with model type selection
     */
    public String call(ModelType modelType, String systemPrompt, String userPrompt) {
        Objects.requireNonNull(modelType, "modelType cannot be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(userPrompt, "userPrompt cannot be null");

        ModelConfig config = getModelConfig(modelType);

        log.info("Preparing simple call to {} model: {}", modelType, config.model);

        String body = buildRequestBody(config, systemPrompt, userPrompt);

        log.debug("Request body length: {} chars", body.length());

        return sendRequest(body, modelType.toString());
    }

    /**
     * Call with full conversation history (defaults to NARRATIVE)
     */
    public String callWithHistory(
            String systemPrompt,
            String userPrompt,
            List<ConversationHistory.Message> history) {
        return callWithHistory(ModelType.NARRATIVE, systemPrompt, userPrompt, history);
    }

    /**
     * Call with full conversation history and model type selection
     */
    public String callWithHistory(
            ModelType modelType,
            String systemPrompt,
            String userPrompt,
            List<ConversationHistory.Message> history) {

        Objects.requireNonNull(modelType, "modelType cannot be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
        Objects.requireNonNull(history, "history cannot be null");

        ModelConfig config = getModelConfig(modelType);

        log.info("Preparing callWithHistory to {} model: {} with {} history messages",
            modelType, config.model, history.size());
        String body = buildRequestBodyWithHistory(config, systemPrompt, userPrompt, history);

        log.debug("Request body length: {} chars", body.length());

        return sendRequest(body, modelType.toString());
    }

    /**
     * Get model configuration based on type
     */
    private ModelConfig getModelConfig(ModelType modelType) {
        return switch (modelType) {
            case NARRATIVE -> new ModelConfig(
                configService.getNarrativeConfig().getModel(),
                configService.getNarrativeConfig().getTemperature(),
                configService.getNarrativeConfig().getMaxTokens()
            );
            case ANALYTICAL -> new ModelConfig(
                configService.getAnalyticalConfig().getModel(),
                configService.getAnalyticalConfig().getTemperature(),
                configService.getAnalyticalConfig().getMaxTokens()
            );
        };
    }

    /**
     * Shared request sending logic
     */
    private String sendRequest(String body, String modelType) {
        String apiKey = configService.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("API key not configured");
            throw new MissingConfigException("OPENROUTER_API_KEY",
                    "Set environment variable or add to application.properties");
        }

        log.debug("Sending request to OpenRouter API...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "http://localhost")
            .header("X-Title", "Erik-Lite")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("Network error calling OpenRouter API", e);
            throw new LLMApiException("Network error calling LLM API", e);
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();
            throw new LLMApiException("Request interrupted", e);
        }

        log.info("Received response (HTTP {})", response.statusCode());
        log.debug("Response body length: {} chars", response.body().length());

        String extractedContent = parseResponse(response.body());

        return extractedContent;
    }

    /**
     * Build JSON request body using Jackson for safe serialization.
     */
    private String buildRequestBody(ModelConfig config, String systemPrompt, String userPrompt) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.model);
            root.put("temperature", config.temperature);
            root.put("max_tokens", config.maxTokens);

            ArrayNode messages = root.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LLMApiException("Failed to build request JSON", e);
        }
    }

    /**
     * Build JSON request body with conversation history using Jackson.
     */
    private String buildRequestBodyWithHistory(
            ModelConfig config,
            String systemPrompt,
            String userPrompt,
            List<ConversationHistory.Message> history) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", config.model);
            root.put("temperature", config.temperature);
            root.put("max_tokens", config.maxTokens);

            ArrayNode messages = root.putArray("messages");

            // System message
            messages.addObject().put("role", "system").put("content", systemPrompt);

            // Conversation history
            for (ConversationHistory.Message msg : history) {
                messages.addObject().put("role", msg.getRole()).put("content", msg.getContent());
            }

            // Final user prompt (if not empty)
            if (userPrompt != null && !userPrompt.isEmpty()) {
                messages.addObject().put("role", "user").put("content", userPrompt);
            }

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LLMApiException("Failed to build request JSON with history", e);
        }
    }

    /**
     * Parse OpenRouter API response using Jackson.
     * 
     * Uses JSON tree parsing to reliably detect error responses
     * (avoids false positives from content containing the word "error").
     */
    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // Check for top-level "error" key structurally
            if (root.has("error")) {
                OpenRouterError errorResponse = objectMapper.treeToValue(root, OpenRouterError.class);
                String errorMsg = errorResponse.toString();
                log.error("OpenRouter API returned error: {}", errorMsg);
                throw new LLMApiException("OpenRouter API returned error", errorMsg);
            }

            // Parse as successful response
            OpenRouterResponse successResponse = objectMapper.treeToValue(root, OpenRouterResponse.class);

            String content = successResponse.getContent();

            if (content == null) {
                log.error("No content in OpenRouter response. Choices: {}",
                    successResponse.getChoices() != null ? successResponse.getChoices().size() : 0);
                throw new LLMInvalidResponseException("No content in response");
            }

            log.debug("Successfully extracted content ({} chars)", content.length());
            return content;

        } catch (LLMApiException | LLMInvalidResponseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response", e);
            log.debug("Response body: {}", responseBody);
            throw new LLMParsingException("OpenRouter API response", responseBody, e);
        }
    }

    /**
     * Get current narrative model name
     */
    public String getNarrativeModel() {
        return configService.getNarrativeConfig().getModel();
    }

    /**
     * Get current analytical model name
     */
    public String getAnalyticalModel() {
        return configService.getAnalyticalConfig().getModel();
    }

    /**
     * Internal class to hold model configuration
     */
    private record ModelConfig(String model, double temperature, int maxTokens) {}
}