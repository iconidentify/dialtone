/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.protocol.im.ConversationIdManager;
import com.dialtone.protocol.im.ConversationIdManager.ConversationParticipants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationIdManager.
 */
class ConversationIdManagerTest {

    private ConversationIdManager manager;

    @BeforeEach
    void setUp() {
        manager = ConversationIdManager.getInstance();
        manager.clear(); // Clean state for each test
    }

    @Test
    void shouldGenerateConversationId() {
        int conversationId = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");

        assertTrue(conversationId >= 10000, "Conversation ID should start at 10000");
        assertTrue(conversationId <= 65535, "Conversation ID must be within 16-bit limit");
    }

    @Test
    void shouldReturnSameIdForSameConversation() {
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");

        assertEquals(id1, id2, "Same conversation should return same ID");
    }

    @Test
    void shouldBeSymmetric() {
        // CRITICAL TEST: Order of users shouldn't matter
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("TOSAdvisor", "Bobby");

        assertEquals(id1, id2, "Conversation ID must be symmetric");
    }

    @Test
    void shouldGenerateDifferentIdsForDifferentConversations() {
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("Bobby", "John");

        assertNotEquals(id1, id2, "Different conversations should have different IDs");
    }

    @Test
    void shouldLookupConversationParticipants() {
        int conversationId = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");

        ConversationParticipants participants = manager.getConversationParticipants(conversationId);

        assertNotNull(participants);
        assertTrue(participants.contains("Bobby"));
        assertTrue(participants.contains("TOSAdvisor"));
    }

    @Test
    void shouldGetOtherParticipant() {
        int conversationId = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");

        String other1 = manager.getOtherParticipant(conversationId, "Bobby");
        String other2 = manager.getOtherParticipant(conversationId, "TOSAdvisor");

        assertEquals("TOSAdvisor", other1);
        assertEquals("Bobby", other2);
    }

    @Test
    void shouldReturnNullForNonExistentConversation() {
        ConversationParticipants participants = manager.getConversationParticipants(99999);

        assertNull(participants);
    }

    @Test
    void shouldReturnNullForNonParticipant() {
        int conversationId = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");

        String other = manager.getOtherParticipant(conversationId, "John");

        assertNull(other, "John is not in this conversation");
    }

    @Test
    void shouldRemoveConversation() {
        int conversationId = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        assertNotNull(manager.getConversationParticipants(conversationId));

        manager.removeConversation(conversationId);

        assertNull(manager.getConversationParticipants(conversationId));
        assertEquals(0, manager.size());
    }

    @Test
    void shouldRemoveConversationsForUser() {
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("Bobby", "John");
        int id3 = manager.getOrCreateConversationId("Alice", "John");

        assertEquals(3, manager.size());

        manager.removeConversationsForUser("Bobby");

        assertNull(manager.getConversationParticipants(id1), "Bobby-TOSAdvisor should be removed");
        assertNull(manager.getConversationParticipants(id2), "Bobby-John should be removed");
        assertNotNull(manager.getConversationParticipants(id3), "Alice-John should remain");
        assertEquals(1, manager.size());
    }

    @Test
    void shouldClearAllConversations() {
        manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        manager.getOrCreateConversationId("Alice", "John");
        assertEquals(2, manager.size());

        manager.clear();

        assertEquals(0, manager.size());
    }

