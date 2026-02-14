package com.github.rrousso.erik_lite.dto.extraction;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a character appearance change.
 * 
 * JSON structure from LLM:
 * {
 *   "characterName": "name",
 *   "changeType": "APPEARED" | "LEFT" | "MENTIONED",
 *   "context": "brief context"
 * }
 */
@Data
@NoArgsConstructor
public class CharacterAppearance {

    private String characterName;
    private String changeType;
    private String context;

    public CharacterAppearance(String characterName, String changeType, String context) {
        this.characterName = characterName;
        this.changeType = changeType;
        this.context = context;
    }

    public boolean isAppearance() {
        return "APPEARED".equalsIgnoreCase(changeType);
    }

    public boolean isDeparture() {
        return "LEFT".equalsIgnoreCase(changeType);
    }

    public boolean isMention() {
        return "MENTIONED".equalsIgnoreCase(changeType);
    }

    @Override
    public String toString() {
        return String.format("CharacterAppearance[%s: %s - %s]", characterName, changeType, context);
    }
}