-- =============================================
-- V5__synopsis_history.sql
-- Rolling synopsis history for revert support.
-- Each row is a synopsis version generated at a specific exchange.
-- The "current" synopsis is the latest row for a stanza.
-- =============================================

CREATE TABLE IF NOT EXISTS synopsis_history (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    exchange_number INTEGER NOT NULL,
    synopsis_text TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_synopsis_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id) ON DELETE CASCADE
);

-- Find current synopsis: latest row for a stanza
CREATE INDEX idx_synopsis_stanza_exchange ON synopsis_history (stanza_id, exchange_number DESC);