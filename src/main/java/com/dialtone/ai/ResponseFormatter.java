/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.chat.bot.ResponseContext;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.utils.MessageSplitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Formats Grok responses to fit within AOL protocol character limits.
 * Uses a three-layer approach:
 * 1. Check if response already fits
 * 2. AI-powered natural splitting (calls Grok to split naturally)
 * 3. Deterministic fallback (word-boundary truncation with "...")
 */
public class ResponseFormatter {

    private final GrokConversationalService grokService;
    private final int chatRoomLimit;
    private final int imLimit;
    private final long splitDelayMs;
    private final boolean aiSplittingEnabled;
    private final int maxMessages;

    /**
     * Result of formatting a response.
     *
     * @param messages List of message parts (may be single element if no split needed)
     * @param delayBetweenMs Recommended delay between sending split messages (in ms)
     * @param wasAiSplit True if AI was used to split the message
     * @param wasTruncated True if any message was truncated with "..."
     */
    public record FormattedResponse(
        List<String> messages,
        long delayBetweenMs,
        boolean wasAiSplit,
        boolean wasTruncated
    ) {
        /**
         * @return true if this is a single message (no splitting occurred)
         */
        public boolean isSingleMessage() {
            return messages.size() == 1;
        }
    }

    /**
     * Create a ResponseFormatter with the given Grok service and configuration.
     *
     * @param grokService The Grok service for AI splitting (can be null to disable AI splitting)
     * @param properties Configuration properties
     */
    public ResponseFormatter(GrokConversationalService grokService, Properties properties) {
        this.grokService = grokService;
        this.chatRoomLimit = Integer.parseInt(
            properties.getProperty("formatter.chat.room.limit",
                String.valueOf(MessageSplitter.CHAT_MAX_LENGTH)));
        this.imLimit = Integer.parseInt(
            properties.getProperty("formatter.im.limit",
                String.valueOf(MessageSplitter.IM_MAX_LENGTH)));
        this.splitDelayMs = Long.parseLong(
            properties.getProperty("formatter.split.delay.ms", "500"));
        this.aiSplittingEnabled = Boolean.parseBoolean(
            properties.getProperty("formatter.ai.splitting.enabled", "true"));
        this.maxMessages = Integer.parseInt(
            properties.getProperty("formatter.max.messages", "10"));

        LoggerUtil.info(String.format(
            "ResponseFormatter initialized: chatLimit=%d, imLimit=%d, delayMs=%d, aiSplit=%s, maxMsgs=%d",
            chatRoomLimit, imLimit, splitDelayMs, aiSplittingEnabled, maxMessages));
    }

    /**
     * Format a response for the given context.
     * Returns a list of messages with recommended delay between them.
     *
     * @param response The raw response to format
     * @param context The context (CHAT_ROOM or INSTANT_MESSAGE)
     * @return FormattedResponse containing message parts and metadata
     */
    public FormattedResponse format(String response, ResponseContext context) {
        if (response == null || response.isEmpty()) {
            return new FormattedResponse(List.of(), 0, false, false);
        }

        int limit = getLimit(context);

        // Layer 1: Response already fits
        if (response.length() <= limit) {
            LoggerUtil.debug(() -> String.format(
                "Response fits within %s limit (%d <= %d chars)",
                context.name(), response.length(), limit));
            return new FormattedResponse(List.of(response), 0, false, false);
        }

        LoggerUtil.info(String.format(
            "Response exceeds %s limit (%d > %d chars), attempting split",
            context.name(), response.length(), limit));

        // Layer 2: Try AI-powered splitting
        if (aiSplittingEnabled && grokService != null) {
            try {
                List<String> aiSplit = splitWithAI(response, context);
                if (isValidSplit(aiSplit, limit)) {
                    List<String> capped = applyMessageCap(aiSplit);
                    LoggerUtil.info(String.format(
                        "AI successfully split response into %d messages (capped from %d)",
                        capped.size(), aiSplit.size()));
                    return new FormattedResponse(capped, splitDelayMs, true, false);
                }
                LoggerUtil.warn("AI split produced invalid result (messages too long), using fallback");
            } catch (Exception e) {
                LoggerUtil.warn("AI splitting failed: " + e.getMessage() + ", using fallback");
            }
        }

        // Layer 3: Hard fallback - deterministic word-boundary truncation
        List<String> fallbackSplit = splitWithFallback(response, limit);
        boolean wasTruncated = fallbackSplit.stream().anyMatch(s -> s.endsWith("..."));
        List<String> capped = applyMessageCap(fallbackSplit);

        LoggerUtil.info(String.format(
            "Fallback split produced %d messages (capped from %d), truncated=%b",
            capped.size(), fallbackSplit.size(), wasTruncated));

        return new FormattedResponse(capped, splitDelayMs, false, wasTruncated);
    }

    /**
     * Get the character limit for the given context.
     *
     * @param context CHAT_ROOM or INSTANT_MESSAGE
     * @return The character limit (68 for chat, 92 for IM by default)
     */
    public int getLimit(ResponseContext context) {
        return switch (context) {
            case CHAT_ROOM -> chatRoomLimit;
            case INSTANT_MESSAGE -> imLimit;
        };
    }

