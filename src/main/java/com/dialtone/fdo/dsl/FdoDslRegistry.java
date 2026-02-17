/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl;

import com.dialtone.utils.LoggerUtil;

import java.util.Collection;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe singleton registry for FDO DSL builders.
 *
 * <p>Maps GID strings to their corresponding DSL builder implementations.
 * GID lookup is case-insensitive and whitespace-trimmed.</p>
 *
 * <p>This registry is checked before falling back to filesystem-based
 * .fdo.txt templates, allowing programmatic FDO generation with type-safe
 * DSL builders.</p>
 *
 * <p><b>Initialization Pattern:</b></p>
 * <pre>
 * // In DialtoneServer.initializeSharedServices():
 * FdoDslRegistry registry = FdoDslRegistry.getInstance();
 * registry.registerBuilder(new Gid69_420FdoBuilder());
 * registry.registerBuilder(new Gid32_117FdoBuilder());
 * </pre>
 *
 * <p><b>Lookup Pattern:</b></p>
 * <pre>
 * // In DodRequestHandler:
 * Optional&lt;FdoDslBuilder&gt; builder = registry.getBuilder(gid);
 * if (builder.isPresent()) {
 *     String fdoSource = builder.get().toSource(variables);
 *     // compile fdoSource...
 * } else {
 *     // fallback to filesystem template
 * }
 * </pre>
 *
 * @see FdoDslBuilder
 */
public class FdoDslRegistry {

    private static volatile FdoDslRegistry INSTANCE = null;

    /**
     * Storage for DSL builders.
     * Key: normalized GID (lowercase, trimmed)
     * Value: builder implementation
     */
    private final ConcurrentHashMap<String, FdoDslBuilder> builders;

    /**
     * Configuration properties for feature flags.
     * Keys:
     * - fdo.dsl.enabled: Global kill switch (default: true)
     * - fdo.dsl.builders.{gid}: Per-builder toggle (default: true)
     */
    private volatile Properties config;

    private FdoDslRegistry() {
        this.builders = new ConcurrentHashMap<>();
        this.config = new Properties();
        LoggerUtil.info("FdoDslRegistry initialized");
    }

    /**
     * Set configuration properties for feature flags.
     *
     * <p>Properties:
     * <ul>
     *   <li>{@code fdo.dsl.enabled} - Global kill switch (default: true)</li>
     *   <li>{@code fdo.dsl.builders.{gid}} - Per-builder toggle (default: true)</li>
     * </ul>
     *
     * <p>Example properties:
     * <pre>
     * fdo.dsl.enabled=true
     * fdo.dsl.builders.receive_im=false  # Disable receive_im builder
     * fdo.dsl.builders.welcome_screen=true
     * </pre>
     *
     * @param properties Configuration properties
     */
    public void setConfig(Properties properties) {
        if (properties != null) {
            this.config = properties;
            LoggerUtil.info("FdoDslRegistry config updated");
        }
    }

    /**
     * Check if DSL builders are globally enabled.
     *
     * @return true if global kill switch is on (default: true)
     */
    public boolean isGloballyEnabled() {
        return !"false".equalsIgnoreCase(config.getProperty("fdo.dsl.enabled", "true"));
    }

    /**
     * Check if a specific builder is enabled via feature flag.
     *
     * @param gid The GID to check
     * @return true if the builder is enabled (default: true)
     */
    public boolean isBuilderEnabled(String gid) {
        if (!isGloballyEnabled()) {
            return false;
        }
        // Convert GID to property key: "69-420" -> "fdo.dsl.builders.69_420"
        String key = "fdo.dsl.builders." + gid.replace("-", "_").toLowerCase();
        return !"false".equalsIgnoreCase(config.getProperty(key, "true"));
    }

