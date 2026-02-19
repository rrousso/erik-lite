package com.github.rrousso.erik_lite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaCharacter;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.persistence.repositories.StanzaRepository;

/**
 * Integration tests for the persistence layer.
 * 
 * Uses H2 in-memory database (test profile) to verify:
 * - Entity creation and cascade saves
 * - Relationship loading (characters, beats, events)
 * - Stanza lifecycle updates (status, exchange counter, synopsis)
 * - Repository query methods
 * 
 * These tests catch wiring issues that unit tests with mocks cannot.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Stanza Persistence Integration Tests")
public class StanzaPersistenceIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private StanzaRepository stanzaRepository;

    private Persona testPersona;

    @BeforeEach
    void setUp() {
        // Use the persona seeded by V3__test_data.sql
        testPersona = entityManager.find(Persona.class, 1L);
        assertNotNull(testPersona, "Test persona should exist from V3__test_data.sql");
    }

    // ========================================
    // BASIC ENTITY CREATION
    // ========================================

    @Test
    @DisplayName("Should create stanza with persona relationship")
    void shouldCreateStanzaWithPersona() {
        Stanza stanza = new Stanza(testPersona, "cinderella_world");
        stanza.setStatus("active");
        stanza.setCurrentBeat(1);
        stanza.setCurrentExchange(0);
        stanza.setSetting("The Grand Palace");
        stanza.setPremise("A male Cinderella story");
        stanza.setTone("romantic fantasy");

        Stanza saved = stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(saved.getId()).orElseThrow();
        assertEquals("cinderella_world", loaded.getWorldIdentifier());
        assertEquals("active", loaded.getStatus());
        assertEquals("The Grand Palace", loaded.getSetting());
        assertEquals("romantic fantasy", loaded.getTone());
        assertEquals(testPersona.getId(), loaded.getPersona().getId());
    }

    // ========================================
    // CASCADE: CHARACTERS
    // ========================================

    @Test
    @DisplayName("Should cascade-save characters with stanza")
    void shouldCascadeSaveCharacters() {
        Stanza stanza = createBasicStanza();

        // User character
        StanzaCharacter user = new StanzaCharacter(stanza, "Test User");
        user.setUser(true);
        user.setPresenceStatus("present");
        user.setPublicRole("A mysterious stranger");
        stanza.getCharacters().add(user);

        // NPC character
        StanzaCharacter npc = new StanzaCharacter(stanza, "Fairy Godfather");
        npc.setPresenceStatus("present");
        npc.setCanonRole("Magical mentor figure");
        npc.setEmotionalState("Warmly amused");
        npc.setRelationshipToUser("Guardian and guide");
        npc.setBlueprintTier1Essentials("Wise old mentor with dry humor");
        npc.setBlueprintTier2Motivators("Wants to see the user succeed at the ball");
        stanza.getCharacters().add(npc);

        // Background character
        StanzaCharacter bg = new StanzaCharacter(stanza, "Palace Guard");
        bg.setPresenceStatus("background");
        bg.setCanonRole("Minor guard NPC");
        stanza.getCharacters().add(bg);

        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals(3, loaded.getCharacters().size());

        // Verify user character
        StanzaCharacter loadedUser = loaded.getUserCharacter();
        assertNotNull(loadedUser);
        assertEquals("Test User", loadedUser.getName());
        assertTrue(loadedUser.isUser());
        assertEquals("A mysterious stranger", loadedUser.getPublicRole());

        // Verify NPC
        StanzaCharacter loadedNpc = loaded.getCharacters().stream()
                .filter(c -> "Fairy Godfather".equals(c.getName()))
                .findFirst().orElseThrow();
        assertEquals("Warmly amused", loadedNpc.getEmotionalState());
        assertEquals("Wise old mentor with dry humor", loadedNpc.getBlueprintTier1Essentials());

        // Verify presence filtering
        assertEquals(2, loaded.getPresentCharacters().size());
    }

    // ========================================
    // CASCADE: BEATS WITH LOCATIONS
    // ========================================

    @Test
    @DisplayName("Should cascade-save beats with location data")
    void shouldCascadeSaveBeatsWithLocations() {
        Stanza stanza = createBasicStanza();
        stanza.initializeFirstBeat();

        Beat firstBeat = stanza.getCurrentBeat();
        assertNotNull(firstBeat);
        firstBeat.setLocationName("The Grand Ballroom");
        firstBeat.setLocationDescription("A vast hall with crystal chandeliers and marble floors");

        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals(1, loaded.getBeats().size());

        Beat loadedBeat = loaded.getCurrentBeat();
        assertNotNull(loadedBeat);
        assertTrue(loadedBeat.isActive());
        assertEquals("The Grand Ballroom", loadedBeat.getLocationName());
        assertTrue(loadedBeat.hasLocation());
        assertTrue(loadedBeat.getLocationForNarrator().contains("crystal chandeliers"));
    }

    // ========================================
    // CASCADE: EVENTS
    // ========================================

    @Test
    @DisplayName("Should cascade-save events linked to beats")
    void shouldCascadeSaveEventsLinkedToBeats() {
        Stanza stanza = createBasicStanza();
        stanza.initializeFirstBeat();
        stanza.setCurrentExchange(1);
        Beat beat = stanza.getCurrentBeat();

        StanzaEvent event1 = new StanzaEvent(stanza, "User enters the ballroom", 1, 1);
        event1.setBeat(beat);
        event1.setInvolvedCharacters("Test User");
        event1.setMajor(true);
        stanza.getEvents().add(event1);

        StanzaEvent event2 = new StanzaEvent(stanza, "Fairy Godfather winks from the crowd", 1, 1);
        event2.setBeat(beat);
        event2.setInvolvedCharacters("Fairy Godfather,Test User");
        event2.setMajor(false);
        stanza.getEvents().add(event2);

        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals(2, loaded.getEvents().size());

        // Verify major event
        long majorCount = loaded.getEvents().stream().filter(StanzaEvent::isMajor).count();
        assertEquals(1, majorCount);

        // Verify beat linkage
        loaded.getEvents().forEach(e -> {
            assertNotNull(e.getBeat());
            assertEquals(1, e.getBeatNumber());
        });
    }

    // ========================================
    // LIFECYCLE UPDATES
    // ========================================

    @Test
    @DisplayName("Should update stanza status and exchange counter")
    void shouldUpdateStanzaStatusAndExchange() {
        Stanza stanza = createBasicStanza();
        stanzaRepository.save(stanza);
        entityManager.flush();

        // Simulate narration exchanges
        stanza.incrementExchange();
        stanza.incrementExchange();
        stanza.incrementExchange();
        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals(3, loaded.getCurrentExchange());

        // Simulate end
        loaded.setStatus("completed");
        loaded.setQuickSynopsis("A tale of a young man at the ball");
        stanzaRepository.save(loaded);
        entityManager.flush();
        entityManager.clear();

        Stanza completed = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals("completed", completed.getStatus());
        assertTrue(completed.isCompleted());
        assertEquals("A tale of a young man at the ball", completed.getQuickSynopsis());
    }

    // ========================================
    // BEAT TRANSITIONS
    // ========================================

    @Test
    @DisplayName("Should handle beat transition with location carry-forward")
    void shouldHandleBeatTransitionWithLocationCarryForward() {
        Stanza stanza = createBasicStanza();
        stanza.initializeFirstBeat();

        Beat firstBeat = stanza.getCurrentBeat();
        firstBeat.setLocationName("The Garden");
        firstBeat.setLocationDescription("A moonlit garden with rose bushes");
        stanza.setCurrentExchange(5);

        // Transition to next beat
        stanza.startNextBeat("The user walks inside");
        
        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        Stanza loaded = stanzaRepository.findById(stanza.getId()).orElseThrow();
        assertEquals(2, loaded.getBeats().size());

        // First beat should be closed
        Beat loadedFirst = loaded.getBeats().stream()
                .filter(b -> b.getBeatNumber() == 1)
                .findFirst().orElseThrow();
        assertFalse(loadedFirst.isActive());

        // Second beat should carry location forward
        Beat loadedSecond = loaded.getCurrentBeat();
        assertNotNull(loadedSecond);
        assertTrue(loadedSecond.isActive());
        assertEquals("The Garden", loadedSecond.getLocationName());
        assertEquals("The user walks inside", loadedSecond.getTransitionContext());
    }

    // ========================================
    // REPOSITORY QUERIES
    // ========================================

    @Test
    @DisplayName("Should find stanzas by persona and status")
    void shouldFindStanzasByPersonaAndStatus() {
        // Create two stanzas
        Stanza active = createBasicStanza();
        active.setWorldIdentifier("active_world");
        stanzaRepository.save(active);

        Stanza completed = createBasicStanza();
        completed.setWorldIdentifier("completed_world");
        completed.setStatus("completed");
        stanzaRepository.save(completed);

        entityManager.flush();
        entityManager.clear();

        List<Stanza> byPersona = stanzaRepository.findByPersonaId(testPersona.getId());
        assertTrue(byPersona.size() >= 2, "Should have at least 2 stanzas for persona");

        List<Stanza> completedOnly = stanzaRepository.findCompletedByPersonaId(testPersona.getId());
        assertFalse(completedOnly.isEmpty(), "Should have at least 1 completed stanza");
        long matchCount = completedOnly.stream()
            .filter(s -> "completed_world".equals(s.getWorldIdentifier()))
            .count();
        assertEquals(1, matchCount, "Should have exactly 1 completed stanza with worldIdentifier 'completed_world'");
    }

    @Test
    @DisplayName("Should search stanzas by setting keyword")
    void shouldSearchBySettingKeyword() {
        Stanza stanza = createBasicStanza();
        stanza.setSetting("The Enchanted Forest");
        stanzaRepository.save(stanza);
        entityManager.flush();
        entityManager.clear();

        List<Stanza> results = stanzaRepository.searchBySetting("enchanted");
        assertEquals(1, results.size());

        List<Stanza> noResults = stanzaRepository.searchBySetting("desert");
        assertTrue(noResults.isEmpty());
    }

    // ========================================
    // HELPERS
    // ========================================

    private Stanza createBasicStanza() {
        Stanza stanza = new Stanza(testPersona, "test_world");
        stanza.setStatus("active");
        stanza.setCurrentBeat(1);
        stanza.setCurrentExchange(0);
        return stanza;
    }
}