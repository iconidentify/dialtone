/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.art.ArtService;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.ImageViewerConfig;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ImageViewerFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.keyword.KeywordParameterExtractor;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.io.IOException;
import java.util.List;

/**
 * Keyword handler for displaying images by art ID.
 *
 * <p><b>Keyword:</b> "art &lt;art-id&gt;" (case-insensitive, parameterized)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Displays images using art ID syntax: art &lt;32-5446&gt;</li>
 *   <li>Validates art ID format before display</li>
 *   <li>Uses {@link ImageViewerFdoBuilder} DSL builder for FDO generation</li>
 *   <li>Shows image in non-modal window</li>
 *   <li>Supports IDs with or without angle brackets</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 * <pre>
 * art &lt;32-5446&gt;          → Displays art ID 32-5446
 * art 1-0-21029          → Works without angle brackets
 * ART &lt;1-0-27256&gt;        → Case-insensitive, works the same
 * </pre>
 *
 * <p><b>FDO Builder:</b> {@link ImageViewerFdoBuilder}
 *
 * <p><b>Known Art IDs for Testing:</b>
 * <ul>
 *   <li>1-0-21029 - Welcome screen background</li>
 *   <li>1-0-21030 - UI button art</li>
 *   <li>1-0-21016 - Icon art</li>
 *   <li>1-0-27256 - Tiled background</li>
 * </ul>
 */
public class ImageViewerKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "art";
    private static final String DESCRIPTION = "Display an image by art ID (usage: art <art-id>)";
    private static final int MAX_BURST_FRAMES = 1;

    /**
     * Art ID validation pattern.
     * Format: X-Y or X-Y-Z where X, Y, Z are numbers
     * Examples: "32-5446", "1-0-21029"
     */
    private static final String ART_ID_PATTERN = "^[0-9]+-[0-9]+(-[0-9]+)?$";

    // Window sizing constants
    private static final int MARGIN = 20;          // Symmetric 20px margin on all sides (image at x=20, y=20)
    private static final int BORDER_COMPENSATION = 10;  // Extra space for mat_frame_style <5> border on right/bottom
    private static final int MIN_WINDOW_WIDTH = 200;  // Minimum width for title bar art ID legibility

    // Default fallback dimensions if art dimensions can't be determined (client-side database art)
    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;

    private final FdoCompiler fdoCompiler;
    private final ArtService artService;

    /**
     * Creates a new ImageViewerKeywordHandler.
     *
     * @param fdoCompiler the FDO compiler for template compilation
     * @param artService the art service for dimension lookup
     * @throws IllegalArgumentException if fdoCompiler or artService is null
     */
    public ImageViewerKeywordHandler(FdoCompiler fdoCompiler, ArtService artService) {
        if (fdoCompiler == null) {
            throw new IllegalArgumentException("FdoCompiler cannot be null");
        }
        if (artService == null) {
            throw new IllegalArgumentException("ArtService cannot be null");
        }
        this.fdoCompiler = fdoCompiler;
        this.artService = artService;
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
                "[IMAGE_VIEWER] art command requires art ID parameter. " +
                "Usage: art <art-id> (user: %s)",
                session.getDisplayName()));
            // Note: Could send error FDO here in future
            return;
        }

        artId = artId.trim();

        // Validate art ID format to prevent injection attacks
        if (!artId.matches(ART_ID_PATTERN)) {
            LoggerUtil.warn(String.format(
                "[IMAGE_VIEWER] Invalid art ID format: '%s'. " +
                "Expected format: X-Y or X-Y-Z (numbers only). User: %s",
                artId, session.getDisplayName()));
            return;
        }

        LoggerUtil.info(String.format(
            "[IMAGE_VIEWER] Displaying image viewer for art ID: %s (user: %s)",
            artId, session.getDisplayName()));

        // Look up art dimensions to calculate window size
        int imageWidth;
        int imageHeight;
        try {
            int[] dimensions = artService.getArtDimensions(artId);
            imageWidth = dimensions[0];
            imageHeight = dimensions[1];
            LoggerUtil.info(String.format(
                "[IMAGE_VIEWER] Art dimensions for %s: %dx%d",
                artId, imageWidth, imageHeight));
        } catch (IOException e) {
            // Fallback to default dimensions if lookup fails
            LoggerUtil.warn(String.format(
                "[IMAGE_VIEWER] Failed to get dimensions for art ID: %s - using defaults (%dx%d). Error: %s",
                artId, DEFAULT_WIDTH, DEFAULT_HEIGHT, e.getMessage()));
            imageWidth = DEFAULT_WIDTH;
            imageHeight = DEFAULT_HEIGHT;
        }

        // Calculate base window dimensions with margins + 5px border compensation
        // Width: imageWidth + 40px margins + 5px compensation (left: 20px, right: 25px effective)
        // Height: imageHeight + 40px margins + 5px compensation (top: 20px, bottom: 25px effective)
        int windowWidth = imageWidth + (MARGIN * 2) + BORDER_COMPENSATION;
        int windowHeight = imageHeight + (MARGIN * 2) + BORDER_COMPENSATION;

        // Default image position: symmetric margins
        int imageX = MARGIN;
        int imageY = MARGIN;

        // If window is too narrow for title legibility, scale up proportionally
        // This maintains the aspect ratio of the content (image + margins)
        if (windowWidth < MIN_WINDOW_WIDTH) {
            double scale = (double) MIN_WINDOW_WIDTH / windowWidth;
            windowWidth = MIN_WINDOW_WIDTH;
            windowHeight = (int) Math.round(windowHeight * scale);

            // Center the image in the scaled window
            imageX = (windowWidth - imageWidth) / 2;
            imageY = (windowHeight - imageHeight) / 2;

            LoggerUtil.info(String.format(
                "[IMAGE_VIEWER] Window scaled up to meet minimum width: %s (scale: %.2f, centered at %d,%d)",
                artId, scale, imageX, imageY));
        }

        LoggerUtil.info(String.format(
            "[IMAGE_VIEWER] Window size for %s: %dx%d (image: %dx%d at %d,%d)",
            artId, windowWidth, windowHeight, imageWidth, imageHeight, imageX, imageY));

        // Build FDO using DSL builder
        ImageViewerConfig config = new ImageViewerConfig(
            artId, artId, windowWidth, windowHeight, imageX, imageY);
        ImageViewerFdoBuilder builder = new ImageViewerFdoBuilder(config);
        String fdoSource = builder.toSource(RenderingContext.DEFAULT);

        // Compile FDO source to P3 chunks
        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
            fdoSource,
            "AT",
            FdoCompiler.AUTO_GENERATE_STREAM_ID
        );

        LoggerUtil.info(String.format(
            "[IMAGE_VIEWER] Compiled %d chunks for art ID: %s",
            chunks != null ? chunks.size() : 0, artId));

        // Send compiled chunks to client
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "IMAGE_VIEWER", MAX_BURST_FRAMES,
                                session.getDisplayName());
    }
}
