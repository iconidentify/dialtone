/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.ObjectBuilder;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.*;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL-based FDO builder for GID 69-420.
 *
 * <p>Chat input form with scrollable view and edit field with Send button.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * IND_GROUP (root)
 * +-- ORG_GROUP (VFF - outer)
 *     +-- ORG_GROUP (HEF - toolbar row)
 *     |   +-- TRIGGER "Inventory" (Mac color only)
 *     |   +-- ORG_GROUP (HLE - left toolbar)
 *     |   +-- ORG_GROUP (spacer) [sibling]
 *     +-- ORG_GROUP (VLT - content) [sibling]
 *         +-- VIEW (message area)
 *         +-- ORG_GROUP (HLF - input row)
 *             +-- EDIT_VIEW (input field)
 *             +-- TRIGGER "Send" [sibling]
 * </pre>
 *
 * <p>Platform differences:</p>
 * <ul>
 *   <li>Mac color mode: Shows "Inventory" button in toolbar</li>
 *   <li>Windows: No "Inventory" button</li>
 *   <li>Low color mode: Smaller view/edit sizes, simpler fonts</li>
 * </ul>
 *
 * <p>Uses fully scoped DSL with typed methods - all streams, objects, and contexts
 * are automatically closed when lambdas complete.</p>
 */
public final class Gid69_420FdoBuilder implements FdoDslBuilder {

    private static final String GID = "69-420";

    // View dimensions
    private static final int VIEW_WIDTH_COLOR = 86;
    private static final int VIEW_HEIGHT_COLOR = 24;
    private static final int VIEW_WIDTH_BW = 63;
    private static final int VIEW_HEIGHT_BW = 16;
    private static final int VIEW_MAX_SIZE = 8192;

    // Edit dimensions
    private static final int EDIT_WIDTH_COLOR = 82;
    private static final int EDIT_WIDTH_BW = 56;
    private static final int EDIT_HEIGHT = 1;
    private static final int EDIT_MAX_CHARS = 92;

    // Font sizes
    private static final int FONT_SIZE = 8;

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Chat input form with view and edit areas";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, "", root -> {
                        root.matObjectId(FdoGid.of(69, 420));
                        root.matOrientation(Orientation.VFF);
                        root.matPosition(Position.CENTER_CENTER);

                        root.object(ObjectType.ORG_GROUP, "", outer -> {
                            outer.matOrientation(Orientation.HLF);
                            buildToolbarRow(outer, ctx);
                            buildContentArea(outer, ctx);
                        });

