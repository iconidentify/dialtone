/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import io.netty.channel.ChannelHandlerContext;

/**
 * Interface for handling keyword commands sent via the Kk protocol token.
 *
 * <p>Keyword commands allow clients to request server-side actions or information
 * by sending short text commands (e.g., "server logs", "help", "stats"). Each
 * keyword handler implements the logic for a specific command.
 *
 * <p><b>Example implementation:</b>
 * <pre>
 * public class ServerLogsKeywordHandler implements KeywordHandler {
 *     {@literal @}Override
 *     public String getKeyword() {
 *         return "server logs";  // Case-insensitive
 *     }
 *
 *     {@literal @}Override
 *     public String getDescription() {
 *         return "Display recent server log messages";
 *     }
 *
 *     {@literal @}Override
 *     public void handle(String keyword, SessionContext session,
 *                        ChannelHandlerContext ctx, Pacer pacer) throws Exception {
 *         // 1. Gather log data
 *         List&lt;String&gt; logs = LoggerUtil.getRecentLogs(200);
 *
 *         // 2. Format as FDO variables
 *         Map&lt;String, String&gt; vars = new HashMap&lt;&gt;();
 *         vars.put("LOG_CONTENT", String.join("\n", logs));
 *
 *         // 3. Compile and send FDO response
 *         FdoCompiler compiler = new FdoCompiler(props);
 *         List&lt;FdoChunk&gt; chunks = compiler.compileFdoTemplateToP3Chunks(
 *             "fdo/server_logs.fdo.txt", vars, "AT", 0x25, false);
 *         P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "SERVER_LOGS", 10);
 *     }
 * }
 * </pre>
 *
 * <p><b>Registration:</b>
 * <pre>
 * KeywordRegistry registry = KeywordRegistry.getInstance();
 * registry.registerHandler(new ServerLogsKeywordHandler());
 * </pre>
 *
 * @see KeywordRegistry
 * @see KeywordProcessor
 */
public interface KeywordHandler {

    /**
     * Returns the keyword this handler responds to.
     *
     * <p>Keywords are matched case-insensitively. Leading and trailing whitespace
     * is trimmed before matching.
     *
     * <p><b>Examples:</b>
     * <ul>
     *   <li>"server logs" - matches "Server Logs", "SERVER LOGS", "  server logs  "</li>
     *   <li>"help" - matches "HELP", "Help", "help"</li>
     * </ul>
     *
     * @return the keyword string (case-insensitive)
     */
    String getKeyword();

    /**
     * Returns a human-readable description of what this handler does.
     *
     * <p>Used for logging, debugging, and potentially for auto-generated help text.
     *
     * @return description of the handler's functionality
     */
    String getDescription();

    /**
     * Handles the keyword command.
     *
     * <p>This method is called when a client sends a Kk token with de_data matching
     * this handler's keyword. The handler should:
     * <ol>
     *   <li>Gather necessary data (logs, stats, etc.)</li>
     *   <li>Format data as FDO template variables</li>
     *   <li>Compile FDO template to P3 chunks</li>
     *   <li>Send chunks via Pacer (using enqueue methods)</li>
     * </ol>
     *
     * <p><b>Error Handling:</b><br>
     * Implementations should throw exceptions for critical failures (e.g., FDO
     * compilation failure). The caller (KeywordProcessor) will catch and log
     * exceptions, preventing protocol disruption.
     *
     * <p><b>Threading:</b><br>
     * This method is called from the Netty event loop thread. Long-running operations
     * (e.g., external API calls) should be handled asynchronously to avoid blocking.
     *
     * @param keyword the exact keyword text received from the client (after normalization)
     * @param session the client's session context (username, auth state, etc.)
     * @param ctx     the Netty channel context for this connection
     * @param pacer   the frame pacer for queueing response frames
     * @throws Exception if the handler encounters a critical error
     */
    void handle(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) throws Exception;
}
