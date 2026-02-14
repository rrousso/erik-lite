package com.github.rrousso.erik_lite.services.stanza;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.domain.enums.ModelType;
import com.github.rrousso.erik_lite.domain.models.ConversationHistory;
import com.github.rrousso.erik_lite.persistence.entities.Beat;
import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;
import com.github.rrousso.erik_lite.services.llm.LLMClientService;
import com.github.rrousso.erik_lite.services.prompt.PromptLoaderService;

/**
 * Generates prose summaries for completed beats.
 *
 * Dynamic word count based on total beats:
 * - More beats = shorter summaries per beat
 * - Range: 100-500 words per beat
 */
@Service
public class BeatSummaryService {

    private static final Logger log = LoggerFactory.getLogger(BeatSummaryService.class);

    private final LLMClientService llmClient;
    private final PromptLoaderService promptLoader;

    private String summaryTemplate;

    private static final int MAX_TOTAL_WORDS = 1500;
    private static final int MIN_WORDS_PER_BEAT = 100;
    private static final int MAX_WORDS_PER_BEAT = 500;

    public BeatSummaryService(LLMClientService llmClient, PromptLoaderService promptLoader) {
        this.llmClient = llmClient;
        this.promptLoader = promptLoader;
    }

    @jakarta.annotation.PostConstruct
    public void loadTemplate() {
        log.info("Loading beat summary prompt template");
        this.summaryTemplate = promptLoader.load("analytical/beat_summary.txt");
    }

    /**
     * Generate prose summary for a completed beat.
     */
    public String generateBeatSummary(Beat beat, Stanza stanza, ConversationHistory conversationHistory) {
        log.info("[BeatSummary] Generating summary for Beat {} (exchanges {}-{})",
            beat.getBeatNumber(), beat.getStartExchange(), beat.getEndExchange());

        List<StanzaEvent> beatEvents = stanza.getEventsForBeat(beat);

        if (beatEvents.isEmpty()) {
            log.warn("[BeatSummary] No events found for beat {}, generating minimal summary",
                beat.getBeatNumber());
            return generateMinimalSummary(beat);
        }

        String eventsText = formatEventsForSummary(beatEvents);

        String synopsis = conversationHistory != null ? conversationHistory.getSynopsis() : "";
        if (synopsis.isEmpty()) {
            synopsis = "[No synopsis available]";
        }

        int totalBeats = stanza.getBeats().size();
        int maxWords = calculateMaxWords(totalBeats);

        String prompt = buildPrompt(beat, eventsText, synopsis, maxWords, totalBeats);

        try {
            String summary = llmClient.call(
                ModelType.ANALYTICAL,
                "You create concise beat summaries for interactive stories.",
                prompt);

            log.info("[BeatSummary] Generated summary ({} chars, target: ~{} words)",
                summary.length(), maxWords);

            return summary;

        } catch (Exception e) {
            log.error("[BeatSummary] Failed to generate summary for beat {}",
                beat.getBeatNumber(), e);
            return generateFallbackSummary(beat, beatEvents);
        }
    }

    private int calculateMaxWords(int totalBeats) {
        if (totalBeats <= 0) return MAX_WORDS_PER_BEAT;
        int wordsPerBeat = MAX_TOTAL_WORDS / totalBeats;
        return Math.max(MIN_WORDS_PER_BEAT, Math.min(MAX_WORDS_PER_BEAT, wordsPerBeat));
    }

    private String formatEventsForSummary(List<StanzaEvent> events) {
        StringBuilder sb = new StringBuilder();

        List<StanzaEvent> majorEvents = events.stream()
            .filter(StanzaEvent::isMajor)
            .collect(Collectors.toList());

        List<StanzaEvent> minorEvents = events.stream()
            .filter(e -> !e.isMajor())
            .collect(Collectors.toList());

        if (!majorEvents.isEmpty()) {
            sb.append("MAJOR EVENTS (story-critical):\n");
            for (StanzaEvent event : majorEvents) {
                sb.append("- Exchange ").append(event.getExchangeNumber())
                  .append(": ").append(event.getDescription()).append("\n");
            }
            sb.append("\n");
        }

        if (!minorEvents.isEmpty()) {
            sb.append("MINOR EVENTS (for flavor and context):\n");
            for (StanzaEvent event : minorEvents) {
                sb.append("- Exchange ").append(event.getExchangeNumber())
                  .append(": ").append(event.getDescription()).append("\n");
            }
        }

        return sb.toString();
    }

    private String buildPrompt(Beat beat, String eventsText, String synopsis,
                               int maxWords, int totalBeats) {
        return summaryTemplate
            .replace("${beatNumber}", beat.getBeatNumber().toString())
            .replace("${transitionContext}", beat.getTransitionContextOrDefault())
            .replace("${startExchange}", beat.getStartExchange().toString())
            .replace("${endExchange}", beat.getEndExchange().toString())
            .replace("${eventsText}", eventsText)
            .replace("${synopsis}", synopsis)
            .replace("${maxWords}", String.valueOf(maxWords))
            .replace("${totalBeats}", String.valueOf(totalBeats));
    }

    private String generateMinimalSummary(Beat beat) {
        String context = beat.getTransitionContextOrDefault();
        return String.format("Beat %d: %s (Exchanges %d-%d). Scene transition with minimal recorded events.",
            beat.getBeatNumber(), context, beat.getStartExchange(), beat.getEndExchange());
    }

    private String generateFallbackSummary(Beat beat, List<StanzaEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("Beat ").append(beat.getBeatNumber()).append(": ");
        sb.append(beat.getTransitionContextOrDefault());
        sb.append(" (Exchanges ").append(beat.getStartExchange())
          .append("-").append(beat.getEndExchange()).append("). ");

        List<StanzaEvent> majorEvents = events.stream()
            .filter(StanzaEvent::isMajor)
            .limit(5)
            .collect(Collectors.toList());

        if (!majorEvents.isEmpty()) {
            sb.append("Key events: ");
            sb.append(majorEvents.stream()
                .map(StanzaEvent::getDescription)
                .collect(Collectors.joining("; ")));
        }

        return sb.toString();
    }
}