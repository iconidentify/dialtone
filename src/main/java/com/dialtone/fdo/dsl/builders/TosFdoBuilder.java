/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.ObjectBuilder;
import com.atomforge.fdo.dsl.atoms.CclAtom;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.*;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * DSL-based FDO builder for Terms of Service modal dialog.
 *
 * <p>Displays TOS content with AGREE/DISAGREE buttons. Supports both
 * color and black-and-white modes via {@link RenderingContext}.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * IND_GROUP "TERMS OF SERVICE" (modal, centered, background art 1-69-27256)
 * +-- VIEW (TOS content - 68x20 color, 59x16 BW)
 * +-- ORG_GROUP (HCF - button row)
 *     +-- TRIGGER "AGREE" (default, sends "TA" token)
 *     +-- TRIGGER "DISAGREE" (triggers ccl_hang_up)
 * +-- man_update_display
 * +-- uni_wait_off
 * </pre>
 *
 * <p>Color vs BW differences:</p>
 * <ul>
 *   <li>View size: 68x20 (color) vs 59x16 (BW)</li>
 *   <li>Button colors: only in color mode</li>
 *   <li>Trigger style: only in color mode</li>
 *   <li>AGREE action extras: man_set_context_globalid/man_make_focus only in color mode</li>
 * </ul>
 */
public final class TosFdoBuilder implements FdoDslBuilder {

    private static final String GID = "tos";
    private static final String DEFAULT_TOS = "Terms of Service content unavailable.";

    // View dimensions
    private static final int VIEW_WIDTH_COLOR = 68;
    private static final int VIEW_HEIGHT_COLOR = 20;
    private static final int VIEW_WIDTH_BW = 59;
    private static final int VIEW_HEIGHT_BW = 16;

    // Font configuration
    private static final int FONT_SIZE = 10;

    // Background art GID
    private static final FdoGid BACKGROUND_ART = FdoGid.of(1, 69, 27256);

    // Post-agree context GID (used in color mode only)
    private static final FdoGid POST_AGREE_CONTEXT = FdoGid.of(32, 30);

    private final String tosContent;
    private final ButtonTheme buttonTheme;

    /**
     * Create builder with TOS content and button theme.
     *
     * @param tosContent The TOS text to display (null uses default message)
     * @param buttonTheme Button color theme (null uses default theme)
     */
    public TosFdoBuilder(String tosContent, ButtonTheme buttonTheme) {
        this.tosContent = tosContent != null ? tosContent : DEFAULT_TOS;
        this.buttonTheme = buttonTheme != null ? buttonTheme : ButtonTheme.DEFAULT;
    }

    /**
     * Create builder with TOS content and default button theme.
     *
     * @param tosContent The TOS text to display
     */
    public TosFdoBuilder(String tosContent) {
        this(tosContent, ButtonTheme.DEFAULT);
    }

