package com.github.rrousso.erik_lite.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A single message in a chat conversation.
 *
 * Messages are ordered by createdAt within a chat.
 * VOID messages have no exchange_number.
 * STANZA messages have an exchange_number matching the stanza's exchange counter,
 * which enables targeted undo/retry.
 */
@Entity
@Table(name = "chat_messages")
@Getter @Setter @NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    /** VOID or STANZA */
    @Column(nullable = false, length = 10)
    private String mode;

    /** user or assistant */
    @Column(nullable = false, length = 10)
    private String role;

    /** Only set for STANZA messages. Maps to stanza exchange counter. */
    @Column(name = "exchange_number")
    private Integer exchangeNumber;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ChatMessage(Chat chat, String mode, String role, String content) {
        this.chat = chat;
        this.mode = mode;
        this.role = role;
        this.content = content;
    }

    // === CONVENIENCE ===

    public boolean isVoid() { return "VOID".equals(mode); }
    public boolean isStanza() { return "STANZA".equals(mode); }
    public boolean isUser() { return "user".equals(role); }
    public boolean isAssistant() { return "assistant".equals(role); }
}