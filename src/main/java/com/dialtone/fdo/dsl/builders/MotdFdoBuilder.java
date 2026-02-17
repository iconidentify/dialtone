/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for Message of the Day (MOTD) FDO response.
 *
 * <p>Generates a minimal FDO stream that signals wait-off without any UI action.
 * Used for MOTD acknowledgments where no visual feedback is needed.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream &lt;00x&gt;
 *   uni_wait_off
 * uni_end_stream &lt;00x&gt;
 * </pre>
 *
 * <p>Replaces: {@code fdo/motd.fdo.txt}</p>
 */
public final class MotdFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "motd";

    /**
     * Singleton instance - no configuration needed.
     */
    public static final MotdFdoBuilder INSTANCE = new MotdFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Message of the Day FDO response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.uniWaitOff();
                })
                .toSource();
    }
}
