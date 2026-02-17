package com.github.rrousso.erik_lite.dto.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for tracking state changes to existing characters.
 * 
 * Captures emotional state shifts, relationship changes,
 * and name revelations for characters already in the database.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterStateChange {
    
    @JsonProperty("characterCurrentName")
    private String characterCurrentName;
    
    @JsonProperty("characterNewName")
    private String characterNewName;
    
    @JsonProperty("newEmotionalState")
    private String newEmotionalState;
    
    @JsonProperty("updatedRelationshipToUser")
    private String updatedRelationshipToUser;

    @Override
    public String toString() {
        return String.format("CharacterStateChange[%s -> emotional: %s, relationship: %s]", 
            characterCurrentName, newEmotionalState, updatedRelationshipToUser);
    }
}