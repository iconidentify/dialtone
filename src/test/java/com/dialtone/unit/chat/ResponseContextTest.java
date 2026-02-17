/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.chat;

import com.dialtone.chat.bot.ResponseContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResponseContext enum.
 */
class ResponseContextTest {

    @Test
    void shouldHaveChatRoomValue() {
        assertNotNull(ResponseContext.CHAT_ROOM);
        assertEquals("CHAT_ROOM", ResponseContext.CHAT_ROOM.name());
    }

    @Test
    void shouldHaveInstantMessageValue() {
        assertNotNull(ResponseContext.INSTANT_MESSAGE);
        assertEquals("INSTANT_MESSAGE", ResponseContext.INSTANT_MESSAGE.name());
    }

    @Test
    void shouldHaveExactlyTwoValues() {
        ResponseContext[] values = ResponseContext.values();
        assertEquals(2, values.length, "Should have exactly two context types");
    }

    @Test
    void valuesShouldBeDifferent() {
        assertNotEquals(ResponseContext.CHAT_ROOM, ResponseContext.INSTANT_MESSAGE);
    }

    @Test
    void shouldBeConvertibleFromString() {
        assertEquals(ResponseContext.CHAT_ROOM, ResponseContext.valueOf("CHAT_ROOM"));
        assertEquals(ResponseContext.INSTANT_MESSAGE, ResponseContext.valueOf("INSTANT_MESSAGE"));
    }

    @Test
    void invalidValueShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> {
            ResponseContext.valueOf("INVALID");
        });
    }
}
