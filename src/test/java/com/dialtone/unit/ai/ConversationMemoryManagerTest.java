/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.ai;

import com.dialtone.ai.ConversationMemoryManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationMemoryManager.
 * Tests conversation history tracking, memory limits, and cleanup.
 */
class ConversationMemoryManagerTest {

    private ConversationMemoryManager memoryManager;

    @BeforeEach
    void setUp() {
        // Create memory manager with 5 turns (10 messages) and 1 minute timeout for testing
        memoryManager = new ConversationMemoryManager(5, 1);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (memoryManager != null) {
            memoryManager.close();
        }
    }

    @Test
    void shouldStartWithEmptyHistory() {
        // Given: New memory manager
        // When: Getting history for a user
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory("testuser");

        // Then: History should be empty
        assertTrue(history.isEmpty(), "New user should have empty history");
    }

    @Test
    void shouldAddUserMessage() {
        // Given: A user
        String username = "steve";
        String message = "Hello!";

        // When: Adding a user message
        memoryManager.addUserMessage(username, message);

        // Then: Message should be in history
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory(username);
        assertEquals(1, history.size(), "Should have 1 message");
        assertEquals("user", history.get(0).getRole(), "Role should be 'user'");
        assertEquals(message, history.get(0).getContent(), "Content should match");
    }

    @Test
    void shouldAddBotMessage() {
        // Given: A user
        String username = "steve";
        String message = "Hi! How can I help?";

        // When: Adding a bot message
        memoryManager.addBotMessage(username, message);

        // Then: Message should be in history
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory(username);
        assertEquals(1, history.size(), "Should have 1 message");
        assertEquals("assistant", history.get(0).getRole(), "Role should be 'assistant'");
        assertEquals(message, history.get(0).getContent(), "Content should match");
    }

    @Test
    void shouldMaintainConversationOrder() {
        // Given: A user having a conversation
        String username = "steve";

        // When: Adding multiple messages in order
        memoryManager.addUserMessage(username, "Hello!");
        memoryManager.addBotMessage(username, "Hi! How can I help?");
        memoryManager.addUserMessage(username, "What is Dialtone?");
        memoryManager.addBotMessage(username, "Dialtone is an AOL protocol server.");

        // Then: Messages should be in chronological order
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory(username);
        assertEquals(4, history.size(), "Should have 4 messages");
        assertEquals("user", history.get(0).getRole(), "First message should be user");
        assertEquals("Hello!", history.get(0).getContent());
        assertEquals("assistant", history.get(1).getRole(), "Second message should be bot");
        assertEquals("Hi! How can I help?", history.get(1).getContent());
        assertEquals("user", history.get(2).getRole(), "Third message should be user");
        assertEquals("What is Dialtone?", history.get(2).getContent());
        assertEquals("assistant", history.get(3).getRole(), "Fourth message should be bot");
        assertEquals("Dialtone is an AOL protocol server.", history.get(3).getContent());
    }

    @Test
    void shouldEnforceMaxTurnsLimit() {
        // Given: Memory manager with 5 turns (10 messages)
        String username = "steve";

        // When: Adding more than 10 messages (6 turns = 12 messages)
        for (int i = 0; i < 6; i++) {
            memoryManager.addUserMessage(username, "User message " + i);
            memoryManager.addBotMessage(username, "Bot response " + i);
        }

        // Then: Should only keep last 10 messages (5 turns)
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory(username);
        assertEquals(10, history.size(), "Should limit to 10 messages (5 turns)");

        // Verify oldest messages were removed
        assertEquals("User message 1", history.get(0).getContent(), "First message should be from turn 1");
        assertEquals("Bot response 5", history.get(9).getContent(), "Last message should be from turn 5");
    }

    @Test
    void shouldSeparateConversationsByUser() {
        // Given: Multiple users
        String user1 = "steve";
        String user2 = "root";

        // When: Each user has their own conversation
        memoryManager.addUserMessage(user1, "Steve's message");
        memoryManager.addBotMessage(user1, "Response to Steve");
        memoryManager.addUserMessage(user2, "Root's message");
        memoryManager.addBotMessage(user2, "Response to Root");

        // Then: Histories should be separate
        List<ConversationMemoryManager.Message> history1 = memoryManager.getHistory(user1);
        List<ConversationMemoryManager.Message> history2 = memoryManager.getHistory(user2);

        assertEquals(2, history1.size(), "Steve should have 2 messages");
        assertEquals(2, history2.size(), "Root should have 2 messages");
        assertEquals("Steve's message", history1.get(0).getContent());
        assertEquals("Root's message", history2.get(0).getContent());
    }

    @Test
    void shouldHandleCaseInsensitiveUsernames() {
        // Given: Same user with different casing
        // When: Adding messages with different username casing
        memoryManager.addUserMessage("Steve", "Message 1");
        memoryManager.addUserMessage("STEVE", "Message 2");
        memoryManager.addUserMessage("steve", "Message 3");

        // Then: All should be in same conversation history
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory("Steve");
        assertEquals(3, history.size(), "All messages should be in same history (case-insensitive)");
    }

