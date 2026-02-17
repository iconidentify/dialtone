/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.AsyncAtom;
import com.atomforge.fdo.dsl.atoms.SmAtom;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for configuring the active username during post-login.
 *
 * <p>Generates an FDO stream that sets the screen name, marks the user as online,
 * sets the plus group, and triggers auto-launch. This is called during the
 * post-login sequence after authentication.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   async_set_screen_name &lt;"username"&gt;
 *   async_online &lt;01x&gt;
 *   sm_set_plus_group &lt;71x, c1x&gt;
 *   async_auto_launch
 * man_update_woff_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/post_login/configure_active_username.fdo.txt}</p>
 */
public final class ConfigureActiveUsernameFdoBuilder
        implements FdoDslBuilder, FdoBuilder.Dynamic<ConfigureActiveUsernameFdoBuilder.Config> {

    private static final String GID = "post_login/configure_active_username";

    /**
     * Configuration for active username setup.
     *
     * @param username The screen name to set as active
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
    public ConfigureActiveUsernameFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new builder with the given username.
     *
     * @param username The screen name to configure
     */
    public ConfigureActiveUsernameFdoBuilder(String username) {
        this(new Config(username));
    }

    /**
     * Factory method for type-safe builder creation.
     *
     * @param username The screen name to configure
     * @return New builder instance
     */
    public static ConfigureActiveUsernameFdoBuilder forUser(String username) {
        return new ConfigureActiveUsernameFdoBuilder(username);
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
        return "Configure active username and online status";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .atom(AsyncAtom.SET_SCREEN_NAME, config.username)
                    .atom(AsyncAtom.ONLINE, "01x")
                    .atom(SmAtom.SET_PLUS_GROUP, "71x", "c1x")
                    .atom(AsyncAtom.AUTO_LAUNCH)
                .manUpdateWoffEndStream()
                .toSource();
    }
}