    /**
     * Check if response needs splitting for the given context.
     *
     * @param response The response to check
     * @param context The context
     * @return true if the response exceeds the limit for this context
     */
    public boolean needsSplitting(String response, ResponseContext context) {
        return response != null && response.length() > getLimit(context);
    }

    /**
     * Get configured delay between split messages.
     *
     * @return Delay in milliseconds
     */
    public long getSplitDelayMs() {
        return splitDelayMs;
    }

    /**
     * Get configured maximum number of messages.
     *
     * @return Max messages cap
     */
    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * Apply the message cap to a list of messages.
     * If the list exceeds maxMessages, truncate to maxMessages.
     *
     * @param messages The original message list
     * @return Capped message list (may be same as input if under cap)
     */
    private List<String> applyMessageCap(List<String> messages) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages;
        }
        return messages.subList(0, maxMessages);
    }

    /**
     * Use Grok AI to split a long response into natural chunks.
     * Each chunk should be a complete, natural thought under the character limit.
     */
    private List<String> splitWithAI(String response, ResponseContext context) throws IOException {
        int limit = getLimit(context);

        String splitPrompt = String.format(
            "Split this message into multiple short messages that each fit in %d characters.\n" +
            "Rules:\n" +
            "- Each message MUST be under %d characters\n" +
            "- Make each message a natural, complete thought\n" +
            "- NO numbering, NO indicators like (1/3) or 'continued'\n" +
            "- Messages should flow naturally when read in sequence\n" +
            "- Output ONLY the messages, one per line, no extra text\n" +
            "- If something must be cut, summarize rather than truncate\n\n" +
            "Message to split:\n%s",
            limit, limit, response
        );

        // Use a focused system prompt for the splitting task
        String splitSystemPrompt = "You are a text formatting assistant. " +
            "Output only the split messages, one per line. No explanations or extra text.";

        // Make a dedicated API call for splitting (using lower temperature for consistency)
        String splitResult = grokService.generateResponse(splitPrompt, splitSystemPrompt);

        return parseSplitResult(splitResult, limit);
    }

    /**
     * Parse the AI's split result into individual messages.
     * Handles various output formats and ensures each message fits the limit.
     */
    private List<String> parseSplitResult(String result, int limit) {
        List<String> messages = new ArrayList<>();

        if (result == null || result.isEmpty()) {
            return messages;
        }

        // Split on newlines and filter empty/too-long lines
        String[] lines = result.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            // Skip empty lines and lines that look like metadata
            if (trimmed.isEmpty()) {
                continue;
            }
            // Skip lines that look like numbering or instructions
            if (trimmed.matches("^\\d+\\.\\s.*") || trimmed.matches("^-\\s.*")) {
                // Remove the numbering prefix and use the rest
                trimmed = trimmed.replaceFirst("^\\d+\\.\\s*", "")
                                 .replaceFirst("^-\\s*", "")
                                 .trim();
            }

            if (!trimmed.isEmpty()) {
                // If AI still produced a line too long, truncate it
                if (trimmed.length() > limit) {
                    trimmed = truncateAtWordBoundary(trimmed, limit);
                }
                messages.add(trimmed);
            }
        }

        return messages;
    }

    /**
     * Validate that all split messages fit within the limit.
     */
    private boolean isValidSplit(List<String> messages, int limit) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.stream().allMatch(m -> m != null && m.length() <= limit);
    }

    /**
     * Hard fallback: split at word boundaries, truncate with "..." as last resort.
     * This is deterministic and doesn't require AI.
     */
    private List<String> splitWithFallback(String response, int limit) {
        List<String> result = new ArrayList<>();

        // Use MessageSplitter with room for potential "..."
        // We use (limit - 3) to leave room for ellipsis if needed
        List<String> rawSplit = MessageSplitter.splitMessage(response, limit - 3);

        for (int i = 0; i < rawSplit.size(); i++) {
            String chunk = rawSplit.get(i).trim();

            // Ensure each chunk fits (should already, but safety check)
            if (chunk.length() > limit) {
                chunk = truncateAtWordBoundary(chunk, limit);
            }

            if (!chunk.isEmpty()) {
                result.add(chunk);
            }
        }

        // If we have multiple chunks from what was a continuous thought,
        // and any chunk got cut off, add "..." to indicate continuation
        // Actually, the MessageSplitter already handles word boundaries well,
        // so we mainly truncate if a single word exceeds the limit

        return result;
    }

    /**
     * Truncate at word boundary with ellipsis.
     * Finds the last space before the limit and adds "...".
     *
     * @param text The text to truncate
     * @param limit Maximum length including the "..."
     * @return Truncated text ending with "..."
     */
    private String truncateAtWordBoundary(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }

        // Reserve space for "..."
        int maxContent = limit - 3;

        if (maxContent <= 0) {
            return "...";
        }

        // Find last space before limit
        String candidate = text.substring(0, Math.min(text.length(), maxContent));
        int lastSpace = candidate.lastIndexOf(' ');

        if (lastSpace > maxContent / 3) {
            // Good word boundary found - cut there
            return text.substring(0, lastSpace).trim() + "...";
        } else {
            // No good word boundary, hard cut
            return candidate.trim() + "...";
        }
    }
}
