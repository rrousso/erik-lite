package com.github.rrousso.erik_lite.services.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.exceptions.configuration.MissingConfigException;
import com.github.rrousso.erik_lite.persistence.entities.Persona;
import com.github.rrousso.erik_lite.persistence.repositories.PersonaRepository;

import jakarta.annotation.PostConstruct;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * Service responsible for managing user persona/identity.
 * 
 * Provides:
 * - User persona retrieval (formatted for prompts)
 * - First-time setup wizard
 * - Persona database interactions
 */
@Service
public class PersonaService {

    private static final Logger log = LoggerFactory.getLogger(PersonaService.class);

    private final PersonaRepository personaRepository;
    private String userPersona; // Cached formatted persona text

    public PersonaService(PersonaRepository personaRepository) {
        this.personaRepository = personaRepository;
    }

    @PostConstruct
    public void initialize() throws IOException {
        log.info("Initializing PersonaService...");

        long personaCount = personaRepository.count();
        if (personaCount == 0) {
            log.info("No persona found in database, running first-time setup");
            runFirstTimeSetup();
        } else {
            log.info("Loading existing persona from database");
            loadUserPersonaFromDatabase();
        }
    }

    /**
     * Get user persona text formatted for prompt injection.
     * This is the primary method used by other services.
     */
    public String getUserPersona() {
        if (userPersona == null || userPersona.isBlank()) {
            log.warn("User persona is empty or null");
            return "USER IDENTITY:\n- No persona configured\n";
        }
        return userPersona;
    }

    /**
     * Get the current persona entity from database.
     * For now, returns the first persona (single-user system).
     */
    public Persona getCurrentPersona() {
        return personaRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow(() -> new MissingConfigException("persona",
                "Run the application to trigger first-time setup, or insert a persona into the database"));
    }

    /**
     * Check if a persona exists in the database
     */
    public boolean hasPersona() {
        return personaRepository.count() > 0;
    }

    /**
     * Run interactive first-time setup wizard.
     * Prompts user for name, pronouns, physical description, and other details.
     * Saves to database and caches formatted text.
     */
    public void runFirstTimeSetup() throws IOException {
        System.out.println("\n=== FIRST TIME SETUP ===");
        System.out.println("Welcome! Let me get to know you a bit so I can personalize your stories.\n");

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        System.out.print("What's your name? > ");
        String name = reader.readLine().trim();

        System.out.print("What are your pronouns? (e.g., he/him, she/her, they/them) > ");
        String pronouns = reader.readLine().trim();

        System.out.print("How would you describe yourself physically? (optional, press enter to skip) > ");
        String physicalDesc = reader.readLine().trim();

        System.out.print("Any other details you'd like stories to know about you? (optional) > ");
        String otherDetails = reader.readLine().trim();

        // Save to database
        Persona personaEntity = new Persona(name, pronouns, physicalDesc, otherDetails);
        personaEntity = personaRepository.save(personaEntity);

        // Build cached persona text
        userPersona = buildPersonaText(personaEntity);

        log.info("User persona saved to database with ID: {}", personaEntity.getId());
        System.out.println("\n✓ Persona saved to database");
        System.out.println("You can view it in your PostgreSQL database.\n");
    }

    /**
     * Load persona from database and cache formatted text.
     * Currently loads the first persona (single-user system).
     */
    private void loadUserPersonaFromDatabase() {
        List<Persona> personas = personaRepository.findAll();

        if (personas.isEmpty()) {
            throw new MissingConfigException("persona",
                "Run the application to trigger first-time setup, or insert a persona into the database");
        }

        Persona persona = personas.get(0);
        userPersona = buildPersonaText(persona);

        log.info("Loaded persona: {} ({})", persona.getName(), persona.getPronouns());
    }

    /**
     * Build formatted persona text from Persona entity.
     * This text is injected into system prompts to give Erik/Narrator
     * context about the user's identity.
     */
    private String buildPersonaText(Persona persona) {
        StringBuilder sb = new StringBuilder();
        sb.append("USER IDENTITY:\n");

        appendFieldIf(sb, "Name", persona.getName());
        appendFieldIf(sb, "Pronouns", persona.getPronouns());
        appendFieldIf(sb, "Physical description", persona.getDescription());
        appendFieldIf(sb, "Additional details", persona.getOtherDetails());

        String pronouns = hasValue(persona.getPronouns()) ? persona.getPronouns() : "not specified";

        sb.append("\n**CRITICAL PRONOUN USAGE:**\n");
        sb.append("The user's pronouns are: ").append(pronouns).append("\n");
        sb.append("ALL references to the user MUST use these pronouns.\n");
        sb.append("Characters in scenes MUST use these pronouns when referring to or addressing the user.\n");
        sb.append("Do NOT use 'they' unless the user's pronouns are specifically they/them.\n");
        sb.append("Do NOT default to neutral pronouns - use the specified pronouns.\n");
        sb.append("\nThis is the baseline for all scenes and dialogue.\n");
        sb.append("Characters will interact with the user according to these details.\n");

        return sb.toString();
    }

    private void appendFieldIf(StringBuilder sb, String label, String value) {
        if (hasValue(value)) {
            sb.append("- ").append(label).append(": ").append(value).append("\n");
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isEmpty();
    }
}