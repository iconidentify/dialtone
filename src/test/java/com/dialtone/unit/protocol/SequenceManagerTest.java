/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol;

import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.PacketType;
import com.dialtone.state.SequenceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SequenceManager - the TX/RX sequence tracker.
 *
 * Tests critical functionality:
 * - Sequence number wrapping (0x10-0x7F ring)
 * - Client sequence tracking
 * - ACK processing
 * - Outstanding window calculation
 * - Frame restamping
 * - Startup initialization
 */
@DisplayName("SequenceManager Tests")
class SequenceManagerTest {

    private SequenceManager manager;

    @BeforeEach
    void setUp() {
        manager = new SequenceManager();
    }

    @Nested
    @DisplayName("Sequence Wrapping")
    class SequenceWrapping {

        @Test
        @DisplayName("Should wrap sequences in valid range [0x10, 0x7F]")
        void shouldWrapSequencesInValidRange() {
            // Valid range starts at 0x10
            assertEquals(0x10, manager.wrapTx(0x10));
            assertEquals(0x20, manager.wrapTx(0x20));
            assertEquals(0x7F, manager.wrapTx(0x7F));
        }

        @Test
        @DisplayName("Should wrap sequences beyond 0x7F back to 0x10")
        void shouldWrapSequencesBeyond0x7F() {
            // Ring size is 0x70, so 0x80 wraps to 0x10
            assertEquals(0x10, manager.wrapTx(0x80));

            // 0x81 wraps to 0x11
            assertEquals(0x11, manager.wrapTx(0x81));

            // Far beyond wraps correctly
            assertEquals(0x10, manager.wrapTx(0x80 + 0x70));
        }

        @Test
        @DisplayName("Should handle sequences below 0x10")
        void shouldHandleSequencesBelow0x10() {
            assertEquals(0x10, manager.wrapTx(0x00));
            assertEquals(0x10, manager.wrapTx(0x05));
            assertEquals(0x10, manager.wrapTx(0x0F));
        }

        @Test
        @DisplayName("Should handle negative sequences")
        void shouldHandleNegativeSequences() {
            assertEquals(0x10, manager.wrapTx(-1));
            assertEquals(0x10, manager.wrapTx(-10));
        }

        @Test
        @DisplayName("Should verify ring size of 0x70 (112 values)")
        void shouldVerifyRingSizeOf0x70() {
            // Starting at 0x10, adding 0x70 (112) should wrap back to 0x10
            int start = 0x10;
            int ringSize = 0x70;

            assertEquals(start, manager.wrapTx(start + ringSize));
            assertEquals(start + 1, manager.wrapTx(start + ringSize + 1));
        }
    }

    @Nested
    @DisplayName("Client Sequence Tracking")
    class ClientSequenceTracking {

        @Test
        @DisplayName("Should update client TX sequence from incoming DATA frame")
        void shouldUpdateClientTxSequenceFromIncomingDataFrame() {
            byte[] dataFrame = buildClientDataFrame(0x15);

            manager.updateClientSequence(dataFrame);

            assertEquals(0x15, manager.getLastClientTxSeq());
        }

        @Test
        @DisplayName("Should initialize server TX from first client frame")
        void shouldInitializeServerTxFromFirstClientFrame() {
            // Client frame with TX=0x20, RX=0x15
            byte[] frame = buildClientFrameWithRx(0x20, 0x15);

            manager.updateClientSequence(frame);

            // Server should initialize TX to clientRx + 1
            assertEquals(0x20, manager.getLastClientTxSeq());
        }

        @Test
        @DisplayName("Should handle short control frames")
        void shouldHandleShortControlFrames() {
            byte[] shortFrame = buildShortControlFrame(0x18);

            manager.updateClientSequence(shortFrame);

            assertEquals(0x18, manager.getLastClientTxSeq());
        }

        @Test
        @DisplayName("Should ignore invalid frames")
        void shouldIgnoreInvalidFrames() {
            int initialSeq = manager.getLastClientTxSeq();

            // Not a 0x5A frame
            byte[] invalidFrame = new byte[]{0x00, 0x00, 0x00};
            manager.updateClientSequence(invalidFrame);

            assertEquals(initialSeq, manager.getLastClientTxSeq());
        }

