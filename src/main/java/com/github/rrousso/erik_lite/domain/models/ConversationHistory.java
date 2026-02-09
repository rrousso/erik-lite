package com.github.rrousso.erik_lite.domain.models;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages conversation history for any mode with rolling synopsis.
 * fullHistory is kept ONLY for logging/debugging, not for synopsis generation.
 * Synopsis generation uses: synopsis + currentHistory (the rolling window).
 */
public class ConversationHistory {
    private static final Logger log = LoggerFactory.getLogger(ConversationHistory.class);

    public static class Message {
        private final String role;      // "user" or "assistant"
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }

    // Full conversation history (ONLY for logging/debugging, never used for synopsis)
    private final List<Message> fullHistory = new ArrayList<>();

    // Current mode's history (gets trimmed for context management)
    private List<Message> currentHistory = new ArrayList<>();

    // Rolling synopsis
    private String synopsis = "";

    public ConversationHistory() {
    }

    public void addUserMessage(String content) {
        Message msg = new Message("user", content);
        fullHistory.add(msg);
        currentHistory.add(msg);
        log.info("[ConversationHistory] Added user message. CurrentMode size: " + currentHistory.size() +
            ", Full history size: " + fullHistory.size());
    }

    public void addAssistantMessage(String content) {
        Message msg = new Message("assistant", content);
        fullHistory.add(msg);
        currentHistory.add(msg);
        log.info("[ConversationHistory] Added assistant message. CurrentMode size: " + currentHistory.size() +
            ", Full history size: " + fullHistory.size());
    }

    /**
     * Get recent messages formatted as TEXT for system prompt inclusion
     * Format: "USER: content\n\nASSISTANT: content\n\n"
     */
    public String getRecentExchangesForSystemPrompt() {
        if (currentHistory.isEmpty()) {
            return "";
        }

        StringBuilder exchanges = new StringBuilder();
        for (Message msg : currentHistory) {
            exchanges.append(msg.getRole().toUpperCase())
                     .append(": ")
                     .append(msg.getContent())
                     .append("\n\n");
        }

        log.info("[ConversationHistory] Formatted " + currentHistory.size() +
                " messages as text for system prompt (" + exchanges.length() + " chars)");

        return exchanges.toString();
    }

    /**
     * Get last N exchanges for extraction, with synopsis if needed.
     *
     * This is used by the extraction system to provide narrative context
     * for analyzing state changes. It automatically handles:
     * - If N <= current history size: Returns last N exchanges only
     * - If N > current history size: Returns synopsis + all current exchanges
     *
     * Format: "USER: content\n\nASSISTANT: content\n\n"
     *
     * @param n Number of exchanges requested (1 exchange = user + assistant pair)
     * @return Formatted string ready for extraction prompt
     */
    public String getLastNExchangesForExtraction(int n) {
        int messagesRequested = n * 2;  // Each exchange is 2 messages (user + assistant)
        int currentSize = currentHistory.size();

        StringBuilder context = new StringBuilder();

        // If we need more context than we have in current history, include synopsis
        if (messagesRequested > currentSize && !synopsis.isEmpty()) {
            context.append("=== SYNOPSIS OF EARLIER EVENTS ===\n\n");
            context.append(synopsis);
            context.append("\n\n=== RECENT EXCHANGES ===\n\n");

            log.info("[ConversationHistory] Extraction context: synopsis ({} chars) + all {} current messages",
                synopsis.length(), currentSize);
        }

        // Determine which messages to include
        int startIndex;
        if (messagesRequested >= currentSize) {
            startIndex = 0;
            log.info("[ConversationHistory] Extraction context: all {} messages", currentSize);
        } else {
            startIndex = currentSize - messagesRequested;
            log.info("[ConversationHistory] Extraction context: last {} exchanges ({} messages)",
                n, messagesRequested);
        }

        // Format the messages
        for (int i = startIndex; i < currentSize; i++) {
            Message msg = currentHistory.get(i);
            context.append(msg.getRole().toUpperCase())
                   .append(": ")
                   .append(msg.getContent())
                   .append("\n\n");
        }

        String result = context.toString();
        log.info("[ConversationHistory] Generated extraction context: {} chars", result.length());

        return result;
    }

    /**
     * Get synopsis (raw text, no formatting)
     */
    public String getSynopsis() {
        return synopsis;
    }

    /**
     * Get current (trimmed) messages
     * For synopsis generation, use this + getSynopsis() to get complete context
     */
    public List<Message> getAllMessages() {
        return new ArrayList<>(currentHistory);
    }

    public void updateSynopsis(String newSynopsis, int window) {
        int oldHistorySize = currentHistory.size();
        String oldSynopsis = synopsis;

        log.info("[ConversationHistory] Old synopsis length: " + oldSynopsis.length());
        log.info("[ConversationHistory] New synopsis length: " + newSynopsis.length());
        log.info("[ConversationHistory] History size before trim: " + oldHistorySize);

        synopsis = newSynopsis;
        trimCondensedMessages(window);

        log.info("[ConversationHistory] Messages trimmed: " + (oldHistorySize - currentHistory.size()));
        log.info("[ConversationHistory] Full history size (logging only): " + fullHistory.size());
    }

    private void trimCondensedMessages(int windowSize) {
        int historySize = currentHistory.size();
        int keepCount = windowSize;

        if (historySize > keepCount) {
            int startIdx = historySize - keepCount;

            log.info("[ConversationHistory] trimCondensedMessages - Window size: " + windowSize +
                ", Keep count: " + keepCount +
                ", History size: " + historySize +
                ", Start index: " + startIdx +
                ", Will keep: " + keepCount + " most recent messages");

            currentHistory = new ArrayList<>(currentHistory.subList(startIdx, historySize));
        } else {
            log.info("[ConversationHistory] trimCondensedMessages - Window size: " + windowSize +
                ", Keep count: " + keepCount +
                ", History size: " + historySize +
                ", No trimming needed (history smaller than keep count)");
        }
    }

    /**
     * Get messages that should be condensed into synopsis (OLD messages beyond window)
     */
    public List<Message> getExchangesForSynopsis(int windowSize) {
        int historySize = currentHistory.size();
        int keepCount = windowSize;

        int oldMessagesCount = historySize - keepCount;

        log.info("[ConversationHistory] getExchangesForSynopsis - Window size: " + windowSize +
            ", Keep count: " + keepCount +
            ", History size: " + historySize +
            ", Old messages to condense: " + Math.max(0, oldMessagesCount));

        if (oldMessagesCount <= 0) {
            return new ArrayList<>();
        }

        return new ArrayList<>(currentHistory.subList(0, oldMessagesCount));
    }

    /**
     * Get conversation for extraction (planning -> stanza setup)
     */
    public List<Message> getConversationForExtraction() {
        return new ArrayList<>(currentHistory);
    }

    public void clearHistory() {
        currentHistory.clear();
        synopsis = "";
        log.info("[ConversationHistory] Current history and synopsis cleared");
        log.info("[ConversationHistory] Full history preserved for logging: " + fullHistory.size() + " messages");
    }

    public int getCurrentHistorySize() {
        return currentHistory.size();
    }
}