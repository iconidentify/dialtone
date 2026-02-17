/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.DodAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for DOD (Download On Demand) response FDO.
 *
 * <p>Generates an FDO stream that delivers downloaded art data to the client.
 * This is the primary DOD response template used when serving art assets.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   dod_start
 *     uni_transaction_id &lt;transactionId&gt;
 *     dod_no_hints
 *     dod_form_id &lt;formId&gt;
 *     dod_gid &lt;gid&gt;
 *     dod_type &lt;2&gt;
 *     dod_data &lt;data&gt;
 *     dod_end_data
 *     dod_close_form
 *   dod_end
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/dod_response.fdo.txt}</p>
 */
public final class DodResponseFdoBuilder implements FdoDslBuilder {

    private static final String GID = "dod_response";

    /**
     * Configuration for DOD response.
     *
     * @param transactionId The transaction ID from the original request
     * @param formId The form ID for the response
     * @param gid The GID of the art being delivered
     * @param data The binary art data
     */
    public record Config(
            int transactionId,
            String formId,
            FdoGid gid,
            byte[] data
    ) {
        public Config {
            if (formId == null) throw new IllegalArgumentException("formId cannot be null");
            if (gid == null) throw new IllegalArgumentException("gid cannot be null");
            if (data == null) throw new IllegalArgumentException("data cannot be null");
        }
    }

    private final Config config;

    /**
     * Create a new DodResponse builder with the given configuration.
     *
     * @param config The DOD response configuration
     */
    public DodResponseFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new DodResponse builder with individual parameters.
     *
     * @param transactionId The transaction ID
     * @param formId The form ID
     * @param gid The art GID
     * @param data The binary data
     */
    public DodResponseFdoBuilder(int transactionId, String formId, FdoGid gid, byte[] data) {
        this(new Config(transactionId, formId, gid, data));
    }

    /**
     * Get the configuration used by this builder.
     *
     * @return the configuration
     */
    public Config getConfig() {
        return config;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "DOD art download response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.atom(DodAtom.START);
                    s.uniTransactionId(config.transactionId);
                    s.atom(DodAtom.NO_HINTS);
                    s.atom(DodAtom.FORM_ID, config.formId);
                    s.atom(DodAtom.GID, config.gid);
                    s.atom(DodAtom.TYPE, 2);
                    s.rawData(DodAtom.DATA, config.data);
                    s.atom(DodAtom.END_DATA);
                    s.atom(DodAtom.CLOSE_FORM);
                    s.atom(DodAtom.END);
                })
                .toSource();
    }
}
