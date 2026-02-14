package com.github.rrousso.erik_lite.services.stanza;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.persistence.entities.Stanza;
import com.github.rrousso.erik_lite.persistence.entities.StanzaEvent;

import lombok.Getter;
import lombok.Setter;

/**
 * Compresses accumulated events to prevent database bloat.
 *
 * Rules:
 * - Keep all events from recent exchanges (configurable)
 * - Keep all events marked as "major"
 * - Compress remaining into summary events grouped by beat
 *
 * Configuration:
 * erik.events.keep-recent-exchanges=10
 * erik.events.compress-frequency=20
 * erik.events.always-keep-major=true
 */
@Service
@ConfigurationProperties(prefix = "erik.events")
@Getter @Setter
public class EventCompressionService {

    private static final Logger log = LoggerFactory.getLogger(EventCompressionService.class);

    private int keepRecentExchanges = 10;
    private int compressFrequency = 20;
    private boolean alwaysKeepMajor = true;

    /**
     * Compress old events for a stanza.
     *
     * @param stanza The stanza to compress events for
     * @return Number of events compressed
     */
    public int compressEvents(Stanza stanza) {
        int currentExchange = stanza.getCurrentExchange();
        int keepThreshold = currentExchange - keepRecentExchanges;

        log.debug("[EventCompression] Starting compression for stanza {} (exchange {}, threshold: {})",
            stanza.getId(), currentExchange, keepThreshold);

        List<StanzaEvent> compressibleEvents = stanza.getEvents().stream()
            .filter(e -> e.getExchangeNumber() < keepThreshold)
            .filter(e -> !alwaysKeepMajor || !e.isMajor())
            .collect(Collectors.toList());

        if (compressibleEvents.isEmpty()) {
            log.debug("[EventCompression] No events to compress");
            return 0;
        }

        log.info("[EventCompression] Compressing {} events (keeping {} recent, {} major)",
            compressibleEvents.size(),
            stanza.getEvents().size() - compressibleEvents.size(),
            stanza.getEvents().stream().filter(StanzaEvent::isMajor).count());

        Map<Integer, List<StanzaEvent>> byBeat = compressibleEvents.stream()
            .collect(Collectors.groupingBy(StanzaEvent::getBeatNumber));

        int totalCompressed = 0;

        for (Map.Entry<Integer, List<StanzaEvent>> entry : byBeat.entrySet()) {
            int beat = entry.getKey();
            List<StanzaEvent> events = entry.getValue();

            String summary = String.format("Beat %d summary (%d events): ", beat, events.size()) +
                events.stream()
                    .map(StanzaEvent::getDescription)
                    .collect(Collectors.joining("; "));

            if (summary.length() > 280) {
                summary = summary.substring(0, 277) + "...";
            }

            int firstExchange = events.stream()
                .mapToInt(StanzaEvent::getExchangeNumber)
                .min()
                .orElse(0);

            String involvedCharacters = events.stream()
                .map(StanzaEvent::getInvolvedCharacters)
                .filter(chars -> chars != null && !chars.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));

            StanzaEvent compressed = new StanzaEvent();
            compressed.setStanza(stanza);
            compressed.setDescription(summary);
            compressed.setBeatNumber(beat);
            compressed.setExchangeNumber(firstExchange);
            compressed.setMajor(false);
            compressed.setInvolvedCharacters(involvedCharacters.isEmpty() ? "COMPRESSED" : involvedCharacters);

            stanza.getEvents().removeAll(events);
            totalCompressed += events.size();

            stanza.getEvents().add(compressed);

            log.debug("[EventCompression] Compressed {} events from beat {} into summary",
                events.size(), beat);
        }

        log.info("[EventCompression] Compression complete: {} events -> {} summaries (saved {} records)",
            totalCompressed, byBeat.size(), totalCompressed - byBeat.size());

        return totalCompressed;
    }

    /**
     * Check if compression should run based on current exchange number.
     */
    public boolean shouldCompress(int exchangeNumber) {
        if (compressFrequency == 0) {
            return false;
        }
        return exchangeNumber % compressFrequency == 0 && exchangeNumber > keepRecentExchanges;
    }
}