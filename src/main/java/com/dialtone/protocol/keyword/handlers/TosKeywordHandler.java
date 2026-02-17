/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.db.models.ScreennamePreferences;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoVariableBuilder;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.TosFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.services.ScreennamePreferencesService;
import io.netty.channel.ChannelHandlerContext;

import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Keyword handler for displaying Terms of Service.
 *
 * <p><b>Keyword:</b> "tos" (case-insensitive)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Displays Dialtone Terms of Service in a modal window</li>
 *   <li>Shows formatted TOS text in scrollable view</li>
 *   <li>Provides AGREE/DISAGREE buttons</li>
 *   <li>Supports color and BW modes via DSL builder</li>
 * </ul>
 *
 * <p><b>DSL Builder:</b> {@link TosFdoBuilder} - generates FDO programmatically
 */
public class TosKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "tos";
    private static final String DESCRIPTION = "Display Dialtone Terms of Service";
    private static final int MAX_BURST_FRAMES = 1;

    private final FdoCompiler fdoCompiler;
    private final Properties properties;
    private final ScreennamePreferencesService preferencesService;

    /**
     * Creates a new TosKeywordHandler.
     *
     * @param fdoCompiler the FDO compiler for template compilation
     * @param properties  application properties for button theming
     * @param preferencesService service for loading screenname preferences (for BW variant resolution)
     */
    public TosKeywordHandler(FdoCompiler fdoCompiler, Properties properties,
                             ScreennamePreferencesService preferencesService) {
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        this.fdoCompiler = fdoCompiler;
        this.properties = properties;
        this.preferencesService = preferencesService;
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
        LoggerUtil.info("Displaying Terms of Service for user: " + session.getDisplayName());

        // Determine rendering context (platform + color mode)
        boolean lowColorMode = isLowColorModeEnabled(session);
        RenderingContext renderCtx = new RenderingContext(session.getPlatform(), lowColorMode);

        // Load TOS content from file via FdoVariableBuilder
        Map<String, String> variables = new FdoVariableBuilder()
            .withTos()
            .build();
        String tosContent = variables.getOrDefault("TOS_DATA", "Terms of Service content unavailable.");

        // Build button theme from properties
        ButtonTheme buttonTheme = ButtonTheme.fromProperties(properties);

        // Generate FDO source using DSL builder
        TosFdoBuilder builder = new TosFdoBuilder(tosContent, buttonTheme);
        String fdoSource = builder.toSource(renderCtx);

        // Compile FDO source to P3 chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "at",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        LoggerUtil.info(String.format(
            "Compiled TOS FDO via DSL: %d chunks, %d characters%s",
            chunks != null ? chunks.size() : 0,
            tosContent.length(),
            lowColorMode ? " (BW mode)" : ""
        ));

        // Send P3 chunks via Pacer if chunks are available
        if (chunks != null && !chunks.isEmpty()) {
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "TOS", MAX_BURST_FRAMES,
                                    session.getDisplayName());
            LoggerUtil.info("Terms of Service sent to user: " + session.getDisplayName());
        } else {
            LoggerUtil.warn("No TOS chunks generated - cannot send to user: " + session.getDisplayName());
        }
    }

    /**
     * Check if low color mode is enabled for the given session's user.
     *
     * @param session the user session
     * @return true if low color mode is enabled, false otherwise or on error
     */
    private boolean isLowColorModeEnabled(SessionContext session) {
        if (preferencesService == null || session == null || !session.isAuthenticated()) {
            return false;
        }
        try {
            ScreennamePreferences prefs = preferencesService.getPreferencesByScreenname(session.getUsername());
            return prefs.isLowColorModeEnabled();
        } catch (Exception e) {
            LoggerUtil.warn("Failed to load preferences for BW variant resolution: " + e.getMessage());
            return false;
        }
    }
}