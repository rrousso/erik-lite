package com.github.rrousso.erik_lite.dto.extraction;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Character blueprint structure for emergent characters.
 * Matches the nested blueprint object in extraction JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CharacterBlueprint {
    
    @JsonProperty("tier1_essentials")
    private String tier1Essentials;
    
    @JsonProperty("tier2_motivators")
    private String tier2Motivators;
    
    @JsonProperty("tier3_anchors")
    private String[] tier3Anchors =  new String[0];

}