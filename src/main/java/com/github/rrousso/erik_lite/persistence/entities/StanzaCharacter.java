package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * A character in a stanza, including the user.
 *
 * User character has isUser=true.
 * NPC characters have blueprint data for narrator context.
 *
 * Erik-lite: No knowledge tracking or secret state.
 * The narrator sees all character info and is responsible
 * for writing characters consistently.
 */
@Entity
@Table(name = "stanza_characters")
@Getter @Setter @NoArgsConstructor
public class StanzaCharacter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id", nullable = false)
    private Stanza stanza;

    // === IDENTITY ===
    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "is_user")
    private boolean isUser = false;

    @Column(name = "canon_role", length = 300)
    private String canonRole;

    // === PRESENCE ===
    @Column(name = "presence_status", length = 20)
    private String presenceStatus = "background"; // present, potential, background

    // === USER-ONLY FIELDS ===
    @Column(name = "public_role", length = 500)
    private String publicRole;

    @Column(name = "private_backstory", length = 2000)
    private String privateBackstory;

    @Column(name = "visible_traits", columnDefinition = "TEXT[]")
    private String[] visibleTraits;

    // === CHARACTER STATE ===
    @Column(name = "emotional_state", length = 300)
    private String emotionalState;

    @Column(columnDefinition = "TEXT[]")
    private String[] motivations;

    @Column(name = "relationship_to_user", length = 300)
    private String relationshipToUser;

    @Column(columnDefinition = "TEXT[]")
    private String[] goals;

    // === BLUEPRINT (3-TIER CHARACTER DEFINITION) ===
    @Column(name = "blueprint_tier1_essentials", columnDefinition = "TEXT")
    private String blueprintTier1Essentials;

    @Column(name = "blueprint_tier2_motivators", columnDefinition = "TEXT")
    private String blueprintTier2Motivators;

    @Column(name = "blueprint_tier3_anchors", columnDefinition = "TEXT[]")
    private String[] blueprintTier3Anchors;

    // === TIMESTAMPS ===
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public StanzaCharacter(Stanza stanza, String name) {
        this.stanza = stanza;
        this.name = name;
    }

    // === PRESENCE CHECKS ===

    public boolean isPresent() { return "present".equals(presenceStatus); }
    public boolean isPotential() { return "potential".equals(presenceStatus); }
    public boolean isBackground() { return "background".equals(presenceStatus); }

    // === NARRATOR FORMATTING ===

    /**
     * Format this character's info for the narrator prompt.
     * All info is visible to the narrator — erik-lite trusts the narrator
     * to write characters consistently based on their role and context.
     */
    public String formatForNarrator() {
        StringBuilder sb = new StringBuilder();

        sb.append("**").append(name).append("**\n");

        if (canonRole != null && !canonRole.isEmpty()) {
            sb.append("Role: ").append(canonRole).append("\n");
        }

        if (isUser) {
            if (publicRole != null && !publicRole.isEmpty()) {
                sb.append("Public Role: ").append(publicRole).append("\n");
            }
            if (privateBackstory != null && !privateBackstory.isEmpty()) {
                sb.append("Private Backstory (narrator-only): ").append(privateBackstory).append("\n");
            }
            if (visibleTraits != null && visibleTraits.length > 0) {
                sb.append("Visible Traits: ").append(String.join(", ", visibleTraits)).append("\n");
            }
        }

        if (emotionalState != null && !emotionalState.isEmpty()) {
            sb.append("Emotional State: ").append(emotionalState).append("\n");
        }

        if (relationshipToUser != null && !relationshipToUser.isEmpty()) {
            sb.append("Relationship to User: ").append(relationshipToUser).append("\n");
        }

        if (motivations != null && motivations.length > 0) {
            sb.append("Motivations: ").append(String.join(", ", motivations)).append("\n");
        }

        if (goals != null && goals.length > 0) {
            sb.append("Goals: ").append(String.join(", ", goals)).append("\n");
        }

        // Blueprint
        if (blueprintTier1Essentials != null && !blueprintTier1Essentials.isEmpty()) {
            sb.append("Essentials: ").append(blueprintTier1Essentials).append("\n");
        }
        if (blueprintTier2Motivators != null && !blueprintTier2Motivators.isEmpty()) {
            sb.append("Motivators: ").append(blueprintTier2Motivators).append("\n");
        }
        if (blueprintTier3Anchors != null && blueprintTier3Anchors.length > 0) {
            sb.append("Visual Anchors: ").append(String.join(", ", blueprintTier3Anchors)).append("\n");
        }

        return sb.toString();
    }
}