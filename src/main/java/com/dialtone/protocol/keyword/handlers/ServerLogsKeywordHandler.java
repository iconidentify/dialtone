/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ServerLogsFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Keyword handler for displaying recent server log messages.
 *
 * <p><b>Keyword:</b> "server logs" (case-insensitive)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Retrieves last 200 log messages from LoggerUtil</li>
 *   <li>Formats messages in monospace font</li>
 *   <li>Displays in scrollable window (like MOTD)</li>
 *   <li>Includes timestamp of log retrieval</li>
 * </ul>
 *
 * <p><b>FDO Template:</b> {@code fdo/server_logs.fdo.txt}
 *
 * <p><b>Variables:</b>
 * <ul>
 *   <li>{@code {{WINDOW_TITLE}}}: "Server Logs"</li>
 *   <li>{{TIMESTAMP}}}: Current time when logs retrieved</li>
 *   <li>{@code {{LOG_CONTENT}}}: Formatted log messages (one per line)</li>
 *   <li>{@code {{LOG_COUNT}}}: Number of log entries displayed</li>
 * </ul>
 */
public class ServerLogsKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "server logs";
    private static final String DESCRIPTION = "Display recent server log messages";
    private static final int MAX_LOGS_TO_RETRIEVE = 100;
    private static final int MAX_BURST_FRAMES = 1;
    private static final int MAX_CONTENT_BYTES = 4400;  // Client crash threshold

    /**
     * Holds the result of formatting log content with byte limit protection.
     */
    private static class LogContentResult {
        final String content;
        final int actualLineCount;

        LogContentResult(String content, int actualLineCount) {
            this.content = content;
            this.actualLineCount = actualLineCount;
        }
    }

    private final FdoCompiler fdoCompiler;

    /**
     * Creates a new ServerLogsKeywordHandler.
     *
     * @param fdoCompiler the FDO compiler for template compilation
     */
    public ServerLogsKeywordHandler(FdoCompiler fdoCompiler) {
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        this.fdoCompiler = fdoCompiler;
    }

    @Override
    public String getKeyword() {
        return KEYWORD;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void handle(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) throws Exception {
        LoggerUtil.info("Retrieving server logs for user: " + session.getDisplayName());

        // Retrieve recent log messages
        List<String> logs = LoggerUtil.getRecentLogs(MAX_LOGS_TO_RETRIEVE);

        if (logs.isEmpty()) {
            LoggerUtil.warn("No log messages available to display");
            // Could send an error FDO or just return silently
            return;
        }

        // Format log content with byte limit protection
        LogContentResult result = formatLogContent(logs);
        String logContent = result.content;
        String timestamp = formatTimestamp();

        // Build FDO using DSL builder
        ServerLogsFdoBuilder builder = new ServerLogsFdoBuilder(
            "Server Logs", timestamp, result.actualLineCount, logContent);
        String fdoSource = builder.toSource(RenderingContext.DEFAULT);

        // Compile FDO to P3 chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID  // Auto-generate random stream ID
        );

        LoggerUtil.info(String.format(
            "Compiled server logs FDO: %d chunks, retrieved %d lines, sent %d lines, %d bytes",
            chunks != null ? chunks.size() : 0,
            logs.size(),
            result.actualLineCount,
            logContent.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
        ));

        // Send P3 chunks via Pacer
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "SERVER_LOGS", MAX_BURST_FRAMES,
                                session.getDisplayName());

        LoggerUtil.info("Server logs sent to user: " + session.getDisplayName());
    }

    /**
     * Formats log messages for display in FDO view with byte limit protection.
     *
     * <p>Adds log lines one by one, checking that the total content doesn't exceed
     * {@link #MAX_CONTENT_BYTES}. If a line would exceed the limit, stops adding
     * more lines. If the first line itself exceeds the limit, truncates it.
     *
     * @param logs list of log messages
     * @return LogContentResult with formatted content and actual line count
     */
    private LogContentResult formatLogContent(List<String> logs) {
        StringBuilder sb = new StringBuilder();
        int lineCount = 0;

        for (String log : logs) {
            // Build candidate string with this line
            String candidate = lineCount == 0 ? log : sb.toString() + "\r" + log;
            byte[] bytes = candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            if (bytes.length <= MAX_CONTENT_BYTES) {
                // Fits! Add it
                if (lineCount > 0) {
                    sb.append("\r");
                }
                sb.append(log);
                lineCount++;
            } else {
                // Would exceed limit
                if (lineCount == 0) {
                    // Edge case: first line itself is too large
                    // Truncate it to fit, leaving room for "..."
                    String truncated = truncateToFit(log, MAX_CONTENT_BYTES - 3);
                    sb.append(truncated).append("...");
                    lineCount = 1;
                }
                // Stop adding more lines
                break;
            }
        }

        return new LogContentResult(sb.toString(), lineCount);
    }

    /**
     * Truncates text to fit within the specified byte limit.
     *
     * <p>Uses binary search to handle multi-byte UTF-8 characters correctly.
     *
     * @param text the text to truncate
     * @param maxBytes maximum number of bytes
     * @return truncated text that fits within maxBytes
     */
    private String truncateToFit(String text, int maxBytes) {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }

        // Binary search to find how many characters fit
        int left = 0;
        int right = text.length();

        while (left < right) {
            int mid = (left + right + 1) / 2;
            if (text.substring(0, mid).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= maxBytes) {
                left = mid;
            } else {
                right = mid - 1;
            }
        }

        return text.substring(0, left);
    }

    /**
     * Formats the current timestamp for display.
     *
     * @return formatted timestamp string
     */
    private String formatTimestamp() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return LocalDateTime.now().format(formatter);
    }
}
