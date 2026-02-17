/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for invoke local art FDO response.
 *
 * <p>Generates an FDO stream that invokes a local art asset by GID.
 * Used for keyword/command responses that trigger display of cached art.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 * uni_invoke_local &lt;artId&gt;
 * uni_wait_off
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/invoke_response.fdo.txt}</p>
 */
public final class InvokeResponseFdoBuilder implements FdoDslBuilder {

    private static final String GID = "invoke_response";

    private final FdoGid artId;

    /**
     * Create a new InvokeResponse builder with the given art GID.
     *
     * @param artId The art GID to invoke (e.g., FdoGid.of(69, 420))
     */
    public InvokeResponseFdoBuilder(FdoGid artId) {
        if (artId == null) {
            throw new IllegalArgumentException("artId cannot be null");
        }
        this.artId = artId;
    }

    /**
     * Create a new InvokeResponse builder with the given art GID components.
     *
     * @param major The major GID component
     * @param minor The minor GID component
     */
    public InvokeResponseFdoBuilder(int major, int minor) {
        this(FdoGid.of(major, minor));
    }

    /**
     * Get the art GID this builder will invoke.
     *
     * @return the art GID
     */
    public FdoGid getArtId() {
        return artId;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Invoke local art response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.uniInvokeLocal(artId);
                    s.uniWaitOff();
                })
                .toSource();
    }
}
