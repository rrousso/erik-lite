package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A versioned snapshot of the rolling synopsis for a stanza.
 *
 * A new row is created each time synopsis generation runs (every ~N exchanges).
 * The "current" synopsis is the latest row by exchange_number.
 * For undo/revert, load the most recent row where exchange_number < target.
 */
@Entity
@Table(name = "synopsis_history")
@Getter @Setter @NoArgsConstructor
public class SynopsisSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;

    @Column(name = "exchange_number", nullable = false)
    private int exchangeNumber;

    @Column(name = "synopsis_text", nullable = false, columnDefinition = "TEXT")
    private String synopsisText;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SynopsisSnapshot(Stanza stanza, int exchangeNumber, String synopsisText) {
        this.stanza = stanza;
        this.exchangeNumber = exchangeNumber;
        this.synopsisText = synopsisText;
    }
}