/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt.fdo;

import com.atomforge.fdo.dsl.FdoScript;
import com.atomforge.fdo.dsl.values.Criterion;
import com.atomforge.fdo.model.FdoGid;
import com.dialtone.terminal.AnsiColorStripper;

/**
 * FDO builder for Skalholt MAP window operations.
 *
 * <p>Generates FDO streams for:
 * <ul>
 *   <li>Opening the map window (GID 69-421)</li>
 *   <li>Updating map content when DRAW_MAP events arrive</li>
 * </ul>
 *
 * <p>The map VIEW uses relative_tag 256, same convention as the main Skalholt form.</p>
 */
public final class SkalholtMapFdoBuilder {

    private static final FdoGid MAP_FORM_GID = FdoGid.of(69, 421);
    private static final String WINDOW_TITLE = "Skalholt Map";

    /**
     * Generates FDO to open the map window.
     *
     * <p>Opens GID 69-421 with:
     * <ul>
     *   <li>Title "Skalholt Map"</li>
     *   <li>Close action sends "MC" token to notify server</li>
     * </ul>
     *
     * @return FDO source string
     */
    public String openMapWindow() {
        return FdoScript.stream()
                .uniStartStream()
                .manPresetGid(MAP_FORM_GID)
                .ifLastReturnFalseThen(1, 0)
                .uniStartStream("01x")
                .uniInvokeNoContext(MAP_FORM_GID)
                .manSetContextGlobalId(MAP_FORM_GID)
                .manSetContextRelative(256)    // Target the VIEW control
                .manReplaceData("Map is loading...")    // Replace all content
                .manEndContext()
                .actSetCriterion(Criterion.CLOSE)
                .actReplaceAction(nested -> {
                    nested.uniStartStream()
                            .manClose(MAP_FORM_GID)
                            .smSendTokenRaw("MC")  // Map Close token
                            .uniEndStream();
                })
                .matTitle(WINDOW_TITLE)
                .uniEndStream("01x")
                .uniSyncSkip(1)
                .manUpdateDisplay()
                .manMakeFocus()
                .uniWaitOffEndStream()
                .toSource();
    }

    /**
     * Generates FDO to update the map content in an open window.
     *
     * <p>Uses client-side conditional logic: checks if window exists via
     * man_preset_gid, then skips the update if window is closed. This
     * eliminates the need for server-side state tracking.</p>
     *
     * <p>Targets the VIEW control (relative_tag 256) and replaces all content
     * with the new map data.</p>
     *
     * @param mapData the ASCII map data (with newlines)
     * @return FDO source string
     */
    public String updateMapContent(String mapData) {
        // Strip ANSI escape codes (colors, formatting) - AOL client can't render them
        String cleanedData = AnsiColorStripper.stripAnsiCodes(mapData);

        // Normalize line endings: remove CR, then convert LF to AOL protocol newline (0x7F DEL)
        String aolMapData = cleanedData.replace("\r", "").replace("\n", "\u007F");

        return FdoScript.stream()
                .uniStartStream()
                .manPresetGid(MAP_FORM_GID)
               // Label 1: window is open, proceed
                .uniStartStream("01x")
                .manSetContextGlobalId(MAP_FORM_GID)
                .manSetContextRelative(256)    // Target the VIEW control
                .manReplaceData(aolMapData)    // Replace all content
                .manEndContext()
                .manUpdateDisplay()
                .uniEndStream("01x")// Label 0: end (skip here if closed)
                .uniWaitOffEndStream()
                .toSource();
    }
}
