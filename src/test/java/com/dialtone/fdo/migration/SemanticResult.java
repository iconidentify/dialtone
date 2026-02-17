/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.migration;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of semantic comparison between template and DSL FDO binaries.
 *
 * <p>Unlike binary comparison, semantic comparison decodes both FDOs and compares
 * their atom sequences. This is more forgiving of minor formatting differences
 * while still catching functional discrepancies.</p>
 */
public class SemanticResult {

    private final boolean equivalent;
    private final List<String> templateAtoms;
    private final List<String> dslAtoms;
    private final List<String> differences;
    private final String summary;

    private SemanticResult(boolean equivalent, List<String> templateAtoms,
                           List<String> dslAtoms, List<String> differences, String summary) {
        this.equivalent = equivalent;
        this.templateAtoms = templateAtoms != null ? templateAtoms : new ArrayList<>();
        this.dslAtoms = dslAtoms != null ? dslAtoms : new ArrayList<>();
        this.differences = differences != null ? differences : new ArrayList<>();
        this.summary = summary;
    }

    /**
     * Create an equivalent result (atoms match semantically).
     */
    public static SemanticResult equivalent(List<String> templateAtoms, List<String> dslAtoms) {
        return new SemanticResult(true, templateAtoms, dslAtoms, List.of(),
                String.format("Semantically equivalent: %d atoms match", templateAtoms.size()));
    }

    /**
     * Create a non-equivalent result with differences.
     */
    public static SemanticResult notEquivalent(List<String> templateAtoms, List<String> dslAtoms,
                                               List<String> differences) {
        return new SemanticResult(false, templateAtoms, dslAtoms, differences,
                String.format("Not equivalent: %d differences found", differences.size()));
    }

    /**
     * Create an error result when decoding fails.
     */
    public static SemanticResult error(String errorMessage) {
        return new SemanticResult(false, null, null, List.of(errorMessage),
                "Decoding error: " + errorMessage);
    }

    /**
     * @return true if template and DSL are semantically equivalent
     */
    public boolean isEquivalent() {
        return equivalent;
    }

    /**
     * @return list of atom descriptions from template binary
     */
    public List<String> getTemplateAtoms() {
        return templateAtoms;
    }

    /**
     * @return list of atom descriptions from DSL binary
     */
    public List<String> getDslAtoms() {
        return dslAtoms;
    }

    /**
     * @return list of differences found during comparison
     */
    public List<String> getDifferences() {
        return differences;
    }

    /**
     * @return summary of comparison result
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Generate a detailed report of semantic differences.
     *
     * @return formatted difference report
     */
    public String generateDiffReport() {
        if (equivalent) {
            return summary;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(summary).append("\n\n");

        sb.append(String.format("Template atoms: %d, DSL atoms: %d%n%n",
                templateAtoms.size(), dslAtoms.size()));

        if (!differences.isEmpty()) {
            sb.append("Differences:\n");
            for (int i = 0; i < differences.size() && i < 20; i++) {
                sb.append("  - ").append(differences.get(i)).append("\n");
            }
            if (differences.size() > 20) {
                sb.append(String.format("  ... and %d more%n", differences.size() - 20));
            }
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("SemanticResult{equivalent=%s, templateAtoms=%d, dslAtoms=%d, differences=%d}",
                equivalent, templateAtoms.size(), dslAtoms.size(), differences.size());
    }
}
