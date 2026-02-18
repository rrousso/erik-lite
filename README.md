# Erik-Lite

**Interactive storytelling engine with dual-mode LLM architecture**

Erik-Lite is a simplified successor to erik-core, designed for persistent multi-session roleplay narratives. A planning companion (Erik) helps build worlds and characters in VOID mode; a Narrator runs the story in STANZA mode. Character details persist across sessions through rolling synopsis compression.

---

## Overview

Erik-Lite manages two distinct modes:

### VOID Mode (Planning)
Erik acts as a creative collaborator, helping the user plan:
- World-building (settings, rules, locations)
- Character creation (blueprints, relationships, backstories)
- Narrative setup (premise, tone, starting situation)

### STANZA Mode (Narration)
The Narrator runs the interactive story:
- Responds to user actions in second-person ("You...")
- Tracks events, character appearances, and state changes
- Compresses history into persistent synopsis for long sessions
- Supports pause/resume workflow with continuity

---

## Core Capabilities

### Dual-LLM Architecture
- **Narrative Model** (Gemini 2.5 Pro): Creative writing, world-building, synopsis generation
- **Analytical Model** (Gemini 2.5 Flash): Extraction, flag detection, change analysis

### Persistent State Management
- **Events**: What happened in the narrative (major/minor classification)
- **Character Appearances**: Who appeared/departed and when
- **Emergent Characters**: NPCs that appear during play
- **Character State Changes**: Emotional state, relationships, name reveals

### Rolling Synopsis
- Compresses conversation history to prevent context window overflow
- Preserves character details and major events
- Generated at beat transitions and stanza end
- User sees quick synopsis; Narrator gets detailed synopsis

### Multi-Beat Structure
- Stanzas divided into Beats (narrative scenes)
- Beat transitions tracked with exchange numbers
- Location tracking at beat level
- Clean transition summaries between beats

### Lifecycle Control
Commands that manage stanza flow:
- `start` / `begin` — Initialize stanza from VOID planning
- `((pause))` — Pause narration, return to VOID (with synopsis)
- `continue` — Resume paused stanza from VOID
- `((end))` — End stanza, generate final synopsis and reflection

---

## What Erik-Lite Does NOT Do

Erik-Lite is intentionally simplified compared to erik-core:

- ❌ No fact tracking or knowledge boundaries
- ❌ No character knowledge states (KNOWS/SUSPICIOUS/UNAWARE)
- ❌ No secrets or secret revelation mechanics
- ❌ No tension tracking
- ❌ No blueprint updates after initialization

These features were removed to reduce complexity and focus on core narrative persistence.

---

## Architecture

### Domain Model

**Core Entities:**
- **Persona** — User profile (name, pronouns, preferences)
- **Stanza** — A narrative session (world state, synopsis, status)
- **Beat** — Scene within a stanza (location, summary, exchanges)
- **StanzaCharacter** — Character instance (state, blueprint, relationships)
- **StanzaEvent** — Narrative event (description, significance, participants)

**Enums:**
- **StanzaStatus**: `PLANNING`, `ACTIVE`, `PAUSED`, `ENDED`
- **ModelType**: `NARRATIVE`, `ANALYTICAL`

### Service Layer

**Orchestration:**
- `SessionFlowService` — Routes requests to appropriate mode
- `ModeDetectionService` — Determines VOID vs STANZA mode

**VOID Mode:**
- `VoidModeStrategy` — Handles planning conversations
- `StanzaInitializationService` — Parses planning into structured stanza

**STANZA Mode:**
- `StanzaModeStrategy` — Main narration flow
- `BeatTransitionService` — Manages beat boundaries
- `PauseStanzaStrategy` — Handles pause command
- `ContinueStanzaStrategy` — Handles resume command
- `EndStanzaStrategy` — Handles end command

**Extraction & Synthesis:**
- `StanzaExtractionService` — Extracts state changes from narrative
- `SynopsisGeneratorService` — Compresses history into synopsis
- `EventHistoryService` — Manages event compression

**Prompts:**
- `SystemPromptBuilderService` — Assembles prompts from templates
- `ConversationHistory` — Manages message history window

### Database Design

**Tables:**
- `personas` — User profiles
- `stanzas` — Narrative sessions
- `beats` — Scenes within stanzas
- `stanza_characters` — Character instances
- `stanza_events` — Narrative events

