/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt.fdo;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.model.FdoGid;

public final class SkalholtFdoBuilder {

    private static final FdoGid SKALHOLT_FORM_GID = FdoGid.of(69, 420);


    public String toSource() {
        String windowTitle = "Skalholt";

        return FdoScript.stream()
                .uniStartStream()
                .manPresetGid(SKALHOLT_FORM_GID)
                .ifLastReturnFalseThen(1, 0)
                .uniStartStream("01x")
                .uniInvokeNoContext(SKALHOLT_FORM_GID)
                .manSetContextGlobalId(SKALHOLT_FORM_GID)
                .actSetCriterion(Criterion.CLOSE)
                .actReplaceAction(nested -> {
                    nested.uniStartStream()
                            .manClose(SKALHOLT_FORM_GID)
                            .smSendTokenRaw("Sl")
                            .uniEndStream();
                })
                .matTitle(windowTitle)
                .uniEndStream("01x")
                .uniSyncSkip(1)
                .manUpdateDisplay()
                .manMakeFocus()
                .uniWaitOffEndStream()
                .toSource();
    }

    public String addLine(String line) {
        // Newline character in AOL protocol is 0x7F (DEL)
        byte[] newlineByte = new byte[]{(byte) 0x7F};

        return FdoScript.stream()
                .uniStartStream()
                .manPresetGid(SKALHOLT_FORM_GID)
                .uniStartStream("01x")
                .manSetContextGlobalId(SKALHOLT_FORM_GID)
                .manSetContextRelative(256)
                .manAppendData(line)
                .manAppendData(newlineByte)
                .manEndContext()
                .manUpdateDisplay()
                .uniEndStream("01x")
                .uniWaitOffEndStream()
                .toSource();
    }
}
