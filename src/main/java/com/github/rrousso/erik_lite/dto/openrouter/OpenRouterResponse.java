package com.github.rrousso.erik_lite.dto.openrouter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for OpenRouter API successful response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenRouterResponse {

    private String id;
    private String model;
    private List<Choice> choices;

    @JsonProperty("created")
    private Long created;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Long getCreated() {
        return created;
    }

    public void setCreated(Long created) {
        this.created = created;
    }

    /**
     * Helper method to extract content from first choice.
     * Returns null if no choices available.
     */
    public String getContent() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }

        Choice firstChoice = choices.get(0);
        if (firstChoice == null || firstChoice.getMessage() == null) {
            return null;
        }

        return firstChoice.getMessage().getContent();
    }
}