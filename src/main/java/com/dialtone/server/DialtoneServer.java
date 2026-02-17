/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.server;

import com.dialtone.ai.GrokConversationalService;
import com.dialtone.ai.ResponseFormatter;
import com.dialtone.ai.UnifiedNewsService;
import com.dialtone.art.ArtService;
import com.dialtone.auth.UserRegistry;
import com.dialtone.db.DatabaseManager;
import com.dialtone.protocol.keyword.handlers.*;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.web.services.ScreennamePreferencesService;
import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.chat.bot.GrokBot;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.dsl.FdoDslRegistry;
import com.dialtone.fdo.dsl.builders.DodNotAvailableFdoBuilder;
import com.dialtone.fdo.dsl.builders.EphemeralWelcomeFdoBuilder;
import com.dialtone.fdo.dsl.builders.F1DodFailedFdoBuilder;
import com.dialtone.fdo.dsl.builders.Gid32_294FdoBuilder;
import com.dialtone.fdo.dsl.builders.Gid69_420FdoBuilder;
import com.dialtone.fdo.dsl.builders.Gid69_421FdoBuilder;
import com.dialtone.fdo.dsl.builders.GuestLoginFdoBuilder;
import com.dialtone.fdo.dsl.builders.IncorrectLoginFdoBuilder;
import com.dialtone.fdo.dsl.builders.LogoutFdoBuilder;
import com.dialtone.fdo.dsl.builders.MotdFdoBuilder;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.fdo.dsl.builders.ResetWelcomeWindowArtFdoBuilder;
import com.dialtone.fdo.dsl.builders.TosFdoBuilder;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.keyword.KeywordRegistry;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageFactory;
import com.dialtone.utils.LoggerUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.io.IOException;
import java.util.Properties;

public class DialtoneServer {

    private final int port;
    private final String bindAddr;
    private final boolean verbose;

    @SuppressWarnings("unused")
    private final long delayMs;

    private final Properties configuration;

    private UnifiedNewsService unifiedNewsService;
    private GrokConversationalService grokConversationalService;
    private UserRegistry userRegistry;
    private FileStorage fileStorage;

    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;
    private Channel serverChannel;

    public DialtoneServer(int port, String bindAddr, boolean verbose, long delayMs) {
        this(port, bindAddr, verbose, delayMs, null);
    }

    public DialtoneServer(int port, String bindAddr, boolean verbose, long delayMs, Properties configuration) {
        this.port = port;
        this.bindAddr = bindAddr;
        this.verbose = verbose;
        this.delayMs = delayMs;
        this.configuration = configuration;
    }

