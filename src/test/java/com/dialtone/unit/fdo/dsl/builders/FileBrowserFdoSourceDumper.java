/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl.builders;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.FileBrowserFdoBuilder;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dumps raw FDO source for manual inspection/editing.
 * Run this test to see what FDO is being generated.
 */
class FileBrowserFdoSourceDumper {

    @TempDir
    Path tempDir;

    private FileBrowserService service;

    @BeforeEach
    void setUp() throws IOException {
        // Create realistic test directory structure
        Files.writeString(tempDir.resolve("readme.txt"), "Welcome to the file browser!");
        Files.writeString(tempDir.resolve("config.ini"), "[settings]\nverbose=true");
        Files.writeString(tempDir.resolve("image.gif"), "GIF89a fake image data");
        Files.createDirectories(tempDir.resolve("documents"));
        Files.writeString(tempDir.resolve("documents/report.doc"), "Important report content");
        Files.createDirectories(tempDir.resolve("downloads"));
        Files.createDirectories(tempDir.resolve("music"));
        Files.writeString(tempDir.resolve("music/song.mp3"), "fake mp3 data bytes here");

        service = new FileBrowserService(tempDir, 10);
    }

    @Test
    void dumpRootDirectoryFdo() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ROOT DIRECTORY FDO (Color Mode - Windows)");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false));
        System.out.println(fdo);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END ROOT DIRECTORY FDO");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void dumpRootDirectoryFdoBW() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("ROOT DIRECTORY FDO (BW Mode - Windows)");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, true));
        System.out.println(fdo);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END ROOT DIRECTORY FDO (BW)");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void dumpSubdirectoryFdo() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUBDIRECTORY FDO (/documents) - Has Back Button");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult result = service.browse("/documents", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false));
        System.out.println(fdo);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END SUBDIRECTORY FDO");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void dumpEmptyDirectoryFdo() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("EMPTY DIRECTORY FDO (/downloads)");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult result = service.browse("/downloads", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(new RenderingContext(ClientPlatform.WINDOWS, false));
        System.out.println(fdo);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END EMPTY DIRECTORY FDO");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void dumpPaginatedFdo() throws IOException {
        // Create enough files to trigger pagination
        for (int i = 0; i < 15; i++) {
            Files.writeString(tempDir.resolve("file_" + String.format("%02d", i) + ".txt"), "Content " + i);
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("PAGINATED FDO - Page 1 of 2 (Has Next Button)");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult page1 = service.browse("/", 1);
        FileBrowserFdoBuilder builder1 = new FileBrowserFdoBuilder(page1, service);
        System.out.println(builder1.toSource(new RenderingContext(ClientPlatform.WINDOWS, false)));

        System.out.println("\n" + "-".repeat(80));
        System.out.println("PAGINATED FDO - Page 2 of 2 (Has Prev Button)");
        System.out.println("-".repeat(80) + "\n");

        BrowseResult page2 = service.browse("/", 2);
        FileBrowserFdoBuilder builder2 = new FileBrowserFdoBuilder(page2, service);
        System.out.println(builder2.toSource(new RenderingContext(ClientPlatform.WINDOWS, false)));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END PAGINATED FDO");
        System.out.println("=".repeat(80) + "\n");
    }

    @Test
    void dumpMacFdo() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MAC PLATFORM FDO");
        System.out.println("=".repeat(80) + "\n");

        BrowseResult result = service.browse("/", 1);
        FileBrowserFdoBuilder builder = new FileBrowserFdoBuilder(result, service);

        String fdo = builder.toSource(new RenderingContext(ClientPlatform.MAC, false));
        System.out.println(fdo);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("END MAC PLATFORM FDO");
        System.out.println("=".repeat(80) + "\n");
    }
}
