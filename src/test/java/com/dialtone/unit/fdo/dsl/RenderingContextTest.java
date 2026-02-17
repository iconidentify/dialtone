/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo.dsl;

import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.protocol.ClientPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RenderingContext.
 */
class RenderingContextTest {

    @Test
    void defaultContextHasUnknownPlatformAndColorMode() {
        RenderingContext ctx = RenderingContext.DEFAULT;

        assertEquals(ClientPlatform.UNKNOWN, ctx.getPlatform());
        assertFalse(ctx.isLowColorMode());
        assertFalse(ctx.isMac());
        assertFalse(ctx.isWindows());
    }

    @Test
    void macPlatformColorMode() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.MAC, false);

        assertEquals(ClientPlatform.MAC, ctx.getPlatform());
        assertFalse(ctx.isLowColorMode());
        assertTrue(ctx.isMac());
        assertFalse(ctx.isWindows());
    }

    @Test
    void windowsPlatformBwMode() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.WINDOWS, true);

        assertEquals(ClientPlatform.WINDOWS, ctx.getPlatform());
        assertTrue(ctx.isLowColorMode());
        assertFalse(ctx.isMac());
        assertTrue(ctx.isWindows());
    }

    @Test
    void nullPlatformTreatedAsUnknown() {
        RenderingContext ctx = new RenderingContext(null, false);

        assertEquals(ClientPlatform.UNKNOWN, ctx.getPlatform());
        assertFalse(ctx.isMac());
        assertFalse(ctx.isWindows());
    }

    @Test
    void equalContextsAreEqual() {
        RenderingContext ctx1 = new RenderingContext(ClientPlatform.MAC, true);
        RenderingContext ctx2 = new RenderingContext(ClientPlatform.MAC, true);

        assertEquals(ctx1, ctx2);
        assertEquals(ctx1.hashCode(), ctx2.hashCode());
    }

    @Test
    void differentContextsAreNotEqual() {
        RenderingContext macColor = new RenderingContext(ClientPlatform.MAC, false);
        RenderingContext macBw = new RenderingContext(ClientPlatform.MAC, true);
        RenderingContext winColor = new RenderingContext(ClientPlatform.WINDOWS, false);

        assertNotEquals(macColor, macBw);
        assertNotEquals(macColor, winColor);
        assertNotEquals(macBw, winColor);
    }

    @Test
    void toStringIncludesPlatformAndMode() {
        RenderingContext ctx = new RenderingContext(ClientPlatform.MAC, true);

        String str = ctx.toString();
        // ClientPlatform.MAC.toString() returns "Mac" (not "MAC")
        assertTrue(str.contains("Mac"), "toString should contain platform name");
        assertTrue(str.contains("true"), "toString should contain lowColorMode value");
    }

    @Test
    void allFourCombinationsWork() {
        // Mac + Color
        RenderingContext macColor = new RenderingContext(ClientPlatform.MAC, false);
        assertTrue(macColor.isMac());
        assertFalse(macColor.isLowColorMode());

        // Mac + BW
        RenderingContext macBw = new RenderingContext(ClientPlatform.MAC, true);
        assertTrue(macBw.isMac());
        assertTrue(macBw.isLowColorMode());

        // Windows + Color
        RenderingContext winColor = new RenderingContext(ClientPlatform.WINDOWS, false);
        assertTrue(winColor.isWindows());
        assertFalse(winColor.isLowColorMode());

        // Windows + BW
        RenderingContext winBw = new RenderingContext(ClientPlatform.WINDOWS, true);
        assertTrue(winBw.isWindows());
        assertTrue(winBw.isLowColorMode());
    }
}
