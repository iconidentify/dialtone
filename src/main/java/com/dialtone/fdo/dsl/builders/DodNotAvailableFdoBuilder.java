/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for DOD not available response.
 *
 * <p>Generates an FDO stream indicating the requested DOD resource is not available.
 * This tells the client to stop waiting and the asset cannot be downloaded.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/dod_not_available.fdo.txt}</p>
 */
public final class DodNotAvailableFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "dod_not_available";

    /**
     * Singleton instance - no configuration needed.
     */
    public static final DodNotAvailableFdoBuilder INSTANCE = new DodNotAvailableFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "DOD resource not available response";
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