    /**
     * Default constructor - loads TOS from file, uses default theme.
     */
    public TosFdoBuilder() {
        this(loadDefaultTosContent(), ButtonTheme.DEFAULT);
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Terms of Service modal with AGREE/DISAGREE buttons";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, "TERMS OF SERVICE", root -> {
                        root.matOrientation(Orientation.VFF);
                        root.matPosition(Position.CENTER_CENTER);
                        root.modal();
                        root.backgroundTile();
                        root.artId(BACKGROUND_ART);

                        buildTosView(root, ctx);
                        buildButtonGroup(root, ctx);

                        root.manUpdateDisplay();
                    });
                    s.uniWaitOff();
                })
                .toSource();
    }

    /**
     * Build the TOS content view with mode-specific dimensions.
     */
    private void buildTosView(ObjectBuilder root, RenderingContext ctx) {
        root.object(ObjectType.VIEW, "", view -> {
            view.matParagraph(1);
            int width = ctx.isLowColorMode() ? VIEW_WIDTH_BW : VIEW_WIDTH_COLOR;
            int height = ctx.isLowColorMode() ? VIEW_HEIGHT_BW : VIEW_HEIGHT_COLOR;
            view.matSize(width, height);
            view.matFontSis(FontId.COURIER, FONT_SIZE, FontStyle.NORMAL);
            // Convert newlines to AOL protocol format (0x7F = DEL character)
            String aolFormattedContent = convertNewlinesToAolFormat(tosContent);
            view.manAppendData(aolFormattedContent);
        });
    }

    /**
     * Convert standard newlines to AOL protocol format.
     * AOL protocol uses 0x7F (DEL) character for line breaks.
     *
     * @param content Input string with standard newlines
     * @return String with AOL protocol newlines (0x7F characters)
     */
    private static String convertNewlinesToAolFormat(String content) {
        if (content == null) {
            return content;
        }
        // Handle Windows-style line endings first (\r\n)
        String converted = content.replace("\r\n\r\n", "\u007F\u007F")
                                  .replace("\r\n", "\u007F");
        // Handle remaining Unix-style line endings (\n)
        converted = converted.replace("\n\n", "\u007F\u007F")
                             .replace("\n", "\u007F");
        return converted;
    }

    /**
     * Build the button group with AGREE and DISAGREE buttons.
     */
    private void buildButtonGroup(ObjectBuilder root, RenderingContext ctx) {
        root.object(ObjectType.ORG_GROUP, "", buttonRow -> {
            buttonRow.matOrientation(Orientation.HCF);
            buildAgreeButton(buttonRow, ctx);
            buildDisagreeButton(buttonRow, ctx);
        });
    }

    /**
     * Build AGREE button that closes modal and sends "TA" token.
     *
     * <p>Action sequence (order matters!):</p>
     * <ol>
     *   <li>man_close_update - atomically close modal and update display</li>
     *   <li>sm_send_token_raw "TA" - notify server of acceptance</li>
     *   <li>(color mode only) man_set_context_globalid, man_make_focus</li>
     * </ol>
     */
    private void buildAgreeButton(ObjectBuilder buttonRow, RenderingContext ctx) {
        buttonRow.object(ObjectType.TRIGGER, "AGREE", btn -> {
            btn.mat(MatAtom.BOOL_DEFAULT, "yes");

            // Color mode: apply button colors and trigger style
            if (!ctx.isLowColorMode()) {
                applyButtonColors(btn);
                btn.matTriggerStyle(TriggerStyle.PLACE);
            }

            // Font styling
            btn.matFontId(FontId.ARIAL);
            btn.matFontSize(FONT_SIZE);
            btn.matFontStyle(FontStyle.BOLD);

            // Action: close modal FIRST (atomic), then send token
            btn.onSelect(action -> {
                action.stream(inner -> {
                    inner.manCloseUpdate();           // Close + update atomically FIRST
                    inner.smSendTokenRaw("TA");       // Then notify server

                    // Color mode only: set context and focus after close
                    if (!ctx.isLowColorMode()) {
                        inner.manSetContextGlobalId(POST_AGREE_CONTEXT);
                        inner.manMakeFocus();
                    }
                });
            });
        });
    }

    /**
     * Build DISAGREE button that hangs up the connection.
     */
    private void buildDisagreeButton(ObjectBuilder buttonRow, RenderingContext ctx) {
        buttonRow.object(ObjectType.TRIGGER, "", btn -> {
            btn.matTitle("DISAGREE");

            // Color mode: apply button colors and trigger style
            if (!ctx.isLowColorMode()) {
                applyButtonColors(btn);
                btn.matTriggerStyle(TriggerStyle.PLACE);
            }

            // Font styling
            btn.matFontId(FontId.ARIAL);
            btn.matFontSize(FONT_SIZE);
            btn.matFontStyle(FontStyle.BOLD);

            // Set criterion and action: hang up, close, update display
            btn.actSetCriterion(Criterion.SELECT);
            btn.actReplaceAction(action -> {
                action.uniStartStream();
                action.atom(CclAtom.HANG_UP);
                action.manClose();
                action.manUpdateDisplay();
                action.uniEndStream();
            });
        });
    }

    /**
     * Apply button theme colors to a button.
     */
    private void applyButtonColors(ObjectBuilder btn) {
        int[] face = buttonTheme.colorFace();
        int[] text = buttonTheme.colorText();
        int[] top = buttonTheme.colorTopEdge();
        int[] bottom = buttonTheme.colorBottomEdge();

        btn.mat(MatAtom.COLOR_FACE, face[0], face[1], face[2]);
        btn.mat(MatAtom.COLOR_TEXT, text[0], text[1], text[2]);
        btn.mat(MatAtom.COLOR_TOP_EDGE, top[0], top[1], top[2]);
        btn.mat(MatAtom.COLOR_BOTTOM_EDGE, bottom[0], bottom[1], bottom[2]);
    }

    /**
     * Load TOS content from the public/TOS.txt resource.
     *
     * @return TOS content or default message if not found
     */
    private static String loadDefaultTosContent() {
        try (InputStream is = TosFdoBuilder.class.getClassLoader()
                .getResourceAsStream("public/TOS.txt")) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
            // Fall through to default
        }
        return DEFAULT_TOS;
    }

    /**
     * Factory method for creating builder with specific content.
     *
     * @param tosContent The TOS text to display
     * @return New builder instance
     */
    public static TosFdoBuilder forContent(String tosContent) {
        return new TosFdoBuilder(tosContent);
    }

    /**
     * Factory method for creating builder with content and theme.
     *
     * @param tosContent The TOS text to display
     * @param theme Button color theme
     * @return New builder instance
     */
    public static TosFdoBuilder forContent(String tosContent, ButtonTheme theme) {
        return new TosFdoBuilder(tosContent, theme);
    }
}
