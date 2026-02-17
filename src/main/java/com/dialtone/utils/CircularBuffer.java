/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Thread-safe circular (ring) buffer with fixed capacity.
 *
 * <p>This buffer stores a fixed number of elements. When the buffer is full,
 * adding a new element overwrites the oldest element. This is ideal for
 * maintaining a rolling window of recent events (e.g., log messages).
 *
 * <p><b>Example Usage:</b>
 * <pre>
 * // Create buffer for last 200 log messages
 * CircularBuffer&lt;String&gt; logs = new CircularBuffer&lt;&gt;(200);
 *
 * // Add messages (oldest are automatically evicted when full)
 * logs.add("[INFO] Server started");
 * logs.add("[DEBUG] Processing request...");
 *
 * // Retrieve last N messages
 * List&lt;String&gt; recent = logs.getLast(50);  // Get last 50 messages
 * </pre>
 *
 * @param <T> the type of elements stored in the buffer
 */
public class CircularBuffer<T> {

    private final Object[] buffer;
    private final int capacity;
    private int writeIndex = 0;
    private int size = 0;

    /**
     * Creates a circular buffer with the specified capacity.
     *
     * @param capacity maximum number of elements to store
     * @throws IllegalArgumentException if capacity is less than 1
     */
    public CircularBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be at least 1");
        }
        this.capacity = capacity;
        this.buffer = new Object[capacity];
    }

    /**
     * Adds an element to the buffer.
     *
     * <p>If the buffer is full, the oldest element is overwritten.
     *
     * @param element the element to add (null elements are allowed)
     */
    public synchronized void add(T element) {
        buffer[writeIndex] = element;
        writeIndex = (writeIndex + 1) % capacity;

        if (size < capacity) {
            size++;
        }
    }

    /**
     * Retrieves the last N elements in insertion order (oldest to newest).
     *
     * <p>If fewer than N elements have been added, returns all available elements.
     *
     * @param count number of elements to retrieve
     * @return list of last N elements (may be fewer if buffer contains fewer)
     * @throws IllegalArgumentException if count is negative
     */
    @SuppressWarnings("unchecked")
    public synchronized List<T> getLast(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("Count cannot be negative");
        }

        int n = Math.min(count, size);
        List<T> result = new ArrayList<>(n);

        if (n == 0) {
            return result;
        }

        // Calculate starting index
        int startIndex;
        if (size < capacity) {
            // Buffer not yet full - start from beginning
            startIndex = Math.max(0, size - n);
        } else {
            // Buffer is full - calculate wraparound
            startIndex = (writeIndex - n + capacity) % capacity;
        }

        // Collect elements in insertion order
        for (int i = 0; i < n; i++) {
            int index = (startIndex + i) % capacity;
            result.add((T) buffer[index]);
        }

        return result;
    }

    /**
     * Retrieves all elements in the buffer in insertion order (oldest to newest).
     *
     * @return list of all elements in the buffer
     */
    public synchronized List<T> getAll() {
        return getLast(size);
    }

    /**
     * Returns the number of elements currently in the buffer.
     *
     * @return current buffer size (0 to capacity)
     */
    public synchronized int size() {
        return size;
    }

    /**
     * Returns the maximum capacity of the buffer.
     *
     * @return buffer capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Checks if the buffer is empty.
     *
     * @return true if the buffer contains no elements, false otherwise
     */
    public synchronized boolean isEmpty() {
        return size == 0;
    }

    /**
     * Checks if the buffer is full.
     *
     * @return true if the buffer has reached capacity, false otherwise
     */
    public synchronized boolean isFull() {
        return size == capacity;
    }

    /**
     * Clears all elements from the buffer.
     */
    public synchronized void clear() {
        for (int i = 0; i < capacity; i++) {
            buffer[i] = null;
        }
        writeIndex = 0;
        size = 0;
    }
}
