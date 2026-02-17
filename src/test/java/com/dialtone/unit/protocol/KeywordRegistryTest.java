/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.keyword.KeywordRegistry;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KeywordRegistry.
 */
@DisplayName("KeywordRegistry")
class KeywordRegistryTest {

    private KeywordRegistry registry;

    // Test handler implementations
    private static class TestHandler implements KeywordHandler {
        private final String keyword;
        private final String description;
        private int callCount = 0;

        TestHandler(String keyword, String description) {
            this.keyword = keyword;
            this.description = description;
        }

        @Override
        public String getKeyword() {
            return keyword;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public void handle(String keyword, SessionContext session, ChannelHandlerContext ctx, Pacer pacer) {
            callCount++;
        }

        int getCallCount() {
            return callCount;
        }
    }

    @BeforeEach
    void setUp() {
        KeywordRegistry.resetInstance();
        registry = KeywordRegistry.getInstance();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    @DisplayName("Should register handler successfully")
    void shouldRegisterHandler() {
        TestHandler handler = new TestHandler("test", "Test handler");

        registry.registerHandler(handler);

        assertTrue(registry.hasHandler("test"));
        assertEquals(1, registry.getHandlerCount());
        assertSame(handler, registry.getHandler("test"));
    }

    @Test
    @DisplayName("Should retrieve handler case-insensitively")
    void shouldRetrieveHandlerCaseInsensitively() {
        TestHandler handler = new TestHandler("Server Logs", "Test");

        registry.registerHandler(handler);

        assertNotNull(registry.getHandler("server logs"));
        assertNotNull(registry.getHandler("SERVER LOGS"));
        assertNotNull(registry.getHandler("Server Logs"));
        assertNotNull(registry.getHandler("  server logs  "));
    }

    @Test
    @DisplayName("Should unregister handler")
    void shouldUnregisterHandler() {
        TestHandler handler = new TestHandler("test", "Test");
        registry.registerHandler(handler);

        boolean removed = registry.unregisterHandler("test");

        assertTrue(removed);
        assertFalse(registry.hasHandler("test"));
        assertEquals(0, registry.getHandlerCount());
    }

    @Test
    @DisplayName("Should return false when unregistering non-existent handler")
    void shouldReturnFalseWhenUnregisteringNonExistent() {
        boolean removed = registry.unregisterHandler("nonexistent");

        assertFalse(removed);
    }

    @Test
    @DisplayName("Should replace existing handler with warning")
    void shouldReplaceExistingHandler() {
        TestHandler handler1 = new TestHandler("test", "First");
        TestHandler handler2 = new TestHandler("test", "Second");

        registry.registerHandler(handler1);
        registry.registerHandler(handler2);

        assertEquals(1, registry.getHandlerCount());
        assertSame(handler2, registry.getHandler("test"));
    }

    @Test
    @DisplayName("Should support multiple handlers")
    void shouldSupportMultipleHandlers() {
        TestHandler handler1 = new TestHandler("logs", "Logs");
        TestHandler handler2 = new TestHandler("help", "Help");
        TestHandler handler3 = new TestHandler("stats", "Stats");

        registry.registerHandler(handler1);
        registry.registerHandler(handler2);
        registry.registerHandler(handler3);

        assertEquals(3, registry.getHandlerCount());
        assertTrue(registry.hasHandler("logs"));
        assertTrue(registry.hasHandler("help"));
        assertTrue(registry.hasHandler("stats"));
    }

    @Test
    @DisplayName("Should return all handlers")
    void shouldReturnAllHandlers() {
        TestHandler handler1 = new TestHandler("logs", "Logs");
        TestHandler handler2 = new TestHandler("help", "Help");

        registry.registerHandler(handler1);
        registry.registerHandler(handler2);

        var allHandlers = registry.getAllHandlers();

        assertEquals(2, allHandlers.size());
        assertTrue(allHandlers.contains(handler1));
        assertTrue(allHandlers.contains(handler2));
    }

    @Test
    @DisplayName("Should clear all handlers")
    void shouldClearAllHandlers() {
        registry.registerHandler(new TestHandler("test1", "Test 1"));
        registry.registerHandler(new TestHandler("test2", "Test 2"));

        registry.clear();

        assertEquals(0, registry.getHandlerCount());
        assertTrue(registry.isEmpty());
    }

    @Test
    @DisplayName("Should reject null handler")
    void shouldRejectNullHandler() {
        assertThrows(IllegalArgumentException.class, () -> {
            registry.registerHandler(null);
        });
    }

    @Test
    @DisplayName("Should reject handler with null keyword")
    void shouldRejectHandlerWithNullKeyword() {
        KeywordHandler handler = new TestHandler(null, "Test");

        assertThrows(IllegalArgumentException.class, () -> {
            registry.registerHandler(handler);
        });
    }

    @Test
    @DisplayName("Should reject handler with empty keyword")
    void shouldRejectHandlerWithEmptyKeyword() {
        KeywordHandler handler = new TestHandler("  ", "Test");

        assertThrows(IllegalArgumentException.class, () -> {
            registry.registerHandler(handler);
        });
    }

    @Test
    @DisplayName("Should return null for null keyword lookup")
    void shouldReturnNullForNullKeyword() {
        assertNull(registry.getHandler(null));
    }

    @Test
    @DisplayName("Should return null for empty keyword lookup")
    void shouldReturnNullForEmptyKeyword() {
        assertNull(registry.getHandler(""));
        assertNull(registry.getHandler("   "));
    }

    @Test
    @DisplayName("Should return null for unknown keyword")
    void shouldReturnNullForUnknownKeyword() {
        assertNull(registry.getHandler("unknown"));
    }

    @Test
    @DisplayName("Should be singleton")
    void shouldBeSingleton() {
        KeywordRegistry instance1 = KeywordRegistry.getInstance();
        KeywordRegistry instance2 = KeywordRegistry.getInstance();

        assertSame(instance1, instance2);
    }

    @Test
    @DisplayName("Should report empty state correctly")
    void shouldReportEmptyStateCorrectly() {
        assertTrue(registry.isEmpty());

        registry.registerHandler(new TestHandler("test", "Test"));

        assertFalse(registry.isEmpty());

        registry.clear();

        assertTrue(registry.isEmpty());
    }

    @Nested
    @DisplayName("Prefix Matching for Parameterized Keywords")
    class PrefixMatchingForParameterizedKeywords {

        @Test
        @DisplayName("Should match parameterized keyword by prefix")
        void shouldMatchParameterizedKeywordByPrefix() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            // Should match prefix before first space
            KeywordHandler found = registry.getHandler("mat_art_id <32-5446>");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should match exact keyword before prefix")
        void shouldMatchExactKeywordBeforePrefix() {
            TestHandler exactHandler = new TestHandler("cmd param", "Exact match");
            TestHandler prefixHandler = new TestHandler("cmd", "Prefix match");

            registry.registerHandler(exactHandler);
            registry.registerHandler(prefixHandler);

            // Exact match should take precedence
            KeywordHandler found = registry.getHandler("cmd param");

            assertSame(exactHandler, found);
        }

        @Test
        @DisplayName("Should match prefix when no exact match exists")
        void shouldMatchPrefixWhenNoExactMatchExists() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            KeywordHandler found = registry.getHandler("mat_art_id <1-0-21029>");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should not match if prefix does not exist")
        void shouldNotMatchIfPrefixDoesNotExist() {
            TestHandler handler = new TestHandler("other_cmd", "Other");
            registry.registerHandler(handler);

            KeywordHandler found = registry.getHandler("mat_art_id <32-5446>");

            assertNull(found);
        }

        @Test
        @DisplayName("Should match prefix case-insensitively")
        void shouldMatchPrefixCaseInsensitively() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            KeywordHandler found1 = registry.getHandler("MAT_ART_ID <32-5446>");
            KeywordHandler found2 = registry.getHandler("Mat_Art_Id <32-5446>");

            assertNotNull(found1);
            assertNotNull(found2);
            assertSame(handler, found1);
            assertSame(handler, found2);
        }

        @Test
        @DisplayName("Should match prefix with multiple parameters")
        void shouldMatchPrefixWithMultipleParameters() {
            TestHandler handler = new TestHandler("send_file", "File sender");
            registry.registerHandler(handler);

            KeywordHandler found = registry.getHandler("send_file <file.txt> <user>");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should handle prefix with leading/trailing whitespace")
        void shouldHandlePrefixWithWhitespace() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            KeywordHandler found = registry.getHandler("  mat_art_id <32-5446>  ");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should match base command without parameters")
        void shouldMatchBaseCommandWithoutParameters() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            // Exact match for base command
            KeywordHandler found = registry.getHandler("mat_art_id");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should match prefix with adjacent parameters (no space)")
        void shouldMatchPrefixWithAdjacentParameters() {
            TestHandler handler = new TestHandler("cmd", "Command");
            registry.registerHandler(handler);

            // "cmd<param>" splits on space, so "cmd<param>" is exact match attempt
            // Then no space found, so prefix logic doesn't apply
            // This should NOT match (no space means no prefix)
            KeywordHandler found = registry.getHandler("cmd<param>");

            // No exact match for "cmd<param>", and no space to split on
            assertNull(found);
        }

        @Test
        @DisplayName("Should handle multiple handlers with prefix matching")
        void shouldHandleMultipleHandlersWithPrefixMatching() {
            TestHandler handler1 = new TestHandler("mat_art_id", "Image viewer");
            TestHandler handler2 = new TestHandler("send_file", "File sender");
            TestHandler handler3 = new TestHandler("server logs", "Server logs");

            registry.registerHandler(handler1);
            registry.registerHandler(handler2);
            registry.registerHandler(handler3);

            // Parameterized keywords
            assertSame(handler1, registry.getHandler("mat_art_id <32-5446>"));
            assertSame(handler2, registry.getHandler("send_file <test.txt> <user>"));

            // Exact match keyword
            assertSame(handler3, registry.getHandler("server logs"));
        }

        @Test
        @DisplayName("Should return null for parameter without base command")
        void shouldReturnNullForParameterWithoutBaseCommand() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            // Just a parameter without base command
            KeywordHandler found = registry.getHandler("<32-5446>");

            assertNull(found);
        }

        @Test
        @DisplayName("Should match prefix with complex parameter content")
        void shouldMatchPrefixWithComplexParameterContent() {
            TestHandler handler = new TestHandler("cmd", "Command");
            registry.registerHandler(handler);

            KeywordHandler found = registry.getHandler("cmd <param with spaces and special-chars_123>");

            assertNotNull(found);
            assertSame(handler, found);
        }

        @Test
        @DisplayName("Should not match partial prefix")
        void shouldNotMatchPartialPrefix() {
            TestHandler handler = new TestHandler("mat_art_id", "Image viewer");
            registry.registerHandler(handler);

            // "mat" is not the full prefix "mat_art_id"
            KeywordHandler found = registry.getHandler("mat <param>");

            assertNull(found);
        }
    }
}
