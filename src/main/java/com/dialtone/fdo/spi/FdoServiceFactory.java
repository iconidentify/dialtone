/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.spi;

import com.dialtone.fdo.spi.impl.NativeFdoCompilationService;
import com.dialtone.utils.LoggerUtil;

import java.util.Properties;

/**
 * Factory for creating FDO compilation services.
 *
 * <p>Uses the native atomforge-fdo Java library for compilation.
 * For data extraction from FDO binary, use {@link com.dialtone.fdo.FdoStreamExtractor}
 * which provides type-safe access via the FdoStream object model API.</p>
 */
public final class FdoServiceFactory {

    private static final String COMPILER_BACKEND_PROPERTY = "fdo.compiler.backend";
    private static final String DEFAULT_BACKEND = "java";

    private FdoServiceFactory() {
        // Utility class - prevent instantiation
    }

    /**
     * Create a compilation service based on configuration.
     *
     * @param properties configuration properties
     * @return configured FdoCompilationService
     * @throws IllegalArgumentException if backend name is unknown
     */
    public static FdoCompilationService createCompilationService(Properties properties) {
        String backend = properties.getProperty(COMPILER_BACKEND_PROPERTY, DEFAULT_BACKEND).toLowerCase().trim();

        LoggerUtil.info(String.format("[FdoServiceFactory] Creating compilation service with backend: %s", backend));

        return switch (backend) {
            case "java", "native" -> {
                int wireFrameLength = Integer.parseInt(
                    properties.getProperty("p3.max.frame.length", "194"));
                LoggerUtil.info(String.format(
                    "[FdoServiceFactory] Using native Java FDO compiler (atomforge-fdo library), frame length=%d",
                    wireFrameLength));
                yield new NativeFdoCompilationService(wireFrameLength);
            }
            default -> throw new IllegalArgumentException(
                String.format("Unknown FDO compiler backend: '%s'. Valid option: 'java'", backend)
            );
        };
    }

    /**
     * Get the configured backend name without creating a service.
     * Useful for logging and diagnostics.
     *
     * @param properties configuration properties
     * @return backend name
     */
    public static String getConfiguredBackend(Properties properties) {
        return properties.getProperty(COMPILER_BACKEND_PROPERTY, DEFAULT_BACKEND);
    }
}
