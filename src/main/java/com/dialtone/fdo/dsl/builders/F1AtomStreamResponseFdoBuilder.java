/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.IdbAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for f1 atom stream response FDO.
 *
 * <p>Generates an FDO stream that delivers f1 atom stream data to the client.
 * This includes the IDB data plus context setting and local invocation.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_start_stream
 *     idb_start_obj &lt;"a"&gt;
 *     idb_dod_progress_gauge &lt;"Please wait while we add new data to AOL."&gt;
 *     idb_atr_globalid &lt;atomGid&gt;
 *     idb_atr_length &lt;dataLength&gt;
 *     idb_atr_dod &lt;01x&gt;
 *     idb_append_data &lt;data&gt;
 *     idb_end_obj
 *     idb_set_context &lt;atomGid&gt;
 *     idb_atr_dod &lt;00x&gt;
 *     idb_end_context
 *   uni_end_stream
 *   uni_invoke_local &lt;atomGid&gt;
 * man_update_woff_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/f1_atom_stream_response.fdo.txt}</p>
 */
public final class F1AtomStreamResponseFdoBuilder implements FdoDslBuilder {

    private static final String GID = "f1_atom_stream_response";
    private static final String DEFAULT_PROGRESS_MSG = "Please wait while we add new data to AOL.";
    private static final String OBJ_TYPE = "a"; // atom type

    /**
     * Configuration for f1 atom stream response.
     *
     * @param atomGid The GID of the art/data being delivered
     * @param dataLength The length of the data in bytes
     * @param data The binary data
     */
    public record Config(
            FdoGid atomGid,
            int dataLength,
            byte[] data
    ) {
        public Config {
            if (atomGid == null) throw new IllegalArgumentException("atomGid cannot be null");
            if (data == null) throw new IllegalArgumentException("data cannot be null");
        }
    }

    private final Config config;

    /**
     * Create a new F1AtomStreamResponse builder with the given configuration.
     *
     * @param config The atom stream response configuration
     */
    public F1AtomStreamResponseFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new F1AtomStreamResponse builder with individual parameters.
     *
     * @param atomGid The art GID
     * @param dataLength The data length
     * @param data The binary data
     */
    public F1AtomStreamResponseFdoBuilder(FdoGid atomGid, int dataLength, byte[] data) {
        this(new Config(atomGid, dataLength, data));
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
        return "f1 atom stream response with IDB data and local invocation";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .uniStartStream()
                        .idb(IdbAtom.START_OBJ, OBJ_TYPE)
                        .idb(IdbAtom.DOD_PROGRESS_GAUGE, DEFAULT_PROGRESS_MSG)
                        .idb(IdbAtom.ATR_GLOBALID, config.atomGid)
                        .idbAtrLength(config.dataLength)
                        .idb(IdbAtom.ATR_DOD, "01x")
                        .idb(IdbAtom.APPEND_DATA, config.data)
                        .idbEndObj()
                        .idbSetContext(config.atomGid)
                        .idb(IdbAtom.ATR_DOD, "00x")
                        .idbEndContext()
                    .uniEndStream()
                    .uniInvokeLocal(config.atomGid)
                .manUpdateWoffEndStream()
                .toSource();
    }
}
