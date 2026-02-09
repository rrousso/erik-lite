-- =============================================
-- V1__baseline.sql
-- Erik Lite baseline migration
-- Carries forward: personas, stanzas, beats,
--   characters, tensions, events
-- Dropped from erik-core: stanza_facts,
--   character_knowledge, secrets, character_secret_state
-- =============================================

-- Personas (users of the system)
CREATE TABLE IF NOT EXISTS personas (
    id BIGSERIAL NOT NULL,
    name VARCHAR(255) NOT NULL,
    pronouns VARCHAR(255),
    description VARCHAR(1000),
    other_details VARCHAR(1000),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id)
);

-- Stanzas (narrative sessions)
CREATE TABLE IF NOT EXISTS stanzas (
    id BIGSERIAL NOT NULL,
    persona_id BIGINT NOT NULL,
    world_identifier VARCHAR(100),
    status VARCHAR(20),
    current_beat INTEGER,
    current_exchange INTEGER,
    time_context VARCHAR(500),
    world_state VARCHAR(1000),
    world_rules TEXT[],
    locations JSONB,
    setting VARCHAR(500),
    premise VARCHAR(1000),
    tone VARCHAR(200),
    quick_synopsis VARCHAR(2000),
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_stanzas_persona FOREIGN KEY (persona_id) REFERENCES personas(id)
);

-- Beats (scenes within a stanza)
CREATE TABLE IF NOT EXISTS beats (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    beat_number INTEGER NOT NULL,
    start_exchange INTEGER NOT NULL,
    end_exchange INTEGER,
    transition_context TEXT,
    summary TEXT,
    created_at TIMESTAMP(6),
    completed_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_beats_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);

-- Characters in a stanza
CREATE TABLE IF NOT EXISTS stanza_characters (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    is_user BOOLEAN,
    canon_role VARCHAR(300),
    presence_status VARCHAR(20),
    current_location VARCHAR(200),
    public_role VARCHAR(500),
    private_backstory VARCHAR(2000),
    visible_traits TEXT[],
    emotional_state VARCHAR(300),
    motivations TEXT[],
    relationship_to_user VARCHAR(300),
    goals TEXT[],
    blueprint_tier1_essentials TEXT,
    blueprint_tier2_motivators TEXT,
    blueprint_tier3_anchors TEXT[],
    created_at TIMESTAMP(6),
    updated_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_characters_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id)
);

-- Events (what happened in the narrative)
CREATE TABLE IF NOT EXISTS stanza_events (
    id BIGSERIAL NOT NULL,
    stanza_id BIGINT NOT NULL,
    beat_id BIGINT,
    description VARCHAR(280) NOT NULL,
    beat_number INTEGER,
    exchange_number INTEGER,
    involved_characters VARCHAR(300),
    is_major BOOLEAN,
    created_at TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_events_stanza FOREIGN KEY (stanza_id) REFERENCES stanzas(id),
    CONSTRAINT fk_events_beat FOREIGN KEY (beat_id) REFERENCES beats(id)
);