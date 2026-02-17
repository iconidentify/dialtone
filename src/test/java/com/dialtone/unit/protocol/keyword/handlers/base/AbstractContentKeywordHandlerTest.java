/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.keyword.handlers.base;

import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.ContentWindowConfig;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.handlers.base.AbstractContentKeywordHandler;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.*;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AbstractContentKeywordHandler")
class AbstractContentKeywordHandlerTest {

    private FdoCompiler mockCompiler;
    private ChannelHandlerContext mockCtx;
    private Pacer mockPacer;
    private SessionContext mockSession;

    @BeforeEach
    void setUp() {
        mockCompiler = mock(FdoCompiler.class);
        mockCtx = mock(ChannelHandlerContext.class);
        mockPacer = mock(Pacer.class);
        mockSession = mock(SessionContext.class);

        when(mockSession.getDisplayName()).thenReturn("TestUser");
    }

    /**
     * Concrete test implementation of AbstractContentKeywordHandler.
     */
    static class TestContentHandler extends AbstractContentKeywordHandler {
        private static final String KEYWORD = "testcontent";
        private static final String DESCRIPTION = "Test content handler";
        private static final String LOG_PREFIX = "TEST";
        private static final String CONTENT = "Test window content";

        private ContentWindowConfig config;
        private RenderingContext renderingContext = RenderingContext.DEFAULT;

        public TestContentHandler(FdoCompiler fdoCompiler) {
            super(fdoCompiler);
            this.config = ContentWindowConfig.withDefaults(KEYWORD, "Test Window", null);
        }

        public TestContentHandler(FdoCompiler fdoCompiler, ContentWindowConfig config) {
            super(fdoCompiler);
            this.config = config;
        }

        @Override
        public String getKeyword() {
            return KEYWORD;
        }

        @Override
        public String getDescription() {
            return DESCRIPTION;
        }

        @Override
        protected ContentWindowConfig getConfig() {
            return config;
        }

        @Override
        protected String getContent(SessionContext session) {
            return CONTENT;
        }

        @Override
        protected String getLogPrefix() {
            return LOG_PREFIX;
        }

        @Override
        protected RenderingContext getRenderingContext(SessionContext session) {
            return renderingContext;
        }

        public void setRenderingContext(RenderingContext ctx) {
            this.renderingContext = ctx;
        }

        public void setConfig(ContentWindowConfig config) {
            this.config = config;
        }

        /** Expose protected method for testing */
        public FdoCompiler getCompilerForTest() {
            return getFdoCompiler();
        }
    }

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("Should create handler with valid FdoCompiler")
        void shouldCreateWithValidCompiler() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            assertNotNull(handler);
            assertSame(mockCompiler, handler.getCompilerForTest());
        }

        @Test
        @DisplayName("Should reject null FdoCompiler")
        void shouldRejectNullCompiler() {
            assertThrows(IllegalArgumentException.class, () ->
                new TestContentHandler(null)
            );
        }
    }

    @Nested
    @DisplayName("Interface implementation")
    class InterfaceImplementation {

        @Test
        @DisplayName("Should return correct keyword")
        void shouldReturnCorrectKeyword() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            assertEquals("testcontent", handler.getKeyword());
        }

        @Test
        @DisplayName("Should return non-empty description")
        void shouldReturnDescription() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            String description = handler.getDescription();
            assertNotNull(description);
            assertFalse(description.isEmpty());
        }
    }

    @Nested
    @DisplayName("Template method pattern")
    class TemplateMethodPattern {

        @Test
        @DisplayName("getConfig should return configured config")
        void getConfigShouldReturnConfig() {
            ContentWindowConfig customConfig = new ContentWindowConfig(
                "custom", "Custom Title", "1-69-27256", "1-69-40001",
                518, 300, null
            );
            TestContentHandler handler = new TestContentHandler(mockCompiler, customConfig);

            assertEquals(customConfig, handler.getConfig());
        }

        @Test
        @DisplayName("getContent should return content")
        void getContentShouldReturnContent() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            String content = handler.getContent(mockSession);
            assertNotNull(content);
            assertFalse(content.isEmpty());
        }

        @Test
        @DisplayName("getLogPrefix should return prefix")
        void getLogPrefixShouldReturnPrefix() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            assertEquals("TEST", handler.getLogPrefix());
        }

        @Test
        @DisplayName("getRenderingContext should return default by default")
        void getRenderingContextShouldReturnDefault() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            assertEquals(RenderingContext.DEFAULT, handler.getRenderingContext(mockSession));
        }

        @Test
        @DisplayName("getRenderingContext can be overridden")
        void getRenderingContextCanBeOverridden() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            RenderingContext customCtx = new RenderingContext(
                com.dialtone.protocol.ClientPlatform.WINDOWS, true
            );
            handler.setRenderingContext(customCtx);

            assertEquals(customCtx, handler.getRenderingContext(mockSession));
        }
    }

    @Nested
    @DisplayName("Handle method")
    class HandleMethod {

        @Test
        @DisplayName("Should compile FDO source with correct parameters")
        void shouldCompileFdoSource() throws Exception {
            TestContentHandler handler = new TestContentHandler(mockCompiler);

            // Return empty list to avoid NPE in P3ChunkEnqueuer
            when(mockCompiler.compileFdoScriptToP3Chunks(
                anyString(), anyString(), anyInt()
            )).thenReturn(Collections.emptyList());

            handler.handle("testcontent", mockSession, mockCtx, mockPacer);

            verify(mockCompiler).compileFdoScriptToP3Chunks(
                argThat(source -> source != null && source.contains("uni_start_stream")),
                eq("At"),
                eq(FdoCompiler.AUTO_GENERATE_STREAM_ID)
            );
        }

        @Test
        @DisplayName("Should use correct config in FDO generation")
        void shouldUseCorrectConfig() throws Exception {
            ContentWindowConfig customConfig = new ContentWindowConfig(
                "custom", "My Custom Title", "1-69-27256", null,
                518, 300, null
            );
            TestContentHandler handler = new TestContentHandler(mockCompiler, customConfig);

            when(mockCompiler.compileFdoScriptToP3Chunks(
                anyString(), anyString(), anyInt()
            )).thenReturn(Collections.emptyList());

            handler.handle("custom", mockSession, mockCtx, mockPacer);

            verify(mockCompiler).compileFdoScriptToP3Chunks(
                argThat(source -> source.contains("My Custom Title")),
                anyString(),
                anyInt()
            );
        }
    }

    @Nested
    @DisplayName("getFdoCompiler accessor")
    class GetFdoCompilerAccessor {

        @Test
        @DisplayName("Should return the injected compiler")
        void shouldReturnInjectedCompiler() {
            TestContentHandler handler = new TestContentHandler(mockCompiler);
            assertSame(mockCompiler, handler.getCompilerForTest());
        }
    }
}
