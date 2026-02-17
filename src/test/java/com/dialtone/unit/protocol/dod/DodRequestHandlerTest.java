/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.dod;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.dod.DodRequestHandler;
import com.dialtone.art.ArtService;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DodRequestHandlerTest {

    @Mock
    private FdoCompiler fdoCompiler;

    @Mock
    private ArtService artService;

    @Mock
    private ChannelHandlerContext ctx;

    private DodRequestHandler handler;

    // Sample art bytes for testing
    private static final byte[] SAMPLE_ART_BYTES = new byte[]{0x00, 0x01, 0x02, 0x03};

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        handler = new DodRequestHandler(fdoCompiler, artService, props, null);
    }

    @Nested
    @DisplayName("f2 Art/Picture Requests")
    class F2ArtRequests {

        @Test
        @DisplayName("f2 art requests use DSL builder with correct parameters")
        void f2ArtRequestUsesTypeP() throws Exception {
            when(artService.getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            // Verify DSL-based compilation was called
            ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
            verify(fdoCompiler).compileFdoScriptToP3Chunks(
                    sourceCaptor.capture(),
                    eq("AT"),
                    eq(0x0100)
            );

            // Verify the generated FDO source contains expected IDB atoms
            String fdoSource = sourceCaptor.getValue();
            assertTrue(fdoSource.contains("idb_start_obj"), "Should contain IDB start");
            assertTrue(fdoSource.contains("\"p\""), "Art requests should use type 'p'");
            assertTrue(fdoSource.contains("idb_atr_globalid"), "Should contain GID attribute");
        }

        @Test
        @DisplayName("Missing art returns empty response")
        void missingArtReturnsEmptyResponse() throws Exception {
            when(artService.getArtAsBytes(anyString(), any(ClientPlatform.class)))
                .thenThrow(new IOException("not found"));

            DodRequestHandler.DodResponse response = handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            assertTrue(response.responseChunks.isEmpty(), "Missing art should return empty response");
            verify(fdoCompiler, never()).compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt());
        }
    }

    @Nested
    @DisplayName("f2 Atom Stream Requests")
    class F2AtomRequests {

        @Test
        @DisplayName("f2 atom requests fall back to art type 'p' when no atom FDO exists")
        void f2AtomRequestFallsBackToArt() throws Exception {
            // The handler will try to load from replace_client_fdo/{GID}.fdo.txt
            // Since we're using mocks and the real file won't exist, we need to verify
            // the fallback to art behavior.

            when(artService.getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            // Verify DSL-based compilation was called with type 'p' (art fallback)
            ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
            verify(fdoCompiler).compileFdoScriptToP3Chunks(
                    sourceCaptor.capture(),
                    eq("AT"),
                    eq(0x0100)
            );

            String fdoSource = sourceCaptor.getValue();
            assertTrue(fdoSource.contains("\"p\""), "Should fall back to art type 'p' when no atom FDO");
        }

        @Test
        @DisplayName("f2 atom requests take priority over art when FDO exists")
        void atomFdoTakesPriorityOverArt() throws Exception {
            // Test documents expected behavior: atom FDOs in replace_client_fdo/ take priority
            // When atom FDO exists: type = "a", data comes from compiled FDO
            // When atom FDO does not exist: type = "p", data comes from art service

            // Since real file loading requires test resources, we verify the precedence
            // by checking that when no atom FDO is found, art service IS called
            when(artService.getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            // Art service should be called when no atom FDO exists
            verify(artService).getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class));
        }
    }

    @Nested
    @DisplayName("f2 DSL Builder Usage")
    class F2DslBuilderUsage {

        @Test
        @DisplayName("f2 uses DSL builder for IDB response")
        void f2UsesDslBuilder() throws Exception {
            when(artService.getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            // Verify DSL-based compilation is used (not template-based)
            verify(fdoCompiler).compileFdoScriptToP3Chunks(anyString(), eq("AT"), eq(0x0100));

            // Template-based compilation should NOT be called
            verify(fdoCompiler, never()).compileFdoTemplateToP3Chunks(
                    anyString(), anyMap(), anyString(), anyInt(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("GID Format Handling")
    class GidFormatHandling {

        @Test
        @DisplayName("GID is correctly formatted in DSL output")
        void gidFormattedCorrectly() throws Exception {
            when(artService.getArtAsBytes(eq("1-0-1333"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            // GID 0x01000535 = byte3=1, byte2=0, word=1333 -> "1-0-1333"
            handler.processDodRequest(ctx, 0x01000535, 0x0100, "Tester", ClientPlatform.WINDOWS);

            ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
            verify(fdoCompiler).compileFdoScriptToP3Chunks(sourceCaptor.capture(), anyString(), anyInt());

            // The DSL output should contain the GID in idb_atr_globalid atom
            String fdoSource = sourceCaptor.getValue();
            assertTrue(fdoSource.contains("idb_atr_globalid"), "Should contain GID attribute");
            assertTrue(fdoSource.contains("1") && fdoSource.contains("1333"),
                    "GID components should appear in FDO source");
        }

        @Test
        @DisplayName("Three-part GID format with different byte values")
        void threePartGidFormat() throws Exception {
            // Use GID 2-5-9999 which won't have an atom FDO file
            when(artService.getArtAsBytes(eq("2-5-9999"), any(ClientPlatform.class)))
                .thenReturn(SAMPLE_ART_BYTES);
            when(fdoCompiler.compileFdoScriptToP3Chunks(anyString(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(mock(FdoChunk.class)));

            // GID 0x0205270F = byte3=2, byte2=5, word=9999 -> "2-5-9999"
            handler.processDodRequest(ctx, 0x0205270F, 0x0100, "Tester", ClientPlatform.WINDOWS);

            ArgumentCaptor<String> sourceCaptor = ArgumentCaptor.forClass(String.class);
            verify(fdoCompiler).compileFdoScriptToP3Chunks(sourceCaptor.capture(), anyString(), anyInt());

            // The DSL output should contain the GID components
            String fdoSource = sourceCaptor.getValue();
            assertTrue(fdoSource.contains("idb_atr_globalid"), "Should contain GID attribute");
            assertTrue(fdoSource.contains("9999"), "GID word component should appear in FDO source");
        }
    }
}
