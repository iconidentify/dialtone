/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for K1 DOD response wrapper.
 *
 * <p>Generates an FDO stream that wraps inner FDO content with a response ID.
 * Used for K1 DOD responses that need to route replies back to the correct handler.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   man_set_response_id &lt;responseId&gt;
 *   [inner FDO content]
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/k1_response.fdo.txt}</p>
 */
public final class K1ResponseFdoBuilder implements FdoDslBuilder, FdoBuilder.Dynamic<K1ResponseFdoBuilder.Config> {

    private static final String GID = "k1_response";

    /**
     * Configuration for K1 response wrapper.
     *
     * @param responseId The response ID for routing replies
     * @param innerFdo   The inner FDO source content to wrap (already generated FDO source)
     */
    public record Config(
            int responseId,
            String innerFdo
    ) {
        public Config {
            if (innerFdo == null) innerFdo = "";
        }
    }

    private final Config config;

    /**
     * Create a new K1Response builder with the given configuration.
     *
     * @param config The K1 response configuration
     */
    public K1ResponseFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new K1Response builder with individual parameters.
     *
     * @param responseId The response ID
     * @param innerFdo   The inner FDO source content
     */
    public K1ResponseFdoBuilder(int responseId, String innerFdo) {
        this(new Config(responseId, innerFdo));
    }

    /**
     * Factory method for creating a K1 response wrapper.
     *
     * @param responseId The response ID
     * @param innerFdo   The inner FDO source content
     * @return new builder instance
     */
    public static K1ResponseFdoBuilder wrap(int responseId, String innerFdo) {
        return new K1ResponseFdoBuilder(responseId, innerFdo);
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
        return "K1 DOD response wrapper with response ID";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        // Build FDO source manually since we need to embed raw inner FDO content
        StringBuilder sb = new StringBuilder();
        sb.append("uni_start_stream\n");
        sb.append("  man_set_response_id <").append(config.responseId).append(">\n");
        if (!config.innerFdo.isEmpty()) {
            sb.append(config.innerFdo);
            if (!config.innerFdo.endsWith("\n")) {
                sb.append("\n");
            }
        }
        sb.append("uni_end_stream\n");
        return sb.toString();
    }
}