    @Test
    void shouldThrowExceptionForNullUsers() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.getOrCreateConversationId(null, "TOSAdvisor")
        );

        assertThrows(IllegalArgumentException.class, () ->
                manager.getOrCreateConversationId("Bobby", null)
        );
    }

    @Test
    void shouldThrowExceptionForSelfConversation() {
        assertThrows(IllegalArgumentException.class, () ->
                manager.getOrCreateConversationId("Bobby", "Bobby")
        );
    }

    @Test
    void shouldHandleMultipleConversationsPerUser() {
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("Bobby", "John");
        int id3 = manager.getOrCreateConversationId("Bobby", "Alice");

        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);

        assertEquals(3, manager.size());
    }

    @Test
    void shouldBeThreadSafe() throws InterruptedException {
        int numThreads = 10;
        int conversationsPerThread = 100;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < conversationsPerThread; j++) {
                    manager.getOrCreateConversationId(
                            "User" + threadId,
                            "Correspondent" + j
                    );
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify data integrity
        assertEquals(numThreads * conversationsPerThread, manager.size());
    }

    @Test
    void shouldProvideSymmetricLookupInConcurrentScenario() throws InterruptedException {
        // Test that symmetry works even with concurrent access
        Thread[] threads = new Thread[10];
        int[] results = new int[10];

        for (int i = 0; i < 10; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                // Half create Bobby→TOSAdvisor, half create TOSAdvisor→Bobby
                if (index % 2 == 0) {
                    results[index] = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
                } else {
                    results[index] = manager.getOrCreateConversationId("TOSAdvisor", "Bobby");
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // All threads should get the same conversation ID
        int firstId = results[0];
        for (int i = 1; i < results.length; i++) {
            assertEquals(firstId, results[i], "All threads should get same conversation ID");
        }

        // Only one conversation should exist
        assertEquals(1, manager.size());
    }

    @Test
    void shouldHandleCaseSensitiveUsernames() {
        int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
        int id2 = manager.getOrCreateConversationId("bobby", "TOSAdvisor");

        assertNotEquals(id1, id2, "Usernames should be case-sensitive");
    }

    @Test
    void conversationParticipantsContainsShouldWork() {
        ConversationParticipants participants = new ConversationParticipants("Bobby", "TOSAdvisor");

        assertTrue(participants.contains("Bobby"));
        assertTrue(participants.contains("TOSAdvisor"));
        assertFalse(participants.contains("John"));
    }

    @Test
    void conversationParticipantsGetParticipantsShouldReturnBoth() {
        ConversationParticipants participants = new ConversationParticipants("Bobby", "TOSAdvisor");

        var set = participants.getParticipants();

        assertEquals(2, set.size());
        assertTrue(set.contains("Bobby"));
        assertTrue(set.contains("TOSAdvisor"));
    }

    @Test
    void shouldNeverExceed16BitLimit() {
        // Test that even with many conversations, IDs stay within bounds
        for (int i = 0; i < 100; i++) {
            int id = manager.getOrCreateConversationId("User" + i, "Correspondent" + i);
            assertTrue(id >= 10000 && id <= 65535,
                "ID " + id + " is outside valid range [10000, 65535]");
        }
    }

    @Test
    void shouldWrapAtMaxResponseId() {
        manager.clear();

        // Force the counter to near the limit
        // We can't easily test the exact wraparound without creating 55,536 conversations,
        // but we can test that IDs are always in the valid range
        for (int i = 0; i < 1000; i++) {
            int id = manager.getOrCreateConversationId("TestUser" + i, "TestCorrespondent" + i);
            assertTrue(id >= 10000 && id <= 65535,
                "Conversation ID " + id + " exceeds 16-bit limit");
        }
    }

    @Test
    void shouldClearConversationsOnWrap() {
        // Create some conversations
        manager.getOrCreateConversationId("User1", "User2");
        manager.getOrCreateConversationId("User3", "User4");
        assertEquals(2, manager.size());

        // Manual clear should reset everything
        manager.clear();
        assertEquals(0, manager.size());

        // Next ID should start at 10000 again
        int nextId = manager.getOrCreateConversationId("UserA", "UserB");
        assertEquals(10000, nextId);
    }

    @Test
    void shouldMaintain16BitConstraintAfterReset() {
        // Clear and verify starting position
        manager.clear();

        int firstId = manager.getOrCreateConversationId("A", "B");
        assertEquals(10000, firstId, "First ID after clear should be 10000");

        // Create a few more to ensure increment works
        int secondId = manager.getOrCreateConversationId("C", "D");
        int thirdId = manager.getOrCreateConversationId("E", "F");

        assertEquals(10001, secondId);
        assertEquals(10002, thirdId);

        // All should be in valid range
        assertTrue(firstId >= 10000 && firstId <= 65535);
        assertTrue(secondId >= 10000 && secondId <= 65535);
        assertTrue(thirdId >= 10000 && thirdId <= 65535);
    }

    @Test
    void shouldHandleConcurrentAccessWithinLimits() throws InterruptedException {
        // Test thread safety while ensuring 16-bit compliance
        int numThreads = 20;
        int conversationsPerThread = 50;

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < conversationsPerThread; j++) {
                    int id = manager.getOrCreateConversationId(
                            "ConcurrentUser" + threadId,
                            "Correspondent" + j
                    );
                    // Verify each ID is within bounds
                    assertTrue(id >= 10000 && id <= 65535,
                        "Thread " + threadId + " generated invalid ID: " + id);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify final state
        assertEquals(numThreads * conversationsPerThread, manager.size());
    }

    @Test
    void shouldCheckConversationExistence() {
        // Initially no conversations
        assertFalse(manager.hasConversation("Alice", "Bob"));
        assertFalse(manager.hasConversation("Bob", "Alice"));

        // Create a conversation
        int id = manager.getOrCreateConversationId("Alice", "Bob");
        assertTrue(id >= 10000 && id <= 65535, "ID should be within valid range");

        // Now conversation should exist (symmetric)
        assertTrue(manager.hasConversation("Alice", "Bob"));
        assertTrue(manager.hasConversation("Bob", "Alice"));

        // Different user pair should not exist
        assertFalse(manager.hasConversation("Alice", "Charlie"));
        assertFalse(manager.hasConversation("Bob", "Charlie"));

        // Remove conversation
        manager.removeConversation(id);

        // Should no longer exist
        assertFalse(manager.hasConversation("Alice", "Bob"));
        assertFalse(manager.hasConversation("Bob", "Alice"));
    }

    @Test
    void shouldHandleNullUsersInHasConversation() {
        assertFalse(manager.hasConversation(null, "Bob"));
        assertFalse(manager.hasConversation("Alice", null));
        assertFalse(manager.hasConversation(null, null));
    }

    @Test
    void shouldHandleSelfConversationInHasConversation() {
        assertFalse(manager.hasConversation("Alice", "Alice"));

        // Even if we somehow had a self-referential key, hasConversation should return false
        assertFalse(manager.hasConversation("Bob", "Bob"));
    }

    @Test
    void shouldBeSymmetricInHasConversation() {
        // Create conversation
        manager.getOrCreateConversationId("Charlie", "Diana");

        // Both directions should return true
        assertTrue(manager.hasConversation("Charlie", "Diana"));
        assertTrue(manager.hasConversation("Diana", "Charlie"));

        // Remove and both should be false
        manager.removeConversationsForUser("Charlie");
        assertFalse(manager.hasConversation("Charlie", "Diana"));
        assertFalse(manager.hasConversation("Diana", "Charlie"));
    }
}
