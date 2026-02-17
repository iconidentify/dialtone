/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.keyword.KeywordParameterExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeywordParameterExtractor.
 */
@DisplayName("KeywordParameterExtractor")
class KeywordParameterExtractorTest {

    @Test
    @DisplayName("Should extract single parameter from keyword")
    void shouldExtractSingleParameter() {
        String result = KeywordParameterExtractor.extractParameter("mat_art_id <32-5446>");

        assertEquals("32-5446", result);
    }

    @Test
    @DisplayName("Should extract parameter with complex art ID format")
    void shouldExtractComplexArtId() {
        String result = KeywordParameterExtractor.extractParameter("mat_art_id <1-0-21029>");

        assertEquals("1-0-21029", result);
    }

    @Test
    @DisplayName("Should extract parameter with whitespace variations")
    void shouldExtractParameterWithWhitespace() {
        String result1 = KeywordParameterExtractor.extractParameter("mat_art_id  <32-5446>");
        String result2 = KeywordParameterExtractor.extractParameter("mat_art_id<32-5446>");
        String result3 = KeywordParameterExtractor.extractParameter("  mat_art_id <32-5446>  ");

        assertEquals("32-5446", result1);
        assertEquals("32-5446", result2);
        assertEquals("32-5446", result3);
    }

    @Test
    @DisplayName("Should return first parameter when multiple parameters present")
    void shouldReturnFirstParameterWhenMultiple() {
        String result = KeywordParameterExtractor.extractParameter("cmd <param1> <param2> <param3>");

        assertEquals("param1", result);
    }

    @Test
    @DisplayName("Should return null when no parameters present")
    void shouldReturnNullForNoParameters() {
        String result1 = KeywordParameterExtractor.extractParameter("server logs");
        String result2 = KeywordParameterExtractor.extractParameter("help");
        String result3 = KeywordParameterExtractor.extractParameter("mat_art_id");

        assertNull(result1);
        assertNull(result2);
        assertNull(result3);
    }

    @Test
    @DisplayName("Should return null for null keyword")
    void shouldReturnNullForNullKeyword() {
        String result = KeywordParameterExtractor.extractParameter(null);

        assertNull(result);
    }

    @Test
    @DisplayName("Should return null for empty keyword")
    void shouldReturnNullForEmptyKeyword() {
        String result1 = KeywordParameterExtractor.extractParameter("");
        String result2 = KeywordParameterExtractor.extractParameter("   ");

        assertNull(result1);
        assertNull(result2);
    }

    @Test
    @DisplayName("Should handle parameter with spaces inside")
    void shouldHandleParameterWithSpaces() {
        String result = KeywordParameterExtractor.extractParameter("cmd <param with spaces>");

        assertEquals("param with spaces", result);
    }

    @Test
    @DisplayName("Should handle parameter with special characters")
    void shouldHandleParameterWithSpecialCharacters() {
        String result1 = KeywordParameterExtractor.extractParameter("cmd <param-with-dashes>");
        String result2 = KeywordParameterExtractor.extractParameter("cmd <param_with_underscores>");
        String result3 = KeywordParameterExtractor.extractParameter("cmd <param.with.dots>");

        assertEquals("param-with-dashes", result1);
        assertEquals("param_with_underscores", result2);
        assertEquals("param.with.dots", result3);
    }

    @Test
    @DisplayName("Should return null for empty parameter brackets")
    void shouldHandleEmptyBrackets() {
        String result = KeywordParameterExtractor.extractParameter("cmd <>");

        // Empty brackets should be treated as no parameter
        assertNull(result);
    }

    @Test
    @DisplayName("Should not match incomplete brackets")
    void shouldNotMatchIncompleteBrackets() {
        String result1 = KeywordParameterExtractor.extractParameter("cmd <incomplete");
        String result2 = KeywordParameterExtractor.extractParameter("cmd incomplete>");

        assertNull(result1);
        assertNull(result2);
    }

    // Tests for extractAllParameters()

    @Test
    @DisplayName("Should extract all parameters from keyword")
    void shouldExtractAllParameters() {
        List<String> results = KeywordParameterExtractor.extractAllParameters("cmd <param1> <param2> <param3>");

        assertEquals(3, results.size());
        assertEquals("param1", results.get(0));
        assertEquals("param2", results.get(1));
        assertEquals("param3", results.get(2));
    }

    @Test
    @DisplayName("Should extract single parameter as list")
    void shouldExtractSingleParameterAsList() {
        List<String> results = KeywordParameterExtractor.extractAllParameters("mat_art_id <32-5446>");

        assertEquals(1, results.size());
        assertEquals("32-5446", results.get(0));
    }

