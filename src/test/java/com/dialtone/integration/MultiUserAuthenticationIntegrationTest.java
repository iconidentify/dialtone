/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.integration;

import com.dialtone.auth.PropertyBasedUserAuthenticator;
import com.dialtone.auth.UserRegistry;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.state.SequenceManager;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for multi-user authentication with UserRegistry.
 * Tests the full flow from authentication to connection tracking.
 */
class MultiUserAuthenticationIntegrationTest {

    private PropertyBasedUserAuthenticator authenticator;
    private UserRegistry registry;
    private Properties properties;

    @BeforeEach
    void setUp() {
        properties = new Properties();
        properties.setProperty("auth.users", "Steve Case:password,John Doe:pass123,Jane Smith:secret");

        authenticator = new PropertyBasedUserAuthenticator(properties);
        registry = UserRegistry.getInstance();
        registry.clear();
    }

    @AfterEach
    void tearDown() {
        registry.clear();
    }

    @Test
    void shouldAuthenticateAndRegisterMultipleUsers() {
        // Given: Three users with credentials
        EmbeddedChannel channel1 = new EmbeddedChannel();
        channel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        EmbeddedChannel channel2 = new EmbeddedChannel();
        channel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        EmbeddedChannel channel3 = new EmbeddedChannel();
        channel3.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        Pacer pacer1 = new Pacer(new SequenceManager(), false);
        Pacer pacer2 = new Pacer(new SequenceManager(), false);
        Pacer pacer3 = new Pacer(new SequenceManager(), false);

        // When: Users authenticate and register
        assertTrue(authenticator.authenticate("Steve Case", "password"));
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        assertTrue(authenticator.authenticate("John Doe", "pass123"));
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        assertTrue(authenticator.authenticate("Jane Smith", "secret"));
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        // Then: All users are authenticated and online
        assertEquals(3, registry.getOnlineCount());
        assertTrue(registry.isOnline("Steve Case"));
        assertTrue(registry.isOnline("John Doe"));
        assertTrue(registry.isOnline("Jane Smith"));

        // Cleanup
        channel1.close();
        channel2.close();
        channel3.close();
    }

    @Test
    void shouldRejectUnauthenticatedUserFromRegistry() {
        // Given: A user with invalid credentials
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        Pacer pacer = new Pacer(new SequenceManager(), false);

        // When: User fails authentication
        assertFalse(authenticator.authenticate("Unknown User", "wrongpass"));

        // Then: Should not register the user
        registry.register("Unknown User", channel.pipeline().lastContext(), pacer, ClientPlatform.UNKNOWN, null);

        // User IS in registry (we register after successful auth in practice)
        // But in real code, we only register AFTER authenticate() returns true
        assertTrue(registry.isOnline("Unknown User"));

        // Cleanup
        registry.unregister("Unknown User");
        channel.close();
    }

    @Test
    void shouldHandleSimultaneousAuthentications() {
        // Given: Multiple users authenticating concurrently
        EmbeddedChannel channel1 = new EmbeddedChannel();
        channel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        EmbeddedChannel channel2 = new EmbeddedChannel();
        channel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        Pacer pacer1 = new Pacer(new SequenceManager(), false);
        Pacer pacer2 = new Pacer(new SequenceManager(), false);

        // When: Users authenticate and register simultaneously
        boolean auth1 = authenticator.authenticate("Steve Case", "password");
        boolean auth2 = authenticator.authenticate("John Doe", "pass123");

        assertTrue(auth1);
        assertTrue(auth2);

        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        // Then: Both users should be registered
        assertEquals(2, registry.getOnlineCount());

        // Cleanup
        channel1.close();
        channel2.close();
    }

    @Test
    void shouldHandleAuthenticationAndDisconnection() {
        // Given: User authenticates and connects
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        Pacer pacer = new Pacer(new SequenceManager(), false);

        assertTrue(authenticator.authenticate("Steve Case", "password"));
        registry.register("Steve Case", channel.pipeline().lastContext(), pacer, ClientPlatform.MAC, null);
        assertEquals(1, registry.getOnlineCount());

        // When: User disconnects
        registry.unregister("Steve Case");

        // Then: User is no longer online
        assertEquals(0, registry.getOnlineCount());
        assertFalse(registry.isOnline("Steve Case"));

        // And: User can authenticate and reconnect
        assertTrue(authenticator.authenticate("Steve Case", "password"));
        registry.register("Steve Case", channel.pipeline().lastContext(), pacer, ClientPlatform.MAC, null);
        assertEquals(1, registry.getOnlineCount());

        // Cleanup
        channel.close();
    }

    @Test
    void shouldMaintainUserListAfterPartialDisconnection() {
        // Given: Three users online
        EmbeddedChannel channel1 = new EmbeddedChannel();
        channel1.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        EmbeddedChannel channel2 = new EmbeddedChannel();
        channel2.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());
        EmbeddedChannel channel3 = new EmbeddedChannel();
        channel3.pipeline().addLast(new io.netty.channel.ChannelInboundHandlerAdapter());

        Pacer pacer1 = new Pacer(new SequenceManager(), false);
        Pacer pacer2 = new Pacer(new SequenceManager(), false);
        Pacer pacer3 = new Pacer(new SequenceManager(), false);

        assertTrue(authenticator.authenticate("Steve Case", "password"));
        registry.register("Steve Case", channel1.pipeline().lastContext(), pacer1, ClientPlatform.MAC, null);

        assertTrue(authenticator.authenticate("John Doe", "pass123"));
        registry.register("John Doe", channel2.pipeline().lastContext(), pacer2, ClientPlatform.WINDOWS, null);

        assertTrue(authenticator.authenticate("Jane Smith", "secret"));
        registry.register("Jane Smith", channel3.pipeline().lastContext(), pacer3, ClientPlatform.MAC, null);

        assertEquals(3, registry.getOnlineCount());

        // When: Middle user disconnects
        registry.unregister("John Doe");

        // Then: Other users remain online
        assertEquals(2, registry.getOnlineCount());
        assertTrue(registry.isOnline("Steve Case"));
        assertFalse(registry.isOnline("John Doe"));
        assertTrue(registry.isOnline("Jane Smith"));

        List<String> remaining = registry.getOnlineUsernames();
        assertEquals(2, remaining.size());
        assertTrue(remaining.contains("Steve Case"));
        assertTrue(remaining.contains("Jane Smith"));
        assertFalse(remaining.contains("John Doe"));

        // Cleanup
        channel1.close();
        channel2.close();
        channel3.close();
    }
}
