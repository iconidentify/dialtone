/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo;

import com.dialtone.fdo.FdoTemplateEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FdoTemplateEngine variable substitution and escaping.
 * Tests verify Atomforge-compatible escape sequences (\\x22 for quotes).
 */
class FdoTemplateEngineTest {

    // Helper to create Map<String, Object> from key-value pairs
    private Map<String, Object> vars(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    @Test
    void testEscapeQuotes_convertsToHexEscape() throws IOException {
        // Given: FDO template with variable
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Variable contains quotes
        Map<String, Object> varsMap = vars("MESSAGE", "He said \"hello\" to me");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Quotes should be escaped as \x22 (Atomforge hex escape)
        assertTrue(result.contains("He said \\x22hello\\x22 to me"),
            "Expected quotes to be escaped as \\x22, got: " + result);
        assertFalse(result.contains("\\\""),
            "Should not use Java-style \\\" escaping");
    }

    @Test
    void testEscapeQuotes_multipleQuotes() throws IOException {
        // Given: Simple template
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Multiple quotes in variable
        Map<String, Object> varsMap = vars("MESSAGE",
            "\"First\" and \"Second\" and \"Third\"");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: All quotes should be escaped with \x22
        assertTrue(result.contains("\\x22First\\x22"),
            "First quote pair should use \\x22");
        assertTrue(result.contains("\\x22Second\\x22"),
            "Second quote pair should use \\x22");
        assertTrue(result.contains("\\x22Third\\x22"),
            "Third quote pair should use \\x22");
    }

    @Test
    void testEscapeLineBreaks_convertedToSpaces() throws IOException {
        // Given: FDO template
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Variable contains line breaks
        Map<String, Object> varsMap = vars("MESSAGE", "Line1\nLine2\rLine3");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Line breaks in variable should be converted to spaces
        // (Note: template itself may have newlines - we're testing variable substitution)
        assertTrue(result.contains("Line1 Line2 Line3"),
            "Line breaks in variable should be converted to spaces");
    }

    @Test
    void testEscapeQuotesAndLineBreaks_combined() throws IOException {
        // Given: FDO template
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Variable has both quotes and line breaks
        Map<String, Object> varsMap = vars("MESSAGE",
            "He said \"hello\"\nand I replied \"hi\"");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Quotes escaped, line breaks converted to spaces
        assertTrue(result.contains("\\x22hello\\x22"),
            "Quotes should be escaped as \\x22");
        assertTrue(result.contains("He said \\x22hello\\x22 and I replied \\x22hi\\x22"),
            "Both quotes and line breaks should be handled correctly");
    }

    @Test
    void testNullVariable_replacedWithEmpty() throws IOException {
        // Given: FDO template
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Variable value is empty string
        Map<String, Object> varsMap = vars("MESSAGE", "");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should be replaced with empty string (check for empty quotes in append_data)
        assertTrue(result.contains("<\"\">"),
            "Empty variable should be replaced with empty string, got: " + result);
    }

    @Test
    void testRealWorldNewsExample_quotesInHeadline() throws IOException {
        // Given: FDO template (simulating news story template)
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: News headline contains quotes (realistic example from persistence)
        Map<String, Object> varsMap = vars("MESSAGE",
            "UK Protesters Call Government's \"Online Safety\" Laws Censorship");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should use \x22 escape for Atomforge compatibility
        assertTrue(result.contains("\\x22Online Safety\\x22"),
            "News headlines with quotes should use \\x22 escape, got: " + result);
        assertTrue(result.contains("Government's"),
            "Apostrophes should remain unchanged");
    }

    @Test
    void testDataVariable_quotesStillEscaped() throws IOException {
        // Given: FDO template with _DATA variable (converted to hex format)
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: _DATA variable contains actual quotes AND newline escape sequences
        Map<String, Object> varsMap = vars("FULLREPORT_DATA",
            "Story with \"online safety\" quotes\\r\\rAnd more text");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should be converted to hex pair format with angle brackets
        assertTrue(result.contains("man_append_data <"),
            "_DATA variables should be converted to hex format, got: " + result);
        assertTrue(result.contains("x,"),
            "Should contain hex pairs with 'x' notation");
        assertTrue(result.contains(">"),
            "Hex format should be enclosed in angle brackets");
        assertFalse(result.contains("\\x22"),
            "_DATA variables use hex format, not escape sequences");
    }

    @Test
    void testDataVariable_realWorldNewsStory() throws IOException {
        // Given: Template with _DATA variable (like news story popup)
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: News content has actual quotes and clean newlines (from AbstractNewsContent.getReport())
        Map<String, Object> varsMap = vars("FULLREPORT_DATA",
            "*** UK Censorship Concerns ***\\r\\rDemonstrators accuse the government of using \"online safety\" as a pretext.");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should be converted to hex pair format
        assertTrue(result.contains("man_append_data <"),
            "Real news stories should use hex format, got: " + result);
        assertTrue(result.contains("x,"),
            "Should contain hex pairs with 'x' notation");
        // Verify the backslashes from escape sequences are also converted to hex (5Cx is '\')
        assertTrue(result.contains("5Cx"),
            "Backslashes from \\r sequences should be in hex format");
        assertFalse(result.contains("\\\""),
            "Should not use Java-style quote escaping");
    }

    @Test
    void testNewlineConversion_singleUnixNewline() throws IOException {
        // Given: FDO template with _DATA variable (triggers hex conversion)
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: Variable contains single Unix newline
        Map<String, Object> varsMap = vars("FULLREPORT_DATA", "Line1\nLine2");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should contain hex for 0x7F (DEL character) instead of 0x0A (LF)
        assertTrue(result.contains("7Fx"),
            "Single \\n should convert to 0x7F character in hex format, got: " + result);
        assertFalse(result.contains("0Ax"),
            "Should not contain raw \\n (0x0A) characters");
    }

    @Test
    void testNewlineConversion_doubleUnixNewline() throws IOException {
        // Given: FDO template with _DATA variable
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: Variable contains double Unix newlines (paragraph break)
        Map<String, Object> varsMap = vars("FULLREPORT_DATA", "Para1\n\nPara2");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should contain two consecutive 0x7F characters for blank line
        assertTrue(result.contains("7Fx, 7Fx"),
            "Double \\n\\n should convert to 0x7F 0x7F in hex format, got: " + result);
        assertFalse(result.contains("0Ax, 0Ax"),
            "Should not contain raw \\n\\n (0x0A 0x0A) characters");
    }

    @Test
    void testNewlineConversion_windowsStyleLineEndings() throws IOException {
        // Given: FDO template with _DATA variable
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: Variable contains Windows-style line endings
        Map<String, Object> varsMap = vars("FULLREPORT_DATA", "Line1\r\nLine2\r\n\r\nPara2");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should handle \r\n properly - convert to 0x7F
        assertTrue(result.contains("7Fx"),
            "\\r\\n should convert to single 0x7F character, got: " + result);
        assertTrue(result.contains("7Fx, 7Fx"),
            "\\r\\n\\r\\n should convert to double 0x7F characters, got: " + result);
        assertFalse(result.contains("0Dx") || result.contains("0Ax"),
            "Should not contain raw \\r (0x0D) or \\n (0x0A) characters");
    }

    @Test
    void testNewlineConversion_mixedLineEndings() throws IOException {
        // Given: FDO template with _DATA variable
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: Variable contains mixed line ending styles
        Map<String, Object> varsMap = vars("FULLREPORT_DATA", "Unix\nWindows\r\n\r\nBlank\n\nLines");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Should handle mixed line endings properly - all become 0x7F
        assertTrue(result.contains("7Fx"),
            "Should convert all newline patterns to 0x7F characters, got: " + result);
        // Count occurrences of 7F to verify multiple conversions
        long delCount = result.split("7Fx").length - 1;
        assertTrue(delCount >= 4,
            "Should convert all 4+ newline patterns, found: " + delCount + " in: " + result);
        assertFalse(result.contains("0Dx") || result.contains("0Ax"),
            "Should not contain any raw \\r (0x0D) or \\n (0x0A) characters");
    }

    @Test
    void testNewlineConversion_realTosContent() throws IOException {
        // Given: FDO template with _DATA variable
        String resourcePath = "fdo/test/data_variable_test.fdo.txt";

        // When: Variable contains TOS-style content structure
        Map<String, Object> varsMap = vars("FULLREPORT_DATA",
            "1. NON-AFFILIATION\n   Dialtone is independent.\n\n2. PURPOSE\n   Research and education.");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: TOS content should have newlines converted to 0x7F
        assertTrue(result.contains("7Fx"),
            "TOS content should have newlines converted to 0x7F, got: " + result);
        assertFalse(result.contains("0Ax") || result.contains("0Dx"),
            "Should not contain raw \\n (0x0A) or \\r (0x0D) characters");
        // Verify paragraph breaks are preserved as double 0x7F
        assertTrue(result.contains("7Fx, 7Fx"),
            "TOS paragraph breaks should convert to double 0x7F, got: " + result);
    }

    @Test
    void testNewlineConversion_doesNotAffectNonDataVariables() throws IOException {
        // Given: FDO template with regular (non-_DATA) variable
        String resourcePath = "fdo/test/quote_escape_test.fdo.txt";

        // When: Regular variable contains newlines
        Map<String, Object> varsMap = vars("MESSAGE", "Line1\nLine2");
        String result = FdoTemplateEngine.processTemplate(resourcePath, varsMap);

        // Then: Non-_DATA variables should have newlines converted to spaces (existing behavior)
        assertTrue(result.contains("Line1 Line2"),
            "Non-_DATA variables should not get 0x7F conversion, got: " + result);
        assertFalse(result.contains("7Fx"),
            "Regular variables should not contain 0x7F characters");
    }

    @Test
    void testByteArrayVariable_autoConvertedToHex() {
        // Given: A template with a byte array variable
        Map<String, Object> varsMap = new HashMap<>();
        byte[] data = new byte[]{0x01, 0x02, 0x03, (byte) 0xFF};
        varsMap.put("DATA", data);

        // When: Substituting variables
        String result = FdoTemplateEngine.substituteVariables("test <{{DATA}}>", varsMap);

        // Then: Byte array should be auto-converted to FDO hex format
        assertEquals("test <01x,02x,03x,ffx>", result.toLowerCase());
    }

    @Test
    void testEmptyByteArray_returnsPlaceholder() {
        // Given: A template with an empty byte array variable
        Map<String, Object> varsMap = new HashMap<>();
        byte[] data = new byte[0];
        varsMap.put("DATA", data);

        // When: Substituting variables
        String result = FdoTemplateEngine.substituteVariables("test <{{DATA}}>", varsMap);

        // Then: Empty byte array should produce "00x" placeholder
        assertEquals("test <00x>", result.toLowerCase());
    }
}
