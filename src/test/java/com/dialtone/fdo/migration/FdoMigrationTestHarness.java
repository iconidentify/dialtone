/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.spi.FdoCompilationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Test harness for validating FDO template to DSL migrations.
 *
 * <p>This harness provides three tiers of comparison:
 * <ol>
 *   <li><b>Binary comparison</b> - Exact byte-for-byte match</li>
 *   <li><b>Semantic comparison</b> - Decoded atom sequence comparison</li>
 *   <li><b>Structural comparison</b> - Verify key atoms present and ordered</li>
 * </ol>
 *
 * <p>Usage example:
 * <pre>{@code
 * FdoMigrationTestHarness harness = new FdoMigrationTestHarness(properties);
 *
 * // Compare template with DSL builder
 * ComparisonResult result = harness.compare(
 *     "fdo/receive_im.fdo.txt",
 *     new ReceiveImFdoBuilder(config),
 *     Map.of("WINDOW_ID", "42", "FROM_USER", "TestUser"),
 *     new RenderingContext(ClientPlatform.WINDOWS, false)
 * );
 *
 * assertTrue(result.isMatch(), result.getDiffReport());
 * }</pre>
 *
 * <p>For templates with variables, provide fixed test values via the variables map.
 * The DSL builder should be configured with matching values.</p>
 */
public class FdoMigrationTestHarness {

    private final FdoCompiler compiler;

    /**
     * Create a new test harness with the given properties.
     *
     * @param properties Configuration properties for FdoCompiler
     */
    public FdoMigrationTestHarness(Properties properties) {
        this.compiler = new FdoCompiler(properties);
    }

    /**
     * Create a test harness with default properties.
     */
    public FdoMigrationTestHarness() {
        Properties props = new Properties();
        props.setProperty("fdo.compiler.backend", "java");
        this.compiler = new FdoCompiler(props);
    }

    /**
     * Compare template compilation output with DSL builder output.
     *
     * <p>Compiles both the template (with variable substitution) and the DSL builder,
     * then performs a binary comparison of the results.</p>
     *
     * @param templatePath Resource path to .fdo.txt template
     * @param builder The DSL builder to compare against
     * @param variables Fixed variables for template substitution
     * @param context Rendering context (platform + color mode)
     * @return Comparison result with match status and diff details
     */
    public ComparisonResult compare(
            String templatePath,
            FdoDslBuilder builder,
            Map<String, String> variables,
            RenderingContext context) {

        try {
            // 1. Compile template with variables
            List<FdoChunk> templateChunks = compiler.compileFdoTemplateToP3Chunks(
                    templatePath, variables, "AT", 0, context.isLowColorMode());
            byte[] templateBinary = extractBinary(templateChunks);

            // 2. Generate DSL source and compile
            String dslSource = builder.toSource(context);
            List<FdoChunk> dslChunks = compiler.compileFdoScriptToP3Chunks(dslSource, "AT", 0);
            byte[] dslBinary = extractBinary(dslChunks);

            // 3. Compare binaries
            if (Arrays.equals(templateBinary, dslBinary)) {
                return ComparisonResult.match(templateBinary, dslBinary);
            } else {
                String diffReport = generateDiffReport(templateBinary, dslBinary);
                return ComparisonResult.mismatch(templateBinary, dslBinary, diffReport);
            }

        } catch (FdoCompilationException | IOException e) {
            return ComparisonResult.error(e.getMessage());
        }
    }

    /**
     * Compare DSL output against expected FDO source string.
     *
     * <p>Useful for testing DSL builders independently without a template file.</p>
     *
     * @param expectedFdoSource Expected FDO source (as would appear in template)
     * @param builder The DSL builder to test
     * @param context Rendering context
     * @return Comparison result
     */
    public ComparisonResult compareWithSource(
            String expectedFdoSource,
            FdoDslBuilder builder,
            RenderingContext context) {

        try {
            // 1. Compile expected source
            List<FdoChunk> expectedChunks = compiler.compileFdoScriptToP3Chunks(expectedFdoSource, "AT", 0);
            byte[] expectedBinary = extractBinary(expectedChunks);

            // 2. Generate DSL source and compile
            String dslSource = builder.toSource(context);
            List<FdoChunk> dslChunks = compiler.compileFdoScriptToP3Chunks(dslSource, "AT", 0);
            byte[] dslBinary = extractBinary(dslChunks);

            // 3. Compare binaries
            if (Arrays.equals(expectedBinary, dslBinary)) {
                return ComparisonResult.match(expectedBinary, dslBinary);
            } else {
                String diffReport = generateDiffReport(expectedBinary, dslBinary);
                return ComparisonResult.mismatch(expectedBinary, dslBinary, diffReport);
            }

        } catch (FdoCompilationException e) {
            return ComparisonResult.error(e.getMessage());
        }
    }

    /**
     * Perform semantic comparison by comparing FDO source strings.
     *
     * <p>More forgiving than binary comparison - compares generated source text
     * rather than compiled bytes. Useful when minor formatting differences are acceptable.</p>
     *
     * @param templateSource Template FDO source
     * @param dslSource DSL-generated FDO source
     * @return Semantic comparison result
     */
    public SemanticResult compareSemantics(String templateSource, String dslSource) {
        // Normalize both sources (trim whitespace, normalize line endings)
        String normalizedTemplate = normalizeSource(templateSource);
        String normalizedDsl = normalizeSource(dslSource);

        List<String> templateLines = List.of(normalizedTemplate.split("\n"));
        List<String> dslLines = List.of(normalizedDsl.split("\n"));

        // Compare line by line
        List<String> differences = compareLines(templateLines, dslLines);

        if (differences.isEmpty()) {
            return SemanticResult.equivalent(templateLines, dslLines);
        } else {
            return SemanticResult.notEquivalent(templateLines, dslLines, differences);
        }
    }

