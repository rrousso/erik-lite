-- =============================================
-- V4__chats_and_messages.sql
-- Chat and message persistence.
-- Chats are conversation containers (planning + narration).
-- Stanzas are narrative data, referenced by chats but with independent lifecycle.
-- =============================================

-- Chats (conversation sessions)
CREATE TABLE IF NOT EXISTS chats (
    id BIGSERIAL NOT NULL,
    persona_id BIGINT NOT NULL,
    stanza_id BIGINT,
    title VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_chats_persona FOREIGN KEY (persona_id) REFERENCES personas(id),
    CONSTRAINT fk_chats_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id) ON DELETE SET NULL
);

-- Chat messages (all messages across all chats)
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGSERIAL NOT NULL,
    chat_id BIGINT NOT NULL,
    mode VARCHAR(10) NOT NULL,
    role VARCHAR(10) NOT NULL,
    exchange_number INTEGER,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_messages_chat FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
);

-- Index for loading a chat's messages in order
CREATE INDEX idx_chat_messages_chat_created ON chat_messages (chat_id, created_at);

-- Index for finding stanza messages by exchange number (for undo/retry)
CREATE INDEX idx_chat_messages_exchange ON chat_messages (chat_id, mode, exchange_number)
    WHERE exchange_number IS NOT NULL;