/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.ObjectBuilder;
import com.atomforge.fdo.dsl.atoms.DeAtom;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.atoms.UniAtom;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.dsl.values.ObjectType;
import com.atomforge.fdo.dsl.values.Orientation;
import com.atomforge.fdo.dsl.values.TitlePosition;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for guest login modal dialog.
 *
 * <p>Generates an FDO stream that creates a modal login form for guest users
 * with username and password input fields.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   man_preset_gid &lt;52&gt;
 *   if_last_return_false_then &lt;1&gt;
 *   uni_sync_skip &lt;1&gt;
 *   uni_start_stream
 *     IND_GROUP (modal, 325x230, object_id 32-494)
 *       +-- ORNAMENT (art icon at 10,10)
 *       +-- ORNAMENT "Guest Sign-On:" [sibling]
 *       +-- ORNAMENT "Please enter your screen name:" [sibling]
 *       +-- EDIT_VIEW (username, tag 1) [sibling]
 *       +-- ORNAMENT "Please enter your password:" [sibling]
 *       +-- EDIT_VIEW (password, tag 2, protected) [sibling]
 *       +-- ORNAMENT "(Use TAB...)" [sibling]
 *       +-- TRIGGER "Cancel" [sibling]
 *       +-- TRIGGER "OK" [sibling]
 *     man_update_display
 *   man_update_display
 *   uni_end_stream
 *   man_update_woff_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/guest_login.fdo.txt}</p>
 */
public final class GuestLoginFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "guest_login";
    private static final FdoGid OBJECT_ID = FdoGid.of(32, 494);
    private static final FdoGid ART_ICON = FdoGid.of(1, 0, 1385);
    private static final FdoGid INVOKE_GID = FdoGid.of(20, 0, 0);

    // Window dimensions
    private static final int WINDOW_WIDTH = 325;
    private static final int WINDOW_HEIGHT = 230;

    // Preset GID for window existence check
    private static final FdoGid PRESET_GID = FdoGid.of(0, 52);

    /**
     * Singleton instance (no configuration needed).
     */
    public static final GuestLoginFdoBuilder INSTANCE = new GuestLoginFdoBuilder();

    private GuestLoginFdoBuilder() {
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Guest login modal dialog with username and password fields";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream("00x")
                    .manPresetGid(PRESET_GID)
                    .ifLastReturnFalseThen(1, 0)
                    .uniSyncSkip(1)
                    .uniStartStream("00x")
                        .object(ObjectType.IND_GROUP, "", root -> {
                            root.matObjectId(OBJECT_ID);
                            root.matOrientation(Orientation.VFF);
                            root.matPreciseWidth(WINDOW_WIDTH);
                            root.matPreciseHeight(WINDOW_HEIGHT);
                            root.mat(MatAtom.BOOL_PRECISE, "yes");
                            root.modal();

                            // Art icon at (10, 10)
                            root.object(ObjectType.ORNAMENT, "", art -> {
                                art.artId(ART_ICON);
                                art.matPreciseX(10);
                                art.matPreciseY(10);
                            });

                            // "Guest Sign-On:" label
                            root.sibling(ObjectType.ORNAMENT, "", label1 -> {
                                label1.manAppendData("Guest Sign-On:");
                                label1.matPreciseX(70);
                                label1.matPreciseY(10);
                            });

                            // "Please enter your screen name:" label
                            root.sibling(ObjectType.ORNAMENT, "", label2 -> {
                                label2.manAppendData("Please enter your screen name:");
                                label2.matPreciseX(40);
                                label2.matPreciseY(65);
                            });

                            // Username edit field
                            root.sibling(ObjectType.EDIT_VIEW, "", usernameEdit -> {
                                usernameEdit.mat(MatAtom.FIELD_SCRIPT, 0);
                                usernameEdit.mat(MatAtom.SORT_ORDER, "00x");
                                usernameEdit.matTitleWidth(16);
                                usernameEdit.matSize(16, 1, 16);
                                usernameEdit.matRelativeTag(1);
                                usernameEdit.matTitlePos(TitlePosition.LEFT_CENTER);
                                usernameEdit.matBoolVerticalScroll(false);
                                usernameEdit.mat(MatAtom.BOOL_WRITEABLE, "yes");
                                usernameEdit.matPreciseX(90);
                                usernameEdit.matPreciseY(85);
                                usernameEdit.mat(MatAtom.BOOL_EXPORTABLE, "no");
                                usernameEdit.mat(MatAtom.BOOL_IMPORTABLE, "no");
                            });

                            // "Please enter your password:" label
                            root.sibling(ObjectType.ORNAMENT, "", label3 -> {
                                label3.manAppendData("Please enter your password:");
                                label3.matPreciseX(40);
                                label3.matPreciseY(115);
                            });

                            // Password edit field (protected)
                            root.sibling(ObjectType.EDIT_VIEW, "", passwordEdit -> {
                                passwordEdit.mat(MatAtom.FIELD_SCRIPT, 0);
                                passwordEdit.mat(MatAtom.SORT_ORDER, "00x");
                                passwordEdit.matTitleWidth(16);
                                passwordEdit.matSize(16, 1, 16);
                                passwordEdit.matRelativeTag(2);
                                passwordEdit.matTitlePos(TitlePosition.LEFT_CENTER);
                                passwordEdit.matBoolVerticalScroll(false);
                                passwordEdit.mat(MatAtom.BOOL_WRITEABLE, "yes");
                                passwordEdit.matPreciseX(90);
                                passwordEdit.matPreciseY(135);
                                passwordEdit.mat(MatAtom.BOOL_PROTECTED_INPUT, "yes");
                                passwordEdit.mat(MatAtom.BOOL_EXPORTABLE, "no");
                            });

                            // TAB instruction label
                            root.sibling(ObjectType.ORNAMENT, "", label4 -> {
                                label4.manAppendData("(Use TAB to move between fields).");
                                label4.matPreciseX(40);
                                label4.matPreciseY(165);
                            });

                            // Cancel button
                            root.sibling(ObjectType.TRIGGER, "Cancel", cancelBtn -> {
                                cancelBtn.matPreciseX(110);
                                cancelBtn.matPreciseY(190);
                                cancelBtn.actSetCriterion(Criterion.SELECT);
                                cancelBtn.actReplaceAction(action -> {
                                    action.uniStartStream()
                                            .manClose()
                                            .manUpdateDisplay()
                                            .uniInvokeLocal(INVOKE_GID)
                                            .uniEndStream();
                                });
                            });

                            // OK button (default)
                            root.sibling(ObjectType.TRIGGER, "OK", okBtn -> {
                                okBtn.matBoolDefault();
                                okBtn.matPreciseX(170);
                                okBtn.matPreciseY(190);
                                okBtn.actSetCriterion(Criterion.SELECT);
                                okBtn.actReplaceAction(action -> {
                                    action.atom(UniAtom.START_STREAM_WAIT_ON, "00x")
                                            .atom(DeAtom.EZ_SEND_FORM, "44x", "67x")
                                            .manEndObject()
                                            .manEndObject();
                                });
                            });

                            root.manUpdateDisplay();
                        })
                    .manUpdateDisplay()
                .uniEndStream("00x")
                .manUpdateWoffEndStream()
                .toSource();
    }
}
