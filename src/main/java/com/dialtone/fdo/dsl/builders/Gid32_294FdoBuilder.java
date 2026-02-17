/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.ObjectType;
import com.atomforge.fdo.dsl.values.Orientation;
import com.atomforge.fdo.dsl.values.Position;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL builder for GID 32-294 (Network News window).
 *
 * <p>Simple news display window with a view for content.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * uni_start_stream
 *   IND_GROUP "Network News" (object_id 32-294, top_left)
 *     +-- VIEW (tag 1, writeable, capacity 1024)
 *   man_update_display
 * uni_end_stream
 * </pre>
 *
 * <p>Replaces: {@code replace_client_fdo/32-294.fdo.txt}</p>
 */
public final class Gid32_294FdoBuilder implements FdoDslBuilder, FdoBuilder.Static {

    private static final String GID = "32-294";
    private static final FdoGid OBJECT_ID = FdoGid.of(32, 294);

    /**
     * Singleton instance.
     */
    public static final Gid32_294FdoBuilder INSTANCE = new Gid32_294FdoBuilder();

    private Gid32_294FdoBuilder() {
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Network News display window";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, "Network News", root -> {
                        root.matObjectId(OBJECT_ID);
                        root.matOrientation(Orientation.VFF);
                        root.matPosition(Position.TOP_LEFT);

                        root.object(ObjectType.VIEW, "", view -> {
                            view.matHeight(2);
                            view.matCapacity(1024);
                            view.matRelativeTag(1);
                            view.mat(MatAtom.BOOL_WRITEABLE, "yes");
                        });

                        root.manUpdateDisplay();
                    });
                })
                .toSource();
    }
}
