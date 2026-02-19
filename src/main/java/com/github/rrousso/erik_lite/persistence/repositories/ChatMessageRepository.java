package com.github.rrousso.erik_lite.persistence.repositories;

import com.github.rrousso.erik_lite.persistence.entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Load all messages for a chat in chronological order. */
    List<ChatMessage> findByChatIdOrderByCreatedAtAsc(Long chatId);

    /** Load only stanza messages for a chat, ordered. */
    List<ChatMessage> findByChatIdAndModeOrderByCreatedAtAsc(Long chatId, String mode);

    /** Delete stanza messages at or after a specific exchange number (for undo/retry). */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.chat.id = :chatId AND m.mode = 'STANZA' AND m.exchangeNumber >= :exchangeNumber")
    int deleteStanzaMessagesFromExchange(@Param("chatId") Long chatId, @Param("exchangeNumber") int exchangeNumber);

    /** Delete the last N messages from a chat by id descending (for void undo). */
    @Modifying
    @Query("DELETE FROM ChatMessage m WHERE m.id IN (SELECT m2.id FROM ChatMessage m2 WHERE m2.chat.id = :chatId ORDER BY m2.createdAt DESC LIMIT :count)")
    int deleteLastNMessages(@Param("chatId") Long chatId, @Param("count") int count);
}