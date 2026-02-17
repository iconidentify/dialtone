/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.chat;

import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.ChatContext;
import com.dialtone.chat.bot.VirtualUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ChatBotRegistryTest {

    private ChatBotRegistry registry;
    private TestBot bot1;
    private TestBot bot2;

    @BeforeEach
    void setUp() {
        registry = ChatBotRegistry.getInstance();
        registry.clear();

        bot1 = new TestBot("TestBot1");
        bot2 = new TestBot("TestBot2");
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void shouldRegisterBot() {
        registry.registerBot(bot1);

        assertTrue(registry.isBot("TestBot1"));
        assertEquals(1, registry.getBotCount());
        assertSame(bot1, registry.getBot("TestBot1"));
    }

    @Test
    void shouldUnregisterBot() {
        registry.registerBot(bot1);
        assertTrue(registry.isBot("TestBot1"));

        boolean removed = registry.unregisterBot("TestBot1");

        assertTrue(removed);
        assertFalse(registry.isBot("TestBot1"));
        assertEquals(0, registry.getBotCount());
    }

    @Test
    void shouldReturnFalseWhenUnregisteringNonexistentBot() {
        boolean removed = registry.unregisterBot("NonexistentBot");

        assertFalse(removed);
    }

    @Test
    void shouldSupportMultipleBots() {
        registry.registerBot(bot1);
        registry.registerBot(bot2);

        assertTrue(registry.isBot("TestBot1"));
        assertTrue(registry.isBot("TestBot2"));
        assertEquals(2, registry.getBotCount());
    }

    @Test
    void shouldBeCaseInsensitiveForLookup() {
        registry.registerBot(bot1);

        assertTrue(registry.isBot("testbot1"));
        assertTrue(registry.isBot("TESTBOT1"));
        assertTrue(registry.isBot("TeStBoT1"));

        VirtualUser bot = registry.getBot("testbot1");
        assertNotNull(bot);
        assertEquals("TestBot1", bot.getUsername());
    }

    @Test
    void shouldReplaceExistingBot() {
        registry.registerBot(bot1);
        assertEquals(1, registry.getBotCount());

        TestBot replacementBot = new TestBot("TestBot1");
        registry.registerBot(replacementBot);

        assertEquals(1, registry.getBotCount());
        assertSame(replacementBot, registry.getBot("TestBot1"));
        assertNotSame(bot1, registry.getBot("TestBot1"));
    }

    @Test
    void shouldGetAllBots() {
        registry.registerBot(bot1);
        registry.registerBot(bot2);

        List<VirtualUser> bots = registry.getAllBots();

        assertEquals(2, bots.size());
        assertTrue(bots.contains(bot1));
        assertTrue(bots.contains(bot2));
    }

    @Test
    void shouldGetActiveBots() {
        bot1.setActive(true);
        bot2.setActive(false);

        registry.registerBot(bot1);
        registry.registerBot(bot2);

        List<VirtualUser> activeBots = registry.getActiveBots();

        assertEquals(1, activeBots.size());
        assertTrue(activeBots.contains(bot1));
        assertFalse(activeBots.contains(bot2));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringNullBot() {
        assertThrows(IllegalArgumentException.class, () -> registry.registerBot(null));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringBotWithNullUsername() {
        VirtualUser invalidBot = new TestBot(null);

        assertThrows(IllegalArgumentException.class, () -> registry.registerBot(invalidBot));
    }

    @Test
    void shouldThrowExceptionWhenRegisteringBotWithEmptyUsername() {
        VirtualUser invalidBot = new TestBot("");

        assertThrows(IllegalArgumentException.class, () -> registry.registerBot(invalidBot));
    }

    @Test
    void shouldProcessMessageAndGenerateResponses() {
        bot1.setShouldRespond(true);
        registry.registerBot(bot1);

        ChatContext context = new ChatContext("TestRoom", List.of(), 2);

        CompletableFuture<List<ChatBotRegistry.BotResponse>> future =
                registry.processMessage("Hello bot!", "User1", context);

        List<ChatBotRegistry.BotResponse> responses = future.join();

        assertEquals(1, responses.size());
        assertEquals("TestBot1", responses.get(0).botUsername());
        assertTrue(responses.get(0).hasContent());
    }

    @Test
    void shouldNotRespondWhenBotShouldNotRespond() {
        bot1.setShouldRespond(false);
        registry.registerBot(bot1);

        ChatContext context = new ChatContext("TestRoom", List.of(), 2);

        CompletableFuture<List<ChatBotRegistry.BotResponse>> future =
                registry.processMessage("Hello", "User1", context);

        List<ChatBotRegistry.BotResponse> responses = future.join();

        assertTrue(responses.isEmpty());
    }

    @Test
    void shouldNotRespondToBotMessages() {
        bot1.setShouldRespond(true);
        registry.registerBot(bot1);
        registry.registerBot(bot2);

        ChatContext context = new ChatContext("TestRoom", List.of(), 2);

        // Bot1 sends a message
        CompletableFuture<List<ChatBotRegistry.BotResponse>> future =
                registry.processMessage("Hello", "TestBot2", context);

        List<ChatBotRegistry.BotResponse> responses = future.join();

        // No responses expected (bots don't respond to other bots)
        assertTrue(responses.isEmpty());
    }

    @Test
    void shouldHandleMultipleBotResponses() {
        bot1.setShouldRespond(true);
        bot2.setShouldRespond(true);
        registry.registerBot(bot1);
        registry.registerBot(bot2);

        ChatContext context = new ChatContext("TestRoom", List.of(), 3);

        CompletableFuture<List<ChatBotRegistry.BotResponse>> future =
                registry.processMessage("@everyone", "User1", context);

        List<ChatBotRegistry.BotResponse> responses = future.join();

        assertEquals(2, responses.size());
    }

    @Test
    void shouldClearAllBots() {
        registry.registerBot(bot1);
        registry.registerBot(bot2);
        assertEquals(2, registry.getBotCount());

        registry.clear();

        assertEquals(0, registry.getBotCount());
        assertFalse(registry.isBot("TestBot1"));
        assertFalse(registry.isBot("TestBot2"));
    }

    @Test
    void shouldReturnSingletonInstance() {
        ChatBotRegistry instance1 = ChatBotRegistry.getInstance();
        ChatBotRegistry instance2 = ChatBotRegistry.getInstance();

        assertSame(instance1, instance2);
    }

    /**
     * Simple test bot for unit testing.
     */
    static class TestBot implements VirtualUser {
        private final String username;
        private boolean shouldRespond = false;
        private boolean active = true;

        TestBot(String username) {
            this.username = username;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean shouldRespondTo(String message, String sender) {
            return shouldRespond;
        }

        @Override
        public String generateResponse(String message, String sender, ChatContext context) {
            return "Response from " + username;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        void setShouldRespond(boolean shouldRespond) {
            this.shouldRespond = shouldRespond;
        }

        void setActive(boolean active) {
            this.active = active;
        }
    }
}
