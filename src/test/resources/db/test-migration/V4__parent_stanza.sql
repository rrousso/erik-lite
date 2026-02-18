-- V3__parent_stanza.sql
-- Adds parent stanza reference for continuation/forking.
-- A stanza with a parent_stanza_id was created from that parent's context.

ALTER TABLE stanzas ADD COLUMN parent_stanza_id BIGINT;

ALTER TABLE stanzas ADD CONSTRAINT fk_stanzas_parent
    FOREIGN KEY (parent_stanza_id) REFERENCES stanzas(id);