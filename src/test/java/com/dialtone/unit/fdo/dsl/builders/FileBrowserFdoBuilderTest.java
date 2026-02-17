/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.FileBrowserFdoBuilder;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.filebrowser.FileBrowserService.FileEntry;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileBrowserFdoBuilder.
 */
class FileBrowserFdoBuilderTest {

    @TempDir
    Path tempDir;

    private FileBrowserService service;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(tempDir.resolve("test.txt"), "content");
        Files.createDirectories(tempDir.resolve("subdir"));
        service = new FileBrowserService(tempDir, 10);
    }

    // ==================== FDO Generation Tests ====================

    @Test
    void toSource_generatesValidFdo() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        assertNotNull(fdo);
        assertTrue(fdo.contains("uni_start_stream"));
        assertTrue(fdo.contains("uni_end_stream"));
        assertTrue(fdo.contains("File Browser"));
    }

    @Test
    void toSource_containsPathDisplay() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Path is shown in window title
        assertTrue(fdo.contains("File Browser - /"));
    }

    @Test
    void toSource_containsFileEntries() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Should contain file/folder payload markers (title IS payload)
        assertTrue(fdo.contains("D:") || fdo.contains("F:"));
    }

    @Test
    void toSource_subdirectory_containsBackButton() {
        // Subdirectory should have Back button
        BrowseResult result = service.browse("/subdir", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        assertTrue(fdo.contains("Back"));
    }

    @Test
    void toSource_containsItemCount() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Shows item count at bottom
        assertTrue(fdo.contains("items"));
    }

    @Test
    void toSource_emptyDirectory_showsMessage() {
        BrowseResult result = service.browse("/subdir", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Shows "(empty)" for empty directories
        assertTrue(fdo.contains("(empty)"));
    }

    // ==================== FDO Compilation Tests ====================

    @Test
    void toSource_compilesWithoutErrors() throws Exception {
        // This test catches atom name errors like "Unknown atom: mat_data"
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        FdoCompiler compiler = new FdoCompiler(new java.util.Properties());
        // This will throw if there are unknown atoms
        byte[] compiled = compiler.compileFdoScript(fdo);
        assertNotNull(compiled);
        assertTrue(compiled.length > 0, "Compiled FDO should not be empty");
    }

    @Test
    void toSource_subdirectory_compilesWithoutErrors() throws Exception {
        // Test subdirectory with Back button
        BrowseResult result = service.browse("/subdir", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        FdoCompiler compiler = new FdoCompiler(new java.util.Properties());
        byte[] compiled = compiler.compileFdoScript(fdo);
        assertNotNull(compiled);
        assertTrue(compiled.length > 0);
    }

    // ==================== Navigation Button Tests ====================

    @Test
    void toSource_rootDirectory_noBackButton() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Count occurrences of "Back" - should only appear in "File Browser" if at all
        int backCount = countOccurrences(fdo, "\"Back\"");
        assertEquals(0, backCount, "Root directory should not have Back button");
    }

    // ==================== List Item Title/Payload Format Tests ====================

    @Test
    void toSource_directoryEntry_hasTitlePayload() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Directory entries should have title with "D:" prefix (title IS the payload)
        assertTrue(fdo.contains("mat_title <\"D:subdir\">"), "Directory should have D: prefix in title");
    }

    @Test
    void toSource_fileEntry_hasTitlePayload() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // File entries should have title with "F:" prefix (title IS the payload)
        assertTrue(fdo.contains("mat_title <\"F:test.txt\">"), "File should have F: prefix in title");
    }

    @Test
    void toSource_usesInheritancePattern() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(RenderingContext.DEFAULT);

        // Items should inherit action from parent list
        assertTrue(fdo.contains("act_set_inheritance <02x>"), "Items should inherit action");
        // List should have the action with de_get_data_pointer
        assertTrue(fdo.contains("de_get_data_pointer"), "List should use de_get_data_pointer");
        // Action should be act_append_action, not act_replace_action
        assertTrue(fdo.contains("act_append_action"), "Should use act_append_action");
    }

    // ==================== Rendering Context Tests ====================

    @Test
    void toSource_colorMode_includesColors() {
        // Use subdirectory to get Back button with colors
        BrowseResult result = service.browse("/subdir", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String colorFdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false));

        // Back button has color attributes
        assertTrue(colorFdo.contains("mat_color"));
    }

    @Test
    void toSource_bwMode_generatesValidFdo() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String bwFdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, true));

        // BW mode should still generate valid FDO
        // (BW-specific color handling can be added later if needed)
        assertNotNull(bwFdo);
        assertTrue(bwFdo.contains("uni_start_stream"));
        assertTrue(bwFdo.contains("dss_list"));
    }

    // ==================== Builder Metadata Tests ====================

    @Test
    void getGid_returnsExpectedValue() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        assertEquals("file_browser", builder.getGid());
    }

    @Test
    void getDescription_returnsNonEmpty() {
        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        assertNotNull(builder.getDescription());
        assertFalse(builder.getDescription().isEmpty());
    }

    // ==================== Constructor Validation Tests ====================

    @Test
    void constructor_nullResult_throws() {
        assertThrows(IllegalArgumentException.class, () ->
            new FileBrowserFdoBuilder(null, service));
    }

    @Test
    void constructor_nullService_throws() {
        BrowseResult result = service.browse("/", 1);
        assertThrows(IllegalArgumentException.class, () ->
            new FileBrowserFdoBuilder(result, null));
    }

    // ==================== Helper Methods ====================

    private int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }
}
