/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword;

import com.dialtone.utils.LoggerUtil;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for keyword command handlers.
 *
 * <p>This singleton maintains a mapping from keyword strings to their handlers.
 * Keywords are matched case-insensitively, and leading/trailing whitespace is
 * trimmed before lookup.
 *
 * <p><b>Initialization Pattern:</b>
 * <pre>
 * // In DialtoneServer.initializeSharedServices():
 * KeywordRegistry registry = KeywordRegistry.getInstance();
 * registry.registerHandler(new ServerLogsKeywordHandler());
 * registry.registerHandler(new HelpKeywordHandler());
 * registry.registerHandler(new StatsKeywordHandler());
 * </pre>
 *
 * <p><b>Lookup Pattern:</b>
 * <pre>
 * // In StatefulClientHandler or KeywordProcessor:
 * String keyword = "Server Logs";  // From Kk token de_data
 * KeywordHandler handler = registry.getHandler(keyword);
 * if (handler != null) {
 *     handler.handle(keyword, session, ctx, pacer);
 * } else {
 *     LoggerUtil.warn("Unknown keyword: " + keyword);
 * }
 * </pre>
 *
 * @see KeywordHandler
 * @see KeywordProcessor
 */
public class KeywordRegistry {

    private static volatile KeywordRegistry INSTANCE = null;

    /**
     * Storage for keyword handlers.
     * Key: normalized keyword (lowercase, trimmed)
     * Value: handler implementation
     */
    private final ConcurrentHashMap<String, KeywordHandler> handlers;

    private KeywordRegistry() {
        this.handlers = new ConcurrentHashMap<>();
        LoggerUtil.info("KeywordRegistry initialized");
    }

    /**
     * Get the singleton instance.
     *
     * @return the singleton instance (never null)
     */
    public static KeywordRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (KeywordRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new KeywordRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Registers a keyword handler.
     *
     * <p>If a handler for the same keyword already exists, it will be replaced
     * and a warning will be logged.
     *
     * @param handler the handler to register
     * @throws IllegalArgumentException if handler is null or keyword is empty
     */
    public void registerHandler(KeywordHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Cannot register null handler");
        }

        String keyword = handler.getKeyword();
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new IllegalArgumentException("Handler keyword cannot be null or empty");
        }

        String normalizedKeyword = normalizeKeyword(keyword);

        KeywordHandler existing = handlers.put(normalizedKeyword, handler);

        if (existing != null) {
            LoggerUtil.warn(String.format("Replaced existing handler for keyword '%s' (was: %s, now: %s)",
                keyword, existing.getClass().getSimpleName(), handler.getClass().getSimpleName()));
        } else {
            LoggerUtil.info(String.format("Registered keyword handler: '%s' -> %s (%s)",
                keyword, handler.getClass().getSimpleName(), handler.getDescription()));
        }
    }

    /**
     * Retrieves a handler for the given keyword.
     *
     * <p>Lookup is case-insensitive and whitespace-trimmed.
     *
     * <p><b>Matching Strategy:</b>
     * <ol>
     *   <li>First tries exact match (e.g., "server logs" → "server logs" handler)</li>
     *   <li>If no exact match, tries prefix match for parameterized commands
     *       (e.g., "mat_art_id &lt;32-5446&gt;" → "mat_art_id" handler)</li>
     * </ol>
     *
     * <p>This enables parameterized keywords while maintaining backward compatibility
     * with exact-match keywords like "server logs".
     *
     * @param keyword the keyword to look up
     * @return the handler if found, null otherwise
     */
    public KeywordHandler getHandler(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        String normalizedKeyword = normalizeKeyword(keyword);

        // First try exact match (backward compatible with existing handlers)
        KeywordHandler handler = handlers.get(normalizedKeyword);
        if (handler != null) {
            return handler;
        }

        // Then try prefix match for parameterized commands
        // Split on first space to get base command
        int spaceIndex = normalizedKeyword.indexOf(' ');
        if (spaceIndex > 0) {
            String baseCommand = normalizedKeyword.substring(0, spaceIndex);
            return handlers.get(baseCommand);
        }

        return null;
    }

    /**
     * Checks if a handler is registered for the given keyword.
     *
     * @param keyword the keyword to check
     * @return true if a handler exists, false otherwise
     */
    public boolean hasHandler(String keyword) {
        return getHandler(keyword) != null;
    }

    /**
     * Unregisters a keyword handler.
     *
     * @param keyword the keyword whose handler should be removed
     * @return true if a handler was removed, false if no handler existed
     */
    public boolean unregisterHandler(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return false;
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        KeywordHandler removed = handlers.remove(normalizedKeyword);

        if (removed != null) {
            LoggerUtil.info(String.format("Unregistered keyword handler: '%s' (%s)",
                keyword, removed.getClass().getSimpleName()));
            return true;
        }

        return false;
    }

    /**
     * Returns all registered handlers.
     *
     * <p>The returned collection is a snapshot and will not reflect subsequent
     * registry changes.
     *
     * @return collection of all registered handlers
     */
    public Collection<KeywordHandler> getAllHandlers() {
        return handlers.values();
    }

    /**
     * Returns the number of registered handlers.
     *
     * @return handler count
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * Checks if the registry is empty (no handlers registered).
     *
     * @return true if no handlers are registered, false otherwise
     */
    public boolean isEmpty() {
        return handlers.isEmpty();
    }

    /**
     * Clears all registered handlers.
     *
     * <p>This is primarily useful for testing. Production code should rarely
     * need to clear the registry.
     */
    public void clear() {
        int count = handlers.size();
        handlers.clear();
        LoggerUtil.info("Cleared " + count + " keyword handler(s) from registry");
    }

    /**
     * Normalizes a keyword for consistent lookup.
     *
     * <p>Normalization: lowercase + trim whitespace
     *
     * @param keyword the raw keyword
     * @return normalized keyword
     */
    private String normalizeKeyword(String keyword) {
        return keyword.trim().toLowerCase();
    }

    /**
     * Resets the singleton instance.
     *
     * <p><b>WARNING:</b> This is for testing only. Do not call in production code.
     */
    public static synchronized void resetInstance() {
        INSTANCE = null;
    }
}
