/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.utils;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

public final class LoggerUtil {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static boolean silent = false;
    private static volatile boolean debugEnabled = false;

    /**
     * Circular buffer for storing recent log messages.
     * Default capacity: 200 messages (configurable via setLogHistoryCapacity).
     */
    private static CircularBuffer<String> logHistory = new CircularBuffer<>(200);

    public static void log(String level, String msg) {
        if (silent) return;
        String line = "[" + TS.format(LocalDateTime.now()) + "][" + level + "] " + msg;
        System.out.println(line);

        // Store in history buffer for "server logs" keyword
        logHistory.add(line);
    }

    public static void info(String msg) { log("INFO", msg); }
    public static void warn(String msg) { log("WARN", msg); }
    public static void error(String msg) { log("ERROR", msg); }
    public static void debug(String msg) { if (debugEnabled) log("DEBUG", msg); }
    public static void debug(Supplier<String> msgSupplier) {
        if (debugEnabled && !silent) {
            log("DEBUG", msgSupplier.get());
        }
    }
    public static boolean isDebugEnabled() { return debugEnabled && !silent; }
    public static void setDebugEnabled(boolean enabled) { debugEnabled = enabled; }

    public static void setSilent(boolean silent) { LoggerUtil.silent = silent; }

    /**
     * Retrieves the last N log messages in chronological order (oldest to newest).
     *
     * @param count maximum number of messages to retrieve
     * @return list of log messages (may be fewer than requested if history is shorter)
     */
    public static List<String> getRecentLogs(int count) {
        return logHistory.getLast(count);
    }

    /**
     * Retrieves all stored log messages in chronological order.
     *
     * @return list of all log messages in history
     */
    public static List<String> getAllLogs() {
        return logHistory.getAll();
    }

    /**
     * Returns the current number of log messages in history.
     *
     * @return log history size
     */
    public static int getLogHistorySize() {
        return logHistory.size();
    }

    /**
     * Configures the capacity of the log history buffer.
     *
     * <p><b>WARNING:</b> This clears the existing history and creates a new buffer.
     *
     * @param capacity new capacity (must be at least 1)
     */
    public static void setLogHistoryCapacity(int capacity) {
        logHistory = new CircularBuffer<>(capacity);
    }

    /**
     * Clears all log messages from history.
     *
     * <p>This is primarily useful for testing.
     */
    public static void clearLogHistory() {
        logHistory.clear();
    }

    private LoggerUtil() {}
}
