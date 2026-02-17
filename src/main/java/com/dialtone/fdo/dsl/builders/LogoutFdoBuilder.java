/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.CclAtom;
import com.atomforge.fdo.dsl.atoms.ManAtom;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.atoms.SmAtom;
import com.atomforge.fdo.dsl.atoms.UniAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for logout confirmation screen.
 *
 * <p>Generates an FDO stream that displays a goodbye message,
 * hangs up the connection, and disables the login form.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_start_stream &lt;00x&gt;
 *     uni_invoke_local &lt;32-5438&gt;
 *     uni_invoke_local &lt;32-281&gt;
 *     man_set_context_globalid &lt;32-221&gt;
 *     sm_set_plus_group &lt;71x, c1x&gt;
 *     man_set_context_relative &lt;4&gt;
 *     man_append_data &lt;goodbye HTML&gt;
 *   man_end_data
 *   man_end_context
 *   man_set_context_relative &lt;6&gt;
 *   man_append_data &lt;logout_message&gt;
 *   man_end_data
 *   man_end_context
 *   ccl_hang_up
 *   man_update_woff_end_stream &lt;0&gt;
 *   ... (disable login form)
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/logout.fdo.txt}</p>
 */
public final class LogoutFdoBuilder
        implements FdoDslBuilder, FdoBuilder.Dynamic<LogoutFdoBuilder.Config> {

    private static final String GID = "logout";

    // GIDs used in logout sequence
    private static final FdoGid INVOKE_GID_1 = FdoGid.of(32, 5438);
    private static final FdoGid INVOKE_GID_2 = FdoGid.of(32, 281);
    private static final FdoGid CONTEXT_GID = FdoGid.of(32, 221);
    private static final FdoGid LOGIN_FORM_GID = FdoGid.of(32, 6086);

    // Default goodbye HTML messages
    private static final String GOODBYE_LINE1 =
            "<HTML><P ALIGN=CENTER><FONT SIZE=4><br><br><b>Goodbye.<b></FONT></P><br></HTML>";
    private static final String GOODBYE_LINE2 =
            "<HTML><P ALIGN=CENTER><FONT SIZE=3><b>We hope to see you again.<b></FONT></P></HTML>";

    /**
     * Configuration for logout message.
     *
     * @param logoutMessage Custom logout message (displayed below goodbye)
     */
    public record Config(String logoutMessage) {
        public Config {
            if (logoutMessage == null) {
                logoutMessage = "";
            }
        }
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The logout configuration
     */
    public LogoutFdoBuilder(Config config) {
        this.config = config != null ? config : new Config("");
    }

    /**
     * Create a new builder with the given logout message.
     *
     * @param logoutMessage The custom logout message
     */
    public LogoutFdoBuilder(String logoutMessage) {
        this(new Config(logoutMessage));
    }

    /**
     * Create a new builder with no custom message.
     */
    public LogoutFdoBuilder() {
        this(new Config(""));
    }

    /**
     * Factory method for type-safe builder creation.
     *
     * @param logoutMessage The custom logout message
     * @return A new LogoutFdoBuilder instance
     */
    public static LogoutFdoBuilder withMessage(String logoutMessage) {
        return new LogoutFdoBuilder(logoutMessage);
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
        return "Logout confirmation screen with goodbye message";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .uniStartStream("00x")
                        .uniInvokeLocal(INVOKE_GID_1)
                        .uniInvokeLocal(INVOKE_GID_2)
                        .manSetContextGlobalId(CONTEXT_GID)
                        .atom(SmAtom.SET_PLUS_GROUP, "71x", "c1x")
                        .manSetContextRelative(4)
                        .manAppendData(GOODBYE_LINE1)
                        .manAppendData(GOODBYE_LINE2)
                    .manEndData()
                    .manEndContext()
                    .manSetContextRelative(6)
                    .manAppendData(config.logoutMessage)
                    .manEndData()
                    .manEndContext()
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
