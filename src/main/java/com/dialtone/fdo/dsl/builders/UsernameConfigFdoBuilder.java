/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.BufAtom;
import com.atomforge.fdo.dsl.atoms.IdbAtom;
import com.atomforge.fdo.dsl.atoms.UniAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

import java.nio.charset.StandardCharsets;

/**
 * DSL builder for username configuration during post-login.
 *
 * <p>Generates an FDO stream that configures the username selector with
 * the authenticated username and creates a host-bound buffer packet.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   idb_set_context &lt;20-0-16&gt;
 *   idb_atr_offset &lt;0&gt;
 *   idb_append_data &lt;02x&gt;
 *   idb_append_data &lt;username_binary_data&gt;
 *   idb_end_context
 *   uni_use_last_atom_value &lt;uni_save_result&gt;
 *   buf_start_buffer &lt;7&gt; (token_header=1 | stream_id_header=2 | host_bound=4)
 *   buf_set_token &lt;"]K"&gt;
 *   uni_get_result
 *   uni_use_last_atom_value &lt;buf_add_data&gt;
 *   buf_close_buffer
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/post_login/username_config.fdo.txt}</p>
 */
public final class UsernameConfigFdoBuilder
        implements FdoDslBuilder, FdoBuilder.Dynamic<UsernameConfigFdoBuilder.Config> {

    private static final String GID = "post_login/username_config";
    private static final FdoGid IDB_CONTEXT_GID = FdoGid.of(20, 0, 16);

    /**
     * Configuration for username config.
     *
     * @param username The username to configure in the selector
     */
    public record Config(String username) {
        public Config {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("username cannot be null or blank");
            }
        }
    }

    private final Config config;

    /**
     * Create a new builder with the given configuration.
     *
     * @param config The username configuration
     */
    public UsernameConfigFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new builder with the given username.
     *
     * @param username The username to configure
     */
    public UsernameConfigFdoBuilder(String username) {
        this(new Config(username));
    }

    /**
     * Factory method for type-safe builder creation.
     *
     * @param username The username to configure
     * @return New builder instance
     */
    public static UsernameConfigFdoBuilder forUser(String username) {
        return new UsernameConfigFdoBuilder(username);
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
        return "Username selector configuration for post-login";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        // Build the username binary data
        byte[] usernameData = buildUsernameData(config.username);

        return FdoScript.stream()
                .uniStartStream()
                    .idbSetContext(IDB_CONTEXT_GID)
                    .idb(IdbAtom.ATR_OFFSET, 0)
                    .idb(IdbAtom.APPEND_DATA, "02x")
                    .idb(IdbAtom.APPEND_DATA, usernameData)
                    .idbEndContext()
                    .atom(UniAtom.USE_LAST_ATOM_VALUE, UniAtom.SAVE_RESULT)
                    .atom(BufAtom.START_BUFFER, 7)  // token_header(1) | stream_id_header(2) | host_bound(4)
                    .atom(BufAtom.SET_TOKEN, "]K")
                    .atom(UniAtom.GET_RESULT)
                    .atom(UniAtom.USE_LAST_ATOM_VALUE, BufAtom.ADD_DATA)
                    .atom(BufAtom.CLOSE_BUFFER)
                .uniEndStream()
                .toSource();
    }

    /**
     * Build the binary data for the username selector.
     * Creates a structure with the username and placeholder entries.
     */
    private byte[] buildUsernameData(String username) {
        // Sanitize username to max 10 chars, padded with spaces
        String sanitized = sanitizeToWidth(username, 10);

        // Build the data structure:
        // username + null + blank + null + Guest + null + 7777777777 + null + ...
        StringBuilder data = new StringBuilder();
        appendField(data, sanitized);
        appendNull(data);
        appendField(data, spaces10());
        appendNull(data);
        appendField(data, pad10("Guest"));
        appendNull(data);
        appendField(data, "7777777777");
        appendNull(data);
        appendField(data, pad10("New User"));
        appendNull(data);
        appendField(data, "6666666666");
        appendNull(data);
        appendField(data, pad10("New Local#"));
        appendNull(data);
        appendField(data, "5555555555");
        appendNull(data);

        return data.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static String sanitizeToWidth(String s, int width) {
        if (s == null) s = "";
        if (s.length() > width) {
            s = s.substring(0, width);
        }
        return String.format("%-" + width + "s", s);
    }

    private static String spaces10() {
        return "          "; // 10 spaces
    }

    private static String pad10(String s) {
        return sanitizeToWidth(s, 10);
    }

    private static void appendField(StringBuilder sb, String s) {
        sb.append(s);
    }

    private static void appendNull(StringBuilder sb) {
        sb.append('\0');
    }
}
