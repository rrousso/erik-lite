package com.github.rrousso.erik_lite.services.prompt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_lite.exceptions.prompt.PromptLoadException;
import com.github.rrousso.erik_lite.exceptions.prompt.PromptNotFoundException;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Spring service for loading prompt templates from resources.
 * 
 * Loads .txt files from src/main/resources/prompts/ directory.
 */
@Service
public class PromptLoaderService {

    private static final Logger log = LoggerFactory.getLogger(PromptLoaderService.class);

    /**
     * Load a prompt file from resources
     * @param path Path relative to /prompts/ directory
     * @return The prompt text
     * @throws PromptNotFoundException if the file doesn't exist
     * @throws PromptLoadException if the file exists but can't be read
     */
    public String load(String path) {
        ClassPathResource resource = new ClassPathResource("prompts/" + path);

        if (!resource.exists()) {
            throw new PromptNotFoundException(path);
        }

        try (InputStream is = resource.getInputStream();
             BufferedReader reader = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new PromptLoadException(path, e);
        }
    }

    /**
     * Load a prompt with persona support.
     * Uses system property 'persona' to select persona folder.
     * Falls back to "default" folder if persona-specific file not found.
     * 
     * @param category Category folder (e.g., "user", "erik", "narrator")
     * @param filename Filename within that category
     * @return The prompt text
     */
    public String loadWithPersona(String category, String filename) {
        String persona = System.getProperty("persona", "default");
        String path = persona + "/" + category + "/" + filename;

        try {
            return load(path);
        } catch (PromptNotFoundException e) {
            if (!persona.equals("default")) {
                log.warn("Persona file not found: {}, falling back to default", path);
                return load("default/" + category + "/" + filename);
            }
            throw e;
        }
    }
}