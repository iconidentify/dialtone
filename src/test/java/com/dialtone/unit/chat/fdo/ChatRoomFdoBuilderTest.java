/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.chat.fdo;

import com.dialtone.chat.ChatRoom;
import com.dialtone.chat.fdo.ChatRoomFdoBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ChatRoomFdoBuilder using the FdoScript DSL.
 * These tests verify that the DSL-based builder produces correct FDO source.
 */
class ChatRoomFdoBuilderTest {

    @Test
    void shouldBuildFdoWithSingleUser() {
        // Given: Chat room with one user
        ChatRoom room = new ChatRoom("Solo Chat");
        room.addUser("Alice");

        // When: Building FDO source with Alice's relative tag = 3
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("Alice", 3);
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Should contain expected structure
        assertNotNull(fdo);
        assertFalse(fdo.isEmpty());

        // Validate header with title (format: "Title - [Screenname]")
        assertTrue(fdo.contains("uni_start_stream"), "Should have stream start");
        assertTrue(fdo.contains("Solo Chat - [TestUser]"), "Should have title with screenname");
        assertTrue(fdo.contains("chat_room_open"), "Should open chat room");

        // Validate single user entry with relative tag
        assertTrue(fdo.contains("chat_add_user"), "Should have user Alice");
        assertTrue(fdo.contains("Alice"), "Should contain Alice's name");
        assertTrue(fdo.contains("mat_relative_tag"), "Should have relative tag");
        assertTrue(fdo.contains("chat_end_object"), "Should end chat object");

        // Validate footer
        assertTrue(fdo.contains("uni_wait_off"), "Should have wait off");
        assertTrue(fdo.contains("uni_end_stream"), "Should have stream end");
    }

    @Test
    void shouldBuildFdoWithMultipleUsers() {
        // Given: Chat room with three users, each with their own unique tag
        ChatRoom room = new ChatRoom("Group Chat");
        room.addUser("Alice");
        room.addUser("Bob");
        room.addUser("Charlie");

        // When: Building FDO source with unique tags for each user
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("Alice", 2);
        userTagMap.put("Bob", 5);
        userTagMap.put("Charlie", 7);
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Should contain all users
        assertTrue(fdo.contains("Group Chat - [TestUser]"), "Should have title with screenname");
        assertTrue(fdo.contains("Alice"), "Should have user Alice");
        assertTrue(fdo.contains("Bob"), "Should have user Bob");
        assertTrue(fdo.contains("Charlie"), "Should have user Charlie");

        // Validate structure
        assertEquals(3, countOccurrences(fdo, "chat_add_user"), "Should have 3 user entries");
        assertEquals(3, countOccurrences(fdo, "mat_relative_tag"), "Should have 3 tag entries");
        assertEquals(3, countOccurrences(fdo, "chat_end_object"), "Should have 3 end objects");
    }

    @Test
    void shouldBuildFdoWithManyUsers() {
        // Given: Chat room with 10 users, each with unique tag
        ChatRoom room = new ChatRoom("Big Chat");
        Map<String, Integer> userTagMap = new HashMap<>();
        for (int i = 1; i <= 10; i++) {
            room.addUser("User" + i);
            userTagMap.put("User" + i, i + 10); // Tags: 11, 12, 13, ... 20
        }

        // When: Building FDO source
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Should contain all 10 users
        for (int i = 1; i <= 10; i++) {
            assertTrue(fdo.contains("User" + i), "Should have User" + i);
        }

        assertEquals(10, countOccurrences(fdo, "chat_add_user"), "Should have 10 user entries");
        assertEquals(10, countOccurrences(fdo, "mat_relative_tag"), "Should have 10 tag entries");
        assertEquals(10, countOccurrences(fdo, "chat_end_object"), "Should have 10 end objects");
    }

    @Test
    void shouldHandleTitleWithSpaces() {
        // Given: Chat room with title containing spaces
        ChatRoom room = new ChatRoom("My Awesome Chat Room");
        room.addUser("User1");

        // When: Building FDO source
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("User1", 2);
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Title should be preserved with spaces
        assertTrue(fdo.contains("My Awesome Chat Room - [TestUser]"),
            "Title with spaces should be preserved");
    }

    @Test
    void shouldHandleUsernameWithSpaces() {
        // Given: Chat room with username containing spaces
        ChatRoom room = new ChatRoom("Test Room");
        room.addUser("Steve Case");

        // When: Building FDO source
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("Steve Case", 4);
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Username should be preserved with spaces
        assertTrue(fdo.contains("Steve Case"), "Username with spaces should be preserved");
    }

    @Test
    void shouldProduceConsistentOutput() {
        // Given: Same chat room built twice
        ChatRoom room = new ChatRoom("Test");
        room.addUser("Alice");
        room.addUser("Bob");

        // When: Building multiple times with same tag map
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("Alice", 3);
        userTagMap.put("Bob", 6);
        ChatRoomFdoBuilder builder1 = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        ChatRoomFdoBuilder builder2 = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo1 = builder1.toSource();
        String fdo2 = builder2.toSource();

        // Then: Output should be identical
        assertEquals(fdo1, fdo2, "Multiple builds should produce identical output");
    }

    @Test
    void shouldHaveCorrectStructuralOrder() {
        // Given: Chat room
        ChatRoom room = new ChatRoom("Ordered Room");
        room.addUser("First");
        room.addUser("Second");

        // When: Building FDO source
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("First", 9);
        userTagMap.put("Second", 10);
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, "TestUser", userTagMap);
        String fdo = builder.toSource();

        // Then: Structural elements should appear in correct order
        int headerStart = fdo.indexOf("uni_start_stream");
        int chatRoomOpen = fdo.indexOf("chat_room_open");
        int firstUser = fdo.indexOf("First");
        int secondUser = fdo.indexOf("Second");
        int waitOff = fdo.indexOf("uni_wait_off");
        int streamEnd = fdo.lastIndexOf("uni_end_stream");

        assertTrue(headerStart < chatRoomOpen, "Header before room open");
        assertTrue(chatRoomOpen < firstUser, "Room open before users");
        assertTrue(firstUser < secondUser, "Users in order");
        assertTrue(secondUser < waitOff, "Users before wait off");
        assertTrue(waitOff < streamEnd, "Wait off before stream end");
    }

    @Test
    void shouldUseGuestForNullScreenname() {
        // Given: Chat room with null screenname
        ChatRoom room = new ChatRoom("Test");
        room.addUser("Alice");
        Map<String, Integer> userTagMap = new HashMap<>();
        userTagMap.put("Alice", 1);

        // When: Building with null screenname
        ChatRoomFdoBuilder builder = new ChatRoomFdoBuilder(room, null, userTagMap);
        String fdo = builder.toSource();

        // Then: Should use "Guest" as default
        assertTrue(fdo.contains("Test - [Guest]"), "Should use Guest for null screenname");
    }

    /**
     * Helper method to count occurrences of a substring.
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
