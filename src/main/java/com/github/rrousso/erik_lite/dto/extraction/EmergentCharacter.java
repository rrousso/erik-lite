package com.github.rrousso.erik_lite.dto.extraction;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for emergent characters detected during extraction.
 * 
 * When the narrator introduces a character not in the setup,
 * the analytical LLM provides a minimal character definition.
 */
@Data
@NoArgsConstructor
public class EmergentCharacter {

    private String characterName;
    private String canonRole;
    private String currentEmotionalState;
    private String relationshipToUser;
    private String hiddenBackstory;
    private String physicalDescription;

    @Override
    public String toString() {
        return String.format("EmergentCharacter[%s: %s]", characterName, canonRole);
    }
}