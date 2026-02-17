/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

/**
 * Core interface for type-safe FDO generation.
 *
 * <p>Builders generate FDO source that compiles to binary protocol data.
 * Unlike the legacy {@link FdoDslBuilder}, this interface does not require
 * GID or description metadata - those are only needed for registry-based lookup.</p>
 *
 * <h2>Usage Pattern</h2>
 * <pre>
 * // Static builder (singleton)
 * fdoProcessor.compileAndSend(ctx, NoopFdo.INSTANCE, session, "At", -1, "LABEL");
 *
 * // Dynamic builder with typed config
 * fdoProcessor.compileAndSend(ctx, ConfigureActiveUsernameFdo.forUser(username), session, "At", -1, "LABEL");
 * </pre>
 *
 * <h2>Why This Pattern?</h2>
 * <ul>
 *   <li>Compile-time type safety - wrong config is a compile error</li>
 *   <li>No string-based lookups or GID mismatches</li>
 *   <li>IDE refactoring support</li>
 *   <li>Self-documenting via Config records</li>
 * </ul>
 */
@FunctionalInterface
public interface FdoBuilder {

    /**
     * Generate FDO source text ready for compilation.
     *
     * @param ctx Rendering context with platform and display mode
     * @return FDO source text (never null)
     */
    String toSource(RenderingContext ctx);

    /**
     * Marker interface for static builders that require no runtime configuration.
     *
     * <p>Implementations should provide a singleton INSTANCE field:</p>
     * <pre>
     * public enum NoopFdo implements FdoBuilder.Static {
     *     INSTANCE;
     *
     *     {@literal @}Override
     *     public String toSource(RenderingContext ctx) { ... }
     * }
     * </pre>
     */
    interface Static extends FdoBuilder {
        // Marker interface - implementations provide INSTANCE singleton
    }

    /**
     * Marker interface for dynamic builders that require runtime configuration.
     *
     * <p>Implementations should accept configuration via constructor and provide
     * factory methods for common use cases:</p>
     * <pre>
     * public final class ConfigureActiveUsernameFdo implements FdoBuilder.Dynamic&lt;Config&gt; {
     *     public record Config(String username) { ... }
     *
     *     public static ConfigureActiveUsernameFdo forUser(String username) {
     *         return new ConfigureActiveUsernameFdo(new Config(username));
     *     }
     * }
     * </pre>
     *
     * @param <C> Configuration type (typically a record)
     */
    interface Dynamic<C> extends FdoBuilder {
        /**
         * Get the configuration used to create this builder.
         * Useful for debugging and logging.
         */
        C getConfig();
    }
}
