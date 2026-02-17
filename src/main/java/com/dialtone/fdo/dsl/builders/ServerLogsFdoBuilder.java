/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.dsl.values.FontId;
import com.atomforge.fdo.dsl.values.FontStyle;
import com.atomforge.fdo.dsl.values.ObjectType;
import com.atomforge.fdo.dsl.values.Orientation;
import com.atomforge.fdo.dsl.values.Position;
import com.atomforge.fdo.dsl.values.TitlePosition;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for server logs display modal.
 *
 * <p>Generates an FDO modal dialog that displays server log entries
 * with a scrollable view and OK button to close.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   IND_GROUP "window_title" (modal, centered, 600x450)
 *   +-- ORNAMENT (title with timestamp)
 *   +-- ORNAMENT (subtitle with log count)
 *   +-- VIEW (scrollable log content, monospace font)
 *   +-- ORG_GROUP (button row)
 *       +-- TRIGGER "OK" (closes modal)
 * man_update_display
 * uni_wait_off_end_stream
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/server_logs.fdo.txt}</p>
 */
public final class ServerLogsFdoBuilder implements FdoDslBuilder {

    private static final String GID = "server_logs";

    // Window dimensions
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 450;

    // Title ornament positioning
    private static final int TITLE_X = 10;
    private static final int TITLE_Y = 10;
    private static final int TITLE_WIDTH = 580;
    private static final int TITLE_HEIGHT = 25;

    // Subtitle positioning
    private static final int SUBTITLE_Y = 35;
    private static final int SUBTITLE_HEIGHT = 20;

    // Log view positioning
    private static final int VIEW_Y = 60;
    private static final int VIEW_HEIGHT = 350;
    private static final int SCROLL_THRESHOLD = 4096;

    // Button row positioning
    private static final int BUTTON_Y = 420;

    /**
     * Configuration for server logs display.
     *
     * @param windowTitle The window title
     * @param timestamp The timestamp to display
     * @param logCount Number of log entries being shown
     * @param logContent The log content text
     */
    public record Config(
            String windowTitle,
            String timestamp,
            int logCount,
            String logContent
    ) {
        public Config {
            if (windowTitle == null) windowTitle = "Server Logs";
            if (timestamp == null) timestamp = "";
            if (logContent == null) logContent = "";
        }
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The server logs configuration
     */
    public ServerLogsFdoBuilder(Config config) {
        this.config = config != null ? config : new Config("Server Logs", "", 0, "");
    }

    /**
     * Create a new builder with individual parameters.
     *
     * @param windowTitle The window title
     * @param timestamp The timestamp
     * @param logCount Number of log entries
     * @param logContent The log content
     */
    public ServerLogsFdoBuilder(String windowTitle, String timestamp, int logCount, String logContent) {
        this(new Config(windowTitle, timestamp, logCount, logContent));
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Server logs display modal with scrollable content";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, config.windowTitle, root -> {
                        // Window properties
                        root.matOrientation(Orientation.VFF);
                        root.matPosition(Position.CENTER_CENTER);
                        root.modal();
                        root.matPreciseWidth(WINDOW_WIDTH);
                        root.matPreciseHeight(WINDOW_HEIGHT);

                        // Title banner with timestamp
                        root.object(ObjectType.ORNAMENT, "", title -> {
                            title.matPreciseX(TITLE_X);
                            title.matPreciseY(TITLE_Y);
                            title.matPreciseWidth(TITLE_WIDTH);
                            title.matPreciseHeight(TITLE_HEIGHT);
                            title.matTitlePos(TitlePosition.LEFT_CENTER);
                            title.matFontSis(FontId.ARIAL, 14, FontStyle.BOLD);
                            title.manAppendData(config.windowTitle + " - " + config.timestamp);
                            title.manEndData();
                        });

                        // Subtitle with log count
                        root.object(ObjectType.ORNAMENT, "", subtitle -> {
                            subtitle.matPreciseX(TITLE_X);
                            subtitle.matPreciseY(SUBTITLE_Y);
                            subtitle.matPreciseWidth(TITLE_WIDTH);
                            subtitle.matPreciseHeight(SUBTITLE_HEIGHT);
                            subtitle.matTitlePos(TitlePosition.LEFT_CENTER);
                            subtitle.matFontSis(FontId.ARIAL, 10, FontStyle.NORMAL);
                            subtitle.manAppendData("Showing last " + config.logCount + " log entries");
                            subtitle.manEndData();
                        });

                        // Scrollable log view (monospace for alignment)
                        root.object(ObjectType.VIEW, "", view -> {
                            view.matPreciseX(TITLE_X);
                            view.matPreciseY(VIEW_Y);
                            view.matPreciseWidth(TITLE_WIDTH);
                            view.matPreciseHeight(VIEW_HEIGHT);
                            view.matFontSis(FontId.COURIER, 9, FontStyle.NORMAL);
                            view.mat(MatAtom.BOOL_WRITEABLE, "yes");
                            view.mat(MatAtom.SCROLL_THRESHOLD, SCROLL_THRESHOLD);
                            view.matParagraph(1);
                            view.matBoolForceScroll();
                            view.manAppendData(config.logContent);
                            view.manEndData();
                        });

                        // Close button row
                        root.object(ObjectType.ORG_GROUP, "", buttonRow -> {
                            buttonRow.matOrientation(Orientation.HCF);
                            buttonRow.matPreciseY(BUTTON_Y);

                            buttonRow.object(ObjectType.TRIGGER, "OK", btn -> {
                                btn.matBoolDefault();
                                btn.actSetCriterion(Criterion.SELECT);
                                btn.actReplaceAction(action -> {
                                    action.uniStartStream();
                                    action.manCloseUpdate();
                                    action.uniEndStream();
                                });
                            });
                        });
                    });
                    s.manUpdateDisplay();
                    s.uniWaitOffEndStream();
                })
                .toSource();
    }
}
