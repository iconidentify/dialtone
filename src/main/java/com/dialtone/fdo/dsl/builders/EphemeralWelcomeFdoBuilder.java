/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.AsyncAtom;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL-based FDO builder for ephemeral guest welcome message.
 *
 * <p>Displays an alert to ephemeral (guest) users explaining:
 * <ul>
 *   <li>They are using a temporary screenname</li>
 *   <li>The screenname will be lost on disconnect</li>
 *   <li>How to register for a permanent screenname at dialtone.live</li>
 * </ul>
 *
 * <p>This is triggered after authentication when a user fails database auth
 * and gets an ephemeral ~GuestXXXX session instead.</p>
 *
 * <p>Structure: Simple async_alert modal with informational message.</p>
 */
public final class EphemeralWelcomeFdoBuilder implements FdoDslBuilder {

    private static final String GID = "ephemeral-welcome";

    /** The guest screenname to display in the message */
    private final String guestScreenname;

    /**
     * Create builder with the generated guest screenname.
     *
     * @param guestScreenname The ~GuestXXXX screenname assigned to this session
     */
    public EphemeralWelcomeFdoBuilder(String guestScreenname) {
        this.guestScreenname = guestScreenname != null ? guestScreenname : "Guest";
    }

    /**
     * Default constructor for registry (uses placeholder).
     */
    public EphemeralWelcomeFdoBuilder() {
        this("Guest");
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Ephemeral guest welcome alert with registration info";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                .uniStartStream("00x")
                .atom(AsyncAtom.ALERT, "info", buildAlertMessage())
                .uniWaitOff()
                .uniEndStream()
                .uniEndStream()
                .toSource();
    }

    /**
     * Build the alert message text.
     *
     * <p>Explains guest status and how to register. Kept concise for
     * the small AOL 3.0 dialog boxes. Uses \r for line breaks within
     * the alert dialog (AOL protocol convention).</p>
     */
    private String buildAlertMessage() {
        return String.format(
            "You're logged in as a temporary screenname: %s.\r" +
            "To get a permanent screenname:\r" +
            "Visit dialtone.live and create an account.",
            guestScreenname
        );
    }

    /**
     * Create a new builder instance with specific screenname.
     *
     * <p>Factory method for use in LoginTokenHandler.</p>
     *
     * @param screenname The ephemeral screenname to display
     * @return New builder instance
     */
    public static EphemeralWelcomeFdoBuilder forScreenname(String screenname) {
        return new EphemeralWelcomeFdoBuilder(screenname);
    }

    /**
     * Generate FDO source directly for a screenname.
     *
     * <p>Convenience method for quick FDO generation without
     * going through the registry.</p>
     *
     * @param screenname The ephemeral screenname
     * @param ctx Rendering context
     * @return FDO source text
     */
    public static String generateSource(String screenname, RenderingContext ctx) {
        return new EphemeralWelcomeFdoBuilder(screenname).toSource(ctx);
    }
}