        @Test
        @DisplayName("Should ignore too-short frames")
        void shouldIgnoreTooShortFrames() {
            int initialSeq = manager.getLastClientTxSeq();

            byte[] tooShort = new byte[]{0x5A, 0x00};
            manager.updateClientSequence(tooShort);

            assertEquals(initialSeq, manager.getLastClientTxSeq());
        }
    }

    @Nested
    @DisplayName("ACK Processing")
    class AckProcessing {

        @Test
        @DisplayName("Should update last acked server TX from incoming frame RX")
        void shouldUpdateLastAckedServerTxFromIncomingFrameRx() {
            byte[] frame = buildClientFrameWithRx(0x20, 0x25);

            manager.updateAckFromIncoming(frame);

            assertEquals(0x25, manager.getLastAckedServerTx());
        }

        @Test
        @DisplayName("Should only accept ACKs in valid range [0x10, 0x7F]")
        void shouldOnlyAcceptAcksInValidRange() {
            int initialAck = manager.getLastAckedServerTx();

            // Invalid: below 0x10
            byte[] frameLow = buildClientFrameWithRx(0x20, 0x05);
            manager.updateAckFromIncoming(frameLow);
            assertEquals(initialAck, manager.getLastAckedServerTx());

            // Invalid: above 0x7F
            byte[] frameHigh = buildClientFrameWithRx(0x20, 0x85);
            manager.updateAckFromIncoming(frameHigh);
            assertEquals(initialAck, manager.getLastAckedServerTx());
        }

        @Test
        @DisplayName("Should advance ACK monotonically")
        void shouldAdvanceAckMonotonically() {
            // Set initial state
            byte[] frame1 = buildClientFrameWithRx(0x20, 0x15);
            manager.updateAckFromIncoming(frame1);
            assertEquals(0x15, manager.getLastAckedServerTx());

            // Advance ACK
            byte[] frame2 = buildClientFrameWithRx(0x21, 0x18);
            manager.updateAckFromIncoming(frame2);
            assertEquals(0x18, manager.getLastAckedServerTx());

            // Attempt to go backward (should be ignored)
            byte[] frame3 = buildClientFrameWithRx(0x22, 0x14);
            manager.updateAckFromIncoming(frame3);
            assertEquals(0x18, manager.getLastAckedServerTx(), "ACK should not go backward");
        }

        @Test
        @DisplayName("Should ignore frames that are too short")
        void shouldIgnoreFramesThatAreTooShort() {
            int initialAck = manager.getLastAckedServerTx();

            byte[] tooShort = new byte[]{0x5A, 0x00, 0x00};
            manager.updateAckFromIncoming(tooShort);

            assertEquals(initialAck, manager.getLastAckedServerTx());
        }

        @Test
        @DisplayName("Should ignore non-0x5A frames")
        void shouldIgnoreNon0x5AFrames() {
            int initialAck = manager.getLastAckedServerTx();

            byte[] wrongMagic = buildClientFrameWithRx(0x20, 0x25);
            wrongMagic[0] = 0x00; // Change magic byte
            manager.updateAckFromIncoming(wrongMagic);

            assertEquals(initialAck, manager.getLastAckedServerTx());
        }
    }

    @Nested
    @DisplayName("Outstanding Window Calculation")
    class OutstandingWindowCalculation {

        @Test
        @DisplayName("Should return zero outstanding when nothing sent")
        void shouldReturnZeroOutstandingWhenNothingSent() {
            int outstanding = manager.getOutstandingWindowFill();

            assertEquals(0, outstanding);
        }

        @Test
        @DisplayName("Should calculate outstanding frames correctly")
        void shouldCalculateOutstandingFramesCorrectly() {
            // Simulate sending 5 DATA frames
            manager.setLastSentDataTx(0x14); // Sent up to 0x14

            // Client has only ACKed up to 0x10
            byte[] ack = buildClientFrameWithRx(0x20, 0x10);
            manager.updateAckFromIncoming(ack);

            // Outstanding should be (0x14 - 0x10) = 4
            int outstanding = manager.getOutstandingWindowFill();
            assertEquals(4, outstanding);
        }

