/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for extracting parameters from keyword commands.
 *
 * <p>Supports two parameter formats:
 * <ol>
 *   <li><b>Angle-bracketed (FDO-style):</b> {@code art &lt;32-5446&gt;}</li>
 *   <li><b>Bare (space-separated):</b> {@code art 32-5446}</li>
 * </ol>
 *
 * <p><b>Single Parameter Extraction:</b>
 * <pre>
 * // Bracketed syntax
 * String keyword1 = "art &lt;32-5446&gt;";
 * String artId1 = KeywordParameterExtractor.extractParameter(keyword1);
 * // artId1 = "32-5446"
 *
 * // Bare syntax (fallback)
 * String keyword2 = "invoke 1-0-21029";
 * String artId2 = KeywordParameterExtractor.extractParameter(keyword2);
 * // artId2 = "1-0-21029"
 * </pre>
 *
 * <p><b>Multiple Parameter Extraction:</b><br>
 * For future commands with multiple parameters (bracketed syntax only):
 * <pre>
 * String keyword = "send_file &lt;filename.txt&gt; &lt;user&gt;";
 * List&lt;String&gt; params = KeywordParameterExtractor.extractAllParameters(keyword);
 * // params = ["filename.txt", "user"]
 * </pre>
 *
 * @see KeywordHandler
 * @see KeywordRegistry
 */
public final class KeywordParameterExtractor {

    /**
     * Pattern to match parameters in angle brackets.
     * Matches: &lt;anything-except-closing-bracket&gt;
     * Examples: &lt;32-5446&gt;, &lt;user123&gt;, &lt;1-0-21029&gt;
     */
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("<([^>]+)>");

    private KeywordParameterExtractor() {
        // Utility class - prevent instantiation
    }

    /**
     * Extract single parameter from keyword command.
     *
     * <p>Parameters must be wrapped in angle brackets to be considered valid.
     * This mirrors the AOL keyword syntax where parameters are explicitly delimited.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code extractParameter("art &lt;32-5446&gt;")} → {@code "32-5446"} (bracketed)</li>
     *   <li>{@code extractParameter("invoke &lt;1-0-21029&gt;")} → {@code "1-0-21029"} (bracketed)</li>
     *   <li>{@code extractParameter("cmd &lt;a&gt; &lt;b&gt;")} → {@code "a"} (first bracketed param only)</li>
     *   <li>{@code extractParameter("keyword")} → {@code null} (no parameter)</li>
     * </ul>
     *
     * @param keyword the keyword command string
     * @return the first parameter value, or null if no parameters found
     */
    public static String extractParameter(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        // First try: extract from angle brackets (preserves existing behavior)
        Matcher matcher = PARAMETER_PATTERN.matcher(keyword);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // No parameter found
        return null;
    }

    /**
     * Extract all parameters from keyword command.
     *
     * <p>Returns a list of all parameters found, in order of appearance.
     * Returns empty list if no parameters exist.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>{@code extractAllParameters("cmd &lt;a&gt; &lt;b&gt; &lt;c&gt;")} → {@code ["a", "b", "c"]}</li>
     *   <li>{@code extractAllParameters("mat_art_id &lt;32-5446&gt;")} → {@code ["32-5446"]}</li>
     *   <li>{@code extractAllParameters("server logs")} → {@code []} (empty list)</li>
     * </ul>
     *
     * @param keyword the keyword command string
     * @return list of parameter values (empty if none found, never null)
     */
    public static List<String> extractAllParameters(String keyword) {
        List<String> params = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            return params;
        }

        Matcher matcher = PARAMETER_PATTERN.matcher(keyword);
        while (matcher.find()) {
            params.add(matcher.group(1));
        }
        return params;
    }
}
