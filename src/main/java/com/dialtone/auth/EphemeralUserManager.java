/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.auth;

import com.dialtone.utils.LoggerUtil;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages ephemeral guest screennames.
 *
 * Generates unique ~GuestXXXX screennames for users who fail authentication
 * when ephemeral fallback is enabled. Screennames are released on disconnect
 * for potential reuse.
 *
 * Thread-safe singleton design for use across multiple connections.
 */
public class EphemeralUserManager {

    /** Prefix for all ephemeral guest screennames */
    public static final String GUEST_PREFIX = "~Guest";

    /** Minimum guest ID (inclusive) - 4-digit numbers look more "real" */
    private static final int MIN_GUEST_ID = 1000;

    /** Maximum guest ID (exclusive) */
    private static final int MAX_GUEST_ID = 10000;

    /** Set of currently active guest screennames */
    private final Set<String> activeGuestNames = ConcurrentHashMap.newKeySet();

    /**
     * Generate a unique ephemeral guest screenname.
     * Format: ~GuestXXXX where XXXX is a random 4-digit number (1000-9999).
     *
     * <p>Uses random selection instead of sequential to:
     * <ul>
     *   <li>Avoid predictable low numbers after server restart</li>
     *   <li>Make guest sessions feel less "tracked"</li>
     *   <li>Reduce appearance of being "first guest"</li>
     * </ul>
     *
     * @return A unique guest screenname
     */
    public String generateGuestName() {
        String guestName;
        int attempts = 0;
        int poolSize = MAX_GUEST_ID - MIN_GUEST_ID;

        // Loop until we find an unused random name
        do {
            int guestId = MIN_GUEST_ID + ThreadLocalRandom.current().nextInt(poolSize);
            guestName = GUEST_PREFIX + guestId;
            attempts++;

            // Safety valve: if pool is nearly exhausted, something is wrong
            if (attempts > poolSize) {
                LoggerUtil.error("Guest name pool exhausted after " + attempts + " attempts");
                throw new RuntimeException("Unable to generate unique guest name - pool exhausted");
            }
        } while (!activeGuestNames.add(guestName));

        LoggerUtil.info("Generated ephemeral guest name: " + guestName);
        return guestName;
    }

    /**
     * Release an ephemeral guest screenname on disconnect.
     * This allows the name to potentially be reused later.
     *
     * @param screenname The guest screenname to release
     */
    public void releaseGuestName(String screenname) {
        if (screenname == null) {
            return;
        }

        if (!isEphemeralName(screenname)) {
            LoggerUtil.debug("Attempted to release non-ephemeral name: " + screenname);
            return;
        }

        boolean removed = activeGuestNames.remove(screenname);
        if (removed) {
            LoggerUtil.info("Released ephemeral guest name: " + screenname);
        } else {
            LoggerUtil.debug("Guest name was not in active set: " + screenname);
        }
    }

    /**
     * Check if a screenname is an ephemeral guest name.
     *
     * @param screenname The screenname to check
     * @return true if this is an ephemeral guest name
     */
    public boolean isEphemeralName(String screenname) {
        return screenname != null && screenname.startsWith(GUEST_PREFIX);
    }

    /**
     * Get the count of currently active ephemeral guests.
     * Useful for monitoring and diagnostics.
     *
     * @return Number of active ephemeral sessions
     */
    public int getActiveGuestCount() {
        return activeGuestNames.size();
    }

    /**
     * Check if a specific guest name is currently active.
     *
     * @param screenname The screenname to check
     * @return true if this guest name is currently active
     */
    public boolean isActiveGuest(String screenname) {
        return activeGuestNames.contains(screenname);
    }
}
