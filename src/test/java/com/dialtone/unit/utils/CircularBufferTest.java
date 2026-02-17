/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.utils;

import com.dialtone.utils.CircularBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CircularBuffer.
 */
@DisplayName("CircularBuffer")
class CircularBufferTest {

    @Test
    @DisplayName("Should create buffer with specified capacity")
    void shouldCreateBufferWithCapacity() {
        CircularBuffer<String> buffer = new CircularBuffer<>(10);

        assertEquals(10, buffer.capacity());
        assertEquals(0, buffer.size());
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    @DisplayName("Should reject capacity less than 1")
    void shouldRejectInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<>(0));
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<>(-1));
    }

    @Test
    @DisplayName("Should add elements and track size")
    void shouldAddElementsAndTrackSize() {
        CircularBuffer<String> buffer = new CircularBuffer<>(5);

        buffer.add("A");
        assertEquals(1, buffer.size());

        buffer.add("B");
        buffer.add("C");
        assertEquals(3, buffer.size());

        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());
    }

    @Test
    @DisplayName("Should retrieve last N elements in insertion order")
    void shouldRetrieveLastNElements() {
        CircularBuffer<String> buffer = new CircularBuffer<>(10);

        buffer.add("A");
        buffer.add("B");
        buffer.add("C");
        buffer.add("D");
        buffer.add("E");

        List<String> last3 = buffer.getLast(3);

        assertEquals(List.of("C", "D", "E"), last3);
    }

    @Test
    @DisplayName("Should handle requesting more elements than available")
    void shouldHandleRequestingMoreThanAvailable() {
        CircularBuffer<String> buffer = new CircularBuffer<>(10);

        buffer.add("A");
        buffer.add("B");

        List<String> result = buffer.getLast(5);

        assertEquals(List.of("A", "B"), result);
    }

    @Test
    @DisplayName("Should overwrite oldest elements when full")
    void shouldOverwriteOldestWhenFull() {
        CircularBuffer<String> buffer = new CircularBuffer<>(3);

        buffer.add("A");
        buffer.add("B");
        buffer.add("C");
        assertTrue(buffer.isFull());

        buffer.add("D");  // Overwrites "A"
        buffer.add("E");  // Overwrites "B"

        List<String> all = buffer.getAll();
        assertEquals(List.of("C", "D", "E"), all);
    }

    @Test
    @DisplayName("Should retrieve all elements correctly")
    void shouldRetrieveAllElements() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(5);

        buffer.add(1);
        buffer.add(2);
        buffer.add(3);

        List<Integer> all = buffer.getAll();

        assertEquals(List.of(1, 2, 3), all);
    }

    @Test
    @DisplayName("Should clear buffer")
    void shouldClearBuffer() {
        CircularBuffer<String> buffer = new CircularBuffer<>(5);

        buffer.add("A");
        buffer.add("B");
        buffer.add("C");

        buffer.clear();

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.getAll());
    }

    @Test
    @DisplayName("Should handle wraparound correctly")
    void shouldHandleWraparoundCorrectly() {
        CircularBuffer<String> buffer = new CircularBuffer<>(3);

        // Fill buffer
        buffer.add("A");
        buffer.add("B");
        buffer.add("C");

        // Cause wraparound
        buffer.add("D");  // Overwrites "A", now: B, C, D
        buffer.add("E");  // Overwrites "B", now: C, D, E

        List<String> last2 = buffer.getLast(2);
        assertEquals(List.of("D", "E"), last2);

        List<String> all = buffer.getAll();
        assertEquals(List.of("C", "D", "E"), all);
    }

    @Test
    @DisplayName("Should return empty list when requesting 0 elements")
    void shouldReturnEmptyListWhenRequestingZero() {
        CircularBuffer<String> buffer = new CircularBuffer<>(5);
        buffer.add("A");

        List<String> result = buffer.getLast(0);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should reject negative count in getLast")
    void shouldRejectNegativeCount() {
        CircularBuffer<String> buffer = new CircularBuffer<>(5);

        assertThrows(IllegalArgumentException.class, () -> buffer.getLast(-1));
    }

    @Test
    @DisplayName("Should handle null elements")
    void shouldHandleNullElements() {
        CircularBuffer<String> buffer = new CircularBuffer<>(3);

        buffer.add("A");
        buffer.add(null);
        buffer.add("C");

        List<String> all = buffer.getAll();

        assertEquals(3, all.size());
        assertEquals("A", all.get(0));
        assertNull(all.get(1));
        assertEquals("C", all.get(2));
    }

    @Test
    @DisplayName("Should maintain insertion order after multiple wraps")
    void shouldMaintainInsertionOrderAfterMultipleWraps() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);

        // Add many elements to trigger multiple wraps
        for (int i = 1; i <= 10; i++) {
            buffer.add(i);
        }

        // Should contain last 3: [8, 9, 10]
        List<Integer> all = buffer.getAll();
        assertEquals(List.of(8, 9, 10), all);

        List<Integer> last2 = buffer.getLast(2);
        assertEquals(List.of(9, 10), last2);
    }

    @Test
    @DisplayName("Should work correctly with capacity of 1")
    void shouldWorkWithCapacityOfOne() {
        CircularBuffer<String> buffer = new CircularBuffer<>(1);

        buffer.add("A");
        assertEquals(List.of("A"), buffer.getAll());

        buffer.add("B");  // Overwrites "A"
        assertEquals(List.of("B"), buffer.getAll());

        buffer.add("C");  // Overwrites "B"
        assertEquals(List.of("C"), buffer.getAll());
    }

    @Test
    @DisplayName("Should handle empty buffer operations")
    void shouldHandleEmptyBufferOperations() {
        CircularBuffer<String> buffer = new CircularBuffer<>(5);

        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(List.of(), buffer.getAll());
        assertEquals(List.of(), buffer.getLast(3));
    }

    @Test
    @DisplayName("Should be thread-safe for concurrent adds and reads")
    void shouldBeThreadSafe() throws InterruptedException {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(100);

        // Thread that adds elements
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                buffer.add(i);
            }
        });

        // Thread that reads elements
        Thread reader = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                buffer.getLast(10);
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });

        writer.start();
        reader.start();

        writer.join();
        reader.join();

        // Should have the last 100 elements
        assertEquals(100, buffer.size());
        assertTrue(buffer.isFull());
    }
}