                        root.manUpdateDisplay();
                    });
                })
                .toSource();
    }

    /**
     * Build toolbar row with platform-specific elements.
     *
     * <p>Mac color mode shows "Inventory" button; Windows and BW modes do not.</p>
     */
    private void buildToolbarRow(ObjectBuilder outer, RenderingContext ctx) {
        outer.object(ObjectType.ORG_GROUP, "", toolbarRow -> {
            toolbarRow.matOrientation(Orientation.HCF);

            // Mac color mode: show Inventory button

            toolbarRow.sibling(ObjectType.TRIGGER, "Map", btn -> {
                btn.triggerStyle(TriggerStyle.RECTANGLE);
                btn.matFontSis(FontId.COURIER_NEW, FONT_SIZE, FontStyle.BOLD);
                btn.onSelect(action -> {
                    action.stream(inner -> {
                        inner.sendTokenRaw("MP");
                    });
                });
            });
        });
    }

    /**
     * Build content area with message view and input row.
     */
    private void buildContentArea(ObjectBuilder outer, RenderingContext ctx) {
        outer.sibling(ObjectType.ORG_GROUP, "", content -> {
            content.matOrientation(Orientation.VLT);
            buildMessageView(content, ctx);
            buildInputRow(content, ctx);
        });
    }

    /**
     * Build message view with mode-specific dimensions and fonts.
     */
    private void buildMessageView(ObjectBuilder content, RenderingContext ctx) {
        content.object(ObjectType.VIEW, "", view -> {
            int width = ctx.isLowColorMode() ? VIEW_WIDTH_BW : VIEW_WIDTH_COLOR;
            int height = ctx.isLowColorMode() ? VIEW_HEIGHT_BW : VIEW_HEIGHT_COLOR;
            if (ctx.isMac()) {
                width = VIEW_WIDTH_COLOR - 22;
                height = VIEW_HEIGHT_COLOR;
                view.matBoolBackgroundFlood(true);
                view.mat(MatAtom.COLOR_FACE, 0, 0, 0);
                view.mat(MatAtom.COLOR_TEXT, 255, 255, 255);
            }
            view.matSize(width, height, VIEW_MAX_SIZE);
            view.matRelativeTag(256);
            applyViewFont(view, ctx);
            view.matBoolWriteable();
            view.matScrollThreshold(4096);
            view.matBoolForceScroll();
            view.matParagraph(1);
        });
    }

    /**
     * Apply font styling to view based on rendering context.
     */
    private void applyViewFont(ObjectBuilder view, RenderingContext ctx) {

        view.matFontSis(FontId.COURIER_NEW, FONT_SIZE, FontStyle.NORMAL);

    }

    /**
     * Build input row with edit field and send button.
     */
    private void buildInputRow(ObjectBuilder content, RenderingContext ctx) {
        content.object(ObjectType.ORG_GROUP, "", inputRow -> {
            inputRow.matOrientation(Orientation.HLF);
            buildEditField(inputRow, ctx);
            buildSendButton(inputRow, ctx);
        });
    }

    /**
     * Build edit field with mode-specific dimensions and fonts.
     */
    private void buildEditField(ObjectBuilder inputRow, RenderingContext ctx) {
        inputRow.object(ObjectType.EDIT_VIEW, "", edit -> {
            int width = ctx.isLowColorMode() ? EDIT_WIDTH_BW : EDIT_WIDTH_COLOR;
            if (ctx.isMac()) {
                width = EDIT_WIDTH_COLOR - 22;
            }
            edit.matSize(width, EDIT_HEIGHT, EDIT_MAX_CHARS);
            edit.matRelativeTag(258);
            edit.matTitlePos(TitlePosition.LEFT_CENTER);
            edit.matBoolVerticalScroll(false);
            edit.matBoolResizeVertical(false);
            edit.matBoolResizeHorizontal();
            edit.matBoolExportable(false);
            edit.matBoolWriteable();
            edit.matValidation(128);
            applyEditFont(edit, ctx);
        });
    }

    /**
     * Apply font styling to edit field based on rendering context.
     */
    private void applyEditFont(ObjectBuilder edit, RenderingContext ctx) {
        if (ctx.isLowColorMode()) {
            edit.matFontId(FontId.COURIER);
            edit.matFontSize(FONT_SIZE);
        } else {
            edit.matFontSis(FontId.COURIER_NEW, FONT_SIZE, FontStyle.NORMAL);
        }
    }

    /**
     * Build send button with mode-specific styling.
     */
    private void buildSendButton(ObjectBuilder inputRow, RenderingContext ctx) {
        inputRow.sibling(ObjectType.TRIGGER, "Send", send -> {
            send.matBoolDefault();
            // Color mode uses PLACE style; BW mode has no style
            if (!ctx.isLowColorMode()) {
                send.matTriggerStyle(TriggerStyle.PLACE);
            }
            send.onSelect(action -> {
                action.stream(inner -> {
                    inner.deStartExtraction();
                    inner.deValidate("display_msg | terminate");
                    inner.bufSetToken("St");
                    inner.context(258, ctxBuilder -> {
                        ctxBuilder.deGetData();
                        ctxBuilder.manClearObject();
                        ctxBuilder.manMakeFocus();
                        ctxBuilder.manUpdateDisplay();
                    });
                    inner.deEndExtraction();
                    inner.bufCloseBuffer();
                });
            });
        });
    }
}
