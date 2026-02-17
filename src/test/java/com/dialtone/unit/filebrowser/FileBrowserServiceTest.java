/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.filebrowser;

import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.filebrowser.FileBrowserService.BrowseState;
import com.dialtone.filebrowser.FileBrowserService.FileEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FileBrowserService.
 */
class FileBrowserServiceTest {

    @TempDir
    Path tempDir;

    private FileBrowserService service;

    @BeforeEach
    void setUp() throws IOException {
        // Create test directory structure:
        // tempDir/
        //   file1.txt
        //   file2.bin
        //   subdir1/
        //     nested.txt
        //   subdir2/

        Files.writeString(tempDir.resolve("file1.txt"), "Hello World");
        Files.writeString(tempDir.resolve("file2.bin"), "Binary data");
        Files.createDirectories(tempDir.resolve("subdir1"));
        Files.writeString(tempDir.resolve("subdir1/nested.txt"), "Nested file");
        Files.createDirectories(tempDir.resolve("subdir2"));

        service = new FileBrowserService(tempDir, 10);
    }

    // ==================== browse() Tests ====================

    @Test
    void browse_rootDirectory_returnsFilesAndFolders() {
        BrowseResult result = service.browse("/", 1);

        assertEquals("/", result.currentPath());
        assertNull(result.parentPath());
        assertEquals(1, result.currentPage());
        assertEquals(4, result.totalItems()); // 2 files + 2 directories

        // Directories should come first
        List<FileEntry> entries = result.entries();
        assertTrue(entries.get(0).isDirectory());
        assertTrue(entries.get(1).isDirectory());
        assertFalse(entries.get(2).isDirectory());
        assertFalse(entries.get(3).isDirectory());
    }

    @Test
    void browse_subdirectory_returnsContentsAndParent() {
        BrowseResult result = service.browse("/subdir1", 1);

        assertEquals("/subdir1", result.currentPath());
        assertEquals("/", result.parentPath());
        assertEquals(1, result.totalItems());

        FileEntry nested = result.entries().get(0);
        assertEquals("nested.txt", nested.name());
        assertFalse(nested.isDirectory());
    }

