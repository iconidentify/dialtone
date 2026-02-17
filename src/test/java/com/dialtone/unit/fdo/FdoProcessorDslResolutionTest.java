/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.fdo;

import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.dsl.FdoBuilder;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.fdo.dsl.builders.ConfigureActiveUsernameFdoBuilder;
import com.dialtone.protocol.ClientPlatform;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("FdoProcessor Type-Safe Builder API")
class FdoProcessorDslResolutionTest {

    @Mock
    private ChannelHandlerContext ctx;

    @Mock
    private Pacer pacer;

    private FdoCompiler compiler;
    private FdoProcessor processor;
    private SessionContext session;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Mock the allocator for P3ChunkEnqueuer
        ByteBufAllocator allocator = UnpooledByteBufAllocator.DEFAULT;
        when(ctx.alloc()).thenReturn(allocator);

        Properties props = new Properties();
        compiler = new FdoCompiler(props);
        processor = new FdoProcessor(compiler, pacer, 10);

        session = new SessionContext();
        session.setUsername("TestUser");
        session.setPlatform(ClientPlatform.WINDOWS);
        session.setAuthenticated(true);
    }

    @Test
    @DisplayName("Should compile and send static builder directly")
    void shouldCompileAndSendStaticBuilderDirectly() throws Exception {
        // Use type-safe static builder
        processor.compileAndSend(
            ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "TEST_NOOP");

        // Verify pacer was called (indicates compilation succeeded)
        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("TEST_NOOP")));
    }

    @Test
    @DisplayName("Should compile and send dynamic builder with config")
    void shouldCompileAndSendDynamicBuilderWithConfig() throws Exception {
        // Use type-safe dynamic builder with factory method
        FdoBuilder builder = ConfigureActiveUsernameFdoBuilder.forUser("SomeUser");

        processor.compileAndSend(ctx, builder, session, "At", -1, "TEST_CONFIG");

        // Verify pacer was called
        verify(pacer, atLeastOnce()).enqueue(any(), argThat(s -> s.startsWith("TEST_CONFIG")));
    }

    @Test
    @DisplayName("Static builder should implement FdoBuilder.Static")
    void staticBuilderShouldImplementStaticInterface() {
        assertTrue(NoopFdoBuilder.INSTANCE instanceof FdoBuilder.Static);
        assertTrue(NoopFdoBuilder.INSTANCE instanceof FdoBuilder);
    }

    @Test
    @DisplayName("Dynamic builder should implement FdoBuilder.Dynamic")
    void dynamicBuilderShouldImplementDynamicInterface() {
        ConfigureActiveUsernameFdoBuilder builder = ConfigureActiveUsernameFdoBuilder.forUser("Test");
        assertTrue(builder instanceof FdoBuilder.Dynamic);
        assertTrue(builder instanceof FdoBuilder);
        assertNotNull(builder.getConfig());
        assertEquals("Test", builder.getConfig().username());
    }

    @Test
    @DisplayName("Builder should generate valid FDO source")
    void builderShouldGenerateValidFdoSource() {
        String source = NoopFdoBuilder.INSTANCE.toSource(RenderingContext.DEFAULT);
        assertNotNull(source);
        assertFalse(source.isEmpty());
        // NoOp contains uni_end_stream
        assertTrue(source.contains("uni_end_stream"));
    }

    @Test
    @DisplayName("Should extract GID correctly from resource path")
    void shouldExtractGidCorrectlyFromResourcePath() {
        String gid1 = FdoCompiler.extractGidFromPath("fdo/noop.fdo.txt");
        assertEquals("noop", gid1);

        String gid2 = FdoCompiler.extractGidFromPath("fdo/post_login/username_config.fdo.txt");
        assertEquals("post_login/username_config", gid2);
    }
}
