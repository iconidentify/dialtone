/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for f1 DOD failed response.
 *
 * <p>Generates an FDO stream indicating a DOD request failed.
 * This tells the client to stop waiting for the requested data.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/f1_dod_failed.fdo.txt}</p>
 */
public final class F1DodFailedFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "f1_dod_failed";

    /**
     * Singleton instance - no configuration needed.
     */
    public static final F1DodFailedFdoBuilder INSTANCE = new F1DodFailedFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "f1 DOD request failed response";
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