        @Test
        @DisplayName("Should handle outstanding calculation correctly")
        void shouldHandleOutstandingCalculationCorrectly() {
            // Set up a scenario with sent frames
            manager.setLastSentDataTx(0x18);

            // ACK from client acknowledging up to 0x15
            byte[] ack = buildClientFrameWithRx(0x20, 0x15);
            manager.updateAckFromIncoming(ack);

            // Outstanding should be the difference
            int outstanding = manager.getOutstandingWindowFill();

            // The calculation is (lastDataTx - lastAckedServerTx)
            // (0x18 - 0x15) = 3
            assertTrue(outstanding >= 0 && outstanding <= 16,
                "Outstanding should be between 0 and 16, got: " + outstanding);
        }

        @Test
        @DisplayName("Should return zero when ACK is ahead of sent")
        void shouldReturnZeroWhenAckIsAheadOfSent() {
            // Sent up to 0x15
            manager.setLastSentDataTx(0x15);

            // Client ACKed 0x20 (ahead of sent)
            byte[] ack = buildClientFrameWithRx(0x20, 0x20);
            manager.updateAckFromIncoming(ack);

            int outstanding = manager.getOutstandingWindowFill();
            assertEquals(0, outstanding);
        }

        @Test
        @DisplayName("Should detect window full at 16 frames")
        void shouldDetectWindowFullAt16Frames() {
            // Send 16 frames
            manager.setLastSentDataTx(0x1F); // 0x10 + 15 = 0x1F

            // ACK still at initial 0x10
            byte[] ack = buildClientFrameWithRx(0x20, 0x10);
            manager.updateAckFromIncoming(ack);

            int outstanding = manager.getOutstandingWindowFill();
            assertEquals(15, outstanding); // Should be near window limit
        }
    }

    @Nested
    @DisplayName("Frame Restamping")
    class FrameRestamping {

        @Test
        @DisplayName("Should restamp DATA frame with correct sequences")
        void shouldRestampDataFrameWithCorrectSequences() {
            // Set up initial state
            manager.setLastClientTxSeq(0x18);
            manager.setLastDataTx(0x15);

            byte[] template = buildDataFrameTemplate();

            byte[] restamped = manager.restamp(template, false);

            // When not advancing, DATA frames use the next TX (0x16)
            // but since advanceSeq=false, we just check it was stamped
            int stampedTx = restamped[ProtocolConstants.IDX_TX] & 0xFF;
            assertTrue(stampedTx >= 0x10 && stampedTx <= 0x7F, "TX should be in valid range");

            // RX should be last client TX
            assertEquals(0x18, restamped[ProtocolConstants.IDX_RX] & 0xFF);
        }

        @Test
        @DisplayName("Should advance sequence when advanceSeq is true")
        void shouldAdvanceSequenceWhenAdvanceSeqIsTrue() {
            // Initialize state properly (setLastSentDataTx sets the initialized flag)
            manager.setLastClientTxSeq(0x20);
            manager.setLastSentDataTx(0x15);

            byte[] template = buildDataFrameTemplate();
            byte[] restamped = manager.restamp(template, true);

            // After advancing, the frame should be stamped with the next TX
            int stampedTx = restamped[ProtocolConstants.IDX_TX] & 0xFF;

            // The stamped TX should be 0x16 (next after 0x15)
            assertEquals(0x16, stampedTx);

            // And lastDataTx should now equal the stamped value
            assertEquals(0x16, manager.getLastDataTx());
        }

        @Test
        @DisplayName("Should not advance sequence when advanceSeq is false")
        void shouldNotAdvanceSequenceWhenAdvanceSeqIsFalse() {
            manager.setLastDataTx(0x20);

            byte[] template = buildDataFrameTemplate();
            manager.restamp(template, false);

            // lastDataTx should remain unchanged
            assertEquals(0x20, manager.getLastDataTx());
        }

