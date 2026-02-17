/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.FontId;
import com.atomforge.fdo.dsl.values.FontStyle;
import com.atomforge.fdo.dsl.values.ObjectType;
import com.atomforge.fdo.dsl.values.Orientation;
import com.atomforge.fdo.dsl.values.Position;
import com.atomforge.fdo.dsl.values.TitlePosition;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for news story display window.
 *
 * <p>Generates an FDO window that displays a news story with a title,
 * date, and scrollable content area.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   IND_GROUP "News" (centered, precise size 580x360, background art)
 *   +-- ORNAMENT (title banner with date)
 *   +-- VIEW (scrollable content area 540x300)
 *   +-- man_update_display
 * uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/news_story.fdo.txt}</p>
 */
public final class NewsStoryFdoBuilder
        implements FdoDslBuilder, FdoBuilder.Dynamic<NewsStoryFdoBuilder.Config> {

    private static final String GID = "news_story";
    private static final FdoGid BACKGROUND_ART = FdoGid.of(1, 69, 27256);

    // Window dimensions
    private static final int WINDOW_WIDTH = 580;
    private static final int WINDOW_HEIGHT = 360;

    // Title ornament positioning
    private static final int TITLE_X = 20;
    private static final int TITLE_Y = 12;
    private static final int TITLE_WIDTH = 540;
    private static final int TITLE_HEIGHT = 20;

    // Content view positioning
    private static final int VIEW_X = 20;
    private static final int VIEW_Y = 40;
    private static final int VIEW_WIDTH = 540;
    private static final int VIEW_HEIGHT = 300;
    private static final int VIEW_CAPACITY = 8192;

    /**
     * Configuration for news story display.
     *
     * @param windowTitle The window title (e.g., "Tech News")
     * @param todaysDate The date string to display
     * @param contentHtml The HTML content for the story
     */
    public record Config(
            String windowTitle,
            String todaysDate,
            String contentHtml
    ) {
        public Config {
            if (windowTitle == null) windowTitle = "News";
            if (todaysDate == null) todaysDate = "";
            if (contentHtml == null) contentHtml = "";
        }
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The news story configuration
     */
    public NewsStoryFdoBuilder(Config config) {
        this.config = config != null ? config : new Config("News", "", "");
    }

    /**
     * Create a new builder with individual parameters.
     *
     * @param windowTitle The window title
     * @param todaysDate The date string
     * @param contentHtml The content HTML
     */
    public NewsStoryFdoBuilder(String windowTitle, String todaysDate, String contentHtml) {
        this(new Config(windowTitle, todaysDate, contentHtml));
    }

    /**
     * Factory method for type-safe builder creation.
     *
     * @param windowTitle The window title
     * @param todaysDate The date string
     * @param contentHtml The content HTML
     * @return New builder instance
     */
    public static NewsStoryFdoBuilder create(String windowTitle, String todaysDate, String contentHtml) {
        return new NewsStoryFdoBuilder(windowTitle, todaysDate, contentHtml);
    }

    @Override
    public Config getConfig() {
        return config;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "News story display window with scrollable content";
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
                        root.matPreciseWidth(WINDOW_WIDTH);
                        root.matPreciseHeight(WINDOW_HEIGHT);
                        root.backgroundTile();
                        root.artId(BACKGROUND_ART);
                        root.matTitle(config.windowTitle);

                        // Title ornament
                        root.object(ObjectType.ORNAMENT, "", title -> {
                            title.matPreciseX(TITLE_X);
                            title.matPreciseY(TITLE_Y);
                            title.matPreciseWidth(TITLE_WIDTH);
                            title.matPreciseHeight(TITLE_HEIGHT);
                            title.matFontId(FontId.TIMES_ROMAN);
                            title.matFontSize(14);
                            title.matFontSis(FontId.TIMES_ROMAN, 16, FontStyle.BOLD);
                            title.matTitlePos(TitlePosition.ABOVE_CENTER);
                            title.manAppendData(config.windowTitle + " - " + config.todaysDate);
                            title.manEndData();
                        });

                        // Content view
                        root.object(ObjectType.VIEW, "", view -> {
                            view.matPreciseX(VIEW_X);
                            view.matPreciseY(VIEW_Y);
                            view.matPreciseWidth(VIEW_WIDTH);
                            view.matPreciseHeight(VIEW_HEIGHT);
                            view.matCapacity(VIEW_CAPACITY);
                            view.mat(MatAtom.BOOL_WRITEABLE, "no");
                            view.matBoolVerticalScroll(true);
                            view.matFontId(FontId.TIMES_ROMAN);
                            view.matFontSize(12);
                            view.manAppendData(convertNewlinesToAolFormat(config.contentHtml));
                            view.manEndData();
                        });

                        root.manUpdateDisplay();
                    });
                    s.uniWaitOff();
                })
                .toSource();
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
        // Handle standalone carriage returns (\r)
        converted = converted.replace("\r\r", "\u007F\u007F")
                             .replace("\r", "\u007F");
        return converted;
    }
}
