package com.github.rrousso.erik_lite.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.rrousso.erik_lite.dto.extraction.ExtractionResult;

/**
 * Unit tests for JsonCleanupUtil.
 *
 * Covers:
 * - cleanJsonResponse: markdown fence stripping
 * - repairJson: bracket mismatch and unclosed bracket repair
 * - parseJson: end-to-end parse into ExtractionResult
 * - isValidJson: validation helper
 */
@DisplayName("JsonCleanupUtil Tests")
public class JsonCleanupUtilTest {

    // =========================================================================
    // cleanJsonResponse
    // =========================================================================

    @Nested
    @DisplayName("cleanJsonResponse")
    class CleanJsonResponseTests {

        @Test
        @DisplayName("Should return null when input is null")
        void shouldReturnNullForNull() {
            assertNull(JsonCleanupUtil.cleanJsonResponse(null));
        }

        @Test
        @DisplayName("Should strip ```json ... ``` fences")
        void shouldStripJsonFence() {
            String input = "```json\n{\"key\": \"value\"}\n```";
            String result = JsonCleanupUtil.cleanJsonResponse(input);
            assertEquals("{\"key\": \"value\"}", result);
        }

        @Test
        @DisplayName("Should strip plain ``` ... ``` fences")
        void shouldStripPlainFence() {
            String input = "```\n{\"key\": \"value\"}\n```";
            String result = JsonCleanupUtil.cleanJsonResponse(input);
            assertEquals("{\"key\": \"value\"}", result);
        }

        @Test
        @DisplayName("Should leave clean JSON untouched")
        void shouldLeaveCleanJsonUntouched() {
            String input = "{\"key\": \"value\"}";
            String result = JsonCleanupUtil.cleanJsonResponse(input);
            assertEquals("{\"key\": \"value\"}", result);
        }

        @Test
        @DisplayName("Should trim surrounding whitespace")
        void shouldTrimWhitespace() {
            String input = "   {\"key\": \"value\"}   ";
            String result = JsonCleanupUtil.cleanJsonResponse(input);
            assertEquals("{\"key\": \"value\"}", result);
        }
    }

    // =========================================================================
    // repairJson
    // =========================================================================

    @Nested
    @DisplayName("repairJson")
    class RepairJsonTests {

        @Test
        @DisplayName("Should return null for null input")
        void shouldReturnNullForNull() {
            assertNull(JsonCleanupUtil.repairJson(null));
        }

        @Test
        @DisplayName("Should return empty string for empty input")
        void shouldReturnEmptyForEmpty() {
            assertEquals("", JsonCleanupUtil.repairJson(""));
        }

        @Test
        @DisplayName("Should leave valid JSON untouched")
        void shouldLeaveValidJsonUntouched() {
            String valid = "{\"events\":[{\"description\":\"test\",\"significance\":\"MINOR\",\"charactersInvolved\":[\"User\"]}]}";
            String result = JsonCleanupUtil.repairJson(valid);
            // After repair, it should still be valid and parseable
            assertTrue(JsonCleanupUtil.isValidJson(result));
        }

        @Test
        @DisplayName("Should fix } where ] is expected (the reported bug)")
        void shouldFixClosingBraceMismatch() {
            // This is the exact pattern from the bug report:
            // array closed with } instead of ]
            String broken = "{\"events\":[{\"description\":\"test\",\"significance\":\"MINOR\",\"charactersInvolved\":[\"A\",\"B\"}]}";
            String repaired = JsonCleanupUtil.repairJson(broken);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "Repaired JSON should be valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should fix ] where } is expected")
        void shouldFixClosingBracketMismatch() {
            String broken = "{\"name\":\"test\"]";
            String repaired = JsonCleanupUtil.repairJson(broken);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "Repaired JSON should be valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should close unclosed array at end of string")
        void shouldCloseUnclosedArray() {
            String truncated = "{\"events\":[{\"description\":\"test\"}";
            String repaired = JsonCleanupUtil.repairJson(truncated);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "Repaired JSON should be valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should close multiple unclosed brackets")
        void shouldCloseMultipleUnclosedBrackets() {
            String truncated = "{\"a\":[{\"b\":\"c\"";
            String repaired = JsonCleanupUtil.repairJson(truncated);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "Repaired JSON should be valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should not misinterpret brackets inside string values")
        void shouldIgnoreBracketsInsideStrings() {
            String valid = "{\"description\":\"user entered [the room] and {sat down}\",\"significance\":\"MINOR\"}";
            String repaired = JsonCleanupUtil.repairJson(valid);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "Valid JSON with brackets in strings should remain valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should handle escaped quotes inside strings without breaking")
        void shouldHandleEscapedQuotes() {
            String valid = "{\"description\":\"he said \\\"hello\\\"\",\"significance\":\"MINOR\"}";
            String repaired = JsonCleanupUtil.repairJson(valid);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "JSON with escaped quotes should remain valid, but got: " + repaired);
        }

