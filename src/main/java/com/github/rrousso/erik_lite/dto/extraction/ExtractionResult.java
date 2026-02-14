package com.github.rrousso.erik_lite.dto.extraction;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Main DTO for extraction results from the analytical LLM.
 * 
 * erik-lite simplified: events + character appearances + emergent characters only.
 * Dropped: factDiscoveries, secretRevelations, tensionChanges, blueprintUpdates.
 */
@Data
@NoArgsConstructor
public class ExtractionResult {

    private List<EventExtraction> events = new ArrayList<>();
    private List<CharacterAppearance> characterAppearances = new ArrayList<>();
    private List<EmergentCharacter> emergentCharacters = new ArrayList<>();

    // === Null-safe setters (override Lombok for defensive copies) ===

    public void setEvents(List<EventExtraction> events) {
        this.events = events != null ? events : new ArrayList<>();
    }

    public void setCharacterAppearances(List<CharacterAppearance> characterAppearances) {
        this.characterAppearances = characterAppearances != null ? characterAppearances : new ArrayList<>();
    }

    public void setEmergentCharacters(List<EmergentCharacter> emergentCharacters) {
        this.emergentCharacters = emergentCharacters != null ? emergentCharacters : new ArrayList<>();
    }

    // === Convenience methods ===

    public boolean hasAnyChanges() {
        return !events.isEmpty() || !characterAppearances.isEmpty() || !emergentCharacters.isEmpty();
    }

    public int getTotalChangeCount() {
        return events.size() + characterAppearances.size() + emergentCharacters.size();
    }

    @Override
    public String toString() {
        return String.format("ExtractionResult[events=%d, appearances=%d, emergent=%d]",
            events.size(), characterAppearances.size(), emergentCharacters.size());
    }
}