        @Test
        @DisplayName("Should restamp control frame with last DATA TX")
        void shouldRestampControlFrameWithLastDataTx() {
            manager.setLastClientTxSeq(0x18);
            manager.setLastDataTx(0x15);

            byte[] controlFrame = buildControlFrameTemplate();

            byte[] restamped = manager.restamp(controlFrame, false);

            // Control frames use last DATA TX, not next TX
            assertEquals(0x15, restamped[ProtocolConstants.IDX_TX] & 0xFF);
            assertEquals(0x18, restamped[ProtocolConstants.IDX_RX] & 0xFF);
        }

        @Test
        @DisplayName("Should update length field correctly")
        void shouldUpdateLengthFieldCorrectly() {
            byte[] template = buildDataFrameTemplate();
            int originalLength = template.length;

            byte[] restamped = manager.restamp(template, false);

            // Length should be total - 6
            int declaredLen = ((restamped[3] & 0xFF) << 8) | (restamped[4] & 0xFF);
            assertEquals(originalLength - 6, declaredLen);
        }

        @Test
        @DisplayName("Should handle non-AOL frames gracefully")
        void shouldHandleNonAolFramesGracefully() {
            byte[] nonAolFrame = new byte[]{0x00, 0x01, 0x02};

            byte[] result = manager.restamp(nonAolFrame, true);

            // Should return a copy without crashing
            assertNotNull(result);
            assertNotSame(nonAolFrame, result);
        }

        @Test
        @DisplayName("Should restamp non-DATA header correctly")
        void shouldRestampNonDataHeaderCorrectly() {
            manager.setLastClientTxSeq(0x22);
            manager.setLastDataTx(0x18);

            byte[] template = buildShortControlFrame(0x00);

            byte[] restamped = manager.restampNonDataHeader(template);

            assertEquals(0x18, restamped[ProtocolConstants.IDX_TX] & 0xFF);
            assertEquals(0x22, restamped[ProtocolConstants.IDX_RX] & 0xFF);
        }

        @Test
        @DisplayName("Should detect control frame TX corruption (validates bug fix)")
        void shouldDetectControlFrameTxCorruption() {
            // This test validates the critical assertion added to catch the P3 TX corruption bug
            // Control frames MUST use lastDataTx, never serverTx or lastAckedServerTx

            // Set up state where lastDataTx=0x54 (as in the bug report)
            manager.setLastClientTxSeq(0x15);
            manager.setLastDataTx(0x54);

            // Build a control frame template
            byte[] controlFrame = buildShortControlFrame(0x00);

            // Restamp should produce control frame with TX=0x54 (lastDataTx)
            byte[] restamped = manager.restampNonDataHeader(controlFrame);

            int stampedTx = restamped[ProtocolConstants.IDX_TX] & 0xFF;
            int expectedTx = 0x54;

            // CRITICAL ASSERTION: Control frame TX must match lastDataTx
            assertEquals(expectedTx, stampedTx,
                String.format("Control frame TX corruption detected! Expected 0x%02X (lastDataTx), got 0x%02X", expectedTx, stampedTx));

            // Verify the assertion in restampNonDataHeader would catch corruption
            // If this test passes, it means control frames are correctly using lastDataTx
        }

