package com.github.rrousso.erik_lite.services.orchestration.strategies;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.rrousso.erik_lite.domain.enums.Flag;
import com.github.rrousso.erik_lite.domain.models.SessionState;
import com.github.rrousso.erik_lite.exceptions.stanza.StanzaException;

/**
 * Factory for selecting the appropriate FlowStrategy based on context.
 * 
 * Two selection methods:
 * 1. getStrategyForFlag(Flag) - Returns strategy for a detected flag
 * 2. getStrategyForMode(SessionState) - Returns strategy for normal conversation
 */
@Component
public class FlowStrategyFactory {
    
    private static final Logger log = LoggerFactory.getLogger(FlowStrategyFactory.class);
    
    private final Map<Flag, FlowStrategy> flagStrategies;
    private final VoidModeStrategy voidModeStrategy;
    private final StanzaModeStrategy stanzaModeStrategy;
    
    public FlowStrategyFactory(
            StartStanzaStrategy startStanzaStrategy,
            PauseStanzaStrategy pauseStanzaStrategy,
            ContinueStanzaStrategy continueStanzaStrategy,
            EndStanzaStrategy endStanzaStrategy,
            AbandonStanzaStrategy abandonStanzaStrategy,
            NextBeatStrategy nextBeatStrategy,
            VoidModeStrategy voidModeStrategy,
            StanzaModeStrategy stanzaModeStrategy) {
        
        this.flagStrategies = Map.of(
            Flag.START_STANZA, startStanzaStrategy,
            Flag.PAUSE_STANZA, pauseStanzaStrategy,
            Flag.CONTINUE_STANZA, continueStanzaStrategy,
            Flag.END_STANZA, endStanzaStrategy,
            Flag.ABANDON_STANZA, abandonStanzaStrategy,
            Flag.NEXT_BEAT, nextBeatStrategy
        );
        
        this.voidModeStrategy = voidModeStrategy;
        this.stanzaModeStrategy = stanzaModeStrategy;
        
        log.info("FlowStrategyFactory initialized with {} flag strategies", flagStrategies.size());
    }
    
    /**
     * Get the strategy for a detected flag.
     * 
     * @param flag The detected flag
     * @return The strategy to handle this flag
     * @throws StanzaException if flag is NONE or unknown
     */
    public FlowStrategy getStrategyForFlag(Flag flag) {
        if (flag == Flag.NONE) {
            throw new StanzaException("Cannot get strategy for Flag.NONE - use getStrategyForMode() instead");
        }
        
        FlowStrategy strategy = flagStrategies.get(flag);
        
        if (strategy == null) {
            log.error("No strategy found for flag: {}", flag);
            throw new StanzaException("Unknown flag: " + flag);
        }
        
        log.debug("Selected strategy {} for flag {}", strategy.getClass().getSimpleName(), flag);
        return strategy;
    }
    
    /**
     * Get the strategy for normal conversation (when no flag is detected).
     */
    public FlowStrategy getStrategyForMode(SessionState state) {
        FlowStrategy strategy = state.isInVoidMode() ? voidModeStrategy : stanzaModeStrategy;
        
        log.debug("Selected strategy {} for mode {}", 
            strategy.getClass().getSimpleName(), 
            state.isInVoidMode() ? "VOID" : "STANZA");
        
        return strategy;
    }
    
    /**
     * Alternative method name for clarity - same as getStrategyForMode().
     */
    public FlowStrategy getStrategyForConversation(SessionState state) {
        return getStrategyForMode(state);
    }
}