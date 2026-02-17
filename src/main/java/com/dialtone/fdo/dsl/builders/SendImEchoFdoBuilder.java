/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for IM echo/send confirmation.
 *
 * <p>Generates an FDO stream that appends a sent message to the sender's
 * own IM window. This provides visual feedback that the message was sent.</p>
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>Check if window exists (man_preset_gid + if_last_return_true_then)</li>
 *   <li>If exists: set context, append message, update display</li>
 *   <li>If not exists: skip to end (uni_sync_skip)</li>
 * </ol>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   man_preset_gid &lt;windowId&gt;
 *   if_last_return_true_then &lt;1, 2&gt;
 *   man_set_context_globalid &lt;windowId&gt;
 *   man_set_context_relative &lt;3&gt;
 *   mat_paragraph &lt;yes&gt;
 *   man_append_data &lt;"fromUser: message\x7F"&gt;
 *   man_end_context
 *   man_make_focus
 *   man_update_display
 *   uni_sync_skip &lt;1&gt;
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/send_im_echo_minimal.fdo.txt}</p>
 */
public final class SendImEchoFdoBuilder implements FdoDslBuilder, FdoBuilder.Dynamic<SendImEchoFdoBuilder.Config> {

    private static final String GID = "send_im_echo_minimal";

    /** AOL uses DEL (0x7F) as line terminator - not stripped by Java's strip() */
    private static final char AOL_LINE_TERMINATOR = '\u007F';

    /**
     * Configuration for IM echo.
     *
     * @param windowId The IM window ID (GID minor component)
     * @param fromUser The sender's screen name (usually the current user)
     * @param message  The message that was sent (leading/trailing whitespace is trimmed)
     */
    public record Config(
            int windowId,
            String fromUser,
            String message
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
     * Create a new SendImEcho builder with the given configuration.
     *
     * @param config The echo configuration
     */
    public SendImEchoFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new SendImEcho builder with individual parameters.
     *
     * @param windowId The IM window ID
     * @param fromUser The sender's screen name
     * @param message  The message sent
     */
    public SendImEchoFdoBuilder(int windowId, String fromUser, String message) {
        this(new Config(windowId, fromUser, message));
    }

    /**
     * Factory method for creating an IM echo.
     *
     * @param windowId The IM window ID
     * @param fromUser The sender's screen name
     * @param message  The message sent
     * @return new builder instance
     */
    public static SendImEchoFdoBuilder echo(int windowId, String fromUser, String message) {
        return new SendImEchoFdoBuilder(windowId, fromUser, message);
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
        return "IM echo for sent message confirmation";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        FdoGid windowGid = FdoGid.of(0, config.windowId);

        return FdoScript.stream()
                .uniStartStream()
                    .manPresetGid(windowGid)
                    .ifLastReturnTrueThen(1, 2)
                    .manSetContextGlobalId(windowGid)
                    .manSetContextRelative(3)
                    .matParagraph(1)
                    .manAppendData(config.fromUser + ": " + config.message + "\u007F")
                    .manEndContext()
                    .manMakeFocus()
                    .manUpdateDisplay()
                    .uniSyncSkip(1)
                .uniEndStream()
                .toSource();
    }
}
