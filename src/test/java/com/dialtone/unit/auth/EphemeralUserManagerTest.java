/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.auth;

import com.dialtone.auth.EphemeralUserManager;
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
 * Unit tests for EphemeralUserManager.
 * Tests guest screenname generation, release, and thread safety.
 */
@DisplayName("EphemeralUserManager")
class EphemeralUserManagerTest {

    private EphemeralUserManager manager;

    @BeforeEach
    void setUp() {
        manager = new EphemeralUserManager();
    }

    @Nested
    @DisplayName("generateGuestName()")
    class GenerateGuestName {

        @Test
        @DisplayName("should generate name with ~Guest prefix")
        void shouldGenerateNameWithPrefix() {
            String name = manager.generateGuestName();
            assertTrue(name.startsWith(EphemeralUserManager.GUEST_PREFIX),
                    "Name should start with " + EphemeralUserManager.GUEST_PREFIX);
        }

        @Test
        @DisplayName("should generate 4-digit numbers (1000-9999)")
        void shouldGenerate4DigitNumbers() {
            String name = manager.generateGuestName();
            String numPart = name.substring(EphemeralUserManager.GUEST_PREFIX.length());
            int num = Integer.parseInt(numPart);
            assertTrue(num >= 1000 && num <= 9999,
                    "Guest number should be 4 digits (1000-9999), got: " + num);
        }

        @Test
        @DisplayName("should generate unique names")
        void shouldGenerateUniqueNames() {
            Set<String> names = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                String name = manager.generateGuestName();
                assertTrue(names.add(name), "Duplicate name generated: " + name);
            }
        }

        @Test
        @DisplayName("should track active guest count")
        void shouldTrackActiveGuestCount() {
            assertEquals(0, manager.getActiveGuestCount());

            manager.generateGuestName();
            assertEquals(1, manager.getActiveGuestCount());

            manager.generateGuestName();
            assertEquals(2, manager.getActiveGuestCount());
        }

        @Test
        @DisplayName("should be thread-safe")
        void shouldBeThreadSafe() throws InterruptedException {
            int threadCount = 10;
            int namesPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<String> allNames = java.util.Collections.synchronizedSet(new HashSet<>());

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < namesPerThread; j++) {
                            String name = manager.generateGuestName();
                            assertTrue(allNames.add(name), "Duplicate name in concurrent generation");
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "Threads should complete");
            assertEquals(threadCount * namesPerThread, allNames.size());
            assertEquals(threadCount * namesPerThread, manager.getActiveGuestCount());

            executor.shutdown();
        }
    }

    @Nested
    @DisplayName("releaseGuestName()")
    class ReleaseGuestName {

        @Test
        @DisplayName("should decrease active count")
        void shouldDecreaseActiveCount() {
            String name = manager.generateGuestName();
            assertEquals(1, manager.getActiveGuestCount());

            manager.releaseGuestName(name);
            assertEquals(0, manager.getActiveGuestCount());
        }

        @Test
        @DisplayName("should allow name reuse after release")
        void shouldAllowNameReuseAfterRelease() {
            // Generate and release 100 names
            Set<String> releasedNames = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                String name = manager.generateGuestName();
                releasedNames.add(name);
                manager.releaseGuestName(name);
            }

            // All should be released
            assertEquals(0, manager.getActiveGuestCount());

            // Generate more - some may reuse released names (random selection)
            for (int i = 0; i < 100; i++) {
                manager.generateGuestName();
            }
            assertEquals(100, manager.getActiveGuestCount());
        }

        @Test
        @DisplayName("should handle null gracefully")
        void shouldHandleNullGracefully() {
            assertDoesNotThrow(() -> manager.releaseGuestName(null));
        }

        @Test
        @DisplayName("should ignore non-ephemeral names")
        void shouldIgnoreNonEphemeralNames() {
            String name = manager.generateGuestName();
            assertEquals(1, manager.getActiveGuestCount());

            manager.releaseGuestName("RegularUser");
            assertEquals(1, manager.getActiveGuestCount()); // Unchanged
        }

        @Test
        @DisplayName("should handle double-release gracefully")
        void shouldHandleDoubleReleaseGracefully() {
            String name = manager.generateGuestName();
            manager.releaseGuestName(name);
            assertDoesNotThrow(() -> manager.releaseGuestName(name));
            assertEquals(0, manager.getActiveGuestCount());
        }
    }

    @Nested
    @DisplayName("isEphemeralName()")
    class IsEphemeralName {

        @Test
        @DisplayName("should return true for guest names")
        void shouldReturnTrueForGuestNames() {
            assertTrue(manager.isEphemeralName("~Guest1234"));
            assertTrue(manager.isEphemeralName("~Guest9999"));
            assertTrue(manager.isEphemeralName("~Guest1000"));
        }

        @Test
        @DisplayName("should return false for regular names")
        void shouldReturnFalseForRegularNames() {
            assertFalse(manager.isEphemeralName("TestUser"));
            assertFalse(manager.isEphemeralName("Admin"));
            assertFalse(manager.isEphemeralName("Guest")); // No ~ prefix
        }

        @Test
        @DisplayName("should return false for null")
        void shouldReturnFalseForNull() {
            assertFalse(manager.isEphemeralName(null));
        }

        @Test
        @DisplayName("should return false for partial prefix")
        void shouldReturnFalseForPartialPrefix() {
            assertFalse(manager.isEphemeralName("~Gues")); // Incomplete prefix
            assertFalse(manager.isEphemeralName("~")); // Just tilde
        }
    }

    @Nested
    @DisplayName("isActiveGuest()")
    class IsActiveGuest {

        @Test
        @DisplayName("should return true for active guests")
        void shouldReturnTrueForActiveGuests() {
            String name = manager.generateGuestName();
            assertTrue(manager.isActiveGuest(name));
        }

        @Test
        @DisplayName("should return false after release")
        void shouldReturnFalseAfterRelease() {
            String name = manager.generateGuestName();
            manager.releaseGuestName(name);
            assertFalse(manager.isActiveGuest(name));
        }

        @Test
        @DisplayName("should return false for unknown names")
        void shouldReturnFalseForUnknownNames() {
            assertFalse(manager.isActiveGuest("~Guest9999"));
            assertFalse(manager.isActiveGuest("Unknown"));
        }
    }
}
