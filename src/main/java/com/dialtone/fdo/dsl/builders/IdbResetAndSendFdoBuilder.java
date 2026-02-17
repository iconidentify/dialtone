/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.IdbAtom;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for IDB reset and send operation.
 *
 * <p>Generates an FDO stream that first deletes an existing IDB object,
 * then creates a new one with fresh data. Used when updating cached art
 * or data that needs to be replaced completely.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   idb_delete_obj &lt;atomGid&gt;
 *   uni_start_stream
 *     idb_start_obj &lt;"objType"&gt;
 *     idb_dod_progress_gauge &lt;"Please wait while we add new data to AOL."&gt;
 *     idb_atr_globalid &lt;atomGid&gt;
 *     idb_atr_length &lt;dataLength&gt;
 *     idb_atr_dod &lt;01x&gt;
 *     idb_atr_offset &lt;0&gt;
 *     idb_append_data &lt;data&gt;
 *     idb_end_obj
 *   uni_end_stream
 * man_update_woff_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/idb_reset_and_send.fdo.txt}</p>
 */
public final class IdbResetAndSendFdoBuilder implements FdoDslBuilder, FdoBuilder.Dynamic<IdbResetAndSendFdoBuilder.Config> {

    private static final String GID = "idb_reset_and_send";
    private static final String DEFAULT_PROGRESS_MSG = "Please wait while we add new data to AOL.";

    /**
     * Configuration for IDB reset and send.
     *
     * @param objType    The IDB object type (e.g., "p" for picture, "a" for atom)
     * @param atomGid    The GID of the art/data being delivered
     * @param dataLength The length of the data in bytes
     * @param data       The binary data
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
     * Create a new IdbResetAndSend builder with the given configuration.
     *
     * @param config The IDB reset configuration
     */
    public IdbResetAndSendFdoBuilder(Config config) {
        if (config == null) throw new IllegalArgumentException("config cannot be null");
        this.config = config;
    }

    /**
     * Create a new IdbResetAndSend builder with individual parameters.
     *
     * @param objType    The IDB object type
     * @param atomGid    The art GID
     * @param dataLength The data length
     * @param data       The binary data
     */
    public IdbResetAndSendFdoBuilder(String objType, FdoGid atomGid, int dataLength, byte[] data) {
        this(new Config(objType, atomGid, dataLength, data));
    }

    /**
     * Factory method for creating an IDB reset and send operation.
     *
     * @param objType    The IDB object type
     * @param atomGid    The art GID
     * @param data       The binary data
     * @return new builder instance
     */
    public static IdbResetAndSendFdoBuilder reset(String objType, FdoGid atomGid, byte[] data) {
        return new IdbResetAndSendFdoBuilder(objType, atomGid, data.length, data);
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
        return "IDB delete and recreate with new data";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .uniStartStream()
                    .idb(IdbAtom.DELETE_OBJ, config.atomGid)
                    .uniStartStream()
                        .idb(IdbAtom.START_OBJ, config.objType)
                        .idb(IdbAtom.DOD_PROGRESS_GAUGE, DEFAULT_PROGRESS_MSG)
                        .idb(IdbAtom.ATR_GLOBALID, config.atomGid)
                        .idbAtrLength(config.dataLength)
                        .idb(IdbAtom.ATR_DOD, "01x")
                        .idb(IdbAtom.ATR_OFFSET, 0)
                        .idb(IdbAtom.APPEND_DATA, config.data)
                        .idbEndObj()
                    .uniEndStream()
                .manUpdateWoffEndStream()
                .toSource();
    }
}
