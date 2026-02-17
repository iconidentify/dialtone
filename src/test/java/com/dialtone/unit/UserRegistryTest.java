/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit;

import com.dialtone.auth.UserRegistry;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.state.SequenceManager;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UserRegistryTest {

    private UserRegistry registry;
    private EmbeddedChannel channel1;
    private EmbeddedChannel channel2;
    private Pacer pacer1;
    private Pacer pacer2;

    @BeforeEach
    void setUp() {
        registry = UserRegistry.getInstance();
        registry.clear();  // Start with clean registry

        channel1 = new EmbeddedChannel();
        channel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        channel2 = new EmbeddedChannel();
        channel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        pacer1 = new Pacer(new SequenceManager(), false);
        pacer2 = new Pacer(new SequenceManager(), false);
    }

    @AfterEach
    void tearDown() {
        registry.clear();  // Clean up after each test
        channel1.close();
        channel2.close();
    }

    @Test
    void shouldRegisterUser() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        assertTrue(registry.isOnline("Steve Case"));
        assertEquals(1, registry.getOnlineCount());
    }

    @Test
    void shouldUnregisterUser() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        assertTrue(registry.isOnline("Steve Case"));

        boolean removed = registry.unregister("Steve Case");

        assertTrue(removed);
        assertFalse(registry.isOnline("Steve Case"));
        assertEquals(0, registry.getOnlineCount());
    }

    @Test
    void shouldReturnFalseWhenUnregisteringNonexistentUser() {
        boolean removed = registry.unregister("Unknown User");

        assertFalse(removed);
    }

    @Test
    void shouldSupportMultipleUsers() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        assertTrue(registry.isOnline("Steve Case"));
        assertTrue(registry.isOnline("John Doe"));
        assertEquals(2, registry.getOnlineCount());
    }

    @Test
    void shouldBeCaseInsensitiveForLookup() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        assertTrue(registry.isOnline("steve case"));
        assertTrue(registry.isOnline("STEVE CASE"));
        assertTrue(registry.isOnline("StEvE cAsE"));

        UserRegistry.UserConnection conn = registry.getConnection("steve case");
        assertNotNull(conn);
        assertEquals("Steve Case", conn.getUsername());  // Original casing preserved
    }

    @Test
    void shouldReplaceExistingConnection() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        assertEquals(1, registry.getOnlineCount());

        // Register same user with different connection
        registry.register("Steve Case", channel2.pipeline().lastContext(), pacer2, ClientPlatform.MAC, null);

        // Should still be 1 user (replaced, not added)
        assertEquals(1, registry.getOnlineCount());

        UserRegistry.UserConnection conn = registry.getConnection("Steve Case");
        assertNotNull(conn);
        // Should have the new pacer, not the old one
        assertSame(pacer2, conn.getPacer());
    }

    @Test
    void shouldGetAllConnections() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        List<UserRegistry.UserConnection> connections = registry.getAllConnections();

        assertEquals(2, connections.size());
        assertTrue(connections.stream().anyMatch(c -> c.getUsername().equals("Steve Case")));
        assertTrue(connections.stream().anyMatch(c -> c.getUsername().equals("John Doe")));
    }

    @Test
    void shouldGetOnlineUsernames() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        List<String> usernames = registry.getOnlineUsernames();

        assertEquals(2, usernames.size());
        assertTrue(usernames.contains("Steve Case"));
        assertTrue(usernames.contains("John Doe"));
    }

    @Test
    void shouldHandleNullValues() {
        registry.register(null, channel1.pipeline().lastContext(), pacer1, ClientPlatform.UNKNOWN, null);
        assertEquals(0, registry.getOnlineCount());

        registry.register("Steve Case", null, pacer1, ClientPlatform.MAC, null);
        assertEquals(0, registry.getOnlineCount());

        registry.register("Steve Case", channel1.pipeline().lastContext(), null, ClientPlatform.MAC, null);
        assertEquals(0, registry.getOnlineCount());

        assertFalse(registry.isOnline(null));
        assertNull(registry.getConnection(null));
        assertFalse(registry.unregister(null));
    }

    @Test
    void shouldGetConnectionDetails() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        UserRegistry.UserConnection conn = registry.getConnection("Steve Case");

        assertNotNull(conn);
        assertEquals("Steve Case", conn.getUsername());
        assertSame(channel1.pipeline().lastContext(), conn.getContext());
        assertSame(pacer1, conn.getPacer());
        assertTrue(conn.isActive());
    }

    @Test
    void shouldDetectInactiveConnection() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        UserRegistry.UserConnection conn = registry.getConnection("Steve Case");
        assertTrue(conn.isActive());

        // Close channel
        channel1.close();

        // Connection should now be inactive
        assertFalse(conn.isActive());
    }

    @Test
    void shouldClearAllConnections() {
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        assertEquals(2, registry.getOnlineCount());

        registry.clear();

        assertEquals(0, registry.getOnlineCount());
        assertFalse(registry.isOnline("Steve Case"));
        assertFalse(registry.isOnline("John Doe"));
    }

    @Test
    void shouldReturnSingletonInstance() {
        UserRegistry instance1 = UserRegistry.getInstance();
        UserRegistry instance2 = UserRegistry.getInstance();

        assertSame(instance1, instance2);
    }

    // Single-Session Enforcement Tests

    @Test
    void shouldReturnOldConnectionWhenReplacingExistingSession() {
        // Given: User is already connected
        UserRegistry.UserConnection oldConnection = registry.register(
            "Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        assertNull(oldConnection, "First registration should not return old connection");

        // When: Same user registers from new location
        UserRegistry.UserConnection replacedConnection = registry.register(
            "Steve Case", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        // Then: Should return the old connection
        assertNotNull(replacedConnection, "Should return old connection when replacing");
        assertSame(pacer1, replacedConnection.getPacer());
        assertEquals(ClientPlatform.MAC, replacedConnection.getPlatform());

        // And: Only new connection should be in registry
        assertEquals(1, registry.getOnlineCount());
        UserRegistry.UserConnection current = registry.getConnection("Steve Case");
        assertSame(pacer2, current.getPacer());
        assertEquals(ClientPlatform.WINDOWS, current.getPlatform());
    }

    @Test
    void shouldInvokeDisconnectHandlerWhenReplacingSession() throws InterruptedException {
        // Given: User is already connected
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        // Create a mock disconnect handler to track invocation
        final boolean[] handlerCalled = {false};
        final UserRegistry.UserConnection[] disconnectedConnection = {null};

        com.dialtone.auth.SessionDisconnectHandler mockHandler =
            (connection, message) -> {
                handlerCalled[0] = true;
                disconnectedConnection[0] = connection;
                assertEquals("You've been signed on from another location", message);
                return java.util.concurrent.CompletableFuture.completedFuture(null);
            };

        // When: Same user registers from new location with disconnect handler
        registry.register("Steve Case", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, mockHandler);

        // Give async disconnect time to execute
        Thread.sleep(100);

        // Then: Disconnect handler should have been called
        assertTrue(handlerCalled[0], "Disconnect handler should have been invoked");
        assertNotNull(disconnectedConnection[0]);
        assertSame(pacer1, disconnectedConnection[0].getPacer());
    }

    @Test
    void shouldForceCloseWhenNoDisconnectHandlerProvided() {
        // Given: User is already connected
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        assertTrue(channel1.isActive());

        // When: Same user registers from new location without disconnect handler
        registry.register("Steve Case", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        // Then: New connection should be registered
        assertEquals(1, registry.getOnlineCount());
        UserRegistry.UserConnection current = registry.getConnection("Steve Case");
        assertSame(pacer2, current.getPacer());
    }

    @Test
    void shouldHandleMultipleRapidReconnects() {
        // Given: User connects multiple times rapidly
        EmbeddedChannel channel3 = new EmbeddedChannel();
        channel3.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        Pacer pacer3 = new Pacer(new SequenceManager(), false);

        // When: Rapid reconnects
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("Steve Case", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        UserRegistry.UserConnection finalConnection = registry.register(
            "Steve Case", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        // Then: Should maintain single session with latest connection
        assertEquals(1, registry.getOnlineCount());
        UserRegistry.UserConnection current = registry.getConnection("Steve Case");
        assertSame(pacer3, current.getPacer());
        assertNotNull(finalConnection);

        channel3.close();
    }

    @Test
    void shouldPreserveCaseAfterSessionReplacement() {
        // Given: User registers with specific casing
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        // When: Same user (different casing) registers from new location
        registry.register("STEVE CASE", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        // Then: Should use new casing
        assertEquals(1, registry.getOnlineCount());
        UserRegistry.UserConnection connection = registry.getConnection("steve case");
        assertEquals("STEVE CASE", connection.getUsername());
    }
}
