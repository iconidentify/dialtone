/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.keyword.KeywordProcessor;
import com.dialtone.protocol.keyword.KeywordRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeywordProcessor.
 */
@DisplayName("KeywordProcessor")
class KeywordProcessorTest {

    private KeywordRegistry registry;
    private SessionContext session;

    // Test handler implementations
    private static class SuccessfulHandler implements KeywordHandler {
        private final String keyword;
        private int callCount = 0;

        SuccessfulHandler(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public String getKeyword() {
            return keyword;
        }

        @Override
        public String getDescription() {
            return "Test handler that succeeds";
        }

        @Override
        public void handle(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) {
            callCount++;
        }

        int getCallCount() {
            return callCount;
        }
    }

    private static class FailingHandler implements KeywordHandler {
        private final String keyword;

        FailingHandler(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public String getKeyword() {
            return keyword;
        }

        @Override
        public String getDescription() {
            return "Test handler that throws exception";
        }

        @Override
        public void handle(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) throws Exception {
            throw new RuntimeException("Simulated handler failure");
        }
    }

    @BeforeEach
    void setUp() {
        KeywordRegistry.resetInstance();
        registry = KeywordRegistry.getInstance();

        // Create real SessionContext (final class cannot be mocked)
        session = new SessionContext();
        session.setUsername("TestUser");
        session.setAuthenticated(true);
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    @DisplayName("Should return false for null keyword")
    void shouldReturnFalseForNullKeyword() {
        boolean result = KeywordProcessor.processKeyword(null, session, null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false for empty keyword")
    void shouldReturnFalseForEmptyKeyword() {
        boolean result = KeywordProcessor.processKeyword("", session, null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false for whitespace-only keyword")
    void shouldReturnFalseForWhitespaceKeyword() {
        boolean result = KeywordProcessor.processKeyword("   ", session, null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should return false for unknown keyword")
    void shouldReturnFalseForUnknownKeyword() {
        boolean result = KeywordProcessor.processKeyword("unknown command", session, null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should return true when handler succeeds")
    void shouldReturnTrueWhenHandlerSucceeds() {
        SuccessfulHandler handler = new SuccessfulHandler("test");
        registry.registerHandler(handler);

        boolean result = KeywordProcessor.processKeyword("test", session, null, null);

        assertTrue(result);
        assertEquals(1, handler.getCallCount());
    }

    @Test
    @DisplayName("Should return true for case-insensitive match")
    void shouldReturnTrueForCaseInsensitiveMatch() {
        SuccessfulHandler handler = new SuccessfulHandler("Server Logs");
        registry.registerHandler(handler);

        boolean result = KeywordProcessor.processKeyword("SERVER LOGS", session, null, null);

        assertTrue(result);
        assertEquals(1, handler.getCallCount());
    }

    @Test
    @DisplayName("Should return true when whitespace is trimmed")
    void shouldReturnTrueWhenWhitespaceIsTrimmed() {
        SuccessfulHandler handler = new SuccessfulHandler("test");
        registry.registerHandler(handler);

        boolean result = KeywordProcessor.processKeyword("  test  ", session, null, null);

        assertTrue(result);
        assertEquals(1, handler.getCallCount());
    }

    @Test
    @DisplayName("Should return false when handler throws exception")
    void shouldReturnFalseWhenHandlerThrowsException() {
        FailingHandler handler = new FailingHandler("fail");
        registry.registerHandler(handler);

        boolean result = KeywordProcessor.processKeyword("fail", session, null, null);

        assertFalse(result);
    }

    @Test
    @DisplayName("Should invoke correct handler for registered keyword")
    void shouldInvokeCorrectHandler() {
        SuccessfulHandler handler1 = new SuccessfulHandler("logs");
        SuccessfulHandler handler2 = new SuccessfulHandler("help");
        registry.registerHandler(handler1);
        registry.registerHandler(handler2);

        KeywordProcessor.processKeyword("help", session, null, null);

        assertEquals(0, handler1.getCallCount());
        assertEquals(1, handler2.getCallCount());
    }

    @Test
    @DisplayName("Should return false when registry is empty")
    void shouldReturnFalseWhenRegistryIsEmpty() {
        registry.clear();

        boolean result = KeywordProcessor.processKeyword("anything", session, null, null);

        assertFalse(result);
    }
}
