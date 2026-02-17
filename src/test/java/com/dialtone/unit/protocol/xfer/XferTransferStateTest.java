/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.xfer;

import com.dialtone.protocol.xfer.XferTransferState;
import com.dialtone.protocol.xfer.XferTransferState.Phase;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link XferTransferState}.
 */
class XferTransferStateTest {

    @Test
    void shouldInitializeInAwaitingXgPhase() {
        XferTransferState state = createTestState();

        assertEquals(Phase.AWAITING_XG, state.getPhase());
    }

    @Test
    void shouldTrackAllConstructorParameters() {
        String transferId = "xfer_test_123";
        String filename = "test.txt";
        byte[] fileData = "hello".getBytes();
        byte[] fileId = {0x01, 0x02, 0x03};
        byte[] encodedData = {0x68, 0x65, 0x6C, 0x6C, 0x6F};
        int timestamp = 1700000000;
        String username = "TestUser";

        XferTransferState state = new XferTransferState(
            transferId, filename, fileData, fileId, encodedData, timestamp, username);

        assertEquals(transferId, state.getTransferId());
        assertEquals(filename, state.getFilename());
        assertArrayEquals(fileData, state.getFileData());
        assertArrayEquals(fileId, state.getFileId());
        assertArrayEquals(encodedData, state.getEncodedData());
        assertEquals(timestamp, state.getTimestamp());
        assertEquals(username, state.getUsername());
    }

    @Test
    void shouldTransitionBetweenPhases() {
        XferTransferState state = createTestState();

        state.setPhase(Phase.SENDING_DATA);
        assertEquals(Phase.SENDING_DATA, state.getPhase());

        state.setPhase(Phase.COMPLETED);
        assertEquals(Phase.COMPLETED, state.getPhase());
    }

    @Test
    void shouldCalculateElapsedTime() throws InterruptedException {
        XferTransferState state = createTestState();

        Thread.sleep(50);

        long elapsed = state.getElapsedMs();
        assertTrue(elapsed >= 50, "Expected elapsed >= 50ms, got " + elapsed);
    }

    @Test
    void shouldReturnFileSizeFromFileData() {
        byte[] fileData = "test content".getBytes();
        XferTransferState state = new XferTransferState(
            "id", "file.txt", fileData, new byte[3], new byte[0], 0, "user");

        assertEquals(fileData.length, state.getFileSize());
    }

    @Test
    void shouldReturnZeroForNullFileData() {
        XferTransferState state = new XferTransferState(
            "id", "file.txt", null, new byte[3], new byte[0], 0, "user");

        assertEquals(0, state.getFileSize());
    }

    @Test
    void shouldCancelTimeoutFuture() {
        XferTransferState state = createTestState();

        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> mockFuture = mock(ScheduledFuture.class);
        when(mockFuture.cancel(false)).thenReturn(true);

        state.setTimeoutFuture(mockFuture);
        assertTrue(state.cancelTimeout());

        verify(mockFuture).cancel(false);
    }

    @Test
    void shouldReturnFalseWhenCancellingNullTimeout() {
        XferTransferState state = createTestState();

        assertFalse(state.cancelTimeout());
    }

    @Test
    void shouldProduceReadableToString() {
        XferTransferState state = new XferTransferState(
            "xfer_test_123", "test.txt", "hello".getBytes(),
            new byte[3], new byte[0], 0, "TestUser");

        String str = state.toString();

        assertTrue(str.contains("xfer_test_123"));
        assertTrue(str.contains("test.txt"));
        assertTrue(str.contains("AWAITING_XG"));
    }

    private XferTransferState createTestState() {
        return new XferTransferState(
            "xfer_test_" + System.currentTimeMillis(),
            "tiny.txt",
            "tiny\n".getBytes(),
            new byte[]{0x01, 0x02, 0x03},
            new byte[]{0x74, 0x69, 0x6E, 0x79, 0x0A},
            (int) (System.currentTimeMillis() / 1000),
            "TestUser"
        );
    }
}
