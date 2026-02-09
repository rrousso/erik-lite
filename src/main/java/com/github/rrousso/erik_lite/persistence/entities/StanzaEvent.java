package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * An event — something that happened in the stanza.
 *
 * Events are append-only, max 280 characters.
 * They provide chronological context for synopsis generation.
 *
 * Beat integration:
 * - Linked to beats via beat_id
 * - Minor events pruned when beat ends
 * - Major events preserved permanently
 */
@Entity
@Table(name = "stanza_events")
@Getter @Setter @NoArgsConstructor
public class StanzaEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "beat_id")
    private Beat beat;

    @Column(nullable = false, length = 280)
    private String description;

    @Column(name = "beat_number")
    private Integer beatNumber;

    @Column(name = "exchange_number")
    private Integer exchangeNumber;

    @Column(name = "involved_characters", length = 300)
    private String involvedCharacters;

    @Column(name = "is_major")
    private boolean isMajor = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public StanzaEvent(Stanza stanza, String description) {
        this.stanza = stanza;
        this.description = truncateDescription(description);
    }

    public StanzaEvent(Stanza stanza, String description, Integer beatNumber, Integer exchangeNumber) {
        this.stanza = stanza;
        this.description = truncateDescription(description);
        this.beatNumber = beatNumber;
        this.exchangeNumber = exchangeNumber;
    }

    // === CUSTOM SETTER (overrides Lombok to enforce truncation) ===

    public void setDescription(String description) {
        this.description = truncateDescription(description);
    }

    // === CUSTOM SETTER (sync beatNumber when beat is set) ===

    public void setBeat(Beat beat) {
        this.beat = beat;
        if (beat != null) {
            this.beatNumber = beat.getBeatNumber();
        }
    }

    // === CONVENIENCE METHODS ===

    private String truncateDescription(String desc) {
        if (desc == null) return "";
        if (desc.length() <= 280) return desc;
        return desc.substring(0, 277) + "...";
    }

    public String[] getInvolvedCharactersArray() {
        if (involvedCharacters == null || involvedCharacters.isEmpty()) {
            return new String[0];
        }
        return involvedCharacters.split(",");
    }

    public boolean involvesCharacter(String characterName) {
        if (involvedCharacters == null || characterName == null) {
            return false;
        }
        for (String name : involvedCharacters.split(",")) {
            if (name.trim().equalsIgnoreCase(characterName)) {
                return true;
            }
        }
        return false;
    }
}