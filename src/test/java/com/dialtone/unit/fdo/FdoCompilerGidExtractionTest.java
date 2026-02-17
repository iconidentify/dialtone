/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo;

import com.dialtone.fdo.FdoCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FdoCompiler GID Extraction")
class FdoCompilerGidExtractionTest {

    @Test
    @DisplayName("Should extract GID from standard fdo/ path")
    void shouldExtractGidFromStandardFdoPath() {
        String gid = FdoCompiler.extractGidFromPath("fdo/noop.fdo.txt");
        assertEquals("noop", gid);
    }

    @Test
    @DisplayName("Should extract GID from nested fdo/ path")
    void shouldExtractGidFromNestedFdoPath() {
        String gid = FdoCompiler.extractGidFromPath("fdo/post_login/username_config.fdo.txt");
        assertEquals("post_login/username_config", gid);
    }

    @Test
    @DisplayName("Should extract GID from replace_client_fdo/ path")
    void shouldExtractGidFromReplaceClientFdoPath() {
        String gid = FdoCompiler.extractGidFromPath("replace_client_fdo/69-420.fdo.txt");
        assertEquals("69-420", gid);
    }

    @Test
    @DisplayName("Should extract GID from BW variant path")
    void shouldExtractGidFromBwVariantPath() {
        String gid = FdoCompiler.extractGidFromPath("fdo/receive_im.bw.fdo.txt");
        assertEquals("receive_im", gid);
    }

    @Test
    @DisplayName("Should handle path without .fdo.txt suffix")
    void shouldHandlePathWithoutFdoTxtSuffix() {
        String gid = FdoCompiler.extractGidFromPath("fdo/noop");
        assertEquals("noop", gid);
    }

    @Test
    @DisplayName("Should handle path without prefix")
    void shouldHandlePathWithoutPrefix() {
        String gid = FdoCompiler.extractGidFromPath("noop.fdo.txt");
        assertEquals("noop", gid);
    }

    @Test
    @DisplayName("Should return null for null path")
    void shouldReturnNullForNullPath() {
        String gid = FdoCompiler.extractGidFromPath(null);
        assertNull(gid);
    }

    @Test
    @DisplayName("Should return empty string for empty path")
    void shouldReturnEmptyStringForEmptyPath() {
        String gid = FdoCompiler.extractGidFromPath("");
        assertNotNull(gid);
        assertEquals("", gid);
    }

    @Test
    @DisplayName("Should handle complex nested path")
    void shouldHandleComplexNestedPath() {
        String gid = FdoCompiler.extractGidFromPath("fdo/post_login/configure_active_username.fdo.txt");
        assertEquals("post_login/configure_active_username", gid);
    }
}

