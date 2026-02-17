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
 * DSL builder for f2 IDB (Internal DataBase) response FDO.
 *
 * <p>Generates an FDO stream that delivers IDB data to the client.
 * Used for f2 DOD responses that carry art data via IDB protocol.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   uni_start_stream
 *     idb_start_obj &lt;"objType"&gt;
 *     idb_dod_progress_gauge &lt;"Please wait while we add new data to AOL."&gt;
 *     idb_atr_globalid &lt;atomGid&gt;
 *     idb_atr_length &lt;dataLength&gt;
 *     idb_atr_dod &lt;01x&gt;
 *     idb_append_data &lt;data&gt;
 *     idb_end_obj
 *   uni_end_stream
 * man_update_woff_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/f2_idb_response.fdo.txt}</p>
 */
public final class F2IdbResponseFdoBuilder implements FdoDslBuilder {

    private static final String GID = "f2_idb_response";
    private static final String DEFAULT_PROGRESS_MSG = "Please wait while we add new data to AOL.";

    /**
     * Configuration for f2 IDB response.
     *
     * @param objType The IDB object type (e.g., "p" for picture, "a" for atom)
     * @param atomGid The GID of the art/data being delivered
     * @param dataLength The length of the data in bytes
     * @param data The binary data
     */
    public record Config(
            String objType,
            FdoGid atomGid,
            int dataLength,
            byte[] data
    ) {
        public Config {
            if (objType == null) throw new IllegalArgumentException("objType cannot be null");
            if (atomGid == null) throw new IllegalArgumentException("atomGid cannot be null");
            if (data == null) throw new IllegalArgumentException("data cannot be null");
        }
    }

    private final Config config;

    /**
     * Create a new F2IdbResponse builder with the given configuration.
     *
     * @param config The IDB response configuration
     */
    public F2IdbResponseFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new F2IdbResponse builder with individual parameters.
     *
     * @param objType The IDB object type
     * @param atomGid The art GID
     * @param dataLength The data length
     * @param data The binary data
     */
    public F2IdbResponseFdoBuilder(String objType, FdoGid atomGid, int dataLength, byte[] data) {
        this(new Config(objType, atomGid, dataLength, data));
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
        return "f2 IDB art download response";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .uniStartStream()
                        .idb(IdbAtom.START_OBJ, config.objType)
                        .idb(IdbAtom.DOD_PROGRESS_GAUGE, DEFAULT_PROGRESS_MSG)
                        .idb(IdbAtom.ATR_GLOBALID, config.atomGid)
                        .idbAtrLength(config.dataLength)
                        .idb(IdbAtom.ATR_DOD, "01x")
                        .idb(IdbAtom.APPEND_DATA, config.data)
                        .idbEndObj()
                    .uniEndStream()
                .manUpdateWoffEndStream()
                .toSource();
    }
}
