/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.utils.LoggerUtil;

import java.util.List;

/**
 * Unified stream reassembler for multi-frame messages.
 *
 * <p>This class provides a single entry point for extracting message content
 * from multi-frame sequences. It uses the native FdoStream API for efficient
 * extraction without HTTP round-trips.</p>
 *
 * <p>The FdoStream API handles:</p>
 * <ul>
 *   <li>Large atom continuation (atoms 4, 5, 6) internally</li>
 *   <li>Fragmented FDO structures across frames</li>
 *   <li>Type-safe access to de_data values</li>
 * </ul>
 *
 * <p>Usage: Simply pass all accumulated frames and get the extracted message.</p>
 */
public class UnifiedStreamReassembler {

    // Strategy success counters for production instrumentation
    private static volatile long fdoStreamSuccesses = 0;
    private static volatile long allStrategiesFailures = 0;
    private static volatile long totalExtractions = 0;

    /**
     * Constructor.
     */
    public UnifiedStreamReassembler() {
    }

    /**
     * @deprecated Use no-arg constructor instead
     */
    @Deprecated
    public UnifiedStreamReassembler(StatefulClientHandler handler) {
        // Handler no longer needed - native extraction only
    }

    /**
     * Extracts message text from a sequence of accumulated frames.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Concatenates all frame FDO payloads (stripping P3 headers)</li>
     *   <li>Decodes using native FdoStream API</li>
     *   <li>Extracts de_data values in order</li>
     *   <li>Returns the message content (typically the second de_data)</li>
     * </ol>
     *
     * @param allFrames List of frame byte arrays accumulated for this Stream ID
     * @return message text if extraction succeeds, null if extraction fails
     */
    public String extractUnifiedMessage(List<byte[]> allFrames) {
        if (allFrames == null || allFrames.isEmpty()) {
            LoggerUtil.debug(() -> "[UnifiedStreamReassembler] No frames to extract from");
            return null;
        }

        totalExtractions++;

        String prefix = "[UnifiedStreamReassembler] ";
        LoggerUtil.debug(() -> prefix + "Extracting message from " + allFrames.size() + " frames using FdoStream");

        try {
            // Native FdoStream extraction
            String result = tryFdoStreamExtraction(allFrames);
            if (result != null && !result.isEmpty()) {
                fdoStreamSuccesses++;
                LoggerUtil.info(prefix + "FdoStream extraction succeeded: " + truncateMessage(result));
                return result;
            }

            // Extraction failed
            allStrategiesFailures++;
            LoggerUtil.warn(prefix + "FdoStream extraction failed for " + allFrames.size() + " frames");

            // Log statistics periodically
            if (totalExtractions % 100 == 0) {
                logStatistics();
            }

            return null;

        } catch (Exception e) {
            allStrategiesFailures++;
            LoggerUtil.error(prefix + "Exception during extraction: " + e.getMessage());
            return null;
        }
    }

    /**
     * FdoStream native extraction.
     *
     * <p>Concatenates frame payloads and decodes using the native FdoStream API.
     * This handles large atom continuations and fragmented FDO structures internally.</p>
     */
    private String tryFdoStreamExtraction(List<byte[]> allFrames) {
        try {
            LoggerUtil.debug(() -> "[FdoStream] Attempting native extraction");

            // Extract all de_data values from concatenated frames
            List<String> deDataValues = FdoStreamExtractor.extractDeData(
                    FdoStreamExtractor.concatenateFrames(allFrames));

            if (deDataValues.isEmpty()) {
                LoggerUtil.debug(() -> "[FdoStream] No de_data atoms found");
                return null;
            }

            // For messages, the content is typically the second de_data (first is recipient)
            // If only one de_data exists, it's the message itself
            String message = deDataValues.size() >= 2
                    ? deDataValues.get(1)
                    : deDataValues.get(0);

            if (message != null && !message.trim().isEmpty()) {
                return message;
            }

        } catch (Exception e) {
            LoggerUtil.debug(() -> "[FdoStream] Native extraction failed: " + e.getMessage());
        }

        return null;
    }

    /**
     * Truncates long messages for logging readability.
     */
    private String truncateMessage(String message) {
        if (message == null) return "null";
        return message.length() > 50 ? message.substring(0, 50) + "..." : message;
    }

    /**
     * Logs production statistics.
     */
    private void logStatistics() {
        String prefix = "[UnifiedStreamReassembler] ";
        double successRate = totalExtractions > 0 ? (fdoStreamSuccesses * 100.0 / totalExtractions) : 0;

        LoggerUtil.info(prefix +
            String.format("Statistics (last %d extractions): Successes: %d (%.1f%%), Failures: %d (%.1f%%)",
                totalExtractions,
                fdoStreamSuccesses, fdoStreamSuccesses * 100.0 / totalExtractions,
                allStrategiesFailures, allStrategiesFailures * 100.0 / totalExtractions));
    }

    /**
     * Gets current statistics for monitoring.
     * @return formatted string with success rates
     */
    public static String getStatistics() {
        return String.format("Total: %d, Successes: %d, Failures: %d, Success Rate: %.1f%%",
            totalExtractions, fdoStreamSuccesses, allStrategiesFailures,
            totalExtractions > 0 ? (fdoStreamSuccesses * 100.0 / totalExtractions) : 0);
    }

    /**
     * Resets statistics (useful for testing or periodic monitoring).
     */
    public static void resetStatistics() {
        fdoStreamSuccesses = 0;
        allStrategiesFailures = 0;
        totalExtractions = 0;
    }
}
