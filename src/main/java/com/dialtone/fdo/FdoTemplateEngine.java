/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Unified templating engine for FDO scripts.
 * Supports {{VARNAME}} template syntax with Map-based variable substitution.
 *
 * <p>Variable types supported:</p>
 * <ul>
 *   <li>String - substituted directly (with optional hex conversion for _DATA suffix)</li>
 *   <li>byte[] - auto-converted to FDO hex format (00x,01x,...) for _DATA suffix variables</li>
 * </ul>
 */
public class FdoTemplateEngine {

    /**
     * Load and process an FDO template file from resources with variable substitution.
     *
     * @param resourcePath Path to the template file in resources (e.g., "fdo/motd.fdo.txt")
     * @param variables Map of variable names to values (String or byte[]) for substitution
     * @return Processed FDO script with variables substituted
     * @throws IOException if template file cannot be loaded
     */
    public static String processTemplate(String resourcePath, Map<String, Object> variables) throws IOException {
        String template = loadTemplate(resourcePath);
        return substituteVariables(template, variables);
    }

    /**
     * Load and process an FDO template file with optional black-and-white variant resolution.
     * When lowColorMode is true, attempts to load the .bw.fdo.txt variant first.
     *
     * @param resourcePath Path to the template file in resources (e.g., "fdo/motd.fdo.txt")
     * @param variables Map of variable names to values (String or byte[]) for substitution
     * @param lowColorMode If true, prefer .bw.fdo.txt variant when available
     * @return Processed FDO script with variables substituted
     * @throws IOException if template file cannot be loaded
     */
    public static String processTemplate(String resourcePath, Map<String, Object> variables, boolean lowColorMode) throws IOException {
        String resolvedPath = resolveVariantPath(resourcePath, lowColorMode);
        String template = loadTemplate(resolvedPath);
        return substituteVariables(template, variables);
    }

    /**
     * Resolve FDO resource path to black-and-white variant if available.
     * When lowColorMode is true, checks for a .bw.fdo.txt variant and returns that path
     * if it exists, otherwise falls back to the original path.
     *
     * <p>Example: "fdo/motd.fdo.txt" with lowColorMode=true checks for "fdo/motd.bw.fdo.txt"
     *
     * @param resourcePath Original FDO resource path (e.g., "fdo/motd.fdo.txt" or "replace_client_fdo/32-117.fdo.txt")
     * @param lowColorMode If true, attempt to resolve to .bw.fdo.txt variant
     * @return Resolved path (variant if exists and lowColorMode, otherwise original)
     */
    public static String resolveVariantPath(String resourcePath, boolean lowColorMode) {
        if (!lowColorMode || resourcePath == null) {
            return resourcePath;
        }

        // Convert something.fdo.txt -> something.bw.fdo.txt
        if (resourcePath.endsWith(".fdo.txt")) {
            String variantPath = resourcePath.replace(".fdo.txt", ".bw.fdo.txt");
            // Check if variant exists in classpath
            if (resourceExists(variantPath)) {
                return variantPath;
            }
        }

        return resourcePath; // Fallback to original
    }

