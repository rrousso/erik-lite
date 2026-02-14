package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Main stanza container — the living state of a narrative session.
 *
 * Updated every exchange throughout the stanza's lifetime.
 * Erik-lite: No fact registry or character knowledge tracking.
 * The narrator receives character info, tensions, and synopsis directly.
 */
@Entity
@Table(name = "stanzas")
@Getter @Setter @NoArgsConstructor
public class Stanza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @Column(name = "world_identifier", length = 100)
    private String worldIdentifier;

    @Column(length = 20)
    private String status = "active"; // active, paused, completed, abandoned

    // === WORLD CONTEXT ===
    @Column(name = "time_context", length = 500)
    private String timeContext;

    @Column(name = "world_state", length = 1000)
    private String worldState;

    @Column(name = "world_rules", columnDefinition = "TEXT[]")
    private String[] worldRules;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String locations;

    // === SEARCH FIELDS ===
    @Column(length = 500)
    private String setting;

    @Column(length = 1000)
    private String premise;

    @Column(length = 200)
    private String tone;

    @Column(name = "quick_synopsis", length = 2000)
    private String quickSynopsis;

    // === TRACKING ===
    @Column(name = "current_beat")
    private Integer currentBeat = 1;

    @Column(name = "current_exchange")
    private Integer currentExchange = 0;

    // === RELATIONSHIPS ===
    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("beatNumber ASC")
    private List<Beat> beats = new ArrayList<>();

    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaCharacter> characters = new ArrayList<>();

    @OneToMany(mappedBy = "stanza", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StanzaEvent> events = new ArrayList<>();

    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Stanza(Persona persona, String worldIdentifier) {
        this.persona = persona;
        this.worldIdentifier = worldIdentifier;
    }

    // === STATUS ===

    public boolean isActive() { return "active".equals(status); }
    public boolean isPaused() { return "paused".equals(status); }
    public boolean isCompleted() { return "completed".equals(status); }
    public boolean isAbandoned() { return "abandoned".equals(status); }

    // === EXCHANGE TRACKING ===

    public void incrementExchange() { this.currentExchange++; }

    /**
     * Get the current beat NUMBER (not the Beat object).
     * Use getCurrentBeat() for the entity.
     */
    public Integer getCurrentBeatNumber() { return currentBeat; }

    // === BEAT MANAGEMENT ===

    public void initializeFirstBeat() {
        if (!beats.isEmpty()) return;
        Beat firstBeat = new Beat(this, 1, 1);
        firstBeat.setTransitionContext("Opening scene");
        beats.add(firstBeat);
    }

    public Beat getCurrentBeat() {
        return beats.stream()
                .filter(Beat::isActive)
                .findFirst()
                .orElse(null);
    }

    public List<Beat> getCompletedBeats() {
        return beats.stream()
                .filter(b -> !b.isActive())
                .sorted(Comparator.comparing(Beat::getBeatNumber))
                .collect(Collectors.toList());
    }

    public Beat closeCurrentBeat() {
        Beat current = getCurrentBeat();
        if (current == null) {
            throw new IllegalStateException("No active beat to close");
        }
        current.setEndExchange(this.currentExchange);
        return current;
    }

    public Beat startNextBeat(String transitionContext) {
        closeCurrentBeat();
        this.currentBeat++;

        Beat newBeat = new Beat(this, this.currentBeat, this.currentExchange + 1);
        newBeat.setTransitionContext(transitionContext);
        beats.add(newBeat);
        return newBeat;
    }

    public void endCurrentBeat(String summary) {
        Beat current = getCurrentBeat();
        if (current == null) return;
        current.end(this.currentExchange, summary);
        deleteMinorEventsFromBeat(current);
    }

    /**
     * Finalize a closed beat and start a new one.
     * The beat must already be closed (endExchange set).
     *
     * @param closedBeat        The beat to finalize
     * @param summary           The generated summary for the beat
     * @param transitionContext  Context for the new beat
     */
    public void finalizeBeatAndStartNew(Beat closedBeat, String summary, String transitionContext) {
        if (closedBeat.getEndExchange() == null) {
            throw new IllegalStateException("Beat must be closed before finalizing");
        }

        closedBeat.setSummary(summary);
        closedBeat.setCompletedAt(LocalDateTime.now());

        deleteMinorEventsFromBeat(closedBeat);

        int nextBeatNumber = beats.stream()
                .map(Beat::getBeatNumber)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        this.currentBeat = nextBeatNumber;

        Beat newBeat = new Beat(this, nextBeatNumber, this.currentExchange + 1);
        newBeat.setTransitionContext(transitionContext);
        beats.add(newBeat);
    }
    
    private void deleteMinorEventsFromBeat(Beat beat) {
        events.removeIf(event ->
                event.getBeat() != null &&
                event.getBeat().equals(beat) &&
                !event.isMajor()
        );
    }

    // === EVENT QUERIES ===

    public List<StanzaEvent> getEventsForBeat(Beat beat) {
        return events.stream()
                .filter(e -> e.getBeat() != null && e.getBeat().equals(beat))
                .sorted(Comparator.comparing(StanzaEvent::getExchangeNumber))
                .collect(Collectors.toList());
    }

    public List<StanzaEvent> getCurrentBeatEvents() {
        Beat current = getCurrentBeat();
        if (current == null) return new ArrayList<>();
        return getEventsForBeat(current);
    }

    // === CHARACTER QUERIES ===

    public StanzaCharacter getUserCharacter() {
        return characters.stream()
                .filter(StanzaCharacter::isUser)
                .findFirst()
                .orElse(null);
    }

    public List<StanzaCharacter> getPresentCharacters() {
        return characters.stream()
                .filter(c -> "present".equals(c.getPresenceStatus()))
                .toList();
    }

    public List<StanzaCharacter> getPotentialCharacters() {
        return characters.stream()
                .filter(c -> "potential".equals(c.getPresenceStatus()))
                .toList();
    }
    
    /**
     * Find a character by name (case-insensitive, with fuzzy matching).
     *
     * Matching priority:
     * 1. Exact match (case-insensitive)
     * 2. DB name starts with search name (e.g., "Rafael" matches "Rafael DeSantis")
     * 3. Search name starts with DB name (e.g., "Rafael DeSantis" matches "Rafael")
     *
     * Returns empty if no match or if multiple ambiguous matches at the same priority.
     */
    public Optional<StanzaCharacter> findCharacterByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        String searchLower = name.trim().toLowerCase();

        // Priority 1: Exact match
        for (StanzaCharacter c : characters) {
            if (c.getName().equalsIgnoreCase(searchLower)) {
                return Optional.of(c);
            }
        }

        // Priority 2: DB name starts with search name
        List<StanzaCharacter> startsWith = characters.stream()
                .filter(c -> c.getName().toLowerCase().startsWith(searchLower))
                .toList();

        if (startsWith.size() == 1) {
            return Optional.of(startsWith.get(0));
        }

        // Priority 3: Search name starts with DB name
        List<StanzaCharacter> reverseMatch = characters.stream()
                .filter(c -> searchLower.startsWith(c.getName().toLowerCase()))
                .toList();

        if (reverseMatch.size() == 1) {
            return Optional.of(reverseMatch.get(0));
        }

        return Optional.empty();
    }

    // === NARRATOR CONTEXT ===

    /**
     * Build full narrator context string.
     *
     * Erik-lite: No fact registry. Characters are shown with all their info.
     * The narrator is trusted to write characters consistently based on
     * their roles, relationships, and the synopsis.
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();

        sb.append("=== STANZA CONTEXT ===\n\n");

        if (worldIdentifier != null && !worldIdentifier.isEmpty()) {
            sb.append("World: ").append(worldIdentifier.toUpperCase()).append("\n\n");
        }

        // 1. COMPLETED BEATS
        String beatSummaries = formatCompletedBeatSummaries();
        if (!beatSummaries.isEmpty()) {
            sb.append("=== PREVIOUS BEATS (Summary) ===\n\n");
            sb.append(beatSummaries);
        }

        // 2. CURRENT BEAT
        Beat currentBeatObj = getCurrentBeat();
        if (currentBeatObj != null) {
            sb.append("=== CURRENT BEAT (Beat ").append(currentBeatObj.getBeatNumber()).append(") ===\n");
            String context = currentBeatObj.getTransitionContext();
            if (context != null && !context.isEmpty()) {
                sb.append("Scene Context: ").append(context).append("\n");
            }
            sb.append("Started: Exchange ").append(currentBeatObj.getStartExchange()).append("\n");
            sb.append("Current Exchange: ").append(this.currentExchange).append("\n\n");
        }

        // 3. USER CHARACTER
        StanzaCharacter userChar = getUserCharacter();
        if (userChar != null) {
            sb.append("=== USER CHARACTER ===\n\n");
            sb.append(userChar.formatForNarrator());
            sb.append("\n");
        }

        // 4. PRESENT CHARACTERS
        List<StanzaCharacter> present = getPresentCharacters();
        if (!present.isEmpty()) {
            sb.append("=== CHARACTERS IN SCENE ===\n\n");
            for (StanzaCharacter c : present) {
                if (c.isUser()) continue;
                sb.append(c.formatForNarrator());
                sb.append("\n---\n\n");
            }
        }

        // 5. POTENTIAL CHARACTERS
        List<StanzaCharacter> potential = getPotentialCharacters();
        if (!potential.isEmpty()) {
            sb.append("=== CHARACTERS WHO MIGHT APPEAR ===\n");
            sb.append("(You MAY introduce these if narratively appropriate)\n\n");
            for (StanzaCharacter c : potential) {
                sb.append("- **").append(c.getName()).append("**");
                if (c.getCanonRole() != null && !c.getCanonRole().isEmpty()) {
                    sb.append(" (").append(c.getCanonRole()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 6. WORLD CONTEXT
        if (hasWorldContext()) {
            sb.append("=== WORLD STATE ===\n\n");
            if (timeContext != null) sb.append("Time: ").append(timeContext).append("\n");
            if (worldState != null) sb.append("State: ").append(worldState).append("\n");
            if (worldRules != null && worldRules.length > 0) {
                sb.append("Rules: ").append(String.join("; ", worldRules)).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format completed beat summaries as plain text.
     */
    public String formatCompletedBeatSummaries() {
        List<Beat> completed = getCompletedBeats();
        if (completed.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (Beat beat : completed) {
            sb.append("**").append(beat.getLabel()).append("**\n");
            if (beat.getSummary() != null && !beat.getSummary().isEmpty()) {
                sb.append(beat.getSummary()).append("\n");
            } else {
                sb.append("[No summary generated]\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private boolean hasWorldContext() {
        return timeContext != null || worldState != null ||
                (worldRules != null && worldRules.length > 0);
    }
}