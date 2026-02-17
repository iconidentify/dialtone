/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.handlers.TosKeywordHandler;
import com.dialtone.web.services.ScreennamePreferencesService;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TosKeywordHandler.
 *
 * Note: TosKeywordHandler now uses TosFdoBuilder (DSL) instead of FDO templates.
 * Tests verify that the handler calls compileFdoScriptToP3Chunks with DSL-generated source.
 */
@DisplayName("TosKeywordHandler")
class TosKeywordHandlerTest {

    @Mock
    private FdoCompiler mockFdoCompiler;

    @Mock
    private ScreennamePreferencesService mockPreferencesService;

    private TosKeywordHandler handler;
    private SessionContext session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        Properties props = new Properties();  // Button theme uses defaults when not configured
        handler = new TosKeywordHandler(mockFdoCompiler, props, mockPreferencesService);

        // Create real SessionContext (final class cannot be mocked)
        session = new SessionContext();
        session.setUsername("TestUser");
        session.setAuthenticated(true);
    }

    @Test
    @DisplayName("Should throw exception when FdoCompiler is null")
    void shouldThrowExceptionWhenFdoCompilerIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new TosKeywordHandler(null, new Properties(), null));
    }

    @Test
    @DisplayName("Should return correct keyword")
    void shouldReturnCorrectKeyword() {
        assertEquals("tos", handler.getKeyword());
    }

    @Test
    @DisplayName("Should return correct description")
    void shouldReturnCorrectDescription() {
        assertEquals("Display Dialtone Terms of Service", handler.getDescription());
    }

    @Test
    @DisplayName("Should compile FDO source via DSL builder")
    void shouldCompileFdoSourceViaDslBuilder() throws Exception {
        // Return empty chunks so handler doesn't try to enqueue
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(),
            eq("at"),
            eq(FdoCompiler.AUTO_GENERATE_STREAM_ID)
        )).thenReturn(Collections.emptyList());

        // Execute the handler
        assertDoesNotThrow(() -> handler.handle("tos", session, null, null));

        // Verify FDO script compilation was called with DSL-generated source
        verify(mockFdoCompiler).compileFdoScriptToP3Chunks(
            argThat(fdoSource -> {
                // DSL-generated source should contain key elements
                return fdoSource.contains("TERMS OF SERVICE") &&
                       fdoSource.contains("AGREE") &&
                       fdoSource.contains("DISAGREE") &&
                       fdoSource.contains("uni_start_stream") &&
                       fdoSource.contains("uni_end_stream");
            }),
            eq("at"),
            eq(FdoCompiler.AUTO_GENERATE_STREAM_ID)
        );
    }

    @Test
    @DisplayName("Should include TOS content in DSL-generated FDO")
    void shouldIncludeTosContentInDslGeneratedFdo() throws Exception {
        // Return empty chunks so handler doesn't try to enqueue
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(), anyString(), anyInt()
        )).thenReturn(Collections.emptyList());

        // Execute the handler
        handler.handle("tos", session, null, null);

        // Verify the FDO source contains TOS content
        verify(mockFdoCompiler).compileFdoScriptToP3Chunks(
            argThat(fdoSource -> {
                // Check that actual TOS text content is embedded in the FDO
                return fdoSource.contains("NON-AFFILIATION") &&
                       fdoSource.contains("Dialtone is an independent") &&
                       fdoSource.contains("ACCEPTANCE");
            }),
            anyString(),
            anyInt()
        );
    }

    @Test
    @DisplayName("Should handle compilation errors gracefully")
    void shouldHandleCompilationErrorsGracefully() throws Exception {
        // Configure mock to throw exception
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(), anyString(), anyInt()
        )).thenThrow(new RuntimeException("Compilation failed"));

        // The handler should propagate the exception (this is expected behavior)
        assertThrows(RuntimeException.class, () ->
            handler.handle("tos", session, null, null));
    }

    @Test
    @DisplayName("Should handle null chunks from compiler")
    void shouldHandleNullChunksFromCompiler() throws Exception {
        // Configure mock to return null
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(), anyString(), anyInt()
        )).thenReturn(null);

        // Should not throw exception (handler now checks for null)
        assertDoesNotThrow(() -> handler.handle("tos", session, null, null));
    }

    @Test
    @DisplayName("Should handle empty chunks from compiler")
    void shouldHandleEmptyChunksFromCompiler() throws Exception {
        // Configure mock to return empty list
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(), anyString(), anyInt()
        )).thenReturn(Collections.emptyList());

        // Should not throw exception (handler now checks for empty list)
        assertDoesNotThrow(() -> handler.handle("tos", session, null, null));
    }

    @Test
    @DisplayName("Should include button styling in DSL output")
    void shouldIncludeButtonStylingInDslOutput() throws Exception {
        // Return empty chunks so handler doesn't try to enqueue
        when(mockFdoCompiler.compileFdoScriptToP3Chunks(
            anyString(), anyString(), anyInt()
        )).thenReturn(Collections.emptyList());

        // Execute the handler
        handler.handle("tos", session, null, null);

        // Verify the FDO source contains button styling (color mode by default)
        verify(mockFdoCompiler).compileFdoScriptToP3Chunks(
            argThat(fdoSource -> {
                // Default theme should have orange button colors
                return fdoSource.contains("mat_color_face") &&
                       fdoSource.contains("mat_trigger_style") &&
                       fdoSource.contains("trigger");
            }),
            anyString(),
            anyInt()
        );
    }
}
