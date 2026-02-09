package com.github.rrousso.erik_lite.services.prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for composing LLM prompts cleanly.
 * Eliminates repetitive StringBuilder.append() calls.
 * 
 * Usage:
 *   String prompt = new PromptComposer()
 *       .section(identityPrompt)
 *       .section(userPersona)
 *       .divider()
 *       .labeledSectionIf("MEMORY:", synopsis, hasSynopsis)
 *       .build();
 */
public class PromptComposer {

    private static final String DIVIDER = "---";
    private static final String SECTION_SEPARATOR = "\n\n";

    private final List<String> sections = new ArrayList<>();

    // === SECTIONS ===

    /** Add a section (skipped if null/empty) */
    public PromptComposer section(String content) {
        if (hasContent(content)) {
            sections.add(content);
        }
        return this;
    }

    /** Add a section only if condition is true */
    public PromptComposer sectionIf(String content, boolean condition) {
        if (condition && hasContent(content)) {
            sections.add(content);
        }
        return this;
    }

    // === LABELED SECTIONS ===

    /** Add a labeled section: "LABEL:\ncontent" */
    public PromptComposer labeledSection(String label, String content) {
        if (hasContent(content)) {
            sections.add(label + "\n" + content);
        }
        return this;
    }

    /** Add a labeled section only if condition is true */
    public PromptComposer labeledSectionIf(String label, String content, boolean condition) {
        if (condition && hasContent(content)) {
            sections.add(label + "\n" + content);
        }
        return this;
    }

    // === DIVIDERS ===

    /** Add a divider (---) */
    public PromptComposer divider() {
        sections.add(DIVIDER);
        return this;
    }

    /** Add a divider only if condition is true */
    public PromptComposer dividerIf(boolean condition) {
        if (condition) {
            sections.add(DIVIDER);
        }
        return this;
    }

    // === WRAPPED SECTIONS (content between dividers) ===

    /** Add a wrapped section: ---\ncontent\n--- */
    public PromptComposer wrappedSection(String content) {
        if (hasContent(content)) {
            sections.add(DIVIDER);
            sections.add(content);
            sections.add(DIVIDER);
        }
        return this;
    }

    /** Add a wrapped section only if condition is true */
    public PromptComposer wrappedSectionIf(String content, boolean condition) {
        if (condition && hasContent(content)) {
            sections.add(DIVIDER);
            sections.add(content);
            sections.add(DIVIDER);
        }
        return this;
    }

    /** Add a wrapped labeled section: ---\nLABEL:\ncontent\n--- */
    public PromptComposer wrappedLabeledSection(String label, String content) {
        if (hasContent(content)) {
            sections.add(DIVIDER);
            sections.add(label + "\n" + content);
            sections.add(DIVIDER);
        }
        return this;
    }

    /** Add a wrapped labeled section only if condition is true */
    public PromptComposer wrappedLabeledSectionIf(String label, String content, boolean condition) {
        if (condition && hasContent(content)) {
            sections.add(DIVIDER);
            sections.add(label + "\n" + content);
            sections.add(DIVIDER);
        }
        return this;
    }

    // === BUILD ===

    /** Build the final prompt string, joining sections with double newlines */
    public String build() {
        return String.join(SECTION_SEPARATOR, sections);
    }

    /** Check if any sections have been added */
    public boolean isEmpty() {
        return sections.isEmpty();
    }

    // === INTERNAL ===

    private boolean hasContent(String value) {
        return value != null && !value.isEmpty();
    }
}