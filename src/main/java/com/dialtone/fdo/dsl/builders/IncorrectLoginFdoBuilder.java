/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.AsyncAtom;
import com.atomforge.fdo.dsl.atoms.CclAtom;
import com.atomforge.fdo.dsl.atoms.ManAtom;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.atoms.UniAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for incorrect login response.
 *
 * <p>Generates an FDO stream that displays an authentication error,
 * hangs up the connection, and disables the login form.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_start_stream &lt;00x&gt;
 *     async_alert &lt;info, "Incorrect username or password."&gt;
 *   ccl_hang_up
 *   man_update_woff_end_stream &lt;0&gt;
 *   uni_start_stream_wait_on
 *     man_set_context_globalid &lt;32-6086&gt;
 *   man_set_context_relative &lt;1&gt;
 *   mat_bool_disabled &lt;yes&gt;
 *   man_end_context
 *   man_update_display
 *   man_end_context
 *   uni_wait_off_end_stream
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/incorrect_login.fdo.txt}</p>
 */
public final class IncorrectLoginFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "incorrect_login";
    private static final String ERROR_MESSAGE = "Incorrect username or password.";
    private static final FdoGid LOGIN_FORM_GID = FdoGid.of(32, 6086);

    /**
     * Singleton instance - no configuration needed.
     */
    public static final IncorrectLoginFdoBuilder INSTANCE = new IncorrectLoginFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Incorrect login authentication failure response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .uniStartStream("00x")
                        .atom(AsyncAtom.ALERT, "info", ERROR_MESSAGE)
                    .atom(CclAtom.HANG_UP)
                    .atom(ManAtom.UPDATE_WOFF_END_STREAM, 0)
                    .atom(UniAtom.START_STREAM_WAIT_ON)
                        .manSetContextGlobalId(LOGIN_FORM_GID)
                    .manSetContextRelative(1)
                    .mat(MatAtom.BOOL_DISABLED, "yes")
                    .manEndContext()
                    .manUpdateDisplay()
                    .manEndContext()
                    .uniWaitOffEndStream()
                .uniEndStream()
                .toSource();
    }
}