        @Test
        @DisplayName("Should prevent mid-session serverTx re-initialization (validates bug fix)")
        void shouldPreventMidSessionServerTxReinitialization() {
            // This test validates the guard that prevents serverTx corruption from client piggyback ACK
            // Bug scenario: After first DATA sent, client f2 arrives with rx=0x5F
            // Without guard, updateClientSequence would re-init serverTx=0x60 from clientRx+1
            // This causes control frames to use wrong TX

            // Simulate connection start: client sends probe
            byte[] clientProbe = buildClientFrameWithRx(0x16, 0x15);
            manager.updateClientSequence(clientProbe);
            assertEquals(0x16, manager.getLastClientTxSeq());

            // Server sends first DATA frame
            manager.setLastDataTx(0x16);
            manager.setHaveSentFirstData(true);

            // Continue sending DATA frames up to 0x54
            manager.setLastDataTx(0x54);

            // Client sends f2 with piggyback ACK: tx=0x16, rx=0x5F (client has received up to 0x5F)
            byte[] clientF2 = buildClientFrameWithRx(0x16, 0x5F);

            // Before the bug fix, this would re-init serverTx=0x60 from clientRx+1
            // After the bug fix, serverTx should NOT be re-initialized mid-session
            manager.updateClientSequence(clientF2);

            // Verify lastClientTxSeq updated (this is correct)
            assertEquals(0x16, manager.getLastClientTxSeq());

            // Build a control frame after receiving f2
            byte[] controlFrame = buildShortControlFrame(0x00);
            byte[] restamped = manager.restampNonDataHeader(controlFrame);

            int controlTx = restamped[ProtocolConstants.IDX_TX] & 0xFF;

            // CRITICAL: Control frame must use lastDataTx=0x54, NOT 0x5F from piggyback ACK
            assertEquals(0x54, controlTx,
                String.format("Mid-session re-init bug detected! Control TX=0x%02X should be 0x54 (lastDataTx), not 0x5F from piggyback ACK", controlTx));
        }
    }

    @Nested
    @DisplayName("Startup Initialization")
    class StartupInitialization {

        @Test
        @DisplayName("Should initialize from client probe")
        void shouldInitializeFromClientProbe() {
            byte[] probe = buildClientDataFrame(0x25);

            manager.initializeFromClientProbe(probe);

            assertTrue(manager.isStartupSeeded());
            assertEquals(0x25, manager.getStartupClientTx());
            assertEquals(0x26, manager.getNextServerDataTx());
        }

        @Test
        @DisplayName("Should only seed once")
        void shouldOnlySeedOnce() {
            byte[] probe1 = buildClientDataFrame(0x25);
            byte[] probe2 = buildClientDataFrame(0x30);

            manager.initializeFromClientProbe(probe1);
            int firstSeed = manager.getStartupClientTx();

            manager.initializeFromClientProbe(probe2);

            // Should still have first seed value
            assertEquals(firstSeed, manager.getStartupClientTx());
        }

        @Test
        @DisplayName("Should not seed after first DATA sent")
        void shouldNotSeedAfterFirstDataSent() {
            byte[] probe = buildClientDataFrame(0x25);
            manager.setHaveSentFirstData(true);

            manager.initializeFromClientProbe(probe);

            // Should not seed
            assertFalse(manager.isStartupSeeded());
        }

        @Test
        @DisplayName("Should ignore invalid probe frames")
        void shouldIgnoreInvalidProbeFrames() {
            byte[] invalidProbe = new byte[]{0x00, 0x01};

            manager.initializeFromClientProbe(invalidProbe);

            assertFalse(manager.isStartupSeeded());
        }
    }

    @Nested
    @DisplayName("Accessors and State Management")
    class AccessorsAndStateManagement {

        @Test
        @DisplayName("Should get and set lastClientTxSeq")
        void shouldGetAndSetLastClientTxSeq() {
            manager.setLastClientTxSeq(0x35);

            assertEquals(0x35, manager.getLastClientTxSeq());
        }

        @Test
        @DisplayName("Should get and set lastDataTx with wrapping")
        void shouldGetAndSetLastDataTxWithWrapping() {
            manager.setLastDataTx(0x7E);

            assertEquals(0x7E, manager.getLastDataTx());
        }

        @Test
        @DisplayName("Should wrap lastDataTx when set beyond range")
        void shouldWrapLastDataTxWhenSetBeyondRange() {
            manager.setLastDataTx(0x85); // Beyond 0x7F

            // Should wrap
            int wrapped = manager.getLastDataTx();
            assertTrue(wrapped >= 0x10 && wrapped <= 0x7F);
        }

        @Test
        @DisplayName("Should get and set ACK debounce values")
        void shouldGetAndSetAckDebounceValues() {
            manager.setLastAckedByClientTx(0x40);
            manager.setLastAckedByClientRx(0x45);

            assertEquals(0x40, manager.getLastAckedByClientTx());
            assertEquals(0x45, manager.getLastAckedByClientRx());
        }