    /**
     * Check if a resource exists in the classpath.
     *
     * @param resourcePath Path to check
     * @return true if resource exists, false otherwise
     */
    public static boolean resourceExists(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        try (InputStream inputStream = FdoTemplateEngine.class.getClassLoader().getResourceAsStream(resourcePath)) {
            return inputStream != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Load an FDO template file from resources.
     */
    private static String loadTemplate(String resourcePath) throws IOException {
        if (resourcePath == null) {
            throw new IOException("Template resource path cannot be null");
        }

        try (InputStream inputStream = FdoTemplateEngine.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Template resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Load a static file from the static resources directory.
     * Automatically converts Unix line endings (\n) to AOL protocol format (\r).
     *
     * @param filename Name of the file in static/ directory (e.g., "TOS.txt")
     * @return File content as UTF-8 string with proper AOL line endings
     * @throws IOException if file cannot be loaded
     */
    public static String loadStaticFile(String filename) throws IOException {
        if (filename == null) {
            throw new IOException("Static file name cannot be null");
        }

        String resourcePath = "static/" + filename;
        try (InputStream inputStream = FdoTemplateEngine.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Static file not found: " + resourcePath);
            }
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            // Convert Unix line endings to AOL protocol format
            // AOL client expects \r (0x0D) characters for line breaks, not \n (0x0A)
            // For blank lines, AOL client requires triple \r (\r\r\r) not double \r (\r\r)
            content = content.replace("\n", "\r");
            content = content.replace("\r\r", "\r\r\r");

            return content;
        }
    }

    /**
     * Substitute {{VARNAME}} placeholders with values from the variables map.
     * Supports both String and byte[] values.
     *
     * <p>Value handling:</p>
     * <ul>
     *   <li>byte[] - Always converted to FDO hex format (00x,01x,...)</li>
     *   <li>String with _DATA suffix - Converted to hex pair format</li>
     *   <li>String otherwise - Standard escaping and line break handling</li>
     * </ul>
     *
     * @param template Template string with {{VARNAME}} placeholders
     * @param variables Map of variable names to values (String or byte[]) for substitution
     * @return Processed string with variables substituted
     */
    public static String substituteVariables(String template, Map<String, Object> variables) {
        if (template == null) {
            return "";
        }

        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                Object rawValue = entry.getValue();
                String value = null;

                if (rawValue != null) {
                    // Handle byte[] values - always convert to FDO hex format
                    if (rawValue instanceof byte[]) {
                        value = bytesToFdoHex((byte[]) rawValue);
                    } else {
                        // Handle String values
                        value = rawValue.toString();

                        // Check if this variable should be converted to hex pair format
                        if (shouldConvertToHex(entry.getKey())) {
                            // Convert to hex pair format - no escaping needed
                            value = convertStringToHexPairs(value);
                        } else {
                            // Check if this is raw FDO content that shouldn't have quotes escaped
                            // _FDO variables contain valid FDO source code where quotes are syntax, not content
                            boolean isRawFdo = entry.getKey().contains("_FDO");

                            if (!isRawFdo) {
                                // STEP 1: Escape quotes for Atomforge compatibility (but not for raw FDO content)
                                value = escapeQuotes(value);
                            }

                            // STEP 2: Normalize line breaks for non-_DATA/_LINES variables only
                            boolean preserveNewlines = shouldPreserveNewlines(entry.getKey());
                            if (!preserveNewlines) {
                                value = normalizeLineBreaks(value);
                            }
                        }
                    }
                }
                result = result.replace(placeholder, value != null ? value : "");
            }
        }

        return result;
    }

    /**
     * Convert byte array to FDO hex format (00x,01x,02x,...).
     * This format is used for binary data in FDO atoms like dod_data, idb_append_data, etc.
     * The native FDO compiler handles chunking via UNI continuation protocol.
     *
     * @param bytes Binary data to convert
     * @return FDO hex format string without angle brackets
     */
    private static String bytesToFdoHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "00x";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(String.format("%02xx", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Check if a variable name should preserve newlines (no escaping).
     * Text content variables like TOS_CONTENT, BODY need newlines preserved for AOL display.
     *
     * Matches variables with:
     * - "_DATA" anywhere in the name (for binary/hex data)
     * - "_CONTENT" anywhere in the name (for content like TOS_CONTENT)
     * - "_FDO" anywhere in the name (for embedded FDO scripts like INNER_FDO)
     * - Specific text content variables like "BODY" (for MOTD)
     *
     * Note: The "_LINES" pattern is no longer used - all binary data now uses
     * single large atoms with the native FDO compiler handling chunking automatically.
     */
    private static boolean shouldPreserveNewlines(String variableName) {
        return variableName.contains("_DATA") ||
               variableName.contains("_CONTENT") ||
               variableName.contains("_FDO") ||
               variableName.equals("BODY");
    }

    /**
     * Check if a variable name should be converted to hex pair format.
     *
     * <p>Variables ending with "_DATA" use hex encoding to avoid escaping issues.
     * The _LINES pattern is no longer used - all binary data now uses single large atoms
     * with the native FDO compiler handling chunking automatically.
     *
     * <p>Examples:
     * <ul>
     *   <li>LOG_CONTENT_DATA - converted to hex (ends with _DATA)</li>
     *   <li>DOD_DATA - converted to hex (ends with _DATA)</li>
     *   <li>IDB_APPEND_DATA - converted to hex (ends with _DATA)</li>
     * </ul>
     *
     * @param variableName The variable name to check
     * @return true if the variable should be converted to hex pairs
     */
    private static boolean shouldConvertToHex(String variableName) {
        // Variables ending with "_DATA" should be converted to hex
        return variableName.endsWith("_DATA");
    }

    /**
     * Create username configuration variables for FDO template.
     * This handles the complex binary encoding logic for username data.
     *
     * @param username The username to encode
     * @return Map containing template variables for username configuration
     */
    public static Map<String, String> createUsernameConfigVariables(String username) {
        Map<String, String> variables = new HashMap<>();

        String sanitizedUsername = sanitizeToWidth(username, 10);
        String idbAppendData = buildIdbAppendData(sanitizedUsername);
        variables.put("USERNAME_SELECTOR_BINARY", idbAppendData);

        return variables;
    }

    /**
     * Build the complex idb_append_data section with hex encoding.
     * Handles username data encoding for the legacy DD flow.
     */
    private static String buildIdbAppendData(String username) {
        StringBuilder sb = new StringBuilder();
        sb.append("idb_append_data <");
        appendField(sb, username);                   appendNull(sb);
        appendField(sb, spaces10());                 appendNull(sb); // blank default
        appendField(sb, pad10("Guest"));            appendNull(sb);
        appendField(sb, "7777777777");              appendNull(sb);
        appendField(sb, pad10("New User"));         appendNull(sb);
        appendField(sb, "6666666666");              appendNull(sb);
        appendField(sb, pad10("New Local#"));       appendNull(sb);
        appendField(sb, "5555555555");              appendNull(sb);
        // trim trailing comma+space
        int n = sb.length();
        if (n >= 2 && sb.charAt(n - 2) == ',' && sb.charAt(n - 1) == ' ') sb.setLength(n - 2);
        sb.append('>');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        for (byte b : bytes) {
            sb.append(toHexByte(b)).append('x').append(',').append(' ');
        }
    }

    private static void appendNull(StringBuilder sb) { sb.append("00x, "); }

    private static String toHexByte(byte b) {
        int v = b & 0xFF;
        String hex = Integer.toHexString(v).toUpperCase();
        return (hex.length() == 1 ? "0" + hex : hex);
    }

    /**
     * Convert standard newlines to AOL protocol format using DEL character (0x7F).
     *
     * <p>AOL protocol uses 0x7F (DEL) character for line breaks instead of standard \n.
     * This handles both Unix (\n) and Windows (\r\n) line ending styles.
     *
     * <p>Conversion rules:
     * <ul>
     *   <li>Single \n or \r\n → Single 0x7F (next line/paragraph)</li>
     *   <li>Double \n\n or \r\n\r\n → Double 0x7F (blank line between paragraphs)</li>
     * </ul>
     *
     * @param s Input string with standard newlines
     * @return String with AOL protocol newlines (0x7F characters)
     */
    public static String convertNewlinesToAolFormat(String s) {
        if (s == null) {
            return s;
        }

        // Handle Windows-style line endings first (\r\n)
        // Convert \r\n\r\n -> 0x7F 0x7F (blank line between paragraphs)
        // Convert \r\n     -> 0x7F (next line/paragraph)
        String converted = s.replace("\r\n\r\n", "\u007F\u007F")
                           .replace("\r\n", "\u007F");

        // Handle remaining Unix-style line endings (\n)
        // Convert \n\n -> 0x7F 0x7F (blank line between paragraphs)
        // Convert \n   -> 0x7F (next line/paragraph)
        converted = converted.replace("\n\n", "\u007F\u007F")
                            .replace("\n", "\u007F");

        return converted;
    }

    /**
     * Convert a string to Atomforge hex pair format.
     * Example: "Hello" -> "<48x, 65x, 6Cx, 6Cx, 6Fx>"
     *
     * <p>This format is used for string data in FDO atoms that accept hex sequences.
     * It eliminates the need for quote escaping and provides cleaner string handling.
     * Content is first converted to AOL protocol newline format (0x7F) before hex encoding.
     *
     * @param s Input string to convert
     * @return Hex pair format with angle brackets, or "<>" for null/empty strings
     */
    public static String convertStringToHexPairs(String s) {
        if (s == null || s.isEmpty()) {
            return "<>";
        }

        // STEP 1: Convert standard newlines to AOL protocol format (0x7F)
        String aolFormatted = convertNewlinesToAolFormat(s);

        // STEP 2: Convert to UTF-8 bytes for hex encoding
        byte[] bytes = aolFormatted.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append('<');

        for (int i = 0; i < bytes.length; i++) {
            sb.append(toHexByte(bytes[i])).append('x');
            if (i < bytes.length - 1) {
                sb.append(", ");
            }
        }

        sb.append('>');
        return sb.toString();
    }

    private static String sanitizeToWidth(String s, int width) {
        if (s == null) s = "";
        s = s.replaceAll("[^\\x20-\\x7E]", "");
        if (s.length() > width) s = s.substring(0, width);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private static String pad10(String s) { return sanitizeToWidth(s, 10); }
    private static String spaces10() { return "          "; }

    /**
     * Escape quotes for Atomforge compatibility.
     * This is ALWAYS applied to all variables, including _DATA and _LINES variables.
     *
     * @param s Input string
     * @return String with quotes escaped as \x22
     */
    private static String escapeQuotes(String s) {
        if (s == null) {
            return "";
        }
        // Use Atomforge hex escape sequence for quotes
        return s.replace("\"", "\\x22");
    }

    /**
     * Normalize line breaks by converting raw \r and \n bytes to spaces.
     * This is only applied to non-_DATA and non-_LINES variables.
     * _DATA and _LINES variables preserve their content (including \r escape sequences).
     *
     * @param s Input string
     * @return String with raw line break characters converted to spaces
     */
    private static String normalizeLineBreaks(String s) {
        if (s == null) {
            return "";
        }
        // Convert raw CR/LF bytes to spaces
        // Note: This does NOT affect escape sequences like "\\r" (backslash+r string)
        return s.replace('\r', ' ').replace('\n', ' ');
    }
}