        @Test
        @DisplayName("Should drop extra close markers when stack is empty")
        void shouldDropExtraCloseMarkers() {
            String broken = "{\"key\":\"value\"}}";
            String repaired = JsonCleanupUtil.repairJson(broken);
            assertTrue(JsonCleanupUtil.isValidJson(repaired),
                "JSON with extra close marker should be repaired, but got: " + repaired);
        }
    }

    // =========================================================================
    // parseJson — end-to-end with ExtractionResult
    // =========================================================================

    @Nested
    @DisplayName("parseJson into ExtractionResult")
    class ParseJsonTests {

        @Test
        @DisplayName("Should parse clean extraction JSON successfully")
        void shouldParseCleanExtractionJson() throws Exception {
            String json = """
                {
                  "events": [
                    {
                      "description": "User transformed back to human form",
                      "significance": "MAJOR",
                      "charactersInvolved": ["User"]
                    }
                  ],
                  "characterAppearances": [],
                  "emergentCharacters": [],
                  "charactersStateChanges": []
                }
                """;

            ExtractionResult result = JsonCleanupUtil.parseJson(json, ExtractionResult.class);

            assertNotNull(result);
            assertEquals(1, result.getEvents().size());
            assertEquals("User transformed back to human form", result.getEvents().get(0).getDescription());
            assertEquals("MAJOR", result.getEvents().get(0).getSignificance());
            assertEquals(1, result.getEvents().get(0).getCharactersInvolved().size());
            assertEquals("User", result.getEvents().get(0).getCharactersInvolved().get(0));
            assertTrue(result.getCharacterAppearances().isEmpty());
            assertTrue(result.getEmergentCharacters().isEmpty());
        }

        @Test
        @DisplayName("Should parse JSON wrapped in ```json fences")
        void shouldParseJsonWithFences() throws Exception {
            String json = """
                ```json
                {
                  "events": [],
                  "characterAppearances": [],
                  "emergentCharacters": [],
                  "charactersStateChanges": []
                }
                ```
                """;

            ExtractionResult result = JsonCleanupUtil.parseJson(json, ExtractionResult.class);

            assertNotNull(result);
            assertTrue(result.getEvents().isEmpty());
        }

        @Test
        @DisplayName("Should parse and repair extraction JSON with } instead of ] in charactersInvolved (the bug)")
        void shouldParseAndRepairMismatchedBracketInCharactersInvolved() throws Exception {
            // Replicates the exact structure from the bug report:
            // charactersInvolved array is closed with } instead of ]
            // The rest of the structure (closing } for the event object, ] for events array) is intact.
            String brokenJson =
                "{" +
                "  \"events\": [" +
                "    {" +
                "      \"description\": \"Scott transformed\"," +
                "      \"significance\": \"MAJOR\"," +
                "      \"charactersInvolved\": [\"Scott McCall\", \"Stiles Stilinski\"}" +  // } instead of ]
                "    }" +
                "  ]," +
                "  \"characterAppearances\": []," +
                "  \"emergentCharacters\": []," +
                "  \"charactersStateChanges\": []" +
                "}";

            // Before the fix this would throw JsonMappingException.
            // After the fix (repairJson in cleanJsonResponse) it should parse cleanly.
            assertDoesNotThrow(() -> {
                ExtractionResult result = JsonCleanupUtil.parseJson(brokenJson, ExtractionResult.class);
                assertNotNull(result);
                assertEquals(1, result.getEvents().size());
                assertEquals(2, result.getEvents().get(0).getCharactersInvolved().size());
                assertEquals("Scott McCall", result.getEvents().get(0).getCharactersInvolved().get(0));
                assertEquals("Stiles Stilinski", result.getEvents().get(0).getCharactersInvolved().get(1));
            });
        }

        @Test
        @DisplayName("Should throw on completely invalid JSON (no repair possible)")
        void shouldThrowOnTotallyInvalidJson() {
            assertThrows(Exception.class, () ->
                JsonCleanupUtil.parseJson("not json at all {{{", ExtractionResult.class)
            );
        }
    }

    // =========================================================================
    // isValidJson
    // =========================================================================

    @Nested
    @DisplayName("isValidJson")
    class IsValidJsonTests {

        @Test
        @DisplayName("Should return true for valid JSON")
        void shouldReturnTrueForValidJson() {
            assertTrue(JsonCleanupUtil.isValidJson("{\"key\":\"value\"}"));
        }

        @Test
        @DisplayName("Should return false for invalid JSON")
        void shouldReturnFalseForInvalidJson() {
            assertFalse(JsonCleanupUtil.isValidJson("{invalid}"));
        }

        @Test
        @DisplayName("Should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(JsonCleanupUtil.isValidJson(null));
        }

        @Test
        @DisplayName("Should return false for empty string")
        void shouldReturnFalseForEmpty() {
            assertFalse(JsonCleanupUtil.isValidJson(""));
        }

        @Test
        @DisplayName("Should return true for JSON wrapped in code fences")
        void shouldReturnTrueForFencedJson() {
            assertTrue(JsonCleanupUtil.isValidJson("```json\n{\"key\":\"value\"}\n```"));
        }
    }
}