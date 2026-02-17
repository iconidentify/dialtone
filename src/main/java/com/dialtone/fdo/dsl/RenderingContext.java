/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

import com.dialtone.protocol.ClientPlatform;

import java.util.Objects;

/**
 * Context for FDO rendering that encapsulates platform and display mode.
 *
 * <p>Used by {@link FdoDslBuilder} implementations to generate platform-specific
 * and color-mode-specific FDO variants from a single builder method.</p>
 *
 * <p>Example usage in a builder:</p>
 * <pre>
 * public String toSource(RenderingContext ctx) {
 *     int width = ctx.isLowColorMode() ? 63 : 86;
 *     if (ctx.isMac()) {
 *         // Mac-specific rendering
 *     }
 *     // ...
 * }
 * </pre>
 */
public final class RenderingContext {

    /**
     * Default context: unknown platform, color mode.
     * Use when platform is not detected or not relevant.
     */
    public static final RenderingContext DEFAULT =
            new RenderingContext(ClientPlatform.UNKNOWN, false);

    private final ClientPlatform platform;
    private final boolean lowColorMode;

    /**
     * Create a rendering context.
     *
     * @param platform Client platform (MAC, WINDOWS, UNKNOWN); null treated as UNKNOWN
     * @param lowColorMode true for black-and-white/low-color mode
     */
    public RenderingContext(ClientPlatform platform, boolean lowColorMode) {
        this.platform = platform != null ? platform : ClientPlatform.UNKNOWN;
        this.lowColorMode = lowColorMode;
    }

    /**
     * Get the client platform.
     *
     * @return platform (never null, defaults to UNKNOWN)
     */
    public ClientPlatform getPlatform() {
        return platform;
    }

    /**
     * Check if low color (black-and-white) mode is enabled.
     *
     * @return true if rendering for low color mode
     */
    public boolean isLowColorMode() {
        return lowColorMode;
    }

    /**
     * Check if rendering for Mac platform.
     *
     * @return true if platform is MAC
     */
    public boolean isMac() {
        return platform == ClientPlatform.MAC;
    }

    /**
     * Check if rendering for Windows platform.
     *
     * @return true if platform is WINDOWS
     */
    public boolean isWindows() {
        return platform == ClientPlatform.WINDOWS;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RenderingContext that = (RenderingContext) o;
        return lowColorMode == that.lowColorMode && platform == that.platform;
    }

    @Override
    public int hashCode() {
        return Objects.hash(platform, lowColorMode);
    }

    @Override
    public String toString() {
        return String.format("RenderingContext{platform=%s, lowColorMode=%s}", platform, lowColorMode);
    }
}