**Migrations:**
- `V1__baseline.sql` — Core schema
- `V2__beat_locations.sql` — Beat location tracking

---

## Tech Stack

- **Java 17**
- **Spring Boot 4.0.2**
- **PostgreSQL** (production)
- **H2** (tests)
- **Flyway** (migrations)
- **Hibernate/JPA** (ORM)
- **Lombok** (boilerplate reduction)
- **Jackson** (JSON parsing)
- **OpenRouter** (LLM API gateway)

---

## Configuration

### Required Environment Variables

```bash
# Database credentials
DB_USER=your_db_user
DB_PASS=your_db_password

# OpenRouter API key
OPENROUTER_API_KEY=your_api_key
```

### Application Properties

Key configuration in `application.properties`:

```properties
# LLM Models
erik.narrative.model=google/gemini-2.5-pro
erik.narrative.temperature=0.4
erik.narrative.max-tokens=3000

erik.analytical.model=google/gemini-2.5-flash
erik.analytical.temperature=0.3
erik.analytical.max-tokens=6000

# Context Window
erik.round-window-size=6
erik.round-threshold-size=18

# Extraction
erik.extraction.frequency=1
erik.extraction.enabled=true
erik.extraction.always-extract-on-end=true
erik.extraction.always-extract-on-start=true

# Event Compression
erik.events.keep-recent-exchanges=10
erik.events.compress-frequency=20
erik.events.always-keep-major=true
```

---

## Running Erik-Lite

### Prerequisites
- Java 17+
- PostgreSQL 15+
- Maven 3.8+

### Setup

1. **Create database:**
```sql
CREATE DATABASE erik_lite_db;
```

2. **Set environment variables:**
```bash
export DB_USER=your_user
export DB_PASS=your_password
export OPENROUTER_API_KEY=sk-or-...
```

3. **Build and run:**
```bash
mvn clean install
mvn spring-boot:run
```

Flyway will automatically run migrations on startup.

---

## Development Status

### ✅ Completed

**Core Flow:**
- Dual-mode architecture (VOID/STANZA)
- Mode detection and routing
- Lifecycle commands (start/pause/continue/end)

**STANZA Mode:**
- Narration with character state tracking
- Beat transitions
- Event extraction (events, appearances, emergent chars, state changes)
- Synopsis generation (quick + detailed)
- Event history compression

**VOID Mode:**
- Planning conversations
- Stanza initialization from planning
- Resume after pause

**Persistence:**
- PostgreSQL schema with Flyway migrations
- JPA entities and repositories
- Transaction management

**Testing:**
- Full lifecycle integration test (VOID → START → narration → END)
- H2 test database configuration


### 📋 Planned

- Message rollback (undo last exchange)
- Retry failed LLM calls with exponential backoff
- Swappable VOID assistant personas
- Runtime LLM model switching (per-session model selection)
- Web UI for stanza management

---

## Testing

### Run All Tests
```bash
mvn test
```

### Integration Tests
Located in `src/test/java/com/github/rrousso/erik_lite/integration/`:
- `StanzaLifecycleIntegrationTest` — Full VOID → START → narration → END flow

---

## Key Design Decisions

### Why Simplify from erik-core?

Erik-core had extensive knowledge boundaries (facts, secrets, tensions, character knowledge states). This created complexity in:
- Extraction prompt templates
- Multi-applier coordination
- Testing and debugging

Erik-Lite focuses on **what persists** (events, characters, state) rather than **who knows what**. This reduces moving parts while preserving long-session continuity.

### Why Rolling Synopsis?

LLM context windows are finite. A 100-exchange narrative cannot fit entire history in every prompt. The rolling synopsis approach:
- Compresses old exchanges into structured summaries
- Keeps recent exchanges verbatim
- Preserves character details and major events
- Allows indefinite session length

### Why Dual-LLM?

- **Creative tasks** (narrative, synthesis) benefit from higher-quality models
- **Analytical tasks** (extraction, parsing) run faster on efficient models
- Cost optimization: use Flash for frequent extraction, Pro for user-facing content

---

## Acknowledgments

Erik-Lite is a successor to erik-core, and originally developed as a single-prompt system on a character roleplay site. The architecture evolved through lessons learned in persistent multi-session storytelling.