    @Test
    @DisplayName("Should return empty list when no parameters present")
    void shouldReturnEmptyListForNoParameters() {
        List<String> results1 = KeywordParameterExtractor.extractAllParameters("server logs");
        List<String> results2 = KeywordParameterExtractor.extractAllParameters("help");

        assertNotNull(results1);
        assertTrue(results1.isEmpty());
        assertNotNull(results2);
        assertTrue(results2.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for null keyword")
    void shouldReturnEmptyListForNullKeyword() {
        List<String> results = KeywordParameterExtractor.extractAllParameters(null);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for empty keyword")
    void shouldReturnEmptyListForEmptyKeyword() {
        List<String> results1 = KeywordParameterExtractor.extractAllParameters("");
        List<String> results2 = KeywordParameterExtractor.extractAllParameters("   ");

        assertNotNull(results1);
        assertTrue(results1.isEmpty());
        assertNotNull(results2);
        assertTrue(results2.isEmpty());
    }

    @Test
    @DisplayName("Should extract parameters with different content types")
    void shouldExtractParametersWithDifferentContent() {
        List<String> results = KeywordParameterExtractor.extractAllParameters(
            "cmd <text> <123> <file.txt> <1-0-21029>");

        assertEquals(4, results.size());
        assertEquals("text", results.get(0));
        assertEquals("123", results.get(1));
        assertEquals("file.txt", results.get(2));
        assertEquals("1-0-21029", results.get(3));
    }

    @Test
    @DisplayName("Should handle mixed valid and invalid brackets")
    void shouldHandleMixedBrackets() {
        // Only complete brackets should match
        List<String> results = KeywordParameterExtractor.extractAllParameters(
            "cmd <valid1> incomplete> <valid2> <incomplete");

        assertEquals(2, results.size());
        assertEquals("valid1", results.get(0));
        assertEquals("valid2", results.get(1));
    }

    @Test
    @DisplayName("Should handle adjacent parameters without spaces")
    void shouldHandleAdjacentParameters() {
        List<String> results = KeywordParameterExtractor.extractAllParameters("cmd<a><b><c>");

        assertEquals(3, results.size());
        assertEquals("a", results.get(0));
        assertEquals("b", results.get(1));
        assertEquals("c", results.get(2));
    }

    @Test
    @DisplayName("Should preserve parameter order")
    void shouldPreserveParameterOrder() {
        List<String> results = KeywordParameterExtractor.extractAllParameters(
            "cmd <third> <first> <second>");

        assertEquals(3, results.size());
        assertEquals("third", results.get(0));
        assertEquals("first", results.get(1));
        assertEquals("second", results.get(2));
    }

    @Test
    @DisplayName("Should handle parameters with line breaks")
    void shouldHandleParametersWithLineBreaks() {
        // Parameters can contain various content but not closing bracket
        List<String> results = KeywordParameterExtractor.extractAllParameters(
            "cmd <param\nwith\nnewlines> <normal>");

        assertEquals(2, results.size());
        assertEquals("param\nwith\nnewlines", results.get(0));
        assertEquals("normal", results.get(1));
    }

    // Edge case tests

    @Test
    @DisplayName("Should return null for keyword with only empty brackets")
    void shouldHandleKeywordWithOnlyBrackets() {
        String result = KeywordParameterExtractor.extractParameter("<>");

        // Empty brackets should be treated as no parameter
        assertNull(result);
    }

    @Test
    @DisplayName("Should handle nested angle brackets in text")
    void shouldHandleNestedBracketsInText() {
        // Regex matches first closing bracket, so nested brackets won't work as expected
        // This documents current behavior
        String result = KeywordParameterExtractor.extractParameter("cmd <outer <inner> outer>");

        // Will only match up to first closing bracket
        assertEquals("outer <inner", result);
    }

    @Test
    @DisplayName("Should handle multiple spaces between command and parameter")
    void shouldHandleMultipleSpaces() {
        String result = KeywordParameterExtractor.extractParameter("cmd     <param>");

        assertEquals("param", result);
    }

    @Test
    @DisplayName("Should handle tab characters")
    void shouldHandleTabCharacters() {
        String result = KeywordParameterExtractor.extractParameter("cmd\t<param>");

        assertEquals("param", result);
    }

    @Test
    @DisplayName("Should handle very long parameter content")
    void shouldHandleVeryLongParameter() {
        String longContent = "a".repeat(1000);
        String result = KeywordParameterExtractor.extractParameter("cmd <" + longContent + ">");

        assertEquals(longContent, result);
        assertEquals(1000, result.length());
    }

    @Test
    @DisplayName("Should handle numeric-only parameters")
    void shouldHandleNumericParameters() {
        String result1 = KeywordParameterExtractor.extractParameter("cmd <123>");
        String result2 = KeywordParameterExtractor.extractParameter("cmd <1-0-21029>");

        assertEquals("123", result1);
        assertEquals("1-0-21029", result2);
    }
}
