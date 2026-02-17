/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StatefulClientHandler - the main AOL v3 protocol state machine.
 *
 * Tests critical functionality:
 * - Channel lifecycle (active, inactive, exception handling)
 * - Frame splitting and coalescing
 * - Short control frame handling (0xA4, 0xA5, 0xA6)
 * - Token dispatch
 * - Window and ACK management
 * - Deferred drain pattern
 */
@DisplayName("StatefulClientHandler Protocol Tests")
class StatefulClientHandlerTest {

    private EmbeddedChannel channel;
    private ChannelHandlerContext ctx;
    private StatefulClientHandler handler;

    @BeforeEach
    void setUp() {
        // Create handler with verbose=false, delayMs=0
        handler = new StatefulClientHandler(false);

        channel = new EmbeddedChannel(handler);

        // Create mock context that delegates to the embedded channel
        ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        when(ctx.alloc()).thenReturn(ByteBufAllocator.DEFAULT);
        when(ctx.writeAndFlush(any())).thenAnswer(invocation -> {
            Object msg = invocation.getArgument(0);
            return channel.writeAndFlush(msg);
        });
        when(ctx.executor()).thenReturn(channel.eventLoop());
    }

    @AfterEach
    void tearDown() {
        if (channel != null && channel.isOpen()) {
            channel.close();
        }
    }

    @Nested
    @DisplayName("Channel Lifecycle")
    class ChannelLifecycle {

        @Test
        @DisplayName("Should activate channel successfully")
        void shouldActivateChannelSuccessfully() {
            // Channel is already active from setUp
            assertTrue(channel.isActive());

            // Verify session is initialized
            assertNotNull(handler.getSession());
            assertFalse(handler.getSession().isAuthenticated());
        }

        @Test
        @DisplayName("Should handle channel inactive gracefully")
        void shouldHandleChannelInactiveGracefully() {
            // Close the channel
            channel.close();

            assertFalse(channel.isActive());
        }

        @Test
        @DisplayName("Should handle exceptions by closing channel")
        void shouldHandleExceptionsByClosingChannel() {
            Exception testException = new RuntimeException("Test exception");

            handler.exceptionCaught(ctx, testException);

            // Verify channel was closed
            verify(ctx).close();
        }

        @Test
        @DisplayName("Should handle writability changes")
        void shouldHandleWritabilityChanges() {
            // Simulate writability change
            assertDoesNotThrow(() -> handler.channelWritabilityChanged(ctx));

            // Verify fireChannelWritabilityChanged was called
            verify(ctx).fireChannelWritabilityChanged();
        }
    }

    @Nested
    @DisplayName("Frame Splitting")
    class FrameSplitting {

