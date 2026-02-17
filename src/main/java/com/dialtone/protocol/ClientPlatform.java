/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

/**
 * Represents the client platform (operating system) for connected AOL clients.
 * Used to track which platform users are connecting from and enable platform-specific features.
 */
public enum ClientPlatform {
    /** Mac OS (Classic Mac OS 7-9) */
    MAC,

    /** Windows (Windows 95/98/NT) */
    WINDOWS,

    /** Unknown or undetected platform */
    UNKNOWN;

    @Override
    public String toString() {
        return switch (this) {
            case MAC -> "Mac";
            case WINDOWS -> "Windows";
            case UNKNOWN -> "Unknown";
        };
    }
}
