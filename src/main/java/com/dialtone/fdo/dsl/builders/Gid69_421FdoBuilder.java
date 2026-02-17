/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.ObjectBuilder;
import com.atomforge.fdo.dsl.atoms.MatAtom;
import com.atomforge.fdo.dsl.values.*;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL-based FDO builder for GID 69-421.
 *
 * <p>Skalholt MAP window - displays ASCII map from Creeper MUD DRAW_MAP events.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * IND_GROUP (root)
 * +-- mat_object_id 69-421
 * +-- mat_orientation VFF
 * +-- mat_position CENTER_CENTER
 * +-- ORG_GROUP (content)
 *     +-- mat_orientation VLT
 *     +-- VIEW (map display, relative_tag 256)
 *         +-- Black background, white text
 *         +-- Courier New monospace font
 * </pre>
 *
 * <p>The VIEW control uses relative_tag 256 to allow map content updates via
 * manSetContextRelative(256) + manReplaceData.</p>
 */
public final class Gid69_421FdoBuilder implements FdoDslBuilder {

    private static final String GID = "69-421";

    // Map view dimensions - sized for ~11 lines of ASCII map
    private static final int VIEW_WIDTH = 42;
    private static final int VIEW_HEIGHT = 13;
    private static final int VIEW_MAX_SIZE = 2048;

    // Font configuration
    private static final int FONT_SIZE = 8;

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Skalholt MAP window - displays ASCII map from Creeper MUD";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, "", root -> {
                        root.matObjectId(FdoGid.of(69, 421));
                        root.matOrientation(Orientation.VFF);
                        root.matPosition(Position.CENTER_CENTER);

                        root.object(ObjectType.ORG_GROUP, "", content -> {
                            content.matOrientation(Orientation.VLT);
                            buildMapView(content, ctx);
                        });

                        root.manUpdateDisplay();
                    });
                })
                .toSource();
    }

    /**
     * Buact
     * ild the map VIEW control with monospace font and dark theme.
     *
     * <p>Uses relative_tag 256 for content updates via manSetContextRelative.</p>
     */
    private void buildMapView(ObjectBuilder content, RenderingContext ctx) {
        content.object(ObjectType.VIEW, "", view -> {
            // Dimensions - slightly smaller on Mac
            int width = ctx.isMac() ? VIEW_WIDTH - 14 : VIEW_WIDTH;
            int height = ctx.isMac() ? VIEW_HEIGHT - 4 : VIEW_HEIGHT;
            view.matBoolForceNoScroll();
            if (ctx.isMac()) {
                view.matBoolBackgroundFlood(true);
                view.mat(MatAtom.COLOR_FACE, 0, 0, 0);         // Black background
                view.mat(MatAtom.COLOR_TEXT, 255, 255, 255);   // White text
            }
            view.matSize(width, height, VIEW_MAX_SIZE);
            view.matRelativeTag(256);                       // For content updates
            view.matFontSis(FontId.COURIER_NEW, FONT_SIZE, FontStyle.NORMAL);
            view.matBoolWriteable();
            view.matParagraph(1);
        });
    }
}
