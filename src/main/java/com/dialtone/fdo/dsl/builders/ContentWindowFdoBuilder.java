/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.ObjectBuilder;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.*;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.ContentWindowConfig;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * Reusable FDO builder for content windows triggered by keywords.
 *
 * <p>Generates a standard content window layout with:</p>
 * <ul>
 *   <li>Pixel-based precise sizing (like welcome screen)</li>
 *   <li>Tiled background art</li>
 *   <li>Optional logo display</li>
 *   <li>Scrollable content view</li>
 * </ul>
 *
 * <p>Supports color and black-and-white modes via {@link RenderingContext}.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * IND_GROUP (standard window, centered, background art, precise sizing)
 * +-- ORNAMENT (title with date)
 * +-- VIEW (scrollable content)
 * +-- man_update_display
 * +-- uni_wait_off
 * </pre>
 *
 * <p>This builder is designed to be reused by multiple keyword handlers
 * (e.g., Pieter, OSXDaily) with different configurations and content.</p>
 */
public final class ContentWindowFdoBuilder implements FdoDslBuilder {

    // Window layout constants (press release style)
    private static final int TITLE_X = 10;
    private static final int TITLE_Y = 10;
    private static final int TITLE_WIDTH = 498;
    private static final int TITLE_HEIGHT = 30;

    private static final int VIEW_X = 10;
    private static final int VIEW_Y = 50;
    private static final int VIEW_WIDTH = 498;
    private static final int VIEW_HEIGHT_COLOR = 240;
    private static final int VIEW_HEIGHT_BW = 200;  // Smaller in BW mode
    private static final int VIEW_CAPACITY = 8192;

    // View dimensions for color/BW modes (character-based layout fallback)
    private static final int CHAR_VIEW_WIDTH_COLOR = 60;
    private static final int CHAR_VIEW_HEIGHT_COLOR = 10;
    private static final int CHAR_VIEW_WIDTH_BW = 60;
    private static final int CHAR_VIEW_HEIGHT_BW = 14;
    private static final int VIEW_MAX_SIZE = 8192;

    // Logo dimensions (for positioning)
    private static final int LOGO_WIDTH = 200;
    private static final int LOGO_HEIGHT = 60;

    // Font configuration
    private static final int FONT_SIZE = 10;

    private final ContentWindowConfig config;
    private final String content;

