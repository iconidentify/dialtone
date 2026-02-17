/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers.base;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.ContentWindowConfig;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ContentWindowFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;

/**
 * Abstract base class for keyword handlers that display content windows.
 *
 * <p>Provides a Template Method pattern implementation for keyword handlers
 * that display scrollable content windows with optional logos. Subclasses
 * implement abstract methods to provide configuration and content.</p>
 *
 * <p>The template method {@link #handle(String, SessionContext, ChannelHandlerContext, Pacer)}
 * is final and follows this flow:</p>
 * <ol>
 *   <li>Call {@link #getConfig()} to get window configuration</li>
 *   <li>Call {@link #getContent(SessionContext)} to get content text</li>
 *   <li>Call {@link #getRenderingContext(SessionContext)} for platform/color mode</li>
 *   <li>Build FDO using {@link ContentWindowFdoBuilder}</li>
 *   <li>Compile FDO to P3 chunks</li>
 *   <li>Send chunks via {@link P3ChunkEnqueuer}</li>
 * </ol>
 *
 * <p><b>Example subclass:</b></p>
 * <pre>
 * public class PieterKeywordHandler extends AbstractContentKeywordHandler {
 *     private static final String KEYWORD = "pieter";
 *     private static final String DESCRIPTION = "Pieter retro computing content";
 *
 *     public PieterKeywordHandler(FdoCompiler fdoCompiler) {
 *         super(fdoCompiler);
 *     }
 *
 *     &#64;Override
 *     public String getKeyword() { return KEYWORD; }
 *
 *     &#64;Override
 *     public String getDescription() { return DESCRIPTION; }
 *
 *     &#64;Override
 *     protected ContentWindowConfig getConfig() {
 *         return ContentWindowConfig.withDefaults(KEYWORD, "Pieter Retro Computing", "1-69-40001");
 *     }
 *
 *     &#64;Override
 *     protected String getContent(SessionContext session) {
 *         return "Welcome to Pieter...";
 *     }
 * }
 * </pre>
 */
public abstract class AbstractContentKeywordHandler implements KeywordHandler {

    private static final int MAX_BURST_FRAMES = 1;

    protected final FdoCompiler fdoCompiler;

    /**
     * Create handler with FDO compiler.
     *
     * @param fdoCompiler the FDO compiler for template compilation (required)
     * @throws IllegalArgumentException if fdoCompiler is null
     */
    protected AbstractContentKeywordHandler(FdoCompiler fdoCompiler) {
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        this.fdoCompiler = fdoCompiler;
    }

    /**
     * Get the window configuration for this keyword.
     *
     * <p>Subclasses must implement this to provide the window title,
     * art IDs, dimensions, and button theme.</p>
     *
     * @return window configuration (never null)
     */
    protected abstract ContentWindowConfig getConfig();

    /**
     * Get the content to display in the window.
     *
     * <p>Subclasses implement this to provide static or dynamic content.
     * The session context can be used to personalize content.</p>
     *
     * @param session the client's session context
     * @return content text (can be empty but not null)
     */
    protected abstract String getContent(SessionContext session);

    /**
     * Get the log prefix for this handler.
     *
     * <p>Used in log messages to identify which handler is processing.</p>
     *
     * @return log prefix (e.g., "PIETER", "OSXDAILY")
     */
    protected abstract String getLogPrefix();

    /**
     * Get the rendering context for FDO generation.
     *
     * <p>Override this method to provide platform-specific or color-mode-specific
     * rendering based on session state. Default returns RenderingContext.DEFAULT.</p>
     *
     * @param session the client's session context
     * @return rendering context for FDO generation
     */
    protected RenderingContext getRenderingContext(SessionContext session) {
        return RenderingContext.DEFAULT;
    }

    /**
     * Template method that handles the keyword command.
     *
     * <p>This method is final to ensure consistent behavior across all
     * content keyword handlers. Subclasses customize behavior by
     * implementing the abstract methods.</p>
     *
     * @param keyword the exact keyword text received from the client
     * @param session the client's session context
     * @param ctx     the Netty channel context
     * @param pacer   the frame pacer for queueing response frames
     * @throws Exception if FDO compilation or sending fails
     */
    @Override
    public final void handle(String keyword, SessionContext session,
                             ChannelHandlerContext ctx, Pacer pacer) throws Exception {
        String logPrefix = "[" + getLogPrefix() + "] ";

        LoggerUtil.info(logPrefix + "Handling keyword for user: " + session.getDisplayName());

        // Get configuration and content
        ContentWindowConfig config = getConfig();
        String content = getContent(session);
        RenderingContext renderCtx = getRenderingContext(session);

        LoggerUtil.debug(logPrefix + "Config: keyword=" + config.keyword() +
                        ", title=" + config.windowTitle() +
                        ", dimensions=" + config.windowWidth() + "x" + config.windowHeight());

        // Build FDO using DSL builder
        ContentWindowFdoBuilder builder = new ContentWindowFdoBuilder(config, content);
        String fdoSource = builder.toSource(renderCtx);

        // Compile FDO source to P3 chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "At",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        LoggerUtil.info(logPrefix + "Compiled " +
                       (chunks != null ? chunks.size() : 0) +
                       " chunks for user: " + session.getDisplayName());

        // Send compiled chunks to client
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, getLogPrefix(), MAX_BURST_FRAMES,
                               session.getDisplayName());
    }

    /**
     * Get the FDO compiler.
     *
     * @return the FDO compiler
     */
    protected FdoCompiler getFdoCompiler() {
        return fdoCompiler;
    }
}
