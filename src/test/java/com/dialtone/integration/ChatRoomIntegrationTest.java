/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.integration;

import com.dialtone.auth.PropertyBasedUserAuthenticator;
import com.dialtone.auth.UserRegistry;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.state.SequenceManager;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-user chat room functionality.
 * Tests chat room creation with real users and message broadcasting.
 */
class ChatRoomIntegrationTest {

    private UserRegistry registry;
    private EmbeddedChannel channel1;
    private EmbeddedChannel channel2;
    private EmbeddedChannel channel3;
    private Pacer pacer1;
    private Pacer pacer2;
    private Pacer pacer3;

    @BeforeEach
    void setUp() {
        registry = UserRegistry.getInstance();
        registry.clear();

        channel1 = new EmbeddedChannel();
        channel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        channel2 = new EmbeddedChannel();
        channel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        channel3 = new EmbeddedChannel();
        channel3.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        pacer1 = new Pacer(new SequenceManager(), false);
        pacer2 = new Pacer(new SequenceManager(), false);
        pacer3 = new Pacer(new SequenceManager(), false);
    }

    @AfterEach
    void tearDown() {
        registry.clear();
        channel1.close();
        channel2.close();
        channel3.close();
    }

    @Test
    void shouldCreateChatRoomWithMultipleOnlineUsers() {
        // Given: Multiple users are registered
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        // Then: All users should be online
        assertEquals(3, registry.getOnlineCount());
        assertTrue(registry.isOnline("Steve Case"));
        assertTrue(registry.isOnline("John Doe"));
        assertTrue(registry.isOnline("Jane Smith"));

        List<String> usernames = registry.getOnlineUsernames();
        assertEquals(3, usernames.size());
        assertTrue(usernames.contains("Steve Case"));
        assertTrue(usernames.contains("John Doe"));
        assertTrue(usernames.contains("Jane Smith"));
    }

    @Test
    void shouldUpdateChatRoomWhenUserDisconnects() {
        // Given: Three users are online
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);
        assertEquals(3, registry.getOnlineCount());

        // When: One user disconnects
        registry.unregister("John Doe");

        // Then: Only two users remain
        assertEquals(2, registry.getOnlineCount());
        assertTrue(registry.isOnline("Steve Case"));
        assertFalse(registry.isOnline("John Doe"));
        assertTrue(registry.isOnline("Jane Smith"));
    }

    @Test
    void shouldHandleUserReconnection() {
        // Given: User connects
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        assertEquals(1, registry.getOnlineCount());

        // When: User disconnects and reconnects
        registry.unregister("Steve Case");
        assertEquals(0, registry.getOnlineCount());

        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        // Then: User is online again
        assertEquals(1, registry.getOnlineCount());
        assertTrue(registry.isOnline("Steve Case"));
    }

    @Test
    void shouldBroadcastToAllOnlineUsers() {
        // Given: Multiple users are online
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        // When: Getting all connections for broadcasting
        List<UserRegistry.UserConnection> connections = registry.getAllConnections();

        // Then: Should have all users
        assertEquals(3, connections.size());
        assertTrue(connections.stream().allMatch(c -> c.isActive()));
    }

    @Test
    void shouldExcludeInactiveUsersFromBroadcast() {
        // Given: Multiple users are online
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        // When: One channel becomes inactive
        channel2.close();

        // Then: Should detect inactive connection
        UserRegistry.UserConnection conn2 = registry.getConnection("John Doe");
        assertNotNull(conn2);
        assertFalse(conn2.isActive());

        // Active connections still work
        UserRegistry.UserConnection conn1 = registry.getConnection("Steve Case");
        assertTrue(conn1.isActive());
        UserRegistry.UserConnection conn3 = registry.getConnection("Jane Smith");
        assertTrue(conn3.isActive());
    }

    @Test
    void shouldHandleEmptyChatRoom() {
        // Given: No users registered
        assertEquals(0, registry.getOnlineCount());

        // When: Getting online users
        List<String> usernames = registry.getOnlineUsernames();

        // Then: Should return empty list
        assertTrue(usernames.isEmpty());
    }
}
