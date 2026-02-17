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
import com.dialtone.fdo.dsl.ImageViewerConfig;
import com.dialtone.fdo.dsl.RenderingContext;

/**
 * DSL-based FDO builder for Image Viewer window.
 *
 * <p>Displays an image in a centered, non-modal window with tiled background.
 * Used by ImageViewerKeywordHandler.</p>
 *
 * <p>Structure:</p>
 * <pre>
 * IND_GROUP "{title}" (vcf, center_center, precise dimensions, background tile)
 * +-- ORNAMENT (positioned image with frame style 5)
 * +-- man_update_display
 * +-- uni_wait_off
 * </pre>
 */
public final class ImageViewerFdoBuilder implements FdoDslBuilder {

    private static final String GID = "image-viewer";
    private static final FdoGid BACKGROUND_ART = FdoGid.of(1, 69, 27256);
    private static final int FRAME_STYLE = 5;

    private final ImageViewerConfig config;

    /**
     * Create builder with configuration.
     *
     * @param config Image viewer configuration
     * @throws IllegalArgumentException if config is null
     */
    public ImageViewerFdoBuilder(ImageViewerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config cannot be null");
        }
        this.config = config;
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "Image viewer window with art display";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return FdoScript.stream()
                .stream("00x", s -> {
                    s.object(ObjectType.IND_GROUP, config.title(), root -> {
                        root.matOrientation(Orientation.VCF);
                        root.matPosition(Position.CENTER_CENTER);
                        root.mat(MatAtom.PRECISE_WIDTH, config.windowWidth());
                        root.mat(MatAtom.PRECISE_HEIGHT, config.windowHeight());
                        root.mat(MatAtom.BOOL_PRECISE, "yes");
                        root.mat(MatAtom.BOOL_MODAL, "no");
                        root.backgroundTile();
                        root.artId(BACKGROUND_ART);
                        root.mat(MatAtom.BOOL_BACKGROUND_PIC, "yes");
                        root.matTitle(config.title());

                        buildImageOrnament(root);
                    });
                    s.manUpdateDisplay();
                    s.uniWaitOff();
                })
                .toSource();
    }

    /**
     * Build the ornament object containing the image.
     */
    private void buildImageOrnament(ObjectBuilder root) {
        root.object(ObjectType.ORNAMENT, "", ornament -> {
            ornament.mat(MatAtom.PRECISE_X, config.imageX());
            ornament.mat(MatAtom.PRECISE_Y, config.imageY());
            ornament.mat(MatAtom.FRAME_STYLE, FRAME_STYLE);
            ornament.artId(parseArtId(config.artId()));
        });
    }

    /**
     * Parse art ID string to FdoGid.
     * Handles formats like "1-0-21029" or "32-5446".
     */
    private static FdoGid parseArtId(String artId) {
        String[] parts = artId.split("-");
        if (parts.length == 2) {
            return FdoGid.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } else if (parts.length == 3) {
            return FdoGid.of(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
            );
        } else {
            throw new IllegalArgumentException("Invalid art ID format: " + artId);
        }
    }

    /**
     * Factory method for creating builder with config.
     *
     * @param config Image viewer configuration
     * @return New builder instance
     */
    public static ImageViewerFdoBuilder forConfig(ImageViewerConfig config) {
        return new ImageViewerFdoBuilder(config);
    }
}
