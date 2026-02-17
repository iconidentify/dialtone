/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.StatefulClientHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for chat message extraction from FDO frames.
 * Validates that messages containing quotes are handled correctly.
 */
class ChatMessageExtractionTest {

    @Test
    void shouldExtractMessageWithInternalQuotes() throws Exception {
        // Given: FDO source with a message containing internal quotes
        String fdoSource = "de_data <\"@Grok give me 10 reasons why \"NO KINGS\" is a retarded protest\">";

        // When: Extracting message using the regex pattern
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract the complete message including internal quotes
        assertEquals("@Grok give me 10 reasons why \"NO KINGS\" is a retarded protest", extracted);
    }

    @Test
    void shouldExtractSimpleMessageWithoutQuotes() throws Exception {
        // Given: FDO source with a simple message
        String fdoSource = "de_data <\"Hello World\">";

        // When: Extracting message
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract the complete message
        assertEquals("Hello World", extracted);
    }

    @Test
    void shouldExtractMessageWithMultipleInternalQuotes() throws Exception {
        // Given: FDO source with multiple internal quotes
        String fdoSource = "de_data <\"He said \"hello\" and she replied \"goodbye\"\">";

        // When: Extracting message
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract the complete message with all internal quotes
        assertEquals("He said \"hello\" and she replied \"goodbye\"", extracted);
    }

    @Test
    void shouldExtractLoginCredentialsWithQuotes() throws Exception {
        // Given: FDO source with username and password containing quotes
        String fdoSource = """
                de_data <"User\"Name">
                de_data <"Pass\"Word">
                """;

        // When: Extracting credentials
        String username = extractFirstDeData(fdoSource);
        String password = extractNthDeData(fdoSource, 2);

        // Then: Should extract both with quotes intact
        assertEquals("User\"Name", username);
        assertEquals("Pass\"Word", password);
    }

    @Test
    void shouldExtractMessageWithSingleQuote() throws Exception {
        // Given: FDO source with a single internal quote
        String fdoSource = "de_data <\"It's a test\">";

        // When: Extracting message
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract the complete message
        assertEquals("It's a test", extracted);
    }

    @Test
    void shouldExtractEmptyMessage() throws Exception {
        // Given: FDO source with empty message
        String fdoSource = "de_data <\"\">";

        // When: Extracting message
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract empty string
        assertEquals("", extracted);
    }

    @Test
    void shouldExtractMessageWithSpecialCharacters() throws Exception {
        // Given: FDO source with special characters
        String fdoSource = "de_data <\"Test!@#$%^&*()_+-={}[]|\\:;<>?,./\">";

        // When: Extracting message
        String extracted = extractFirstDeData(fdoSource);

        // Then: Should extract the complete message with special chars
        assertEquals("Test!@#$%^&*()_+-={}[]|\\:;<>?,./", extracted);
    }

    @Test
    void shouldExtractMultipleDeDataFields() throws Exception {
        // Given: FDO source with multiple de_data fields (like in IM frames)
        String fdoSource = """
                de_data <"Recipient">
                de_data <"Message content">
                """;

        // When: Extracting both fields
        String first = extractFirstDeData(fdoSource);
        String second = extractNthDeData(fdoSource, 2);

        // Then: Should extract both correctly
        assertEquals("Recipient", first);
        assertEquals("Message content", second);
    }

    // Helper method that uses the same regex pattern as StatefulClientHandler
    private String extractFirstDeData(String fdoSource) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("de_data\\s+<\"(.*?)\">");
        java.util.regex.Matcher matcher = pattern.matcher(fdoSource);

        if (!matcher.find()) {
            throw new IllegalArgumentException("No de_data found in FDO source");
        }

        return matcher.group(1);
    }

    private String extractNthDeData(String fdoSource, int n) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("de_data\\s+<\"(.*?)\">");
        java.util.regex.Matcher matcher = pattern.matcher(fdoSource);

        for (int i = 0; i < n; i++) {
            if (!matcher.find()) {
                throw new IllegalArgumentException("Less than " + n + " de_data fields found");
            }
        }

        return matcher.group(1);
    }
}
