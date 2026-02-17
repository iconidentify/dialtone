/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.xfer;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.PacketType;
import com.dialtone.protocol.xfer.XferUploadFrameBuilder;
import com.dialtone.protocol.xfer.XferUploadState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for tN token (upload flow control optimization).
 *
 * <p>Per xfer_developer_guide.md: "No XFER-level ACKs are expected from the host for uploads.
 * Use tN opportunistically to prompt more data if desired."
 *
 * <p>When client receives tN, it immediately calls xferSendPkt() without waiting for P3 ACK callback.
 */
class XferTnTokenTest {

    @Test
    void shouldBuildTnFrameWithCorrectTokenBytes() {
        byte[] frame = XferUploadFrameBuilder.buildTnFrame();

        // tN token is 't' (0x74) 'N' (0x4E)
        assertEquals(XferUploadFrameBuilder.TOKEN_TN_HI, frame[ProtocolConstants.IDX_TOKEN]);
        assertEquals(XferUploadFrameBuilder.TOKEN_TN_LO, frame[ProtocolConstants.IDX_TOKEN + 1]);
    }

    @Test
    void shouldBuildTnFrameWithMagicByte() {
        byte[] frame = XferUploadFrameBuilder.buildTnFrame();

        assertEquals((byte) ProtocolConstants.AOL_FRAME_MAGIC, frame[ProtocolConstants.IDX_MAGIC]);
    }

    @Test
    void shouldBuildTnFrameWithDataPacketType() {
        byte[] frame = XferUploadFrameBuilder.buildTnFrame();

        assertEquals((byte) PacketType.DATA.getValue(), frame[ProtocolConstants.IDX_TYPE]);
    }

    @Test
    void shouldBuildTnFrameWithMinimalSize() {
        byte[] frame = XferUploadFrameBuilder.buildTnFrame();

        // tN has no payload - just header (10 bytes) + terminator (1 byte) = 11 bytes
        assertEquals(ProtocolConstants.MIN_FULL_FRAME_SIZE + 1, frame.length);
    }

    @Test
    void shouldBuildTnFrameWithTerminator() {
        byte[] frame = XferUploadFrameBuilder.buildTnFrame();

        // Frame should end with CR (0x0D) terminator
        assertEquals(0x0D, frame[frame.length - 1]);
    }

    @Test
    void shouldHaveCorrectTokenConstants() {
        // Verify token constants match expected values
        assertEquals(0x74, XferUploadFrameBuilder.TOKEN_TN_HI);  // 't'
        assertEquals(0x4E, XferUploadFrameBuilder.TOKEN_TN_LO);  // 'N'
    }

    @Test
    void shouldTriggerTnAtConfiguredInterval() {
        XferUploadState state = new XferUploadState("test-id", "TestUser", new byte[]{0x01, 0x02}, 1024 * 1024);

        // tN interval is 6 frames
        // Frames 1-5 should not trigger tN
        for (int i = 1; i < 6; i++) {
            assertFalse(state.incrementFrameCountAndCheckTn(),
                "Frame " + i + " should not trigger tN");
        }

        // Frame 6 should trigger tN
        assertTrue(state.incrementFrameCountAndCheckTn(),
            "Frame 6 should trigger tN");

        // Frames 7-11 should not trigger
        for (int i = 7; i < 12; i++) {
            assertFalse(state.incrementFrameCountAndCheckTn(),
                "Frame " + i + " should not trigger tN");
        }

        // Frame 12 should trigger tN again
        assertTrue(state.incrementFrameCountAndCheckTn(),
            "Frame 12 should trigger tN");
    }

    @Test
    void shouldTrackTimeSinceLastPacket() throws InterruptedException {
        XferUploadState state = new XferUploadState("test-id", "TestUser", new byte[]{0x01, 0x02}, 1024 * 1024);

        // Initially no packets received
        assertEquals(0, state.getTimeSinceLastPacketMs());

        // After receiving a packet, timestamp should be set
        state.incrementFrameCountAndCheckTn();

        // Sleep briefly to ensure time has passed
        Thread.sleep(10);

        long elapsed = state.getTimeSinceLastPacketMs();
        assertTrue(elapsed >= 10, "Should have at least 10ms elapsed");
        assertTrue(elapsed < 1000, "Should not have unreasonably long elapsed time");
    }

    @Test
    void shouldIncrementFrameCount() {
        XferUploadState state = new XferUploadState("test-id", "TestUser", new byte[]{0x01, 0x02}, 1024 * 1024);

        assertEquals(0, state.getReceivedFrameCount());

        state.incrementFrameCountAndCheckTn();
        assertEquals(1, state.getReceivedFrameCount());

        state.incrementFrameCountAndCheckTn();
        assertEquals(2, state.getReceivedFrameCount());

        // Trigger multiple frames
        for (int i = 0; i < 10; i++) {
            state.incrementFrameCountAndCheckTn();
        }
        assertEquals(12, state.getReceivedFrameCount());
    }
}
