/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.spi;

/**
 * Unified exception for FDO compilation failures.
 * Wraps both HTTP API errors and native library errors with consistent interface.
 */
public class FdoCompilationException extends Exception {

    private final String backendName;

    public FdoCompilationException(String message, String backendName) {
        super(message);
        this.backendName = backendName;
    }

    public FdoCompilationException(String message, String backendName, Throwable cause) {
        super(message, cause);
        this.backendName = backendName;
    }

    /**
     * Get the name of the backend that threw this exception.
     *
     * @return backend name ("http" or "java")
     */
    public String getBackendName() {
        return backendName;
    }

    @Override
    public String getMessage() {
        return String.format("[%s] %s", backendName, super.getMessage());
    }
}
