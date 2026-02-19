package com.github.rrousso.erik_lite.services.chat;

import com.github.rrousso.erik_lite.persistence.entities.Chat;
import com.github.rrousso.erik_lite.persistence.entities.ChatMessage;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.repositories.ChatMessageRepository;
import com.github.rrousso.erik_lite.persistence.repositories.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles chat and message persistence.
 *
 * Responsible for:
 * - Creating chats at session start
 * - Saving messages (dual-write alongside in-memory ConversationHistory)
 * - Linking stanzas to chats
 */
@Service
public class ChatPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ChatPersistenceService.class);

    private final ChatRepository chatRepository;
    private final ChatMessageRepository messageRepository;

    public ChatPersistenceService(ChatRepository chatRepository, ChatMessageRepository messageRepository) {
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Create a new chat for a persona. Called at session startup.
     */
    @Transactional
    public Chat createChat(Persona persona) {
        Chat chat = new Chat(persona);
        chat = chatRepository.save(chat);
        log.info("[Chat] Created chat ID: {} for persona: {}", chat.getId(), persona.getName());
        return chat;
    }

    /**
     * Link a stanza to a chat. Called when StartStanzaStrategy persists a stanza.
     */
    @Transactional
    public void linkStanza(Long chatId, Stanza stanza) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalStateException("Chat not found: " + chatId));
        chat.setStanza(stanza);
        chatRepository.save(chat);
        log.info("[Chat] Linked stanza ID: {} to chat ID: {}", stanza.getId(), chatId);
    }

    /**
     * Save a message to the database.
     * Called after each LLM exchange alongside the in-memory history update.
     */
    @Transactional
    public void saveMessage(Long chatId, String mode, String role, String content, Integer exchangeNumber) {
        Chat chat = chatRepository.getReferenceById(chatId);
        ChatMessage message = new ChatMessage(chat, mode, role, content);
        message.setExchangeNumber(exchangeNumber);
        messageRepository.save(message);
        log.debug("[Chat] Saved {} {} message to chat {} (exchange: {})",
                mode, role, chatId, exchangeNumber);
    }
}