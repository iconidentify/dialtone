/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.StreamBuilder;
import com.atomforge.fdo.dsl.atoms.*;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.dsl.values.FontId;
import com.atomforge.fdo.dsl.values.FontStyle;
import com.atomforge.fdo.dsl.values.ObjectType;
import com.atomforge.fdo.dsl.values.Orientation;
import com.atomforge.fdo.dsl.values.TriggerStyle;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.ButtonTheme;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for instant message receive window.
 *
 * <p>Generates an FDO stream that creates/updates an IM window to display
 * incoming messages. Supports both color and BW modes via RenderingContext.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Conditional window creation (uses man_preset_gid + if_last_return_true_then)</li>
 *   <li>Message history view with scroll</li>
 *   <li>Reply input field</li>
 *   <li>Send button with data extraction action</li>
 *   <li>Color mode: button colors and trigger style</li>
 *   <li>BW mode: no button colors or trigger style</li>
 * </ul>
 *
 * <p>Replaces: {@code fdo/receive_im.fdo.txt} and {@code fdo/receive_im.bw.fdo.txt}</p>
 */
public final class ReceiveImFdoBuilder implements FdoDslBuilder {

    private static final String GID = "receive_im";
    private static final FdoGid STYLE_GID = FdoGid.of(32, 223);
    private static final FdoGid BACKGROUND_ART = FdoGid.of(1, 69, 27256);

    /**
     * Configuration for IM window.
     *
     * @param windowId The window ID (GID for this IM session)
     * @param fromUser The sender's screen name
     * @param message The incoming message (leading/trailing whitespace is trimmed)
     * @param responseId The response ID for reply routing
     * @param buttonTheme Button colors (null for BW mode)
     */
    /** AOL uses DEL (0x7F) as line terminator - not stripped by Java's strip() */
    private static final char AOL_LINE_TERMINATOR = '\u007F';

    public record Config(
            int windowId,
            String fromUser,
            String message,
            int responseId,
            ButtonTheme buttonTheme
    ) {
        public Config {
            // Normalize null to empty string, then trim whitespace + AOL terminators
            fromUser = stripAolWhitespace(fromUser);
            message = stripAolWhitespace(message);
        }
    }

