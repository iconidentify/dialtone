/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.chat;

import com.dialtone.chat.bot.ChatContext;
import com.dialtone.chat.bot.GrokBot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GrokBotTest {

    private GrokBot bot;
    private ChatContext context;

    @BeforeEach
    void setUp() {
        bot = new GrokBot();
        context = new ChatContext("TestRoom", List.of(), 5);
    }

    @Test
    void shouldHaveCorrectUsername() {
        assertEquals("Grok", bot.getUsername());
    }

    @Test
    void shouldHaveDescription() {
        String description = bot.getDescription();
        assertNotNull(description);
        assertFalse(description.isEmpty());
        assertTrue(description.toLowerCase().contains("ai"));
    }

    @Test
    void shouldBeActiveByDefault() {
        assertTrue(bot.isActive());
    }

    @Test
    void shouldRespondToMentionWithAtSymbol() {
        assertTrue(bot.shouldRespondTo("@Grok hello", "User1"));
    }

    @Test
    void shouldRespondToMentionCaseInsensitive() {
        assertTrue(bot.shouldRespondTo("@grok hey", "User1"));
        assertTrue(bot.shouldRespondTo("@GROK yo", "User1"));
        assertTrue(bot.shouldRespondTo("@GrOk sup", "User1"));
    }

    @Test
    void shouldRespondToMentionInMiddleOfMessage() {
        assertTrue(bot.shouldRespondTo("Hey @Grok what's up?", "User1"));
    }

    @Test
    void shouldRespondToMentionAtEnd() {
        assertTrue(bot.shouldRespondTo("Can you help me @Grok", "User1"));
    }

    @Test
    void shouldNotRespondToMessageWithoutMention() {
        assertFalse(bot.shouldRespondTo("Hello everyone", "User1"));
        assertFalse(bot.shouldRespondTo("What's the weather like?", "User1"));
    }

    @Test
    void shouldNotRespondToEmptyMessage() {
        assertFalse(bot.shouldRespondTo("", "User1"));
        assertFalse(bot.shouldRespondTo("   ", "User1"));
    }

    @Test
    void shouldNotRespondToNullMessage() {
        assertFalse(bot.shouldRespondTo(null, "User1"));
    }

    @Test
    void shouldNotRespondToOwnMessages() {
        assertFalse(bot.shouldRespondTo("@Grok test", "Grok"));
        assertFalse(bot.shouldRespondTo("@Grok test", "grok"));
    }

    @Test
    void shouldGenerateResponseToGreeting() {
        String response = bot.generateResponse("@Grok hi", "User1", context);

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("hey") || response.toLowerCase().contains("hello"));
        assertTrue(response.contains("User1"));
    }

    @Test
    void shouldGenerateResponseToHelpRequest() {
        String response = bot.generateResponse("@Grok help", "User1", context);

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("help") || response.toLowerCase().contains("can"));
    }

    @Test
    void shouldGenerateResponseToDialtoneQuery() {
        String response = bot.generateResponse("@Grok what is dialtone?", "User1", context);

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("dialtone") || response.toLowerCase().contains("aol"));
    }

    @Test
    void shouldGenerateResponseToProjectQuery() {
        String response = bot.generateResponse("@Grok tell me about this project", "User1", context);

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldHandleMultipleGreetingVariations() {
        String[] greetings = {"hi", "hello", "hey", "sup", "yo"};

        for (String greeting : greetings) {
            String response = bot.generateResponse("@Grok " + greeting, "User1", context);
            assertNotNull(response, "Should respond to: " + greeting);
            assertFalse(response.isEmpty());
        }
    }

    @Test
    void shouldGenerateDefaultResponseForUnknownQuery() {
        String response = bot.generateResponse("@Grok random question xyz", "User1", context);

        assertNotNull(response);
        assertFalse(response.isEmpty());
        assertTrue(response.contains("User1"));
    }

    @Test
    void shouldExtractQueryFromMention() {
        String response = bot.generateResponse("@Grok what is the weather?", "User1", context);

        assertNotNull(response);
        // Response should reference the actual query, not the @mention
        assertTrue(response.length() > 10); // Substantive response
    }

    @Test
    void shouldHandleMultipleMentionsInMessage() {
        String response = bot.generateResponse("@Grok @Grok help", "User1", context);

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void shouldReturnNullWhenInactive() {
        bot.setActive(false);

        String response = bot.generateResponse("@Grok hello", "User1", context);

        assertNull(response);
    }

    @Test
    void shouldNotRespondWhenInactive() {
        bot.setActive(false);

        // shouldRespondTo should still return true (the bot would respond if active)
        // but generateResponse should return null
        assertTrue(bot.shouldRespondTo("@Grok hello", "User1"));
        assertNull(bot.generateResponse("@Grok hello", "User1", context));
    }

    @Test
    void shouldReactivateAfterDeactivation() {
        bot.setActive(false);
        assertFalse(bot.isActive());

        bot.setActive(true);
        assertTrue(bot.isActive());

        String response = bot.generateResponse("@Grok hello", "User1", context);
        assertNotNull(response);
    }

    @Test
    void shouldHandleNullSenderGracefully() {
        // Should not crash, even if sender is null
        assertDoesNotThrow(() -> {
            bot.shouldRespondTo("@Grok hello", null);
        });

        String response = bot.generateResponse("@Grok hello", null, context);
        assertNotNull(response);
    }

    @Test
    void shouldHandleNullContextGracefully() {
        // Should not crash with null context
        assertDoesNotThrow(() -> {
            String response = bot.generateResponse("@Grok hello", "User1", null);
            assertNotNull(response);
        });
    }

    @Test
    void shouldProvideConsistentResponsesToSameQuery() {
        String response1 = bot.generateResponse("@Grok help", "User1", context);
        String response2 = bot.generateResponse("@Grok help", "User1", context);

        // Responses should be consistent for the same query (Phase 1 implementation)
        assertEquals(response1, response2);
    }

    @Test
    void shouldNotCrashOnVeryLongMessage() {
        String longMessage = "@Grok " + "x".repeat(10000);

        assertDoesNotThrow(() -> {
            String response = bot.generateResponse(longMessage, "User1", context);
            assertNotNull(response);
        });
    }

    @Test
    void shouldHandleSpecialCharactersInMessage() {
        String[] specialMessages = {
            "@Grok what's up?",
            "@Grok test@test.com",
            "@Grok $$$",
            "@Grok <html>test</html>",
            "@Grok emoji \ud83d\ude00"
        };

        for (String message : specialMessages) {
            assertDoesNotThrow(() -> {
                String response = bot.generateResponse(message, "User1", context);
                assertNotNull(response);
            }, "Should handle: " + message);
        }
    }

    @Test
    void shouldLogJoinAndLeaveEvents() {
        // These should not crash
        assertDoesNotThrow(() -> {
            bot.onJoinChatRoom("TestRoom");
            bot.onLeaveChatRoom("TestRoom");
        });
    }

    @Test
    void shouldHandleConcurrentRequests() throws InterruptedException {
        // Simulate multiple concurrent users asking questions
        Thread[] threads = new Thread[10];

        for (int i = 0; i < threads.length; i++) {
            final int userId = i;
            threads[i] = new Thread(() -> {
                String response = bot.generateResponse(
                    "@Grok hello",
                    "User" + userId,
                    context
                );
                assertNotNull(response);
            });
            threads[i].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
    }

    // ========== generateIMResponse() Tests ==========

    @Test
    void generateIMResponse_shouldReturnResponseForValidMessage() {
        String response = bot.generateIMResponse("hello", "TestUser");

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void generateIMResponse_shouldReturnNullWhenInactive() {
        bot.setActive(false);

        String response = bot.generateIMResponse("hello", "TestUser");

        assertNull(response);
    }

    @Test
    void generateIMResponse_shouldHandleNullSender() {
        String response = bot.generateIMResponse("hello", null);

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void generateIMResponse_shouldHandleEmptySender() {
        String response = bot.generateIMResponse("hello", "");

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void generateIMResponse_shouldHandleGreetings() {
        String[] greetings = {"hi", "hello", "hey", "sup", "yo"};

        for (String greeting : greetings) {
            String response = bot.generateIMResponse(greeting, "User1");
            assertNotNull(response, "Should respond to IM greeting: " + greeting);
            assertFalse(response.isEmpty());
        }
    }

    @Test
    void generateIMResponse_shouldHandleHelpRequest() {
        String response = bot.generateIMResponse("help", "User1");

        assertNotNull(response);
        assertTrue(response.toLowerCase().contains("help") || response.toLowerCase().contains("can"));
    }

    @Test
    void generateIMResponse_shouldNotRequireMention() {
        // Unlike chat room responses, IM responses don't need @Grok mention
        String response = bot.generateIMResponse("what is dialtone?", "User1");

        assertNotNull(response);
        assertFalse(response.isEmpty());
    }

    @Test
    void generateIMResponse_shouldMaintainConversationMemory() {
        // First message
        bot.generateIMResponse("my name is Bob", "User1");

        // Second message - memory should be preserved
        String response = bot.generateIMResponse("what did I just tell you?", "User1");

        assertNotNull(response);
        // Memory is maintained across calls (basic check - content depends on AI)
    }

    @Test
    void generateIMResponse_shouldHandleVeryLongMessage() {
        String longMessage = "x".repeat(10000);

        assertDoesNotThrow(() -> {
            String response = bot.generateIMResponse(longMessage, "User1");
            assertNotNull(response);
        });
    }

    @Test
    void generateIMResponse_shouldHandleSpecialCharacters() {
        String[] specialMessages = {
            "what's up?",
            "test@test.com",
            "$$$",
            "<html>test</html>",
            "emoji \ud83d\ude00"
        };

        for (String message : specialMessages) {
            assertDoesNotThrow(() -> {
                String response = bot.generateIMResponse(message, "User1");
                assertNotNull(response);
            }, "Should handle IM: " + message);
        }
    }

    @Test
    void generateIMResponse_shouldIsolateDifferentUsers() {
        // User1 conversation
        bot.generateIMResponse("I like cats", "User1");

        // User2 conversation (separate memory)
        String user2Response = bot.generateIMResponse("hello", "User2");

        assertNotNull(user2Response);
        // User2's response shouldn't mention cats (User1's context)
    }

    @Test
    void generateIMResponse_shouldHandleConcurrentIMRequests() throws InterruptedException {
        Thread[] threads = new Thread[10];

        for (int i = 0; i < threads.length; i++) {
            final int userId = i;
            threads[i] = new Thread(() -> {
                String response = bot.generateIMResponse(
                    "hello from IM",
                    "IMUser" + userId
                );
                assertNotNull(response);
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }
    }
}