    @Test
    void browse_emptyDirectory_returnsEmptyList() {
        BrowseResult result = service.browse("/subdir2", 1);

        assertEquals("/subdir2", result.currentPath());
        assertEquals("/", result.parentPath());
        assertEquals(0, result.totalItems());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void browse_invalidPath_returnsEmptyResult() {
        BrowseResult result = service.browse("/nonexistent", 1);

        assertEquals(0, result.totalItems());
        assertTrue(result.entries().isEmpty());
    }

    @Test
    void browse_pathTraversalBlocked() {
        BrowseResult result = service.browse("/../../../etc", 1);

        // Should return empty result for path outside storage
        assertEquals(0, result.totalItems());
    }

    // ==================== Pagination Tests ====================

    @Test
    void browse_pagination_returnsCorrectPage() throws IOException {
        // Create 15 files to test pagination with 10 items per page
        for (int i = 0; i < 15; i++) {
            Files.writeString(tempDir.resolve("page_test_" + i + ".txt"), "Content " + i);
        }

        // Page 1 should have 10 items
        BrowseResult page1 = service.browse("/", 1);
        assertEquals(10, page1.entries().size());
        assertEquals(1, page1.currentPage());
        assertEquals(2, page1.totalPages());

        // Page 2 should have remaining items (15 - 10 + 2 dirs = 7 total...
        // wait: 15 files + 2 dirs + 2 original files = 19 items)
        BrowseResult page2 = service.browse("/", 2);
        assertEquals(9, page2.entries().size()); // 19 items, 10 on page 1, 9 on page 2
        assertEquals(2, page2.currentPage());
    }

    @Test
    void browse_pageNumberClampedToValid() {
        BrowseResult page0 = service.browse("/", 0);
        assertEquals(1, page0.currentPage());

        BrowseResult pageLarge = service.browse("/", 999);
        assertEquals(1, pageLarge.currentPage()); // Only 1 page with 4 items
    }

    // ==================== State Encoding/Decoding Tests ====================

    @Test
    void encodeState_createsValidPayload() {
        String encoded = service.encodeState("/subdir1", 2);

        assertNotNull(encoded);
        assertTrue(encoded.startsWith("FB:"));
    }

    @Test
    void decodeState_validPayload_returnsCorrectState() {
        String encoded = service.encodeState("/test/path", 5);
        BrowseState state = service.decodeState(encoded);

        assertEquals("/test/path", state.path());
        assertEquals(5, state.page());
    }

    @Test
    void decodeState_invalidPayload_returnsDefaultState() {
        BrowseState state = service.decodeState("invalid_base64!");

        assertEquals("/", state.path());
        assertEquals(1, state.page());
    }

    @Test
    void decodeState_nullPayload_returnsDefaultState() {
        BrowseState state = service.decodeState(null);

        assertEquals("/", state.path());
        assertEquals(1, state.page());
    }

    @Test
    void decodeState_emptyPayload_returnsDefaultState() {
        BrowseState state = service.decodeState("");

        assertEquals("/", state.path());
        assertEquals(1, state.page());
    }

    @Test
    void encodeDecodeState_roundTrip() {
        String path = "/some/deep/path";
        int page = 7;

        String encoded = service.encodeState(path, page);
        BrowseState decoded = service.decodeState(encoded);

        assertEquals(path, decoded.path());
        assertEquals(page, decoded.page());
    }

    // ==================== Path Validation Tests ====================

    @Test
    void isValidPath_existingPath_returnsTrue() {
        assertTrue(service.isValidPath("/"));
        assertTrue(service.isValidPath("/subdir1"));
    }

    @Test
    void isValidPath_nonExistentPath_returnsFalse() {
        assertFalse(service.isValidPath("/nonexistent"));
    }

    @Test
    void isValidPath_traversalAttempt_returnsFalse() {
        // Path that would escape the storage root
        assertFalse(service.isValidPath("/../../../etc"));
        // Note: /subdir1/../.. normalizes to / which is valid
        // The security check is in resolveAndValidatePath which blocks paths outside root
    }

    // ==================== Parent Path Tests ====================

    @Test
    void getParentPath_rootPath_returnsNull() {
        assertNull(service.getParentPath("/"));
    }

    @Test
    void getParentPath_subdirectory_returnsParent() {
        assertEquals("/", service.getParentPath("/subdir1"));
        assertEquals("/subdir1", service.getParentPath("/subdir1/nested"));
    }

    @Test
    void getParentPath_deepPath_returnsImmediateParent() {
        assertEquals("/a/b", service.getParentPath("/a/b/c"));
    }

    // ==================== File Entry Tests ====================

    @Test
    void fileEntry_hasCorrectMetadata() {
        BrowseResult result = service.browse("/", 1);

        // Find a file entry
        FileEntry file = result.entries().stream()
            .filter(e -> !e.isDirectory())
            .findFirst()
            .orElseThrow();

        assertNotNull(file.name());
        assertFalse(file.name().isEmpty());
        assertTrue(file.sizeBytes() > 0);
        assertNotNull(file.formattedSize());
        assertNotNull(file.modifiedAt());
    }

    @Test
    void fileEntry_directorySizeIsZero() {
        BrowseResult result = service.browse("/", 1);

        FileEntry dir = result.entries().stream()
            .filter(FileEntry::isDirectory)
            .findFirst()
            .orElseThrow();

        assertEquals(0, dir.sizeBytes());
    }

    // ==================== Sorting Tests ====================

    @Test
    void browse_entriesSorted_directoriesFirst() {
        BrowseResult result = service.browse("/", 1);
        List<FileEntry> entries = result.entries();

        // Check that all directories come before files
        boolean seenFile = false;
        for (FileEntry entry : entries) {
            if (!entry.isDirectory()) {
                seenFile = true;
            } else if (seenFile) {
                fail("Directory found after file - sorting incorrect");
            }
        }
    }

    @Test
    void browse_entriesSorted_alphabeticalWithinType() {
        BrowseResult result = service.browse("/", 1);
        List<FileEntry> entries = result.entries();

        // Directories should be alphabetical
        List<String> dirNames = entries.stream()
            .filter(FileEntry::isDirectory)
            .map(FileEntry::name)
            .toList();

        for (int i = 1; i < dirNames.size(); i++) {
            assertTrue(
                dirNames.get(i-1).compareToIgnoreCase(dirNames.get(i)) <= 0,
                "Directories not sorted: " + dirNames
            );
        }

        // Files should be alphabetical
        List<String> fileNames = entries.stream()
            .filter(e -> !e.isDirectory())
            .map(FileEntry::name)
            .toList();

        for (int i = 1; i < fileNames.size(); i++) {
            assertTrue(
                fileNames.get(i-1).compareToIgnoreCase(fileNames.get(i)) <= 0,
                "Files not sorted: " + fileNames
            );
        }
    }

    // ==================== Edge Cases ====================

    @Test
    void browse_metadataFilesExcluded() throws IOException {
        // Create a metadata sidecar file
        Files.writeString(tempDir.resolve("test.txt.meta.json"), "{}");

        BrowseResult result = service.browse("/", 1);

        // Metadata file should not be listed
        boolean hasMetaFile = result.entries().stream()
            .anyMatch(e -> e.name().endsWith(".meta.json"));
        assertFalse(hasMetaFile);
    }

    @Test
    void browse_pathNormalization() {
        // Various path formats should work
        BrowseResult r1 = service.browse("", 1);
        BrowseResult r2 = service.browse("/", 1);
        BrowseResult r3 = service.browse("//", 1);

        assertEquals(r1.currentPath(), r2.currentPath());
        assertEquals(r2.currentPath(), r3.currentPath());
    }
}
