/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for no-operation FDO response.
 *
 * <p>Generates a minimal FDO stream that does nothing except signal completion.
 * Used for acknowledgments where no UI action is needed.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/noop.fdo.txt}</p>
 */
public final class NoopFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "noop";

    /**
     * Singleton instance - no configuration needed.
     */
    public static final NoopFdoBuilder INSTANCE = new NoopFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "No-operation FDO response";
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
