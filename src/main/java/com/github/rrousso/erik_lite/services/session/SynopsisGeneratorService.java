package com.github.rrousso.erik_lite.services.session;

import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.services.config.PersonaService;
import com.github.rrousso.erik_lite.services.config.SynopsisConfigService;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.SystemPromptBuilderService;
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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates synopses using template-based prompts and the analytical LLM.
 *
 * Three types of synopsis:
 * 1. Rolling — compresses old exchanges during active narration using extracted events
 * 2. Quick — end-of-stanza narrative summary using beat summaries + rolling synopsis
 * 3. Pause changes — distills what the user wants changed during a pause huddle
 *
 * Events from the database are the source of truth for rolling synopsis.
 * Recent exchanges are included for narrative flavor only.
 */
@Service
public class SynopsisGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(SynopsisGeneratorService.class);

    private static final String QUICK_SYNOPSIS_DEBUG_FILE = "user_data/quick_synopsis.txt";
    private static final String ROLLING_SYNOPSIS_DEBUG_FILE = "user_data/rolling_synopsis.txt";
    private static final String DISTILLED_CHANGES_DEBUG_FILE = "user_data/distilled_changes.txt";

    private final LLMClientService llmClient;
    private final SystemPromptBuilderService promptBuilder;
    private final SynopsisConfigService synopsisConfig;
    private final PersonaService personaService;

    public SynopsisGeneratorService(
            LLMClientService llmClient,
            SystemPromptBuilderService promptBuilder,
            PersonaService personaService,
            SynopsisConfigService synopsisConfig) {
        this.llmClient = llmClient;
        this.promptBuilder = promptBuilder;
        this.personaService = personaService;
        this.synopsisConfig = synopsisConfig;
    }

    // =========================================================================
    // ROLLING SYNOPSIS (during active narration)
    // =========================================================================

    /**
     * Generate rolling synopsis from extracted events.
     *
     * Only runs when conversation history exceeds the threshold.
     * Uses events from the database as source of truth, with recent
     * exchanges included for narrative flavor.
     *
     * @param history The conversation history
     * @param stanza  The stanza entity (for accessing events)
     */
    public String generateSynopsis(ConversationHistory history, Stanza stanza) throws Exception {
        int threshold = getSynopsisThreshold();

        if (history.getCurrentHistorySize() < threshold) {
            log.debug("[Synopsis] History size {} below threshold {}, skipping",
                    history.getCurrentHistorySize(), threshold);
            return history.getSynopsis();
        }

        int windowSize = getWindowSize();
        int historySize = history.getCurrentHistorySize();
        int oldMessagesCount = historySize - windowSize;

        if (oldMessagesCount <= 0) {
            log.info("[Synopsis] No old messages to condense yet. Skipping synopsis generation.");
            return history.getSynopsis();
        }

        // Get events for old exchanges from database
        int startExchange = 1;
        int endExchange = oldMessagesCount;

        List<StanzaEvent> eventsToCondense = stanza.getEvents().stream()
                .filter(e -> e.getExchangeNumber() >= startExchange &&
                             e.getExchangeNumber() <= endExchange)
                .sorted((e1, e2) -> Integer.compare(e1.getExchangeNumber(), e2.getExchangeNumber()))
                .collect(Collectors.toList());

        log.info("[Synopsis] Condensing {} events from exchanges {}-{}",
                eventsToCondense.size(), startExchange, endExchange);

        String eventsText = formatEventsForSynopsis(eventsToCondense);
        log.debug("[Synopsis] Events text ({} chars)", eventsText.length());

        String recentExchangesText = history.getRecentExchangesForSystemPrompt();
        log.debug("[Synopsis] Recent exchanges text ({} chars)", recentExchangesText.length());

        String previousSynopsis = history.getSynopsis();
        if (previousSynopsis.isEmpty()) {
            previousSynopsis = "[No previous snapshot]";
        }
        log.info("[Synopsis] Previous synopsis ({} chars)", previousSynopsis.length());

        // Fill template
        String template = promptBuilder.buildRollingSynopsisPrompt(personaService.getUserPersona());
        String filledPrompt = template
                .replace("${previousSnapshot}", previousSynopsis)
                .replace("${extractedEvents}", eventsText)
                .replace("${recentExchanges}", recentExchangesText);

        log.info("[System] Generating rolling synopsis using rolling_synopsis template (events-based)...");

        String newSynopsis = llmClient.call(
                ModelType.ANALYTICAL,
                "You create concise world snapshot synopses from extracted events.",
                filledPrompt);

        log.info("[Synopsis] Generated new synopsis ({} chars)", newSynopsis.length());

        history.updateSynopsis(newSynopsis, windowSize);
        saveSynopsisToFile(newSynopsis, "rolling", ROLLING_SYNOPSIS_DEBUG_FILE);

        return newSynopsis;
    }

    // =========================================================================
    // QUICK SYNOPSIS (end of stanza)
    // =========================================================================

    /**
     * Generate a short narrative summary of the entire stanza.
     * Uses beat summaries + rolling synopsis + recent messages.
     *
     * @param history The conversation history
     * @param stanza  The stanza entity (for beat summaries)
     */
    public String generateQuickSynopsis(ConversationHistory history, Stanza stanza) throws Exception {
        String beatSummaries = stanza.formatCompletedBeatSummaries();
        if (beatSummaries.isEmpty()) {
            beatSummaries = "[No completed beats - this is the opening beat]";
        }

        String rollingSynopsis = history.getSynopsis();
        String recentMessages = formatMessagesAsText(history.getAllMessages(), true);

        log.info("[QuickSynopsis] Using {} completed beats + rolling synopsis ({} chars) + {} recent messages",
                stanza.getCompletedBeats().size(),
                rollingSynopsis.length(),
                history.getAllMessages().size());

        String template = promptBuilder.buildQuickSynopsisPrompt(personaService.getUserPersona());
        String filledPrompt = template
                .replace("${beatSummaries}", beatSummaries)
                .replace("${rollingSynopsis}", rollingSynopsis.isEmpty() ? "[No synopsis]" : rollingSynopsis)
                .replace("${conversationText}", recentMessages);

        log.info("[QuickSynopsis] Generating quick synopsis...");

        String result = llmClient.call(
                ModelType.ANALYTICAL,
                filledPrompt,
                "Create the brief narrative summary.");

        log.info("[QuickSynopsis] Generated ({} chars)", result.length());
        saveSynopsisToFile(result, "quick", QUICK_SYNOPSIS_DEBUG_FILE);

        return result;
    }

    // =========================================================================
    // PAUSE CHANGES (during pause huddle)
    // =========================================================================

    /**
     * Extract what changes the user wants during a pause.
     * Distills the Erik/user conversation into actionable changes.
     */
    public String generatePauseChanges(ConversationHistory history) throws Exception {
        String conversationText = formatMessagesAsText(history.getAllMessages(), false);

        String systemPrompt = promptBuilder.buildChangeDistillerPrompt();

        String result = llmClient.call(
                ModelType.ANALYTICAL,
                systemPrompt,
                conversationText);

        log.info("[Distilled Changes] Generated ({} chars)", result.length());
        saveSynopsisToFile(result, "distilled", DISTILLED_CHANGES_DEBUG_FILE);

        return result;
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    public boolean shouldGenerateSynopsis(ConversationHistory history) {
        return history.getCurrentHistorySize() >= getSynopsisThreshold();
    }

    /**
     * Format events for synopsis prompt, grouped by beat.
     */
    private String formatEventsForSynopsis(List<StanzaEvent> events) {
        if (events.isEmpty()) {
            return "[No events recorded]";
        }

        Map<Integer, List<StanzaEvent>> eventsByBeat = events.stream()
                .collect(Collectors.groupingBy(StanzaEvent::getBeatNumber));

        StringBuilder sb = new StringBuilder();

        for (Integer beatNum : eventsByBeat.keySet().stream().sorted().collect(Collectors.toList())) {
            List<StanzaEvent> beatEvents = eventsByBeat.get(beatNum);

            sb.append("Beat ").append(beatNum).append(":\n");

            List<StanzaEvent> major = beatEvents.stream()
                    .filter(StanzaEvent::isMajor)
                    .collect(Collectors.toList());

            List<StanzaEvent> minor = beatEvents.stream()
                    .filter(e -> !e.isMajor())
                    .collect(Collectors.toList());

            for (StanzaEvent e : major) {
                sb.append("  - Exchange ").append(e.getExchangeNumber())
                        .append(": ").append(e.getDescription())
                        .append(" (MAJOR)\n");
            }

            for (StanzaEvent e : minor) {
                sb.append("  - Exchange ").append(e.getExchangeNumber())
                        .append(": ").append(e.getDescription())
                        .append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Format message list as text.
     *
     * @param stripOOC If true, removes text in ((double parentheses))
     */
    private String formatMessagesAsText(List<ConversationHistory.Message> messages, boolean stripOOC) {
        if (messages.isEmpty()) {
            return "";
        }

        StringBuilder text = new StringBuilder();
        for (ConversationHistory.Message msg : messages) {
            String content = msg.getContent();

            if (stripOOC) {
                content = stripOOCCommands(content);
            }

            if (content.trim().isEmpty()) {
                continue;
            }

            text.append(msg.getRole().toUpperCase())
                    .append(": ")
                    .append(content)
                    .append("\n\n");
        }
        return text.toString();
    }

    /**
     * Remove out-of-character commands in ((double parentheses)).
     */
    private String stripOOCCommands(String text) {
        String stripped = text.replaceAll("\\(\\([^)]*\\)\\)", "");
        return stripped.replaceAll("\\s+", " ").trim();
    }

    private int getWindowSize() {
        return synopsisConfig.getWindowSize();
    }

    private int getSynopsisThreshold() {
        return synopsisConfig.getThresholdSize();
    }

    /**
     * Save synopsis to file for debugging.
     */
    private void saveSynopsisToFile(String synopsis, String type, String path) {
        try {
            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder output = new StringBuilder();
            output.append("=".repeat(80)).append("\n");
            output.append("SYNOPSIS UPDATE - ").append(type.toUpperCase()).append("\n");
            output.append("Timestamp: ").append(timestamp).append("\n");
            output.append("=".repeat(80)).append("\n\n");
            output.append(synopsis);
            output.append("\n\n");

            Files.writeString(filePath, output.toString(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            log.info("[Synopsis] Saved to file: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("[Synopsis] Failed to save to file: {}", e.getMessage());
        }
    }
}