    @Test
    void shouldClearSpecificUserHistory() {
        // Given: Multiple users with conversations
        memoryManager.addUserMessage("steve", "Steve's message");
        memoryManager.addUserMessage("root", "Root's message");

        // When: Clearing one user's history
        memoryManager.clearHistory("steve");

        // Then: Only that user's history should be cleared
        assertTrue(memoryManager.getHistory("steve").isEmpty(), "Steve's history should be cleared");
        assertEquals(1, memoryManager.getHistory("root").size(), "Root's history should remain");
    }

    @Test
    void shouldClearAllHistory() {
        // Given: Multiple users with conversations
        memoryManager.addUserMessage("steve", "Steve's message");
        memoryManager.addUserMessage("root", "Root's message");
        memoryManager.addUserMessage("guest", "Guest's message");

        // When: Clearing all history
        memoryManager.clearAllHistory();

        // Then: All histories should be cleared
        assertTrue(memoryManager.getHistory("steve").isEmpty());
        assertTrue(memoryManager.getHistory("root").isEmpty());
        assertTrue(memoryManager.getHistory("guest").isEmpty());
        assertEquals(0, memoryManager.getActiveConversationCount(), "No active conversations");
    }

    @Test
    void shouldTrackActiveConversationCount() {
        // Given: New memory manager
        assertEquals(0, memoryManager.getActiveConversationCount(), "Should start with 0");

        // When: Adding conversations
        memoryManager.addUserMessage("steve", "Hello");
        assertEquals(1, memoryManager.getActiveConversationCount(), "Should have 1 after first user");

        memoryManager.addUserMessage("root", "Hi");
        assertEquals(2, memoryManager.getActiveConversationCount(), "Should have 2 after second user");

        memoryManager.clearHistory("steve");
        // Note: clearHistory doesn't remove the conversation, just clears messages
        // Active count is based on conversations map, not message count
    }

    @Test
    void shouldHandleNullMessage() {
        // Given: Null message
        // When: Adding null message (should be converted to empty string)
        memoryManager.addUserMessage("steve", null);

        // Then: Should not throw, message should be empty
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory("steve");
        assertEquals(1, history.size());
        assertEquals("", history.get(0).getContent(), "Null message should become empty string");
    }

    @Test
    void shouldRejectNullUsername() {
        // When/Then: Should throw on null username
        assertThrows(IllegalArgumentException.class, () ->
                memoryManager.addUserMessage(null, "message"));

        assertThrows(IllegalArgumentException.class, () ->
                memoryManager.addBotMessage(null, "message"));
    }

    @Test
    void shouldRejectEmptyUsername() {
        // When/Then: Should throw on empty username
        assertThrows(IllegalArgumentException.class, () ->
                memoryManager.addUserMessage("", "message"));

        assertThrows(IllegalArgumentException.class, () ->
                memoryManager.addUserMessage("   ", "message"));
    }

    @Test
    void shouldReturnDefensiveCopyOfHistory() {
        // Given: User with history
        String username = "steve";
        memoryManager.addUserMessage(username, "Original message");

        // When: Getting history and modifying it
        List<ConversationMemoryManager.Message> history1 = memoryManager.getHistory(username);
        history1.clear(); // Try to clear the returned list

        // Then: Original history should be unaffected
        List<ConversationMemoryManager.Message> history2 = memoryManager.getHistory(username);
        assertEquals(1, history2.size(), "Original history should not be affected by modifications");
    }

    @Test
    void shouldIncludeTimestampInMessages() {
        // Given: User sending message
        long beforeTime = System.currentTimeMillis();
        memoryManager.addUserMessage("steve", "Test message");
        long afterTime = System.currentTimeMillis();

        // When: Getting history
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory("steve");

        // Then: Timestamp should be between before and after
        long timestamp = history.get(0).getTimestamp();
        assertTrue(timestamp >= beforeTime && timestamp <= afterTime,
                "Timestamp should be within expected range");
    }

    @Test
    void shouldValidateConstructorParameters() {
        // When/Then: Should reject invalid maxTurns
        assertThrows(IllegalArgumentException.class, () ->
                new ConversationMemoryManager(0, 30));

        assertThrows(IllegalArgumentException.class, () ->
                new ConversationMemoryManager(-1, 30));

        // Should reject invalid timeout
        assertThrows(IllegalArgumentException.class, () ->
                new ConversationMemoryManager(10, 0));

        assertThrows(IllegalArgumentException.class, () ->
                new ConversationMemoryManager(10, -1));
    }

    @Test
    void shouldHandleEmptyHistoryGracefully() {
        // Given: User with no history
        // When: Getting history for unknown user
        List<ConversationMemoryManager.Message> history = memoryManager.getHistory("nonexistent");

        // Then: Should return empty list, not null
        assertNotNull(history, "Should return non-null list");
        assertTrue(history.isEmpty(), "Should return empty list");
    }
}
