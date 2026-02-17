/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.im;

import com.dialtone.protocol.im.ConversationIdManager;
import com.dialtone.protocol.im.ConversationIdManager.ConversationParticipants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConversationIdManager.
 * Tests symmetric conversation ID generation, participant lookup, and thread safety.
 */
@DisplayName("ConversationIdManager")
class ConversationIdManagerTest {

    private ConversationIdManager manager;

    @BeforeEach
    void setUp() {
        manager = ConversationIdManager.getInstance();
        manager.clear(); // Reset state between tests
    }

    @Nested
    @DisplayName("getOrCreateConversationId()")
    class GetOrCreateConversationId {

        @Test
        @DisplayName("should generate conversation ID for user pair")
        void shouldGenerateConversationId() {
            int id = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
            assertTrue(id >= 10000 && id <= 65535, "ID should be in valid range");
        }

        @Test
        @DisplayName("should be symmetric - same ID regardless of order")
        void shouldBeSymmetric() {
            int id1 = manager.getOrCreateConversationId("Bobby", "TOSAdvisor");
            int id2 = manager.getOrCreateConversationId("TOSAdvisor", "Bobby");
            assertEquals(id1, id2, "Same ID should be returned regardless of user order");
        }

        @Test
        @DisplayName("should return same ID for existing conversation")
        void shouldReturnSameIdForExisting() {
            int id1 = manager.getOrCreateConversationId("Alice", "Bob");
            int id2 = manager.getOrCreateConversationId("Alice", "Bob");
            assertEquals(id1, id2, "Should return existing conversation ID");
        }

        @Test
        @DisplayName("should generate different IDs for different pairs")
        void shouldGenerateDifferentIds() {
            int id1 = manager.getOrCreateConversationId("Alice", "Bob");
            int id2 = manager.getOrCreateConversationId("Alice", "Charlie");
            int id3 = manager.getOrCreateConversationId("Bob", "Charlie");

            assertNotEquals(id1, id2);
            assertNotEquals(id1, id3);
            assertNotEquals(id2, id3);
        }

        @Test
        @DisplayName("should throw on null user1")
        void shouldThrowOnNullUser1() {
            assertThrows(IllegalArgumentException.class,
                () -> manager.getOrCreateConversationId(null, "Bob"));
        }

        @Test
        @DisplayName("should throw on null user2")
        void shouldThrowOnNullUser2() {
            assertThrows(IllegalArgumentException.class,
                () -> manager.getOrCreateConversationId("Alice", null));
        }

        @Test
        @DisplayName("should throw on self-conversation")
        void shouldThrowOnSelfConversation() {
            assertThrows(IllegalArgumentException.class,
                () -> manager.getOrCreateConversationId("Alice", "Alice"));
        }