    /**
     * Create builder with configuration and content.
     *
     * @param config Window configuration (required)
     * @param content Text content to display (null uses empty string)
     */
    public ContentWindowFdoBuilder(ContentWindowConfig config, String content) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.config = config;
        this.content = content != null ? content : "";
    }

    /**
     * Create builder with configuration only (empty content).
     *
     * @param config Window configuration
     */
    public ContentWindowFdoBuilder(ContentWindowConfig config) {
        this(config, "");
    }

    @Override
    public String getGid() {
        return config.keyword();
    }

    @Override
    public String getDescription() {
        return config.windowTitle() + " content window";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, "", root -> {
                        // Window properties
                        root.matOrientation(Orientation.VCF);
                        root.matPosition(Position.CENTER_CENTER);
                        root.mat(MatAtom.BOOL_PRECISE, "yes");
                        root.matPreciseWidth(config.windowWidth());
                        root.matPreciseHeight(config.windowHeight());
                        root.backgroundTile();

                        // Background art (if configured)
                        if (config.backgroundArtId() != null) {
                            FdoGid bgGid = parseArtGid(config.backgroundArtId());
                            if (bgGid != null) {
                                root.artId(bgGid);
                            }
                        }

                        root.matTitle(config.windowTitle());

//                        // Title ornament with window title
//                        root.object(ObjectType.ORNAMENT, "", title -> {
////                            title.matPreciseX(TITLE_X);
////                            title.matPreciseY(TITLE_Y);
////                            title.matPreciseWidth(TITLE_WIDTH);
////                            title.matPreciseHeight(TITLE_HEIGHT);
//                            //title.matFontId(FontId.TIMES_ROMAN);
//                            //title.matFontSize(14);
//                            title.matFontSis(FontId.TIMES_ROMAN, 16, FontStyle.BOLD);
//                            //title.matTitlePos(TitlePosition.RIGHT_CENTER);
//                            title.manAppendData(config.windowTitle());
//                            title.manEndData();
//                        });

                        // Logo ornament (if configured)
                        if (config.logoArtId() != null) {
                            buildLogoOrnament(root, ctx);
                        }

                        // Content view (height varies by color mode)
                        int viewHeight = ctx.isLowColorMode() ? VIEW_HEIGHT_BW : VIEW_HEIGHT_COLOR;
                        root.object(ObjectType.VIEW, "", view -> {
                            view.matPreciseX(VIEW_X);
                            view.matPreciseY(VIEW_Y);
                            view.matPreciseWidth(VIEW_WIDTH);
                            view.matPreciseHeight(viewHeight);
                            view.matCapacity(VIEW_CAPACITY);
                            view.mat(MatAtom.BOOL_WRITEABLE, "no");
                            view.matBoolVerticalScroll(true);
                            view.matFontId(FontId.COURIER);
                            view.matFontSize(12);

                            // Convert and append content
                            String formattedContent = convertNewlinesToAolFormat(content);
                            if (!formattedContent.isEmpty()) {
                                view.manAppendData(formattedContent);
                                view.manEndData();
                            }
                        });

                        root.manUpdateDisplay();
                    });
                    s.uniWaitOff();
                })
                .toSource();
    }

    /**
     * Build the logo ornament.
     */
    private void buildLogoOrnament(ObjectBuilder layout, RenderingContext ctx) {
        layout.object(ObjectType.ORNAMENT, "", logo -> {
            FdoGid logoGid = parseArtGid(config.logoArtId());
            if (logoGid != null) {
                logo.artId(logoGid);
            }
            logo.matSize(LOGO_WIDTH, LOGO_HEIGHT);
        });
    }

    /**
     * Build the scrollable content view (character-based sizing).
     */
    private void buildContentView(ObjectBuilder layout, RenderingContext ctx) {
        layout.object(ObjectType.VIEW, "", view -> {
            view.matParagraph(1);
            int width = ctx.isLowColorMode() ? CHAR_VIEW_WIDTH_BW : CHAR_VIEW_WIDTH_COLOR;
            int height = ctx.isLowColorMode() ? CHAR_VIEW_HEIGHT_BW : CHAR_VIEW_HEIGHT_COLOR;
            view.matSize(width, height, VIEW_MAX_SIZE);
            view.matFontSis(FontId.COURIER, FONT_SIZE, FontStyle.NORMAL);
            view.matBoolForceScroll();

            // Convert and append content
            String formattedContent = convertNewlinesToAolFormat(content);
            if (!formattedContent.isEmpty()) {
                view.manAppendData(formattedContent);
            }
        });
    }

    /**
     * Parse art GID string to FdoGid.
     *
     * @param artIdStr Art ID string (e.g., "1-69-27256" or "32-5447")
     * @return FdoGid or null if parsing fails
     */
    private static FdoGid parseArtGid(String artIdStr) {
        if (artIdStr == null || artIdStr.isBlank()) {
            return null;
        }
        String[] parts = artIdStr.split("-");
        try {
            if (parts.length == 2) {
                return FdoGid.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } else if (parts.length == 3) {
                return FdoGid.of(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            }
        } catch (NumberFormatException ignored) {
            // Fall through to null
        }
        return null;
    }

    /**
     * Convert standard newlines to AOL protocol format.
     * AOL protocol uses 0x7F (DEL) character for line breaks.
     *
     * @param text Input string with standard newlines
     * @return String with AOL protocol newlines (0x7F characters)
     */
    private static String convertNewlinesToAolFormat(String text) {
        if (text == null) {
            return "";
        }
        // Handle Windows-style line endings first (\r\n)
        String converted = text.replace("\r\n\r\n", "\u007F\u007F")
                               .replace("\r\n", "\u007F");
        // Handle remaining Unix-style line endings (\n)
        converted = converted.replace("\n\n", "\u007F\u007F")
                             .replace("\n", "\u007F");
        return converted;
    }

    /**
     * Get the configuration.
     *
     * @return Configuration used by this builder
     */
    public ContentWindowConfig getConfig() {
        return config;
    }

    /**
     * Get the content.
     *
     * @return Content string
     */
    public String getContent() {
        return content;
    }
}