    public void start() throws InterruptedException {
        Properties props = (configuration != null) ? configuration : loadApplicationProperties();
        initializeSharedServices(props);

        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap sb = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast("logger-in", new LoggingHandler("INBOUND", LogLevel.DEBUG));
                            ch.pipeline().addLast(new StatefulClientHandler(verbose, unifiedNewsService, userRegistry, props, fileStorage));
                            ch.pipeline().addLast("logger-out", new LoggingHandler("OUTBOUND", LogLevel.DEBUG));
                        }
                    })
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.SO_LINGER, -1)
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(32 * 1024, 64 * 1024));

            LoggerUtil.info("Binding " + bindAddr + ":" + port + " ...");
            ChannelFuture bindFuture = sb.bind(bindAddr, port).sync();
            serverChannel = bindFuture.channel();
            LoggerUtil.info("Server ready on " + bindAddr + ":" + port);

        } catch (Exception e) {
            shutdownSharedServices();
            if (bossGroup != null) bossGroup.shutdownGracefully();
            if (workerGroup != null) workerGroup.shutdownGracefully();
            throw e;
        }
    }

    private void initializeSharedServices(Properties props) {
        try {
            userRegistry = UserRegistry.getInstance();
            unifiedNewsService = new UnifiedNewsService(props);

            try {
                grokConversationalService = new GrokConversationalService(props);
            } catch (Exception e) {
                LoggerUtil.warn("GrokConversationalService failed: " + e.getMessage());
                grokConversationalService = null;
            }

            // Initialize storage abstraction (used by keyword handlers and StatefulClientHandler)
            try {
                fileStorage = StorageFactory.createWithFallback(props);
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize file storage", e);
            }

            ChatBotRegistry.initialize(props);
            initializeChatBots(props);
            initializeKeywordHandlers(props);
            initializeFdoDslBuilders();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize shared services", e);
        }
    }

    private void initializeChatBots(Properties props) {
        ChatBotRegistry registry = ChatBotRegistry.getInstance();
        ResponseFormatter responseFormatter = new ResponseFormatter(grokConversationalService, props);
        GrokBot grok = new GrokBot(grokConversationalService, responseFormatter);
        registry.registerBot(grok);
    }

    private void initializeKeywordHandlers(Properties props) {
        KeywordRegistry registry = KeywordRegistry.getInstance();
        FdoCompiler fdoCompiler = new FdoCompiler(props);
        ArtService artService = new ArtService();

        // Initialize preferences service for BW variant resolution
        String dbPath = props.getProperty("db.path", "db/dialtone.db");
        DatabaseManager dbManager = DatabaseManager.getInstance(dbPath);
        ScreennamePreferencesService preferencesService = new ScreennamePreferencesService(dbManager);

        registry.registerHandler(new ServerLogsKeywordHandler(fdoCompiler));
        registry.registerHandler(new ImageViewerKeywordHandler(fdoCompiler, artService));
        registry.registerHandler(new InvokeKeywordHandler(fdoCompiler));

        registry.registerHandler(new TosKeywordHandler(fdoCompiler, props, preferencesService));
        registry.registerHandler(new SkalholtKeywordHandler(fdoCompiler));

        // Content window keyword handlers (reusable pattern)
        registry.registerHandler(new PieterKeywordHandler(fdoCompiler));

        // MVP file transfer handlers (fileStorage initialized in initializeSharedServices)
        XferService xferService = new XferService(fdoCompiler);
        registry.registerHandler(new HelloWorldKeywordHandler(xferService));
        registry.registerHandler(new DownloadKeywordHandler(xferService, fdoCompiler, fileStorage));

        // File browser keyword handler (needs direct LocalFileSystemStorage for directory browsing)
        FileBrowserService fileBrowserService = new FileBrowserService(
            StorageFactory.createLocalStorage(props));
        registry.registerHandler(new FileBrowserKeywordHandler(fileBrowserService, fdoCompiler));

        // MVP file upload handlers (client to server)
        long phaseTimeoutMs = Long.parseLong(props.getProperty("upload.phase.timeout.ms", "30000"));
        XferUploadService xferUploadService = new XferUploadService(
            fileStorage,
            (int) fileStorage.getMaxFileSizeBytes(),
            phaseTimeoutMs
        );
        registry.registerHandler(new UploadKeywordHandler(xferUploadService, fdoCompiler));
    }

    /**
     * Initialize FDO DSL builders.
     * DSL builders generate FDO source programmatically as an alternative to text templates.
     * They are checked first during DOD request processing before falling back to filesystem.
     */
    private void initializeFdoDslBuilders() {
        FdoDslRegistry registry = FdoDslRegistry.getInstance();

        // Register DSL builders for atom stream GIDs
        registry.registerBuilder(new Gid69_420FdoBuilder());
        registry.registerBuilder(new Gid69_421FdoBuilder());  // MAP window
        registry.registerBuilder(new TosFdoBuilder());        // TOS modal
        registry.registerBuilder(NoopFdoBuilder.INSTANCE);    // NoOp FDO builder

        // Static singleton builders
        registry.registerBuilder(ResetWelcomeWindowArtFdoBuilder.INSTANCE);
        registry.registerBuilder(GuestLoginFdoBuilder.INSTANCE);
        registry.registerBuilder(IncorrectLoginFdoBuilder.INSTANCE);
        registry.registerBuilder(F1DodFailedFdoBuilder.INSTANCE);
        registry.registerBuilder(DodNotAvailableFdoBuilder.INSTANCE);
        registry.registerBuilder(Gid32_294FdoBuilder.INSTANCE);
        registry.registerBuilder(MotdFdoBuilder.INSTANCE);

        // Additional static builders with no-arg constructors
        registry.registerBuilder(new EphemeralWelcomeFdoBuilder());
        registry.registerBuilder(new LogoutFdoBuilder());

        LoggerUtil.info("FDO DSL Registry: " + registry.getBuilderCount() + " builder(s) registered");
    }

    public void stop() {
        try {
            if (serverChannel != null && serverChannel.isOpen()) serverChannel.close().sync();
            if (bossGroup != null) bossGroup.shutdownGracefully().sync();
            if (workerGroup != null) workerGroup.shutdownGracefully().sync();
            shutdownSharedServices();
        } catch (Exception e) {
            LoggerUtil.error("Error stopping: " + e.getMessage());
        }
    }

    private void shutdownSharedServices() {
        if (unifiedNewsService != null) {
            try {
                unifiedNewsService.close();
            } catch (IOException ignored) {}
        }

        if (grokConversationalService != null) {
            try {
                grokConversationalService.close();
            } catch (IOException ignored) {}
        }
    }

    private static Properties loadApplicationProperties() {
        Properties props = new Properties();
        try (var in = DialtoneServer.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) props.load(in);
        } catch (Exception ignored) {}
        return props;
    }
}