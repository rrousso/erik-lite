package com.github.rrousso.erik_lite.services.stanza;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_lite.dto.initialization.BackgroundCharacter;
import com.github.rrousso.erik_lite.dto.initialization.InitializedStanza;
import com.github.rrousso.erik_lite.dto.initialization.UserCharacter;
import com.github.rrousso.erik_lite.dto.initialization.WorldContext;
import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.persistence.repositories.StanzaRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists stanza state to the database.
 *
 * Bridge between domain DTOs (InitializedStanza, etc.) and JPA entities.
 *
 * Responsibilities:
 * 1. Map InitializedStanza → JPA entities on stanza start
 * 2. Create characters (user, explicit, likely, background)
 * 3. Handle lifecycle updates (status, exchange counter, synopsis)
 * 4. Load stanza with eager relationships for prompt building
 */
@Service
public class StanzaPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(StanzaPersistenceService.class);

    private final StanzaRepository stanzaRepository;
    private final ObjectMapper objectMapper;

    public StanzaPersistenceService(StanzaRepository stanzaRepository) {
        this.stanzaRepository = stanzaRepository;
        this.objectMapper = new ObjectMapper();
    }

    // =========================================================================
    // MAIN SAVE METHOD
    // =========================================================================

    /**
     * Save an InitializedStanza to the database.
     * Called once when a stanza starts.
     */
    @Transactional
    public Stanza saveInitializedStanza(InitializedStanza initialized, Persona persona, Long parentStanzaId) {
        log.info("[Persistence] Saving initialized stanza for persona: {}", persona.getName());

        // 1. Create the main Stanza entity
        Stanza stanza = createStanzaEntity(initialized, persona);
        
        // 2. Set parent stanza ID if this is a nested stanza
        stanza.setParentStanzaId(parentStanzaId);

        // 3. Create the user character
        StanzaCharacter userChar = createUserCharacter(stanza, initialized.getUserCharacter(), persona.getName());
        stanza.getCharacters().add(userChar);

        // 4. Create explicit characters
        for (var charData : initialized.getExplicitCharacters()) {
            StanzaCharacter character = createCharacterEntity(stanza, charData, "explicit");
            stanza.getCharacters().add(character);
        }

        // 5. Create likely characters
        for (var charData : initialized.getLikelyCharacters()) {
            StanzaCharacter character = createCharacterEntity(stanza, charData, "likely");
            stanza.getCharacters().add(character);
        }

        // 6. Create background characters
        for (var bgChar : initialized.getBackgroundCharacters()) {
            StanzaCharacter character = createBackgroundCharacterEntity(stanza, bgChar);
            stanza.getCharacters().add(character);
        }

        // 7. Initialize first beat and save
        stanza.initializeFirstBeat();
        
        // Set Beat 1 location from first relevant location
        WorldContext world = initialized.getWorldContext();
        if (world != null && world.getRelevantLocations() != null && !world.getRelevantLocations().isEmpty()) {
            WorldContext.RelevantLocation firstLoc = world.getRelevantLocations().get(0);
            Beat firstBeat = stanza.getCurrentBeat();
            if (firstBeat != null && firstLoc.getName() != null) {
                firstBeat.setLocationName(firstLoc.getName());
                firstBeat.setLocationDescription(firstLoc.getDescription());
                log.debug("[Persistence] Set Beat 1 location: {}", firstLoc.getName());
            }
        }
        
        Stanza saved = stanzaRepository.save(stanza);

        log.info("[Persistence] Stanza saved with ID: {}, Characters: {}",
                saved.getId(), saved.getCharacters().size());

        return saved;
    }

    @Transactional
    public Stanza save( Stanza stanza) {
        return stanzaRepository.save(stanza);
    }

    // =========================================================================
    // ENTITY CREATION
    // =========================================================================

    private Stanza createStanzaEntity(InitializedStanza initialized, Persona persona) {
        Stanza stanza = new Stanza(persona, initialized.getWorldIdentifier());

        WorldContext world = initialized.getWorldContext();
        if (world != null) {
            stanza.setTimeContext(world.getTimeContext());
            stanza.setWorldState(world.getCurrentWorldState());

            if (world.getTone() != null && !world.getTone().isEmpty()) {
                stanza.setTone(world.getTone());
                log.debug("[Persistence] Set stanza tone: {}", world.getTone());
            }

            if (world.getSupernaturalRules() != null) {
                stanza.setWorldRules(world.getSupernaturalRules().toArray(new String[0]));
            }

            if (world.getRelevantLocations() != null && !world.getRelevantLocations().isEmpty()) {
                try {
                    stanza.setLocations(objectMapper.writeValueAsString(world.getRelevantLocations()));
                } catch (JsonProcessingException e) {
                    log.warn("[Persistence] Failed to serialize locations: {}", e.getMessage());
                }
            }
        }

        // Search field
        if (world != null && world.getRelevantLocations() != null && !world.getRelevantLocations().isEmpty()) {
            stanza.setSetting(world.getRelevantLocations().get(0).getName());
        }

        if (world != null && world.getCurrentWorldState() != null) {
            stanza.setPremise(truncate(world.getCurrentWorldState(), 500));
        }

        stanza.setCurrentBeat(0);
        stanza.setCurrentExchange(0);
        stanza.setStatus("active");

        return stanza;
    }

    private StanzaCharacter createUserCharacter(Stanza stanza, UserCharacter userData, String personaName) {
        StanzaCharacter userChar = new StanzaCharacter(stanza, personaName);
        userChar.setUser(true);
        userChar.setPresenceStatus("present");
        
        if (userData != null) {
            userChar.setPublicRole(userData.getPublicRole());
            userChar.setPrivateBackstory(userData.getPrivateBackstory());

            if (userData.getPubliclyVisibleTraits() != null) {
                userChar.setVisibleTraits(userData.getPubliclyVisibleTraits().toArray(new String[0]));
            }

            if (userData.getCurrentGoals() != null) {
                userChar.setGoals(userData.getCurrentGoals().toArray(new String[0]));
            }
        }

        return userChar;
    }

    private StanzaCharacter createCharacterEntity(
            Stanza stanza,
            com.github.rrousso.erik_lite.dto.initialization.StanzaCharacter charData,
            String tier) {

        StanzaCharacter character = new StanzaCharacter(stanza, charData.getName());
        character.setCanonRole(charData.getCanonRole());
        character.setEmotionalState(charData.getCurrentEmotionalState());
        character.setRelationshipToUser(charData.getRelationshipToUser());

        if (charData.getBlueprint() != null) {
            var blueprint = charData.getBlueprint();
            character.setBlueprintTier1Essentials(blueprint.getTier1Essentials());
            character.setBlueprintTier2Motivators(blueprint.getTier2Motivators());

            if (blueprint.getTier3Anchors() != null && !blueprint.getTier3Anchors().isEmpty()) {
                character.setBlueprintTier3Anchors(blueprint.getTier3Anchors().toArray(new String[0]));
            }
        }

        if (charData.isPresentInFirstScene()) {
            character.setPresenceStatus("present");
        } else if ("explicit".equals(tier) || "likely".equals(tier)) {
            character.setPresenceStatus("potential");
        } else {
            character.setPresenceStatus("background");
        }

        return character;
    }

    private StanzaCharacter createBackgroundCharacterEntity(Stanza stanza, BackgroundCharacter bgChar) {
        StanzaCharacter character = new StanzaCharacter(stanza, bgChar.getName());
        character.setCanonRole(bgChar.getCanonRole());
        character.setPresenceStatus("background");
        character.setEmotionalState(bgChar.getThreatOrAlly() + ": " + bgChar.getRelevanceToStanza());
        return character;
    }

    // =========================================================================
    // LIFECYCLE UPDATES
    // =========================================================================

    @Transactional
    public Stanza updateStatus( Long stanzaId, String newStatus) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
                .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        stanza.setStatus(newStatus);
        return stanzaRepository.save(stanza);
    }

    @Transactional
    public void incrementExchange( Long stanzaId) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
                .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        stanza.incrementExchange();
        stanzaRepository.save(stanza);
    }

    @Transactional
    public void setQuickSynopsis( Long stanzaId, String synopsis) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
                .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));
        stanza.setQuickSynopsis(synopsis);
        stanzaRepository.save(stanza);
    }

    /**
     * Load stanza with all lazy relationships eagerly initialized.
     * Used by SessionAssemblerService for building narrator context from DB.
     */
    @Transactional
    public Stanza loadStanzaWithRelationships( Long stanzaId) {
        Stanza stanza = stanzaRepository.findById(stanzaId)
                .orElseThrow(() -> new IllegalArgumentException("Stanza not found: " + stanzaId));

        // Force initialization of lazy collections
        stanza.getCharacters().size();
        stanza.getBeats().size();
        stanza.getEvents().size();

        return stanza;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}