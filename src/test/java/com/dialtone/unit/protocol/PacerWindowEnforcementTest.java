/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.PacketType;
import com.dialtone.state.SequenceManager;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Critical tests for P3 sliding window protocol enforcement.
 *
 * These tests verify that the Pacer correctly enforces the 16-frame window limit
 * and prevents the bug where 32+ frames are sent before ACKs arrive.
 *
 * EXPECTED BEHAVIOR:
 * - Maximum 16 DATA frames in-flight at any time
 * - Drain operations respect window limits
 * - Concurrent drain attempts don't violate window
 * - Deferred drain flag prevents sends during read processing
 */
public class PacerWindowEnforcementTest {

    private EmbeddedChannel channel;
    private ChannelHandlerContext ctx;
    private SequenceManager sequenceManager;
    private Pacer pacer;
    private AutoCloseable mocks;


    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        channel = new EmbeddedChannel();

        // FIX: EmbeddedChannel needs a handler in pipeline to get valid context
        channel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        ctx = channel.pipeline().lastContext();

        sequenceManager = new SequenceManager();
        pacer = new Pacer(sequenceManager, true, true);

        // Initialize sequence manager to simulate connection start
        sequenceManager.setLastDataTx(0x10);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
        if (channel != null) {
            channel.close();
        }
        if (pacer != null) {
            pacer.close();
        }
    }

    /**
     * CRITICAL TEST: Verifies that window enforcement prevents more than 16 frames in-flight.
     *
     * BUG REPRODUCTION:
     * - Queue 32 DATA frames
     * - Drain without any client ACKs
     * - CURRENT BUG: Math.min(16, outstanding) allows all 32 to send
     * - EXPECTED: Only 16 frames should be sent, rest queued
     */
    @Test
    void shouldStopSendingWhenWindowFull() {
        // Arrange: Queue 32 DATA frames
        int framesToQueue = 32;
        for (int i = 0; i < framesToQueue; i++) {
            byte[] frame = createDataFrame(i);
            pacer.enqueueSafe(ctx, frame, "FRAME_" + i);
        }

        assertEquals(framesToQueue, pacer.getPendingCount(), "All frames should be queued");

        // Act: Drain with no client ACKs (window starts full)
        pacer.drainLimited(ctx, 32); // Try to send all 32

        // Assert: Only 16 frames should have been sent (P3 window limit)
        int framesSent = 0;
        while (channel.readOutbound() != null) {
            framesSent++;
        }

        // CRITICAL ASSERTION: Window limit must be enforced
        assertTrue(framesSent <= 16,
            String.format("Window violation! Sent %d frames (max 16)", framesSent));

        // Remaining frames should still be queued
        int expectedRemaining = framesToQueue - framesSent;
        assertEquals(expectedRemaining, pacer.getPendingCount(),
            "Remaining frames should stay queued");

        // Pacer should be waiting for ACK
        assertTrue(pacer.isWaitingForAck(),
            "Pacer should be waiting for ACK when window full");
    }

    /**
     * Tests that multiple concurrent drain attempts don't send more than 16 total frames.
     *
     * SCENARIO:
     * - Queue 50 frames
     * - Call drainLimited(16) three times (simulating concurrent triggers)
     * - Verify total sent ≤ 16 (not 48!)
     */
    @Test
    void shouldPreventWindowViolationOnConcurrentDrains() {
        // Arrange: Queue 50 frames
        int framesToQueue = 50;
        for (int i = 0; i < framesToQueue; i++) {
            byte[] frame = createDataFrame(i);
            pacer.enqueueSafe(ctx, frame, "FRAME_" + i);
        }

        // Act: Simulate concurrent drain triggers
        pacer.drainLimited(ctx, 16);
        pacer.drainLimited(ctx, 16);
        pacer.drainLimited(ctx, 16);

        // Assert: Total frames sent should not exceed window
        int framesSent = 0;
        while (channel.readOutbound() != null) {
            framesSent++;
        }

        assertTrue(framesSent <= 16,
            String.format("Concurrent drains sent %d frames (max 16)", framesSent));
    }

    /**
     * Tests that deferred drain flag prevents sending during read processing.
     *
     * This simulates the splitAndDispatch pattern where drains are deferred
     * during batch processing and only executed in the finally block.
     */
    @Test
    void shouldRespectDeferredDrainFlag() {
        // Arrange: Queue frames
        for (int i = 0; i < 10; i++) {
            byte[] frame = createDataFrame(i);
            pacer.enqueueSafe(ctx, frame, "FRAME_" + i);
        }

        // Act: Set drains deferred (simulating read processing)
        pacer.setDrainsDeferred(true);
        pacer.drainLimited(ctx, 10);

        // Assert: No frames should be sent while deferred
        int framesSent = 0;
        while (channel.readOutbound() != null) {
            framesSent++;
        }

        assertEquals(0, framesSent, "No frames should be sent while drains deferred");
        assertEquals(10, pacer.getPendingCount(), "All frames should remain queued");

        // Act: Un-defer and drain
        pacer.setDrainsDeferred(false);
        pacer.drainLimited(ctx, 10);

        // Assert: Now frames should be sent
        // Note: With throttle at 8/16 (50% capacity), only 8 frames will be sent
        // before the pacer waits for an ACK to prevent window violations
        framesSent = 0;
        while (channel.readOutbound() != null) {
            framesSent++;
        }

        assertTrue(framesSent >= 8 && framesSent <= 10,
            String.format("Expected 8-10 frames sent after un-deferring (throttle at 8/16), got %d", framesSent));
    }

    /**
     * Tests handling of negative outstanding count (edge case during wraparound).
     *
     * When sequence numbers wrap from 0x7F to 0x10, the calculation
     * (lastDataTx - lastAckedServerTx) might temporarily be negative.
     */
    @Test
    void shouldHandleNegativeOutstanding() {
        // Arrange: Set up wraparound scenario
        // Server has wrapped (sent 0x7F, now at 0x10)
        // Client ACK still shows 0x7E
        sequenceManager.setLastDataTx(0x10); // Wrapped around
        // Note: Can't directly set lastAckedServerTx - would need to simulate ACK frame

        // Queue a frame
        byte[] frame = createDataFrame(1);
        pacer.enqueueSafe(ctx, frame, "WRAP_FRAME");

        // Act: Try to drain
        // This should not throw or crash
        assertDoesNotThrow(() -> pacer.drainLimited(ctx, 16),
            "Pacer should handle wraparound scenario gracefully");

        // The frame should either send or remain queued (both valid)
        // Important: should not crash or calculate wildly wrong window
    }

    /**
     * Tests that Pacer correctly tracks outstanding count after sends.
     *
     * Sends frames incrementally and verifies window fills correctly.
     */
    @Test
    void shouldCorrectlyTrackOutstandingFrames() {
        // Arrange & Act: Send frames in small batches
        List<Integer> outstandingAtEachStep = new ArrayList<>();

        for (int batch = 0; batch < 4; batch++) {
            // Queue 5 frames
            for (int i = 0; i < 5; i++) {
                byte[] frame = createDataFrame(batch * 5 + i);
                pacer.enqueueSafe(ctx, frame, "FRAME_" + (batch * 5 + i));
            }

            // Drain
            pacer.drainLimited(ctx, 5);

            // Clear channel output
            while (channel.readOutbound() != null) {}

            // Track outstanding
            int outstanding = sequenceManager.getOutstandingWindowFill();
            outstandingAtEachStep.add(outstanding);
        }

        // Assert: Outstanding should accumulate (no ACKs)
        // With throttle at 8/16 (50% capacity), window fills more slowly
        // Should be: ~5, ~8, ~8, ~8 (throttle kicks in at 8)
        for (int i = 0; i < outstandingAtEachStep.size(); i++) {
            int outstanding = outstandingAtEachStep.get(i);
            assertTrue(outstanding <= 16,
                String.format("Step %d: outstanding=%d exceeds max 16", i, outstanding));
        }

        // Last step should have hit throttle limit (50% capacity = 8/16)
        // Note: The Pacer now throttles at 8 frames (50% capacity) to allow client ACK time
        // and prevent Mac client crashes during login burst.
        int finalOutstanding = outstandingAtEachStep.get(outstandingAtEachStep.size() - 1);
        assertTrue(finalOutstanding >= 8,
            String.format("Should have reached throttle point by final step (8+/16), got %d", finalOutstanding));
    }

    /**
     * Tests that clearing pending doesn't break window state.
     */
    @Test
    void shouldHandleClearPendingCorrectly() {
        // Arrange: Queue frames and send some
        for (int i = 0; i < 20; i++) {
            byte[] frame = createDataFrame(i);
            pacer.enqueueSafe(ctx, frame, "FRAME_" + i);
        }

        pacer.drainLimited(ctx, 10);

        // Act: Clear pending (simulating sequence restart)
        pacer.clearPending();

        // Assert: Queue should be empty
        assertEquals(0, pacer.getPendingCount(), "Pending queue should be empty");
        assertFalse(pacer.isWaitingForAck(), "Should not be waiting for ACK after clear");

        // Should be able to queue and send new frames
        byte[] newFrame = createDataFrame(100);
        pacer.enqueueSafe(ctx, newFrame, "NEW_FRAME");
        pacer.drainLimited(ctx, 1);

        ByteBuf sent = channel.readOutbound();
        assertNotNull(sent, "Should be able to send new frames after clear");
        sent.release();
    }

    /**
     * Helper: Creates a minimal DATA frame for testing.
     *
     * Frame structure:
     * [0] = 0x5A (magic)
     * [1-2] = CRC (placeholder)
     * [3-4] = length
     * [5] = TX seq
     * [6] = RX seq
     * [7] = type (0x20 = DATA)
     * [8-9] = token
     * [10+] = payload
     */
    private byte[] createDataFrame(int sequenceHint) {
        byte[] frame = new byte[20]; // Minimal frame
        frame[0] = (byte) 0x5A; // Magic
        frame[1] = 0x00; // CRC (high) - will be computed
        frame[2] = 0x00; // CRC (low)
        frame[3] = 0x00; // Length (high)
        frame[4] = 0x0E; // Length (low) = 14 bytes
        frame[5] = (byte) (0x10 + (sequenceHint % 0x70)); // TX
        frame[6] = 0x10; // RX
        frame[7] = (byte) PacketType.DATA.getValue(); // Type = DATA (0x20)
        frame[8] = 0x41; // Token 'A'
        frame[9] = 0x54; // Token 'T'
        // [10-19] = payload (zeros)

        return frame;
    }
}
