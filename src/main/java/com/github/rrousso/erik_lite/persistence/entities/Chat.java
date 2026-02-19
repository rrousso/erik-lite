package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A conversation session containing planning (VOID) and narration (STANZA) messages.
 *
 * One chat can reference at most one stanza. The stanza has an independent lifecycle —
 * deleting the chat does not delete the stanza (FK set to ON DELETE SET NULL).
 * Deleting the chat DOES cascade-delete all its messages.
 *
 * A stanza can be referenced by multiple chats (e.g., Chat #1 created it,
 * Chat #2 continued it after loading).
 */
@Entity
@Table(name = "chats")
@Getter @Setter @NoArgsConstructor
public class Chat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "persona_id", nullable = false)
    private Persona persona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stanza_id")
    private Stanza stanza;

    @Column(length = 255)
    private String title;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ChatMessage> messages = new ArrayList<>();

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public Chat(Persona persona) {
        this.persona = persona;
    }

    // === STATUS ===

    public boolean isActive() { return "active".equals(status); }
    public boolean isArchived() { return "archived".equals(status); }

    // === STANZA ===

    public boolean hasStanza() { return stanza != null; }
}