        @Test
        @DisplayName("should be thread-safe")
        void shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int pairsPerThread = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<Integer> allIds = java.util.Collections.synchronizedSet(new HashSet<>());

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < pairsPerThread; i++) {
                            String user1 = "User" + threadId;
                            String user2 = "User" + (threadId + 100 + i);
                            int id = manager.getOrCreateConversationId(user1, user2);
                            allIds.add(id);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(threadCount * pairsPerThread, allIds.size(), "All IDs should be unique");
            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("hasConversation()")
    class HasConversation {

        @Test
        @DisplayName("should return true for existing conversation")
        void shouldReturnTrueForExisting() {
            manager.getOrCreateConversationId("Alice", "Bob");
            assertTrue(manager.hasConversation("Alice", "Bob"));
        }

        @Test
        @DisplayName("should be symmetric")
        void shouldBeSymmetric() {
            manager.getOrCreateConversationId("Alice", "Bob");
            assertTrue(manager.hasConversation("Bob", "Alice"));
        }

        @Test
        @DisplayName("should return false for non-existing conversation")
        void shouldReturnFalseForNonExisting() {
            assertFalse(manager.hasConversation("Alice", "Bob"));
        }

        @Test
        @DisplayName("should return false for null user1")
        void shouldReturnFalseForNullUser1() {
            assertFalse(manager.hasConversation(null, "Bob"));
        }

        @Test
        @DisplayName("should return false for null user2")
        void shouldReturnFalseForNullUser2() {
            assertFalse(manager.hasConversation("Alice", null));
        }

        @Test
        @DisplayName("should return false for self-conversation")
        void shouldReturnFalseForSelfConversation() {
            assertFalse(manager.hasConversation("Alice", "Alice"));
        }
    }

    @Nested
    @DisplayName("getConversationParticipants()")
    class GetConversationParticipants {

        @Test
        @DisplayName("should return participants for valid ID")
        void shouldReturnParticipants() {
            int id = manager.getOrCreateConversationId("Alice", "Bob");
            ConversationParticipants participants = manager.getConversationParticipants(id);

            assertNotNull(participants);
            assertTrue(participants.contains("Alice"));
            assertTrue(participants.contains("Bob"));
        }

        @Test
        @DisplayName("should return null for unknown ID")
        void shouldReturnNullForUnknownId() {
            assertNull(manager.getConversationParticipants(99999));
        }
    }

    @Nested
    @DisplayName("getOtherParticipant()")
    class GetOtherParticipant {

        @Test
        @DisplayName("should return other participant")
        void shouldReturnOtherParticipant() {
            int id = manager.getOrCreateConversationId("Alice", "Bob");

            assertEquals("Bob", manager.getOtherParticipant(id, "Alice"));
            assertEquals("Alice", manager.getOtherParticipant(id, "Bob"));
        }

        @Test
        @DisplayName("should return null for unknown ID")
        void shouldReturnNullForUnknownId() {
            assertNull(manager.getOtherParticipant(99999, "Alice"));
        }

        @Test
        @DisplayName("should return null if user not in conversation")
        void shouldReturnNullIfUserNotInConversation() {
            int id = manager.getOrCreateConversationId("Alice", "Bob");
            assertNull(manager.getOtherParticipant(id, "Charlie"));
        }
    }

    @Nested
    @DisplayName("removeConversation()")
    class RemoveConversation {

        @Test
        @DisplayName("should remove conversation by ID")
        void shouldRemoveConversation() {
            int id = manager.getOrCreateConversationId("Alice", "Bob");
            assertEquals(1, manager.size());

            manager.removeConversation(id);
            assertEquals(0, manager.size());
            assertFalse(manager.hasConversation("Alice", "Bob"));
        }

        @Test
        @DisplayName("should handle removing non-existent ID gracefully")
        void shouldHandleNonExistentId() {
            assertDoesNotThrow(() -> manager.removeConversation(99999));
        }
    }

    @Nested
    @DisplayName("removeConversationsForUser()")
    class RemoveConversationsForUser {

        @Test
        @DisplayName("should remove all conversations for user")
        void shouldRemoveAllForUser() {
            manager.getOrCreateConversationId("Alice", "Bob");
            manager.getOrCreateConversationId("Alice", "Charlie");
            manager.getOrCreateConversationId("Bob", "Charlie");
            assertEquals(3, manager.size());

            manager.removeConversationsForUser("Alice");
            assertEquals(1, manager.size()); // Only Bob-Charlie remains
            assertFalse(manager.hasConversation("Alice", "Bob"));
            assertFalse(manager.hasConversation("Alice", "Charlie"));
            assertTrue(manager.hasConversation("Bob", "Charlie"));
        }

        @Test
        @DisplayName("should handle null gracefully")
        void shouldHandleNull() {
            assertDoesNotThrow(() -> manager.removeConversationsForUser(null));
        }
    }

    @Nested
    @DisplayName("clear()")
    class Clear {

        @Test
        @DisplayName("should clear all conversations")
        void shouldClearAll() {
            manager.getOrCreateConversationId("Alice", "Bob");
            manager.getOrCreateConversationId("Alice", "Charlie");
            assertEquals(2, manager.size());

            manager.clear();
            assertEquals(0, manager.size());
        }
    }

    @Nested
    @DisplayName("ConversationParticipants")
    class ConversationParticipantsTests {

        @Test
        @DisplayName("contains() should return true for participant")
        void containsShouldReturnTrue() {
            ConversationParticipants participants = new ConversationParticipants("Alice", "Bob");
            assertTrue(participants.contains("Alice"));
            assertTrue(participants.contains("Bob"));
        }

        @Test
        @DisplayName("contains() should return false for non-participant")
        void containsShouldReturnFalse() {
            ConversationParticipants participants = new ConversationParticipants("Alice", "Bob");
            assertFalse(participants.contains("Charlie"));
        }

        @Test
        @DisplayName("getParticipants() should return both users")
        void getParticipantsShouldReturnBoth() {
            ConversationParticipants participants = new ConversationParticipants("Alice", "Bob");
            Set<String> set = participants.getParticipants();

            assertEquals(2, set.size());
            assertTrue(set.contains("Alice"));
            assertTrue(set.contains("Bob"));
        }

        @Test
        @DisplayName("toString() should format nicely")
        void toStringShouldFormat() {
            ConversationParticipants participants = new ConversationParticipants("Alice", "Bob");
            String str = participants.toString();

            assertTrue(str.contains("Alice"));
            assertTrue(str.contains("Bob"));
        }
    }
}
