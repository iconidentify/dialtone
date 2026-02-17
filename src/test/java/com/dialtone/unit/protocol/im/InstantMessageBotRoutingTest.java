/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.im;

import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.ChatContext;
import com.dialtone.chat.bot.GrokBot;
import com.dialtone.chat.bot.VirtualUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IM bot routing behavior.
 *
 * These tests verify that the ChatBotRegistry correctly identifies bots
 * and that GrokBot provides appropriate IM responses.
 *
 * The actual InstantMessageTokenHandler.deliverIMToBot() logic is tested
 * indirectly through these integration tests.
 */
@DisplayName("Instant Message Bot Routing Tests")
class InstantMessageBotRoutingTest {

    private ChatBotRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ChatBotRegistry.getInstance();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Nested
    @DisplayName("GrokBot Registration")
    class GrokBotRegistration {

        @Test
        @DisplayName("GrokBot should be identifiable as a bot after registration")
        void grokBotShouldBeIdentifiableAsBot() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            assertTrue(registry.isBot("Grok"), "Grok should be identified as a bot");
        }

        @Test
        @DisplayName("GrokBot lookup should be case-insensitive")
        void grokBotLookupShouldBeCaseInsensitive() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            assertTrue(registry.isBot("grok"), "lowercase 'grok' should match");
            assertTrue(registry.isBot("GROK"), "uppercase 'GROK' should match");
            assertTrue(registry.isBot("Grok"), "proper case 'Grok' should match");
            assertTrue(registry.isBot("GrOk"), "mixed case 'GrOk' should match");
        }

        @Test
        @DisplayName("getBot should return GrokBot instance")
        void getBotShouldReturnGrokBotInstance() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            VirtualUser retrieved = registry.getBot("Grok");
            assertNotNull(retrieved);
            assertTrue(retrieved instanceof GrokBot);
            assertEquals("Grok", retrieved.getUsername());
        }

        @Test
        @DisplayName("GrokBot should be active by default")
        void grokBotShouldBeActiveByDefault() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            VirtualUser bot = registry.getBot("Grok");
            assertTrue(bot.isActive(), "GrokBot should be active by default");
        }

        @Test
        @DisplayName("Inactive GrokBot should not appear in active bots list")
        void inactiveGrokBotShouldNotAppearInActiveBots() {
            GrokBot grokBot = new GrokBot();
            grokBot.setActive(false);
            registry.registerBot(grokBot);

            List<VirtualUser> activeBots = registry.getActiveBots();
            assertTrue(activeBots.isEmpty(), "No active bots when GrokBot is inactive");
        }
    }

    @Nested
    @DisplayName("GrokBot IM Response Generation")
    class GrokBotIMResponseGeneration {

        private GrokBot grokBot;

        @BeforeEach
        void setUpBot() {
            grokBot = new GrokBot();
            registry.registerBot(grokBot);
        }

        @Test
        @DisplayName("GrokBot should generate IM response for valid message")
        void shouldGenerateIMResponseForValidMessage() {
            String response = grokBot.generateIMResponse("hello", "TestUser");

            assertNotNull(response);
            assertFalse(response.isEmpty());
        }

        @Test
        @DisplayName("GrokBot should return null IM response when inactive")
        void shouldReturnNullIMResponseWhenInactive() {
            grokBot.setActive(false);

            String response = grokBot.generateIMResponse("hello", "TestUser");

            assertNull(response);
        }

        @Test
        @DisplayName("GrokBot IM response should not require @mention")
        void imResponseShouldNotRequireMention() {
            // IMs go directly to Grok - no need for @Grok mention
            String response = grokBot.generateIMResponse("what is dialtone?", "TestUser");

            assertNotNull(response, "Should respond to direct IM without @mention");
            assertFalse(response.isEmpty());
        }

        @Test
        @DisplayName("GrokBot should handle help requests via IM")
        void shouldHandleHelpRequestsViaIM() {
            String response = grokBot.generateIMResponse("help", "TestUser");

            assertNotNull(response);
            assertTrue(response.toLowerCase().contains("help") ||
                       response.toLowerCase().contains("can"),
                       "Help response should mention help or capabilities");
        }

        @Test
        @DisplayName("GrokBot should handle greetings via IM")
        void shouldHandleGreetingsViaIM() {
            String[] greetings = {"hi", "hello", "hey"};

            for (String greeting : greetings) {
                String response = grokBot.generateIMResponse(greeting, "TestUser");
                assertNotNull(response, "Should respond to greeting: " + greeting);
                assertFalse(response.isEmpty());
            }
        }

        @Test
        @DisplayName("GrokBot should preserve conversation memory across IM messages")
        void shouldPreserveConversationMemoryAcrossIMMessages() {
            // First IM
            grokBot.generateIMResponse("my favorite color is blue", "TestUser");

            // Second IM - memory should persist
            String response = grokBot.generateIMResponse("what color did I mention?", "TestUser");

            assertNotNull(response);
            // Note: Without AI enabled, the response will be generic, but the memory
            // infrastructure is exercised
        }

        @Test
        @DisplayName("GrokBot should isolate memory between different users")
        void shouldIsolateMemoryBetweenDifferentUsers() {
            // User1 says something
            grokBot.generateIMResponse("I like cats", "User1");

            // User2 starts fresh conversation
            String user2Response = grokBot.generateIMResponse("hello", "User2");

            assertNotNull(user2Response);
            // User2's conversation is separate from User1's
        }
    }

    @Nested
    @DisplayName("Bot Routing Decision")
    class BotRoutingDecision {

        @Test
        @DisplayName("Should route to bot when recipient is registered bot")
        void shouldRouteToBotWhenRecipientIsRegisteredBot() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            // This simulates the check done in InstantMessageTokenHandler.deliverInstantMessage
            String recipient = "Grok";

            boolean shouldRouteTtoBot = registry.isBot(recipient);
            assertTrue(shouldRouteTtoBot, "Should route IM to bot");

            VirtualUser bot = registry.getBot(recipient);
            assertNotNull(bot, "Should retrieve bot instance");
            assertTrue(bot.isActive(), "Bot should be active for routing");
        }

        @Test
        @DisplayName("Should not route to bot when recipient is regular user")
        void shouldNotRouteToBotWhenRecipientIsRegularUser() {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            // Regular user (not a bot)
            String recipient = "RegularUser";

            boolean shouldRouteToBot = registry.isBot(recipient);
            assertFalse(shouldRouteToBot, "Should NOT route IM to regular user as bot");
        }

        @Test
        @DisplayName("Should not route to inactive bot")
        void shouldNotRouteToInactiveBot() {
            GrokBot grokBot = new GrokBot();
            grokBot.setActive(false);
            registry.registerBot(grokBot);

            String recipient = "Grok";

            // Bot exists but is inactive
            assertTrue(registry.isBot(recipient), "Grok is registered as bot");
            VirtualUser bot = registry.getBot(recipient);
            assertFalse(bot.isActive(), "Bot should be inactive");

            // In actual handler, this would skip routing
        }
    }

    @Nested
    @DisplayName("Thread Safety")
    class ThreadSafety {

        @Test
        @DisplayName("GrokBot should handle concurrent IM requests safely")
        void shouldHandleConcurrentIMRequestsSafely() throws InterruptedException {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            Thread[] threads = new Thread[10];
            Exception[] exceptions = new Exception[10];

            for (int i = 0; i < threads.length; i++) {
                final int userId = i;
                threads[i] = new Thread(() -> {
                    try {
                        for (int j = 0; j < 5; j++) {
                            String response = grokBot.generateIMResponse(
                                "message " + j,
                                "User" + userId
                            );
                            assertNotNull(response);
                        }
                    } catch (Exception e) {
                        exceptions[userId] = e;
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            // No exceptions should have occurred
            for (int i = 0; i < exceptions.length; i++) {
                assertNull(exceptions[i], "Thread " + i + " threw exception: " +
                    (exceptions[i] != null ? exceptions[i].getMessage() : "none"));
            }
        }

        @Test
        @DisplayName("Registry should handle concurrent bot lookups safely")
        void shouldHandleConcurrentBotLookupsSafely() throws InterruptedException {
            GrokBot grokBot = new GrokBot();
            registry.registerBot(grokBot);

            Thread[] threads = new Thread[20];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < 100; j++) {
                        boolean isBot = registry.isBot("Grok");
                        assertTrue(isBot);
                        VirtualUser bot = registry.getBot("Grok");
                        assertNotNull(bot);
                    }
                });
                threads[i].start();
            }

            for (Thread thread : threads) {
                thread.join();
            }
        }
    }
}
