/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.InvokeResponseFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.keyword.KeywordParameterExtractor;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Lightweight keyword handler for invoking art IDs without UI overhead.
 *
 * <p><b>Keyword:</b> "invoke &lt;art-id&gt;" (case-insensitive, parameterized)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Sends minimal uni_invoke_local stream for specified art ID</li>
 *   <li>No window creation or UI elements (unlike image viewer)</li>
 *   <li>Validates art ID format to prevent injection</li>
 *   <li>Supports IDs with or without angle brackets</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 * <pre>
 * invoke &lt;1-0-21000&gt;    → Invokes art ID 1-0-21000
 * invoke 32-5446         → Works without angle brackets
 * INVOKE &lt;1-0-27256&gt;     → Case-insensitive
 * </pre>
 *
 * <p><b>FDO Template:</b> {@code fdo/invoke_response.fdo.txt}
 *
 * <p><b>Variables:</b>
 * <ul>
 *   <li>{@code {{ART_ID}}}: The art ID to invoke (e.g., "1-0-21000")</li>
 * </ul>
 *
 * <p><b>Comparison with Image Viewer:</b>
 * <ul>
 *   <li><b>invoke:</b> Minimal overhead, no window, no dimension lookup</li>
 *   <li><b>art:</b> Full UI with modal window, close button, dimension-aware sizing</li>
 * </ul>
 */
public class InvokeKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "invoke";
    private static final String DESCRIPTION = "Invoke art ID with minimal overhead (usage: invoke <art-id>)";
    private static final int MAX_BURST_FRAMES = 1;

    /**
     * Art ID validation pattern.
     * Format: X-Y or X-Y-Z where X, Y, Z are numbers
     * Examples: "32-5446", "1-0-21000"
     */
    private static final String ART_ID_PATTERN = "^[0-9]+-[0-9]+(-[0-9]+)?$";

    private final FdoCompiler fdoCompiler;

    /**
     * Creates a new InvokeKeywordHandler.
     *
     * @param fdoCompiler the FDO compiler for template compilation
     * @throws IllegalArgumentException if fdoCompiler is null
     */
    public InvokeKeywordHandler(FdoCompiler fdoCompiler) {
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
    public void handle(String keyword, SessionContext session,
                      ChannelHandlerContext ctx, Pacer pacer) throws Exception {

        // Extract art ID parameter from keyword
        String artId = KeywordParameterExtractor.extractParameter(keyword);

        if (artId == null || artId.trim().isEmpty()) {
            LoggerUtil.warn(String.format(
                "[INVOKE] invoke command requires art ID parameter. " +
                "Usage: invoke <art-id> (user: %s)",
                session.getDisplayName()));
            return;
        }

        artId = artId.trim();

        // Validate art ID format to prevent injection attacks
        if (!artId.matches(ART_ID_PATTERN)) {
            LoggerUtil.warn(String.format(
                "[INVOKE] Invalid art ID format: '%s'. " +
                "Expected format: X-Y or X-Y-Z (numbers only). User: %s",
                artId, session.getDisplayName()));
            return;
        }

        LoggerUtil.info(String.format(
            "[INVOKE] Invoking art ID: %s (user: %s)",
            artId, session.getDisplayName()));

        // Parse art ID to FdoGid and build FDO using DSL builder
        FdoGid gid = parseArtId(artId);
        InvokeResponseFdoBuilder builder = new InvokeResponseFdoBuilder(gid);
        String fdoSource = builder.toSource(RenderingContext.DEFAULT);

        // Compile FDO to P3 chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        LoggerUtil.info(String.format(
            "[INVOKE] Compiled %d chunks for art ID: %s",
            chunks != null ? chunks.size() : 0, artId));

        // Send compiled chunks to client
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "INVOKE", MAX_BURST_FRAMES,
                                session.getDisplayName());
    }

    /**
     * Parse art ID string to FdoGid.
     * Supports formats: "X-Y" or "X-Y-Z" where X, Y, Z are integers.
     */
    private static FdoGid parseArtId(String artId) {
        String[] parts = artId.split("-");
        if (parts.length == 2) {
            return FdoGid.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } else if (parts.length == 3) {
            return FdoGid.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        }
        throw new IllegalArgumentException("Invalid art ID format: " + artId);
    }
}
