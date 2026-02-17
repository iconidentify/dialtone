/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.protocol.xfer;

import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.protocol.xfer.XferTransferState;
import com.dialtone.protocol.xfer.XferTransferState.Phase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link XferTransferRegistry}.
 */
class XferTransferRegistryTest {

    private XferTransferRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new XferTransferRegistry("TestUser");
    }

    @Test
    void shouldStartWithNoActiveTransfer() {
        assertFalse(registry.hasTransferAwaitingXg());
        assertNull(registry.getActiveTransfer());
    }

    @Test
    void shouldRegisterPendingTransfer() {
        XferTransferState state = createTestState();

        registry.registerPendingTransfer(state);

        assertTrue(registry.hasTransferAwaitingXg());
        assertSame(state, registry.getActiveTransfer());
    }

    @Test
    void shouldReturnTransferOnXgReceived() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        XferTransferState received = registry.onXgReceived();

        assertSame(state, received);
    }

    @Test
    void shouldReturnNullOnXgWhenNoTransfer() {
        XferTransferState received = registry.onXgReceived();

        assertNull(received);
    }

    @Test
    void shouldReturnNullOnXgWhenWrongPhase() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);
        state.setPhase(Phase.COMPLETED);

        XferTransferState received = registry.onXgReceived();

        assertNull(received);
    }

    @Test
    void shouldRejectDuplicateTransferWhilePending() {
        XferTransferState first = createTestState();
        XferTransferState second = createTestState();

        registry.registerPendingTransfer(first);

        assertThrows(IllegalStateException.class, () ->
            registry.registerPendingTransfer(second));
    }

    @Test
    void shouldAllowNewTransferAfterCompletion() {
        XferTransferState first = createTestState();
        registry.registerPendingTransfer(first);
        first.setPhase(Phase.COMPLETED);

        XferTransferState second = createTestState();
        registry.registerPendingTransfer(second);

        assertSame(second, registry.getActiveTransfer());
    }

    @Test
    void shouldMarkTransferAsCompleted() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        registry.markCompleted();

        assertEquals(Phase.COMPLETED, state.getPhase());
    }

    @Test
    void shouldMarkTransferAsFailed() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        registry.markFailed("Test failure");

        assertEquals(Phase.FAILED, state.getPhase());
    }

    @Test
    void shouldHandleTimeoutGracefully() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        registry.handleTimeout();

        assertEquals(Phase.FAILED, state.getPhase());
        assertFalse(registry.hasTransferAwaitingXg());
    }

    @Test
    void shouldIgnoreTimeoutIfNotAwaitingXg() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);
        state.setPhase(Phase.SENDING_DATA);

        registry.handleTimeout();

        assertEquals(Phase.SENDING_DATA, state.getPhase());
    }

    @Test
    void shouldClearActiveTransfer() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        registry.clearActiveTransfer();

        assertNull(registry.getActiveTransfer());
    }

    @Test
    void shouldCleanupOnClose() {
        XferTransferState state = createTestState();

        @SuppressWarnings("unchecked")
        ScheduledFuture<Void> mockFuture = mock(ScheduledFuture.class);
        state.setTimeoutFuture(mockFuture);

        registry.registerPendingTransfer(state);
        registry.close();

        verify(mockFuture).cancel(false);
        assertNull(registry.getActiveTransfer());
    }

    @Test
    void shouldCreateNewRegistryWithUpdatedUsername() {
        XferTransferState state = createTestState();
        registry.registerPendingTransfer(state);

        XferTransferRegistry newRegistry = registry.withUsername("NewUser");

        assertSame(state, newRegistry.getActiveTransfer());
    }

    private XferTransferState createTestState() {
        return new XferTransferState(
            "xfer_test_" + System.nanoTime(),
            "tiny.txt",
            "tiny\n".getBytes(),
            new byte[]{0x01, 0x02, 0x03},
            new byte[]{0x74, 0x69, 0x6E, 0x79, 0x0A},
            (int) (System.currentTimeMillis() / 1000),
            "TestUser"
        );
    }
}