    private static String stripAolWhitespace(String s) {
        if (s == null) return "";
        s = s.strip();
        if (s.isEmpty()) return s;
        int start = 0, end = s.length();
        while (start < end && s.charAt(start) == AOL_LINE_TERMINATOR) start++;
        while (end > start && s.charAt(end - 1) == AOL_LINE_TERMINATOR) end--;
        return (start > 0 || end < s.length()) ? s.substring(start, end).strip() : s;
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The IM window configuration
     */
    public ReceiveImFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new builder with individual parameters.
     */
    public ReceiveImFdoBuilder(int windowId, String fromUser, String message, int responseId, ButtonTheme buttonTheme) {
        this(new Config(windowId, fromUser, message, responseId, buttonTheme));
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Instant message receive window with reply capability";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        FdoGid windowGid = FdoGid.of(0, config.windowId);
        return FdoScript.stream()
                .uniStartStream()
                    .manPresetGid(windowGid)
                    .ifLastReturnTrueThen(1, 2)
                    .uniSyncSkip(1)
                    .atom(AsyncAtom.PLAYSOUND, "IM")
                    .object(ObjectType.IND_GROUP, "", root -> {
                        root.matOrientation(Orientation.VCF);
                        root.matObjectId(windowGid);
                        root.matTitle("Instant Message: " + config.fromUser);
                        root.matStyleId(STYLE_GID);
                        root.backgroundTile();
                        root.artId(BACKGROUND_ART);
                        root.manSetResponseId(config.responseId);
                        root.atom(ManAtom.DO_MAGIC_RESPONSE_ID, config.responseId);

                        // Hidden ornaments for sender info
                        root.object(ObjectType.ORG_GROUP, "", senderGroup -> {
                            senderGroup.matOrientation(Orientation.HLF);
                            senderGroup.object(ObjectType.ORNAMENT, "", orn1 -> {
                                orn1.mat(MatAtom.BOOL_INVISIBLE, "yes");
                                orn1.matHeight(1);
                                orn1.matWidth(15);
                                orn1.matRelativeTag(1);
                                orn1.mat(MatAtom.BOOL_DEFAULT_SEND, "yes");
                                orn1.manReplaceData(config.fromUser);
                            });
                            senderGroup.object(ObjectType.ORNAMENT, "", orn2 -> {
                                orn2.matHeight(1);
                                orn2.matWidth(15);
                                orn2.matRelativeTag(1);
                                orn2.mat(MatAtom.BOOL_INVISIBLE, "yes");
                                orn2.mat(MatAtom.BOOL_DEFAULT_SEND, "yes");
                            });
                        });

                        // Message history view
                        root.object(ObjectType.VIEW, "", view -> {
                            view.matHeight(5);
                            view.matWidth(40);
                            view.matRelativeTag(3);
                            view.mat(MatAtom.SCROLL_THRESHOLD, 4096);
                            view.matBoolForceScroll(true);
                            view.matParagraph(1);
                        });

                        // Reply edit field
                        root.object(ObjectType.EDIT_VIEW, "", edit -> {
                            edit.matHeight(4);
                            edit.matWidth(40);
                            edit.matCapacity(512);
                            edit.matTitle("Message:");
                            edit.mat(MatAtom.BOOL_INVISIBLE, "no");
                            edit.matRelativeTag(2);
                        });

                        // Button row
                        root.object(ObjectType.ORG_GROUP, "", buttonRow -> {
                            buttonRow.matOrientation(Orientation.HCF);
                            buildSendButton(buttonRow, ctx);
                            buildCancelButton(buttonRow, ctx);
                        });
                    })
                    // uni_sync_skip target: append message to existing window
                    .uniSyncSkip(2)
                    // After man_end_object closes ind_group, explicitly set context back to window
                    .manSetContextGlobalId(windowGid)
                    .uniStartStream()
                        .manSetContextRelative(3)
                        .manAppendData(config.fromUser + ": " + config.message + "\u007F")
                        .manEndContext()
                    .uniEndStream()
                    .manMakeFocus()
                    .manUpdateDisplay()
                .uniEndStream()
                .toSource();
    }

    private void buildSendButton(com.atomforge.fdo.dsl.ObjectBuilder buttonRow, RenderingContext ctx) {
        buttonRow.object(ObjectType.TRIGGER, "", btn -> {
            btn.matTitle("Send");
            btn.matRelativeTag(5);

            // Color mode: apply button colors and trigger style
            if (!ctx.isLowColorMode() && config.buttonTheme != null) {
                applyButtonColors(btn, config.buttonTheme);
                btn.matTriggerStyle(TriggerStyle.PLACE);
            }

            btn.matFontId(FontId.ARIAL);
            btn.matFontSize(10);
            btn.matFontStyle(FontStyle.BOLD);
            btn.mat(MatAtom.COMMAND_KEY, "0dx");
            btn.actSetCriterion(Criterion.SELECT);
            btn.actReplaceAction(action -> {
                action.uniStartStream()
                        .atom(DeAtom.START_EXTRACTION)
                        .atom(BufAtom.SET_TOKEN, "iT")
                        .atom(DeAtom.GET_DATA)
                        .manSetContextRelative(2)
                        .manClearObject()
                        .manEndContext()
                        .atom(DeAtom.END_EXTRACTION)
                        .atom(BufAtom.CLOSE_BUFFER)
                        .manUpdateDisplay()
                        .uniEndStream();
            });
        });
    }

    private void buildCancelButton(com.atomforge.fdo.dsl.ObjectBuilder buttonRow, RenderingContext ctx) {
        buttonRow.object(ObjectType.TRIGGER, "", btn -> {
            btn.matTitle("Cancel");

            // Color mode: apply button colors and trigger style
            if (!ctx.isLowColorMode() && config.buttonTheme != null) {
                applyButtonColors(btn, config.buttonTheme);
                btn.matTriggerStyle(TriggerStyle.PLACE);
            }

            btn.matFontId(FontId.ARIAL);
            btn.matFontSize(10);
            btn.matFontStyle(FontStyle.BOLD);
            btn.actSetCriterion(Criterion.SELECT);
            btn.actReplaceAction(action -> {
                action.uniStartStream()
                        .manClose()
                        .manUpdateDisplay()
                        .uniEndStream();
            });
        });
    }

    private void applyButtonColors(com.atomforge.fdo.dsl.ObjectBuilder btn, ButtonTheme theme) {
        int[] face = theme.colorFace();
        int[] text = theme.colorText();
        int[] top = theme.colorTopEdge();
        int[] bottom = theme.colorBottomEdge();

        btn.mat(MatAtom.COLOR_FACE, face[0], face[1], face[2]);
        btn.mat(MatAtom.COLOR_TEXT, text[0], text[1], text[2]);
        btn.mat(MatAtom.COLOR_TOP_EDGE, top[0], top[1], top[2]);
        btn.mat(MatAtom.COLOR_BOTTOM_EDGE, bottom[0], bottom[1], bottom[2]);
    }
}
