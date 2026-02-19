package com.github.rrousso.erik_lite.services.prompt;

import com.github.rrousso.erik_lite.domain.enums.StanzaStatus;
import com.github.rrousso.erik_lite.domain.models.SessionContext;
import com.github.rrousso.erik_lite.domain.valueobjects.CompletedStanza;
import com.github.rrousso.erik_lite.domain.valueobjects.LoadedStanzaMemory;
import com.github.rrousso.erik_lite.exceptions.stanza.StanzaException;
import com.github.rrousso.erik_lite.services.config.PersonaService;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds system prompts for all LLM calls.
 *
 * Loads prompt templates from /resources/prompts/ at startup and caches them.
 * Assembles final prompts by combining cached templates with dynamic context
 * (persona, narrator context, synopsis, recent exchanges, status directives).
 */
@Service
public class SystemPromptBuilderService {

    private static final Logger log = LoggerFactory.getLogger(SystemPromptBuilderService.class);
    private static final Path PROMPTS_DIR = Paths.get("user_data", "generated_prompts");
    private static final DateTimeFormatter FILENAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final PromptLoaderService promptLoader;

    // Cached prompt templates
    private String fictionalFrame;
    private String voidPersonality;
    private String voidPausedDirective;
    private String voidCompletedDirective;
    private String voidAbandonedDirective;
    private String voidPlanningDirective;
    private String stanzaModeNarrator;
    private String quickSynopsisExtractionPrompt;
    private String changeDistillerPrompt;
    private String flagDetectionPrompt;
    private String rollingSynopsisPrompt;
    private String voidActiveDirective;
    private String extractionPrompt;

    private final PersonaService personaService;

    public SystemPromptBuilderService(PromptLoaderService promptLoader, PersonaService personaService) {
        this.promptLoader = promptLoader;
        this.personaService = personaService;
    }

    @PostConstruct
    public void loadPrompts() {
        log.info("[System] Loading prompts...");
        fictionalFrame = promptLoader.load("user/fictional_frame.txt");
        voidPersonality = promptLoader.load("erik/personality.txt");
        voidPausedDirective = promptLoader.load("erik/directive_paused.txt");
        voidCompletedDirective = promptLoader.load("erik/directive_completed.txt");
        voidAbandonedDirective = promptLoader.load("erik/directive_abandoned.txt");
        voidPlanningDirective = promptLoader.load("erik/directive_planning.txt");
        stanzaModeNarrator = promptLoader.load("narrator/stanza_narrator.txt");
        quickSynopsisExtractionPrompt = promptLoader.load("analytical/quick_synopsis.txt");
        changeDistillerPrompt = promptLoader.load("analytical/changes_distiller.txt");
        flagDetectionPrompt = promptLoader.load("analytical/flag_detection.txt");
        rollingSynopsisPrompt = promptLoader.load("analytical/rolling_synopsis.txt");
        voidActiveDirective = promptLoader.load("erik/directive_active.txt");
        extractionPrompt = promptLoader.load("analytical/state_extraction.txt");
        log.info("[System] Prompts loaded successfully");

        initializePromptsDirectory();
    }

    // =========================================================================
    // STANZA MODE (Narrator)
    // =========================================================================

    /**
     * Build system prompt for STANZA mode using SessionContext.
     * Uses getNarratorContext() which prefers DB context, falls back to InitializedStanza.
     */
    public String buildStanzaPromptFromContext(SessionContext context) {
        String narratorContext = context.getNarratorContext();

        if (narratorContext == null || narratorContext.isEmpty()) {
            throw new StanzaException("Cannot build stanza prompt: no narrator context available " +
                    "(neither from DB nor from InitializedStanza)");
        }

        String prompt = new PromptComposer()
                // Identity layer
                .section(fictionalFrame)
                .section(context.getUserPersona())
                .section(stanzaModeNarrator)
                // Stanza setup
                .divider()
                .section(narratorContext)
                // Memory layer
                .wrappedLabeledSectionIf("PREVIOUS SNAPSHOT:", context.getSynopsis(), context.hasSynopsis())
                .labeledSectionIf("RECENT EXCHANGES:", context.getRecentExchanges(), context.hasRecentExchanges())
                .dividerIf(context.hasRecentExchanges())
                .build();

        log.info("[PromptBuilder] Generated stanza prompt ({} chars)", prompt.length());
        log.debug("[PromptBuilder] Stanza prompt content:\n{}", prompt);
        savePromptToFile(prompt, "stanza");

        return prompt;
    }

    // =========================================================================
    // VOID MODE (Erik)
    // =========================================================================

    /**
     * Build system prompt for VOID mode using SessionContext.
     * Includes loaded stanza memory if present (from /load command).
     */
    public String buildVoidPromptFromContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
                // Identity layer
                .section(fictionalFrame)
                .section(context.getUserPersona())
                .section(voidPersonality);

        // Loaded stanza memory (from /load command) — add before other memory
        if (context.hasLoadedStanzaMemory()) {
            composer.wrappedLabeledSection("LOADED STANZA REFERENCE:",
                    formatLoadedStanzaMemory(context.getLoadedStanzaMemory()));
        }

        // Memory layer
        composer
                .wrappedLabeledSectionIf("PREVIOUS SNAPSHOT:", context.getSynopsis(), context.hasSynopsis())
                .labeledSectionIf("RECENT EXCHANGES:", context.getRecentExchanges(), context.hasRecentExchanges())
                .dividerIf(context.hasRecentExchanges())
                .section(getDirectiveForStatus(context));

