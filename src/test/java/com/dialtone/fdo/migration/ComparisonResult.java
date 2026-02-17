/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import java.util.Arrays;

/**
 * Result of comparing template-compiled FDO with DSL-generated FDO.
 *
 * <p>Used by {@link FdoMigrationTestHarness} to report whether template and DSL
 * outputs match, with detailed diff information for debugging mismatches.</p>
 */
public class ComparisonResult {

    private final boolean match;
    private final byte[] templateBinary;
    private final byte[] dslBinary;
    private final String diffReport;

    private ComparisonResult(boolean match, byte[] templateBinary, byte[] dslBinary, String diffReport) {
        this.match = match;
        this.templateBinary = templateBinary;
        this.dslBinary = dslBinary;
        this.diffReport = diffReport;
    }

    /**
     * Create a successful match result.
     */
    public static ComparisonResult match(byte[] templateBinary, byte[] dslBinary) {
        return new ComparisonResult(true, templateBinary, dslBinary, "Binaries match exactly");
    }

    /**
     * Create a mismatch result with diff report.
     */
    public static ComparisonResult mismatch(byte[] templateBinary, byte[] dslBinary, String diffReport) {
        return new ComparisonResult(false, templateBinary, dslBinary, diffReport);
    }

    /**
     * Create an error result when compilation fails.
     */
    public static ComparisonResult error(String errorMessage) {
        return new ComparisonResult(false, null, null, "Compilation error: " + errorMessage);
    }

    /**
     * @return true if template and DSL outputs match
     */
    public boolean isMatch() {
        return match;
    }

    /**
     * @return the compiled template binary, or null on error
     */
    public byte[] getTemplateBinary() {
        return templateBinary;
    }

    /**
     * @return the compiled DSL binary, or null on error
     */
    public byte[] getDslBinary() {
        return dslBinary;
    }

    /**
     * @return detailed diff report for debugging
     */
    public String getDiffReport() {
        return diffReport;
    }

    /**
     * Generate a detailed hex diff between template and DSL binaries.
     *
     * @param maxBytes maximum bytes to include in diff (0 for all)
     * @return formatted hex diff string
     */
    public String generateHexDiff(int maxBytes) {
        if (templateBinary == null || dslBinary == null) {
            return "Cannot generate hex diff: one or both binaries are null";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Template size: %d bytes, DSL size: %d bytes%n",
                templateBinary.length, dslBinary.length));

        int limit = maxBytes > 0
                ? Math.min(maxBytes, Math.max(templateBinary.length, dslBinary.length))
                : Math.max(templateBinary.length, dslBinary.length);

        int firstDiff = -1;
        for (int i = 0; i < limit; i++) {
            byte tb = i < templateBinary.length ? templateBinary[i] : 0;
            byte db = i < dslBinary.length ? dslBinary[i] : 0;
            if (tb != db && firstDiff < 0) {
                firstDiff = i;
            }
        }

        if (firstDiff >= 0) {
            sb.append(String.format("First difference at byte %d (0x%04X)%n", firstDiff, firstDiff));

            // Show context around first difference
            int start = Math.max(0, firstDiff - 8);
            int end = Math.min(limit, firstDiff + 24);

            sb.append(String.format("%nContext (bytes %d-%d):%n", start, end - 1));
            sb.append("Template: ");
            appendHexRange(sb, templateBinary, start, end);
            sb.append("%nDSL:      ");
            appendHexRange(sb, dslBinary, start, end);
            sb.append("%n");
        }

        return sb.toString();
    }

    private void appendHexRange(StringBuilder sb, byte[] data, int start, int end) {
        for (int i = start; i < end; i++) {
            if (i < data.length) {
                sb.append(String.format("%02X ", data[i]));
            } else {
                sb.append("-- ");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("ComparisonResult{match=%s, templateSize=%d, dslSize=%d}",
                match,
                templateBinary != null ? templateBinary.length : -1,
                dslBinary != null ? dslBinary.length : -1);
    }
}
