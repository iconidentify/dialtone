/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for instant message acknowledgment FDO response.
 *
 * <p>Generates an FDO stream that sets the response ID and pops the response.
 * Used to acknowledge receipt of an instant message.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   man_set_response_id &lt;responseId&gt;
 *   man_response_pop
 * uni_wait_off_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/ack_iS.fdo.txt}</p>
 */
public final class AckIsFdoBuilder implements FdoDslBuilder {

    private static final String GID = "ack_is";

    private final int responseId;

    /**
     * Create a new AckIs builder with the given response ID.
     *
     * @param responseId The response ID to acknowledge
     */
    public AckIsFdoBuilder(int responseId) {
        this.responseId = responseId;
    }

    /**
     * Get the response ID this builder will acknowledge.
     *
     * @return the response ID
     */
    public int getResponseId() {
        return responseId;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Instant message acknowledgment response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.manSetResponseId(responseId);
                    s.manResponsePop();
                    s.uniWaitOff();
                })
                .toSource();
    }
}
