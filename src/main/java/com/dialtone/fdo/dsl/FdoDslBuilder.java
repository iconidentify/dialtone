/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

import com.dialtone.protocol.ClientPlatform;

import java.util.Map;

/**
 * Interface for DSL-based FDO generation.
 *
 * <p>Implementations use the atomforge-fdo-java DSL API to programmatically
 * build FDO structures. This provides type-safety and IDE support compared
 * to text-based templates.</p>
 *
 * <p>DSL builders are registered with {@link FdoDslRegistry} by GID, allowing
 * the DOD request handler to look up DSL implementations before falling back
 * to filesystem-based .fdo.txt templates.</p>
 *
 * <p>Example implementation:</p>
 * <pre>
 * public class Gid69_420FdoBuilder implements FdoDslBuilder {
 *
 *     public String getGid() {
 *         return "69-420";
 *     }
 *
 *     public String getDescription() {
 *         return "Chat input form with view and edit areas";
 *     }
 *
 *     public String toSource(RenderingContext ctx) {
 *         int width = ctx.isLowColorMode() ? 63 : 86;
 *         return FdoScript.stream()
 *             .stream("00x", s -&gt; {
 *                 // ... build FDO structure with platform/mode-specific logic
 *             })
 *             .toSource();
 *     }
 * }
 * </pre>
 *
 * @see FdoDslRegistry
 * @see RenderingContext
 */
public interface FdoDslBuilder {

    /**
     * Get the GID this builder handles.
     *
     * <p>The GID should be in display format (e.g., "69-420", "32-117").
     * This is used as the registry key for lookup.</p>
     *
     * @return GID in display format (never null or empty)
     */
    String getGid();

    /**
     * Get human-readable description for logging.
     *
     * @return Description of what this FDO provides
     */
    String getDescription();

    /**
     * Generate FDO source using the DSL with rendering context.
     *
     * <p>The context provides platform (Mac/Windows) and display mode
     * (color/BW) information. Implementations should use helper methods
     * to isolate platform-specific differences.</p>
     *
     * <p>The returned source should be valid FDO text that can be compiled
     * by FdoCompiler. Typically this is achieved by calling
     * {@code StreamBuilder.toSource()} at the end of the builder chain.</p>
     *
     * @param context Rendering context with platform and color mode (never null)
     * @return FDO source text ready for compilation (never null)
     */
    String toSource(RenderingContext context);

    // -------------------------------------------------------------------------
    // Deprecated methods - kept for backward compatibility during migration
    // -------------------------------------------------------------------------

    /**
     * Generate FDO source using the DSL.
     *
     * @param variables Template variables (ignored - relic from text templating)
     * @return FDO source text ready for compilation (never null)
     * @deprecated Use {@link #toSource(RenderingContext)} instead. Variables
     *             are not used in DSL-based builders.
     */
    @Deprecated
    default String toSource(Map<String, Object> variables) {
        return toSource(RenderingContext.DEFAULT);
    }

    /**
     * Check if this builder supports black-and-white (low color) mode.
     *
     * @return true if a BW variant is available
     * @deprecated Use {@link RenderingContext#isLowColorMode()} in
     *             {@link #toSource(RenderingContext)} instead.
     */
    @Deprecated
    default boolean hasBwVariant() {
        return false;
    }

    /**
     * Generate FDO source for black-and-white (low color) mode.
     *
     * @param variables Template variables (ignored)
     * @return FDO source text for BW mode (never null)
     * @deprecated Use {@link #toSource(RenderingContext)} with
     *             {@code new RenderingContext(platform, true)} instead.
     */
    @Deprecated
    default String toSourceBw(Map<String, Object> variables) {
        return toSource(new RenderingContext(ClientPlatform.UNKNOWN, true));
    }
}