    /**
     * Normalize FDO source for comparison.
     *
     * <p>Normalization includes:
     * <ul>
     *   <li>Trimming whitespace</li>
     *   <li>Normalizing line endings to Unix style</li>
     *   <li>Collapsing multiple spaces/tabs to single space</li>
     *   <li>Removing leading whitespace from lines</li>
     *   <li>Stripping stream labels like {@code <00x>} (DSL adds these, templates don't)</li>
     * </ul>
     */
    private String normalizeSource(String source) {
        if (source == null) return "";
        return source.trim()
            .replaceAll("\r\n", "\n")
            .replaceAll("[ \t]+", " ")
            .replaceAll("\n[ \t]+", "\n")
            // Strip stream labels like <00x> - DSL adds these but templates don't
            .replaceAll(" <[0-9a-fA-Fx]+>", "");
    }

    /**
     * Compare two lists of lines and return differences.
     */
    private List<String> compareLines(List<String> expected, List<String> actual) {
        List<String> differences = new ArrayList<>();
        int maxLen = Math.max(expected.size(), actual.size());

        for (int i = 0; i < maxLen; i++) {
            String exp = i < expected.size() ? expected.get(i) : "<missing>";
            String act = i < actual.size() ? actual.get(i) : "<missing>";

            if (!exp.equals(act)) {
                differences.add(String.format("Line %d: expected '%s' but got '%s'",
                    i + 1, truncate(exp, 60), truncate(act, 60)));
            }
        }

        return differences;
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * Verify that DSL output contains required structural elements.
     *
     * <p>Checks that expected atoms/patterns appear in the DSL source in the correct order.
     * Each pattern is searched for starting from the position after the previous match,
     * which prevents false matches like finding "dod_end" inside "dod_end_data".</p>
     *
     * @param dslSource Generated DSL source
     * @param requiredPatterns Patterns that must appear in order
     * @return true if all patterns found in order
     */
    public boolean verifyStructure(String dslSource, String... requiredPatterns) {
        int searchFrom = 0;
        for (String pattern : requiredPatterns) {
            int index = dslSource.indexOf(pattern, searchFrom);
            if (index < 0) {
                return false;
            }
            // Start next search after the end of this pattern
            searchFrom = index + pattern.length();
        }
        return true;
    }

    /**
     * Get the underlying FdoCompiler for advanced testing.
     */
    public FdoCompiler getCompiler() {
        return compiler;
    }

    /**
     * Compile a DSL builder to binary.
     *
     * <p>Useful for verifying DSL compiles without requiring a template comparison.</p>
     *
     * @param builder The DSL builder
     * @param context Rendering context
     * @return Compiled binary, or null if compilation fails
     */
    public byte[] compileDsl(FdoDslBuilder builder, RenderingContext context) {
        try {
            String dslSource = builder.toSource(context);
            List<FdoChunk> chunks = compiler.compileFdoScriptToP3Chunks(dslSource, "AT", 0);
            return extractBinary(chunks);
        } catch (FdoCompilationException e) {
            return null;
        }
    }

    // --- Private helpers ---

    /**
     * Extract raw binary data from FDO chunks.
     */
    private byte[] extractBinary(List<FdoChunk> chunks) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (FdoChunk chunk : chunks) {
            baos.writeBytes(chunk.getBinaryData());
        }
        return baos.toByteArray();
    }

    /**
     * Generate a detailed diff report between two binaries.
     */
    private String generateDiffReport(byte[] expected, byte[] actual) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Binary size mismatch: expected %d bytes, got %d bytes%n",
                expected.length, actual.length));

        // Find first difference
        int firstDiff = -1;
        int minLen = Math.min(expected.length, actual.length);
        for (int i = 0; i < minLen; i++) {
            if (expected[i] != actual[i]) {
                firstDiff = i;
                break;
            }
        }

        if (firstDiff < 0 && expected.length != actual.length) {
            firstDiff = minLen;
            sb.append(String.format("Binaries match up to byte %d, but lengths differ%n", minLen));
        } else if (firstDiff >= 0) {
            sb.append(String.format("First difference at byte %d (0x%04X)%n", firstDiff, firstDiff));

            // Show hex context around first difference
            int start = Math.max(0, firstDiff - 8);
            int end = Math.min(Math.max(expected.length, actual.length), firstDiff + 24);

            sb.append(String.format("%nExpected (bytes %d-%d): ", start, end - 1));
            appendHex(sb, expected, start, end);

            sb.append(String.format("%nActual   (bytes %d-%d): ", start, end - 1));
            appendHex(sb, actual, start, end);
            sb.append("%n");
        }

        return sb.toString();
    }

    private void appendHex(StringBuilder sb, byte[] data, int start, int end) {
        for (int i = start; i < end; i++) {
            if (i < data.length) {
                sb.append(String.format("%02X ", data[i]));
            } else {
                sb.append("-- ");
            }
        }
    }
}