        return composer.build();
    }

    // =========================================================================
    // ANALYTICAL PROMPT ACCESSORS
    // =========================================================================

    public String buildFlagDetectionPrompt() {
        return flagDetectionPrompt;
    }

    public String buildRollingSynopsisPrompt() {
        return personaService.getUserPersonaForSynopsis() + "\n\n---\n\n" + rollingSynopsisPrompt;
    }

    public String buildQuickSynopsisPrompt() {
        return personaService.getUserPersonaForSynopsis() + "\n\n---\n\n" + quickSynopsisExtractionPrompt;
    }

    public String buildChangeDistillerPrompt() {
        return changeDistillerPrompt;
    }

    /**
     * Build prompt for extraction phase.
     */
    public String buildExtractionPrompt() {
        return extractionPrompt;
    }

    // =========================================================================
    // STATUS DIRECTIVES (VOID mode context based on stanza lifecycle)
    // =========================================================================

    /**
     * Select the appropriate status directive for void mode.
     */
    private String getDirectiveForStatus(SessionContext context) {
        StanzaStatus status = context.getStanzaStatus();

        return switch (status) {
            case ACTIVE -> buildActiveStatusContext();
            case PAUSED -> buildPausedStatusContext(context);
            case COMPLETED -> buildCompletedStatusContext(context);
            case ABANDONED -> buildAbandonedStatusContext(context);
            case NONE -> buildPlanningContext(context);
        };
    }

    private String buildPlanningContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
                .section(voidPlanningDirective);

        if (context.hasLoadedStanzaMemory()) {
            composer.section(
                    "\nNote: The user has loaded a previous stanza for reference. " +
                    "Feel free to discuss it, suggest variations, or help them explore similar themes. " +
                    "Don't force the conversation toward it - let them guide.");
        }

        return composer.build();
    }

    private String buildActiveStatusContext() {
        return new PromptComposer()
                .section(voidActiveDirective)
                .build();
    }

    private String buildPausedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
                .section(voidPausedDirective);

        String narratorContext = context.getNarratorContext();
        if (narratorContext != null && !narratorContext.isEmpty()) {
            composer
                    .divider()
                    .section("## Paused Stanza - Current State");

            if (context.hasSynopsis()) {
                composer.wrappedLabeledSection("STORY SO FAR:", context.getSynopsis());
            }

            if (context.hasRecentExchanges()) {
                composer.labeledSection("RECENT EXCHANGES (where we paused):", context.getRecentExchanges());
            }

            composer
                    .divider()
                    .section("## Current World State")
                    .section(narratorContext);
        }

        return composer.build();
    }

    private String buildCompletedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
                .section(voidCompletedDirective);

        if (context.hasCompletedStanza()) {
            CompletedStanza completed = context.getCompletedStanza();

            composer
                    .divider()
                    .section("## Completed Stanza Reference")
                    .section("The user just completed a stanza.");

            if (completed.getQuickSynopsis() != null && !completed.getQuickSynopsis().isEmpty()) {
                composer.labeledSection("**What happened:**", completed.getQuickSynopsis());
            }

            String narratorContext = context.getNarratorContext();
            if (narratorContext != null && !narratorContext.isEmpty()) {
                composer
                        .divider()
                        .section("## Paused Stanza Context")
                        .labeledSection("**Original Setup:**", narratorContext);

                if (context.hasSynopsis()) {
                    composer.labeledSection("**What happened so far:**", context.getSynopsis());
                }
            }

            composer
                    .divider()
                    .section("Use this information to discuss the stanza with the user if they want to reflect on it. " +
                            "Pay attention to character names and events from the synopsis above.");
        }

        return composer.build();
    }

    private String buildAbandonedStatusContext(SessionContext context) {
        PromptComposer composer = new PromptComposer()
                .section(voidAbandonedDirective)
                .section(voidPlanningDirective);

        if (context.hasCompletedStanza()) {
            composer
                    .divider()
                    .section("## Abandoned Stanza Reference")
                    .section(context.getCompletedStanza().getInitializedStanza().toNarratorContext())
                    .divider()
                    .section("Use this information to discuss the stanza with the user if they want to reflect on it.");
        }

        return composer.build();
    }

    // =========================================================================
    // LOADED STANZA MEMORY FORMATTING
    // =========================================================================

    /**
     * Format a loaded stanza for injection into Erik's VOID prompt.
     * Uses the pre-extracted strings from LoadedStanzaMemory instead of a Stanza entity.
     */
    private String formatLoadedStanzaMemory(LoadedStanzaMemory memory) {
        StringBuilder sb = new StringBuilder();

        sb.append("The user has loaded a previous stanza for reference.\n\n");
        sb.append("World: ").append(memory.getWorldIdentifier()).append("\n\n");

        if (memory.hasQuickSynopsis()) {
            sb.append("What happened:\n").append(memory.getQuickSynopsis()).append("\n\n");
        }

        sb.append("Full context:\n").append(memory.getNarratorContext());

        return sb.toString();
    }

    // =========================================================================
    // DEBUG FILE OUTPUT
    // =========================================================================

    private void initializePromptsDirectory() {
        try {
            if (!Files.exists(PROMPTS_DIR)) {
                Files.createDirectories(PROMPTS_DIR);
                log.info("[PromptBuilder] Created prompts directory: {}", PROMPTS_DIR);
            }
        } catch (IOException e) {
            log.error("[PromptBuilder] Failed to create prompts directory", e);
        }
    }

    private void savePromptToFile(String prompt, String prefix) {
        try {
            String timestamp = LocalDateTime.now().format(FILENAME_FORMATTER);
            String filename = String.format("%s_%s.txt", prefix, timestamp);
            Path filePath = PROMPTS_DIR.resolve(filename);

            Files.writeString(filePath, prompt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("[PromptBuilder] Saved {} prompt to: {}", prefix, filename);
        } catch (IOException e) {
            log.error("[PromptBuilder] Failed to save {} prompt to file", prefix, e);
        }
    }
}