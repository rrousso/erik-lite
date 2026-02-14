package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A beat (scene) in a stanza.
 *
 * Beats are user-controlled narrative boundaries — like scenes in a screenplay.
 * They mark transitions in location, time, or perspective.
 *
 * Lifecycle:
 * 1. Created via ((next beat: context))
 * 2. Active while endExchange is null
 * 3. Ends when next beat starts or stanza ends
 * 4. Summary generated from events, then minor events pruned
 */
@Entity
@Table(name = "beats")
@Getter @Setter @NoArgsConstructor
public class Beat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;

    @Column(name = "beat_number", nullable = false)
    private Integer beatNumber;

    @Column(name = "start_exchange", nullable = false)
    private Integer startExchange;

    @Column(name = "end_exchange")
    private Integer endExchange; // null = active beat

    @Column(name = "transition_context", columnDefinition = "TEXT")
    private String transitionContext;
    
    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "location_description", columnDefinition = "TEXT")
    private String locationDescription;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Beat(Stanza stanza, Integer beatNumber, Integer startExchange) {
        this.stanza = stanza;
        this.beatNumber = beatNumber;
        this.startExchange = startExchange;
    }

    // === CONVENIENCE METHODS ===

    public boolean isActive() {
        return endExchange == null;
    }

    public void end(Integer finalExchange, String summary) {
        this.endExchange = finalExchange;
        this.summary = summary;
        this.completedAt = LocalDateTime.now();
    }

    public String getLabel() {
        StringBuilder label = new StringBuilder();
        label.append("Beat ").append(beatNumber);
        label.append(" (Exchanges ").append(startExchange);
        if (endExchange != null) {
            label.append("-").append(endExchange);
        } else {
            label.append("+");
        }
        label.append(")");
        return label.toString();
    }

    public String getTransitionContextOrDefault() {
        if (transitionContext != null && !transitionContext.isEmpty()) {
            return transitionContext;
        }
        return beatNumber == 1 ? "Opening scene" : "Scene transition";
    }
    
    public boolean hasLocation() {
        return locationName != null && !locationName.isEmpty();
    }

    public String getLocationForNarrator() {
        if (!hasLocation()) return null;
        if (locationDescription != null && !locationDescription.isEmpty()) {
            return locationName + " — " + locationDescription;
        }
        return locationName;
    }
}