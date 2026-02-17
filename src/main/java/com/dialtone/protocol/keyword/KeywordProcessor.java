/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

/**
 * Main coordinator for processing keyword commands from Kk protocol tokens.
 *
 * <p>This class serves as the bridge between the protocol handler (StatefulClientHandler)
 * and the keyword handler system. When a Kk token arrives and is fully assembled
 * (multi-frame or single-frame), the keyword text is passed to this processor.
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Normalize keyword text (trim, lowercase)</li>
 *   <li>Look up handler in KeywordRegistry</li>
 *   <li>Invoke handler with proper error handling</li>
 *   <li>Log unknown keywords for debugging</li>
 * </ul>
 *
 * <p><b>Usage Pattern:</b>
 * <pre>
 * // In StatefulClientHandler, after Kk stream completion:
 * case "Kk":
 *     String keyword = extractKeywordFromStream(frames);
 *     KeywordProcessor.processKeyword(keyword, session, ctx, pacer);
 *     break;
 * </pre>
 *
 * @see KeywordHandler
 * @see KeywordRegistry
 */
public final class KeywordProcessor {

    private KeywordProcessor() {
        // Utility class - prevent instantiation
    }

    /**
     * Processes a keyword command received from the client.
     *
     * <p>This method:
     * <ol>
     *   <li>Normalizes the keyword (trim whitespace)</li>
     *   <li>Looks up the handler in KeywordRegistry</li>
     *   <li>Invokes the handler if found</li>
     *   <li>Logs appropriate messages for success, failure, or unknown keywords</li>
     * </ol>
     *
     * <p><b>Error Handling:</b><br>
     * Handler exceptions are caught and logged, preventing protocol disruption.
     * The connection remains active even if a handler fails.
     *
     * @param keyword the keyword text from the Kk token's de_data field
     * @param session the client's session context
     * @param ctx     the Netty channel context
     * @param pacer   the frame pacer for sending responses
     * @return true if keyword was successfully processed, false if empty or unknown
     */
    public static boolean processKeyword(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) {
        if (keyword == null || keyword.trim().isEmpty()) {
            LoggerUtil.warn("Received empty keyword - ignoring");
            return false;
        }

        // Normalize keyword (trim whitespace - registry handles case-insensitivity)
        String normalizedKeyword = keyword.trim();

        LoggerUtil.info(String.format("Processing keyword: '%s' (user: %s)",
            normalizedKeyword, session.getDisplayName()));

        // Look up handler in registry
        KeywordRegistry registry = KeywordRegistry.getInstance();
        KeywordHandler handler = registry.getHandler(normalizedKeyword);

        if (handler == null) {
            LoggerUtil.warn(String.format("Unknown keyword: '%s' (no handler registered)", normalizedKeyword));
            logAvailableKeywords();
            return false;
        }

        // Invoke handler with error handling
        try {
            LoggerUtil.debug(() -> String.format("Invoking handler: %s for keyword '%s'",
                handler.getClass().getSimpleName(), normalizedKeyword));

            handler.handle(normalizedKeyword, session, ctx, pacer);

            LoggerUtil.info(String.format("Successfully processed keyword: '%s' (handler: %s)",
                normalizedKeyword, handler.getClass().getSimpleName()));

            return true;

        } catch (Exception e) {
            LoggerUtil.error(String.format("Keyword handler failed: '%s' (handler: %s) - %s",
                normalizedKeyword, handler.getClass().getSimpleName(), e.getMessage()));

            if (LoggerUtil.isDebugEnabled()) {
                e.printStackTrace();
            }

            // Connection remains active - handler errors don't kill the session
            return false;
        }
    }

    /**
     * Logs all available keywords for debugging.
     *
     * <p>This is called when an unknown keyword is received to help users
     * discover available commands.
     */
    private static void logAvailableKeywords() {
        KeywordRegistry registry = KeywordRegistry.getInstance();

        if (registry.isEmpty()) {
            LoggerUtil.debug(() -> "No keyword handlers registered");
            return;
        }

        StringBuilder sb = new StringBuilder("Available keywords:\n");
        for (KeywordHandler handler : registry.getAllHandlers()) {
            sb.append(String.format("  - '%s': %s\n", handler.getKeyword(), handler.getDescription()));
        }

        LoggerUtil.debug(() -> sb.toString());
    }
}