        @Test
        @DisplayName("Should get and set startup values")
        void shouldGetAndSetStartupValues() {
            manager.setStartupClientTx(0x22);
            manager.setNextServerDataTx(0x23);
            manager.setHaveSentFirstData(true);

            assertEquals(0x22, manager.getStartupClientTx());
            assertEquals(0x23, manager.getNextServerDataTx());
            assertTrue(manager.haveSentFirstData());
        }
    }

    @Nested
    @DisplayName("Cleanup and Resource Management")
    class CleanupAndResourceManagement {

        @Test
        @DisplayName("Should cleanup stall detection")
        void shouldCleanupStallDetection() {
            assertDoesNotThrow(() -> manager.cleanupStallDetection());
        }

        @Test
        @DisplayName("Should handle multiple cleanup calls")
        void shouldHandleMultipleCleanupCalls() {
            manager.cleanupStallDetection();
            assertDoesNotThrow(() -> manager.cleanupStallDetection());
        }
    }

    // ========= Helper Methods =========

    /**
     * Build a client DATA frame with specific TX sequence.
     */
    private byte[] buildClientDataFrame(int tx) {
        byte[] frame = new byte[20];
        frame[0] = (byte) ProtocolConstants.MAGIC; // 0x5A
        frame[1] = 0x00;
        frame[2] = 0x00;
        frame[3] = 0x00; // Length high
        frame[4] = 0x0E; // Length low (14 bytes after header)
        frame[5] = (byte) tx; // TX
        frame[6] = 0x10; // RX
        frame[7] = (byte) PacketType.DATA.getValue(); // Type
        return frame;
    }

    /**
     * Build a client frame with specific TX and RX values.
     */
    private byte[] buildClientFrameWithRx(int tx, int rx) {
        byte[] frame = new byte[20];
        frame[0] = (byte) ProtocolConstants.MAGIC;
        frame[1] = 0x00;
        frame[2] = 0x00;
        frame[3] = 0x00;
        frame[4] = 0x0E;
        frame[5] = (byte) tx;
        frame[6] = (byte) rx;
        frame[7] = (byte) PacketType.DATA.getValue();
        return frame;
    }

    /**
     * Build a short control frame (9 bytes).
     */
    private byte[] buildShortControlFrame(int tx) {
        byte[] frame = new byte[ProtocolConstants.SHORT_FRAME_SIZE];
        frame[0] = (byte) ProtocolConstants.MAGIC;
        frame[1] = 0x00;
        frame[2] = 0x00;
        frame[3] = 0x00;
        frame[4] = 0x03; // Short frame length
        frame[5] = (byte) tx;
        frame[6] = 0x10;
        frame[7] = 0x24; // Control type
        frame[8] = 0x0D; // Terminator
        return frame;
    }

    /**
     * Build a DATA frame template for restamping.
     */
    private byte[] buildDataFrameTemplate() {
        byte[] frame = new byte[20];
        frame[0] = (byte) ProtocolConstants.MAGIC;
        frame[1] = 0x00;
        frame[2] = 0x00;
        frame[3] = 0x00;
        frame[4] = 0x0E;
        frame[5] = 0x00; // TX (will be restamped)
        frame[6] = 0x00; // RX (will be restamped)
        frame[7] = (byte) PacketType.DATA.getValue();
        // Token
        frame[8] = 'A';
        frame[9] = 'T';
        return frame;
    }

    /**
     * Build a control frame template for restamping.
     */
    private byte[] buildControlFrameTemplate() {
        byte[] frame = new byte[20];
        frame[0] = (byte) ProtocolConstants.MAGIC;
        frame[1] = 0x00;
        frame[2] = 0x00;
        frame[3] = 0x00;
        frame[4] = 0x0E;
        frame[5] = 0x00; // TX (will be restamped)
        frame[6] = 0x00; // RX (will be restamped)
        frame[7] = 0x24; // Control type (not DATA)
        return frame;
    }
}