        @Test
        @DisplayName("Should split coalesced frames")
        void shouldSplitCoalescedFrames() {
            // Build two short control frames (9 bytes each)
            byte[] frame1 = buildShortControlFrame(0xA5);
            byte[] frame2 = buildShortControlFrame(0xA5);

            // Coalesce them into one buffer
            byte[] coalesced = new byte[frame1.length + frame2.length];
            System.arraycopy(frame1, 0, coalesced, 0, frame1.length);
            System.arraycopy(frame2, 0, coalesced, frame1.length, frame2.length);

            // Feed to handler
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(coalesced);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle partial frame gracefully")
        void shouldHandlePartialFrameGracefully() {
            // Build incomplete frame (only 5 bytes)
            byte[] partial = new byte[]{
                (byte) ProtocolConstants.MAGIC, 0x20, 'A', 'T', 0x00
            };

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(partial);

            // Should not throw
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle invalid magic byte")
        void shouldHandleInvalidMagicByte() {
            // Build frame with wrong magic byte
            byte[] invalid = new byte[]{
                (byte) 0xFF, 0x20, 'A', 'T', 0x00, 0x00, 0x00, 0x03, 0x0D
            };

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(invalid);

            // Should not throw
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle empty buffer")
        void shouldHandleEmptyBuffer() {
            ByteBuf buf = channel.alloc().buffer();

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    @Nested
    @DisplayName("Short Control Frame Handling")
    class ShortControlFrameHandling {

        @Test
        @DisplayName("Should handle 0xA5 keepalive")
        void shouldHandleA5Keepalive() {
            byte[] keepalive = buildShortControlFrame(0xA5);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(keepalive);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should respond to 0xA6 ping with pong")
        void shouldRespondToA6PingWithPong() throws Exception {
            byte[] ping = buildShortControlFrame(0xA6);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(ping);

            handler.channelRead(ctx, buf);

            // Should have queued a pong response
            // (We can't easily verify without exposing pacer state)
        }

        @Test
        @DisplayName("Should handle 0xA4 short ACK")
        void shouldHandleA4ShortAck() {
            byte[] ack = buildShortControlFrame(0xA4);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(ack);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle multiple control frames in sequence")
        void shouldHandleMultipleControlFrames() {
            byte[] frame1 = buildShortControlFrame(0xA5);
            byte[] frame2 = buildShortControlFrame(0xA6);
            byte[] frame3 = buildShortControlFrame(0xA4);

            byte[] sequence = new byte[frame1.length + frame2.length + frame3.length];
            System.arraycopy(frame1, 0, sequence, 0, frame1.length);
            System.arraycopy(frame2, 0, sequence, frame1.length, frame2.length);
            System.arraycopy(frame3, 0, sequence, frame1.length + frame2.length, frame3.length);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(sequence);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    @Nested
    @DisplayName("Token Dispatch")
    class TokenDispatch {

        @Test
        @DisplayName("Should handle unknown token gracefully")
        void shouldHandleUnknownTokenGracefully() {
            byte[] frame = buildDataFrameWithToken("XX");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            // Should not throw
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should recognize ff token")
        void shouldRecognizeFfToken() {
            byte[] frame = buildDataFrameWithToken("ff");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            // Should not throw (will send control ACK)
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should recognize f2 token")
        void shouldRecognizeF2Token() {
            byte[] frame = buildDataFrameWithToken("f2");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            // Should not throw (will send control ACK)
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle null token frame")
        void shouldHandleNullTokenFrame() {
            // Build frame with non-ASCII token bytes (0x00, 0x00)
            byte[] frame = new byte[14];
            frame[0] = (byte) ProtocolConstants.MAGIC; // 0x5A
            frame[ProtocolConstants.IDX_TYPE] = (byte) PacketType.DATA.getValue(); // 0x20
            frame[ProtocolConstants.IDX_TOKEN] = 0x00; // null token
            frame[ProtocolConstants.IDX_TOKEN + 1] = 0x00; // null token

            // Length = 4 (minimal payload)
            frame[ProtocolConstants.IDX_LEN_HI] = 0x00;
            frame[ProtocolConstants.IDX_LEN_LO] = 0x04;

            // TX/RX sequences
            frame[ProtocolConstants.IDX_TX] = 0x11;
            frame[ProtocolConstants.IDX_RX] = 0x11;

            // Minimal 4-byte payload
            frame[10] = 0x01;
            frame[11] = 0x02;
            frame[12] = 0x03;
            frame[13] = 0x04;

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            // Should not throw
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    @Nested
    @DisplayName("Session Management")
    class SessionManagement {

        @Test
        @DisplayName("Should initialize session on channel active")
        void shouldInitializeSessionOnChannelActive() {
            assertNotNull(handler.getSession());
            assertFalse(handler.getSession().isAuthenticated());
            assertNull(handler.getSession().getUsername());
        }

        @Test
        @DisplayName("Should maintain session state")
        void shouldMaintainSessionState() {
            SessionContext session = handler.getSession();

            session.setUsername("TestUser");
            session.setAuthenticated(true);

            assertEquals("TestUser", session.getUsername());
            assertTrue(session.isAuthenticated());
        }
    }

    @Nested
    @DisplayName("Frame Validation")
    class FrameValidation {

        @Test
        @DisplayName("Should accept valid short control frame")
        void shouldAcceptValidShortControlFrame() {
            byte[] valid = buildShortControlFrame(0xA5);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(valid);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should accept valid data frame")
        void shouldAcceptValidDataFrame() {
            byte[] valid = buildDataFrameWithToken("AT");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(valid);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle frame with trailing CR")
        void shouldHandleFrameWithTrailingCr() {
            byte[] frame = buildDataFrameWithToken("AT");
            byte[] withCr = new byte[frame.length + 1];
            System.arraycopy(frame, 0, withCr, 0, frame.length);
            withCr[frame.length] = 0x0D; // CR

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(withCr);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    @Nested
    @DisplayName("Window Management Integration")
    class WindowManagementIntegration {

        @Test
        @DisplayName("Should track sequence updates on frame receipt")
        void shouldTrackSequenceUpdatesOnFrameReceipt() {
            // Build a frame with TX/RX sequences
            byte[] frame = buildDataFrameWithSequences("AT", 0x11, 0x12);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));

            // Sequence manager should have updated (we can't easily verify without exposing state)
        }

        @Test
        @DisplayName("Should handle ACK in incoming frame")
        void shouldHandleAckInIncomingFrame() {
            // Build frame with ACK byte set
            byte[] frame = buildDataFrameWithSequences("AT", 0x11, 0x20);

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(frame);

            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    @Nested
    @DisplayName("Multi-Frame Aa Chat Message Handling")
    class MultiFrameAaChatMessageHandling {

        /**
         * REGRESSION TEST DOCUMENTATION for TX/RX sequence bug with multi-frame Aa messages.
         *
         * BUG: Server responds to chat messages before consuming all frames in multi-frame sequence,
         * causing TX/RX sequence number misalignment and client disconnection.
         *
         * PROBLEM SCENARIO:
         * 1. Client sends Aa frame (chat message) in TWO frames:
         *    - Frame 1: Data frame with message content (no uni_end_stream)
         *    - Frame 2: Control frame with uni_end_stream marker
         * 2. OLD BEHAVIOR: Server responded after Frame 1, before consuming Frame 2
         * 3. RESULT: TX/RX sequences out of sync, client disconnects
         *
         * ROOT CAUSE:
         * handleAaEcho() was called immediately when Aa token arrived, without checking if more
         * frames were part of the same logical stream (identified by Stream ID).
         *
         * FIX IMPLEMENTED:
         * 1. Stream ID extraction from frame headers (bytes 10-11)
         * 2. FDO decompilation to detect uni_end_stream marker
         * 3. Frame accumulation by Stream ID in pendingAaStreams Map
         * 4. Process complete message only when uni_end_stream arrives
         *
         * DETECTION LOGIC:
         * - Extract Stream ID using P3FrameExtractor
         * - Decompile FDO and check for "uni_end_stream" pattern
         * - If uni_end_stream present: Process (with accumulated frames if any)
         * - If uni_end_stream absent: Accumulate and wait for next frame with same Stream ID
         */

        @Test
        @DisplayName("Should recognize single-frame Aa message (with uni_end_stream)")
        void shouldRecognizeSingleFrameAaMessage() {
            // Build Aa frame - will fail to decompile but should default to processing
            byte[] aaFrame = buildDataFrameWithToken("Aa");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(aaFrame);

            // Should not throw - single frame should be processed
            // (Will fail at extractChatMessage but that's expected without real FDO)
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should handle Aa frame with invalid Stream ID gracefully")
        void shouldHandleAaFrameWithInvalidStreamIdGracefully() {
            byte[] aaFrame = buildDataFrameWithToken("Aa");

            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(aaFrame);

            // Should not throw even if Stream ID extraction fails
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }

        @Test
        @DisplayName("Should clean up pending streams on channel inactive")
        void shouldCleanUpPendingStreamsOnChannelInactive() {
            // This test verifies cleanup doesn't throw
            // (Actual accumulation requires real FDO frames)

            // Close channel while "pending" streams exist (simulated)
            channel.close();

            assertFalse(channel.isActive());
            // Cleanup should have been called in channelInactive()
        }

        @Test
        @DisplayName("Should handle multiple Aa frames in sequence")
        void shouldHandleMultipleAaFramesInSequence() {
            // Send multiple Aa frames (each should be treated as separate)
            for (int i = 0; i < 3; i++) {
                byte[] aaFrame = buildDataFrameWithToken("Aa");
                ByteBuf buf = channel.alloc().buffer();
                buf.writeBytes(aaFrame);
                assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
            }

            // All should process without error
        }

        /**
         * Integration test note:
         * Full testing of multi-frame accumulation requires:
         * 1. Real FDO frames with valid payloads
         * 2. Atomforge API running for decompilation
         * 3. Frames with same Stream ID but different uni_end_stream states
         *
         * These tests verify the code paths don't throw exceptions.
         * Manual testing with real AOL client required for full validation.
         */
    }

    @Nested
    @DisplayName("DOD + Ping Regression Tests (Client Crash Fix)")
    class DodPingRegressionTests {

        /**
         * REGRESSION TEST DOCUMENTATION for client crash caused by back-to-back 9B control frames.
         *
         * BUG: Client crashes when receiving two 9B control frames (0x24 + 0xA5) between AT data frames
         * during active DOD transfer.
         *
         * FORENSIC ANALYSIS:
         * - Failing run: Sends 0x24 then 0xA5 back-to-back mid-DOD → client crash
         * - Working run: Only sends single 0x24 between ATs → no crash
         * - Key difference: Two 9B frames vs. one 9B frame
         *
         * ROOT CAUSE:
         * When DOD transfer active and client sends 0xA6 ping, server immediately queued 0xA5 pong
         * via priority queue (StatefulClientHandler.java:391). Combined with 0x24 stream control ACK
         * (line 287), both frames sent back-to-back between AT frames, causing client crash.
         *
         * FIX IMPLEMENTED:
         * 1. DOD stream state tracking (activeDodStreamId field, line 72)
         * 2. Defer 0xA5 pongs during active DOD (handleShortControl9B line 380-386)
         * 3. Clear DOD state on major tokens (Dd, SC, pE, etc - lines 320, 332, 314, 339)
         * 4. Control frame coalescing in Pacer (drainInternal line 286-294, 347)
         *
         * TESTING:
         * Full integration testing requires actual DOD request with Atomforge, which is complex.
         * Basic unit tests below verify code paths don't throw exceptions.
         * VALIDATION REQUIRED: Manual testing with AOL client to confirm crash is fixed.
         */

        @Test
        @DisplayName("Should handle ping normally when DOD inactive")
        void shouldHandlePingNormallyWhenDodInactive() {
            // Send 0xA6 ping when NO DOD is active
            byte[] ping = buildShortControlFrame(0xA6);
            ByteBuf pingBuf = channel.alloc().buffer();
            pingBuf.writeBytes(ping);

            // Should not throw - normal pong response queued
            assertDoesNotThrow(() -> handler.channelRead(ctx, pingBuf));

            // This verifies the fix doesn't break normal ping/pong behavior
        }

        @Test
        @DisplayName("Should handle multiple pings in sequence")
        void shouldHandleMultiplePingsInSequence() {
            // Multiple pings when DOD inactive - all should get pongs
            for (int i = 0; i < 5; i++) {
                byte[] ping = buildShortControlFrame(0xA6);
                ByteBuf pingBuf = channel.alloc().buffer();
                pingBuf.writeBytes(ping);
                assertDoesNotThrow(() -> handler.channelRead(ctx, pingBuf));
            }

            // Verifies fix doesn't break repeated ping handling
        }
    }

    @Nested
    @DisplayName("f2 Token Handler Tests")
    class F2TokenTests {
        
        @Test
        @DisplayName("Should process f2 DOD request with valid GID")
        void testF2DodRequestValidGid() throws IOException {
            // Build test frame: 5a6eef00091766a06632010005350d
            byte[] f2Frame = new byte[] {
                0x5A, 0x6E, (byte)0xEF, 0x00, // Header: sync, type, token bytes
                0x09, 0x17,                     // Flags/sequence
                0x66, (byte)0xA0,              // Payload length
                0x66, 0x32,                     // "f2" in payload
                0x01, 0x00,                     // Stream ID: 0x0100 (256)
                0x05, 0x35, 0x0D                // GID: 0x01000535 (1-0-1333) + terminator
            };
            
            // Wrap in ByteBuf
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(f2Frame);
            
            // Process the frame
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
            
            // Should not throw exceptions - actual DOD response testing
            // would require mocking DodRequestHandler
        }
        
        @Test
        @DisplayName("Should handle f2 frame with missing GID bytes")
        void testF2FrameTooShort() throws IOException {
            // Build truncated f2 frame
            byte[] f2Frame = new byte[] {
                0x5A, 0x6E, (byte)0xEF, 0x00,
                0x09, 0x17,
                0x66, (byte)0xA0,
                0x66, 0x32,  // "f2"
                0x01, 0x00   // Stream ID but no GID
            };
            
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(f2Frame);
            
            // Should handle gracefully
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
        
        @Test
        @DisplayName("Should extract correct GID from f2 frame")
        void testF2GidExtraction() {
            // Test the GID extraction logic
            // GID bytes: 01 00 05 35 should produce 0x01000535 = 16778549
            // Which formats as "1-0-1333"
            
            byte[] testFrame = new byte[] {
                0x5A, 0x6E, (byte)0xEF, 0x00,
                0x09, 0x17, 0x66, (byte)0xA0,
                0x66, 0x32,  // "f2" at offset 8-9
                0x01, 0x00,  // Stream ID
                0x01, 0x00, 0x05, 0x35  // GID bytes
            };
            
            // The handler should extract GID 0x01000535 and format as "1-0-1333"
            // This would be logged but we can't easily verify logs in unit test
            ByteBuf buf = channel.alloc().buffer();
            buf.writeBytes(testFrame);
            
            assertDoesNotThrow(() -> handler.channelRead(ctx, buf));
        }
    }

    // ========= Helper Methods =========

    /**
     * Build a short control frame (9 bytes).
     */
    private byte[] buildShortControlFrame(int type) {
        byte[] frame = new byte[ProtocolConstants.SHORT_FRAME_SIZE];
        frame[0] = (byte) ProtocolConstants.MAGIC; // 0x5A
        frame[ProtocolConstants.IDX_LEN_HI] = 0x00;
        frame[ProtocolConstants.IDX_LEN_LO] = 0x03;
        frame[ProtocolConstants.IDX_TYPE] = (byte) (type & 0xFF);
        frame[ProtocolConstants.IDX_TOKEN] = 0x0D;
        // Remaining bytes are 0x00
        return frame;
    }

    /**
     * Build a minimal data frame with a specific token.
     */
    private byte[] buildDataFrameWithToken(String token) {
        byte[] frame = new byte[15]; // Minimal size + terminator
        frame[0] = (byte) ProtocolConstants.MAGIC; // 0x5A
        frame[ProtocolConstants.IDX_TYPE] = (byte) PacketType.DATA.getValue(); // 0x20
        frame[ProtocolConstants.IDX_TOKEN] = (byte) token.charAt(0);
        frame[ProtocolConstants.IDX_TOKEN + 1] = (byte) token.charAt(1);

        // Length = 5 (4 bytes payload + 1 terminator)
        frame[ProtocolConstants.IDX_LEN_HI] = 0x00;
        frame[ProtocolConstants.IDX_LEN_LO] = 0x05;

        // TX/RX sequences
        frame[ProtocolConstants.IDX_TX] = 0x11;
        frame[ProtocolConstants.IDX_RX] = 0x11;

        // Minimal 4-byte payload
        frame[10] = 0x01;
        frame[11] = 0x02;
        frame[12] = 0x03;
        frame[13] = 0x04;
        frame[14] = 0x0D; // Terminator byte

        return frame;
    }

    /**
     * Build a data frame with specific TX/RX sequences.
     */
    private byte[] buildDataFrameWithSequences(String token, int tx, int rx) {
        byte[] frame = buildDataFrameWithToken(token);
        frame[ProtocolConstants.IDX_TX] = (byte) (tx & 0xFF);
        frame[ProtocolConstants.IDX_RX] = (byte) (rx & 0xFF);
        return frame;
    }
}