    /**
     * Get the singleton instance.
     *
     * @return the singleton instance (never null)
     */
    public static FdoDslRegistry getInstance() {
        if (INSTANCE == null) {
            synchronized (FdoDslRegistry.class) {
                if (INSTANCE == null) {
                    INSTANCE = new FdoDslRegistry();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Registers a DSL builder.
     *
     * <p>The builder is registered under its GID (from {@link FdoDslBuilder#getGid()}).
     * If a builder for the same GID already exists, it will be replaced
     * and a warning will be logged.</p>
     *
     * @param builder the builder to register
     * @throws IllegalArgumentException if builder is null or GID is empty
     */
    public void registerBuilder(FdoDslBuilder builder) {
        if (builder == null) {
            throw new IllegalArgumentException("Cannot register null builder");
        }

        String gid = builder.getGid();
        if (gid == null || gid.trim().isEmpty()) {
            throw new IllegalArgumentException("Builder GID cannot be null or empty");
        }

        String normalizedGid = normalizeGid(gid);

        FdoDslBuilder existing = builders.put(normalizedGid, builder);

        if (existing != null) {
            LoggerUtil.warn(String.format(
                "Replaced existing DSL builder for GID '%s' (was: %s, now: %s)",
                gid, existing.getClass().getSimpleName(), builder.getClass().getSimpleName()));
        } else {
            LoggerUtil.info(String.format(
                "Registered FDO DSL builder: '%s' -> %s (%s)",
                gid, builder.getClass().getSimpleName(), builder.getDescription()));
        }
    }

    /**
     * Retrieves a builder for the given GID.
     *
     * <p>Lookup is case-insensitive and whitespace-trimmed.</p>
     *
     * <p>Feature flag behavior:
     * <ul>
     *   <li>If {@code fdo.dsl.enabled=false}, returns empty (global kill switch)</li>
     *   <li>If {@code fdo.dsl.builders.{gid}=false}, returns empty (per-builder disable)</li>
     *   <li>Otherwise returns the registered builder if present</li>
     * </ul>
     *
     * @param gid the GID to look up
     * @return Optional containing the builder if found and enabled, empty otherwise
     */
    public Optional<FdoDslBuilder> getBuilder(String gid) {
        if (gid == null || gid.trim().isEmpty()) {
            return Optional.empty();
        }

        // Check feature flags
        if (!isBuilderEnabled(gid)) {
            LoggerUtil.debug(() -> String.format(
                "DSL builder for GID '%s' is disabled by feature flag", gid));
            return Optional.empty();
        }

        return Optional.ofNullable(builders.get(normalizeGid(gid)));
    }

    /**
     * Retrieves a builder for the given GID, bypassing feature flags.
     *
     * <p>Use this method for testing or when you need to access a builder
     * regardless of its feature flag status.</p>
     *
     * @param gid the GID to look up
     * @return Optional containing the builder if found, empty otherwise
     */
    public Optional<FdoDslBuilder> getBuilderUnchecked(String gid) {
        if (gid == null || gid.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(builders.get(normalizeGid(gid)));
    }

    /**
     * Checks if a builder is registered for the given GID.
     *
     * @param gid the GID to check
     * @return true if a builder exists, false otherwise
     */
    public boolean hasBuilder(String gid) {
        return getBuilder(gid).isPresent();
    }

    /**
     * Unregisters a builder.
     *
     * @param gid the GID whose builder should be removed
     * @return true if a builder was removed, false if no builder existed
     */
    public boolean unregisterBuilder(String gid) {
        if (gid == null || gid.trim().isEmpty()) {
            return false;
        }

        FdoDslBuilder removed = builders.remove(normalizeGid(gid));

        if (removed != null) {
            LoggerUtil.info(String.format(
                "Unregistered FDO DSL builder: '%s' (%s)",
                gid, removed.getClass().getSimpleName()));
            return true;
        }

        return false;
    }

    /**
     * Returns all registered builders.
     *
     * <p>The returned collection is a snapshot and will not reflect subsequent
     * registry changes.</p>
     *
     * @return collection of all registered builders
     */
    public Collection<FdoDslBuilder> getAllBuilders() {
        return builders.values();
    }

    /**
     * Returns the number of registered builders.
     *
     * @return builder count
     */
    public int getBuilderCount() {
        return builders.size();
    }

    /**
     * Checks if the registry is empty (no builders registered).
     *
     * @return true if no builders are registered, false otherwise
     */
    public boolean isEmpty() {
        return builders.isEmpty();
    }

    /**
     * Clears all registered builders.
     *
     * <p>This is primarily useful for testing. Production code should rarely
     * need to clear the registry.</p>
     */
    public void clear() {
        int count = builders.size();
        builders.clear();
        LoggerUtil.info("Cleared " + count + " FDO DSL builder(s) from registry");
    }

    /**
     * Normalizes a GID for consistent lookup.
     *
     * <p>Normalization: lowercase + trim whitespace</p>
     *
     * @param gid the raw GID
     * @return normalized GID
     */
    private String normalizeGid(String gid) {
        return gid.trim().toLowerCase();
    }

    /**
     * Resets the singleton instance.
     *
     * <p><b>WARNING:</b> This is for testing only. Do not call in production code.</p>
     */
    public static synchronized void resetInstance() {
        INSTANCE = null;
    }
}
