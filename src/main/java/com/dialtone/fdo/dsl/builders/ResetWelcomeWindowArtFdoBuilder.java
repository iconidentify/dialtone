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
 * DSL builder for resetting welcome window art objects.
 *
 * <p>Generates an FDO stream that deletes cached IDB objects for the welcome
 * window art, forcing a refresh. This is called during the post-login sequence.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   idb_delete_obj &lt;32-117&gt;
 *   idb_delete_obj &lt;32-5447&gt;
 *   idb_delete_obj &lt;32-168&gt;
 *   idb_delete_obj &lt;32-225&gt;
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code fdo/post_login/reset_welcome_window_art.fdo.txt}</p>
 */
public final class ResetWelcomeWindowArtFdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "post_login/reset_welcome_window_art";

    // Welcome window art GIDs to delete
    private static final FdoGid[] ART_GIDS = {
        FdoGid.of(32, 117),
        FdoGid.of(32, 5447),
        FdoGid.of(32, 168),
        FdoGid.of(32, 225)
    };

    /**
     * Singleton instance - no configuration needed.
     */
    public static final ResetWelcomeWindowArtFdoBuilder INSTANCE = new ResetWelcomeWindowArtFdoBuilder();

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Reset welcome window art cache";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        var stream = FdoScript.stream().uniStartStream();
        for (FdoGid gid : ART_GIDS) {
            stream.idb(IdbAtom.DELETE_OBJ, gid);
        }
        return stream.uniEndStream().toSource();
    }
}
