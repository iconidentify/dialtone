/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.ai.UnifiedNewsService;
import com.dialtone.art.ArtService;
import com.dialtone.aol.core.FrameCodec;
import com.dialtone.aol.core.Hex;
import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.auth.EphemeralUserManager;
import com.dialtone.auth.FallbackAuthenticator;
import com.dialtone.auth.UserRegistry;
import com.dialtone.chat.bot.ChatBotRegistry;
import com.dialtone.db.DatabaseManager;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.FdoVariableBuilder;
import com.dialtone.fdo.dsl.builders.ConfigureActiveUsernameFdoBuilder;
import com.dialtone.fdo.dsl.builders.LogoutFdoBuilder;
import com.dialtone.fdo.dsl.builders.WelcomeScreenFdoBuilder;
import com.dialtone.protocol.auth.LoginTokenHandler;
import com.dialtone.protocol.chat.ChatFrameBuilder;
import com.dialtone.protocol.chat.ChatTokenHandler;
import com.dialtone.protocol.core.AckWindowManager;
import com.dialtone.protocol.core.ControlFrameBuilder;
import com.dialtone.protocol.core.ProtocolFrameDispatcher;
import com.dialtone.protocol.dod.DodRequestHandler;
import com.dialtone.protocol.dod.DodTokenHandler;
import com.dialtone.protocol.im.InstantMessageTokenHandler;
import com.dialtone.protocol.news.NewsTokenHandler;
import com.dialtone.protocol.skalholt.SkalholtTokenHandler;
import com.dialtone.protocol.tos.TosTokenHandler;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserTokenHandler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.protocol.xfer.XferUploadRegistry;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.state.SequenceManager;
import com.dialtone.storage.FileStorage;
import com.dialtone.storage.StorageFactory;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.services.ScreennamePreferencesService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Main v3 protocol handler implementing the deferred drain pattern.
 * <p>
 * Design constraints:
 * <ul>
 *   <li>Never writes during read processing; frames are enqueued to {@link Pacer} and flushed in controlled bursts.</li>
 *   <li>ACK/window tracking is maintained for backpressure correctness and protocol compliance.</li>
 * </ul>
 */
public class StatefulClientHandler extends ChannelInboundHandlerAdapter {

    private static final int MAX_BURST_FRAMES = 10;
    private static final int HEX_LOG_MAX_BYTES = 200;
    private static final int HEX_LOG_LARGE_PREFIX_BYTES = 50;
    private static final String DEFAULT_DB_PATH = "db/dialtone.db";

    private static final AtomicInteger NEXT_CONN_ID = new AtomicInteger(1);

    public static final AttributeKey<XferTransferRegistry> XFER_REGISTRY_KEY =
            AttributeKey.valueOf("xferRegistry");
    public static final AttributeKey<XferUploadRegistry> XFER_UPLOAD_REGISTRY_KEY =
            AttributeKey.valueOf("xferUploadRegistry");
    public static final AttributeKey<StatefulClientHandler> HANDLER_KEY =
            AttributeKey.valueOf("statefulClientHandler");

    private final boolean verbose;

    private final SequenceManager sequenceManager;
    private final Pacer pacer;
    private final AckWindowManager ackWindowManager;

    private final Properties properties;

    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final DodRequestHandler dodRequestHandler;

    private final FallbackAuthenticator authenticator;
    private final ScreennamePreferencesService preferencesService;

    private final UnifiedNewsService unifiedNewsService;
    private final UserRegistry userRegistry;

    private final UnifiedStreamReassembler unifiedStreamReassembler;

    private final SessionContext session = new SessionContext();
    private final int connectionId = NEXT_CONN_ID.getAndIncrement();

    private final TcpFrameAccumulator tcpAccumulator;

    private final ControlFrameBuilder controlFrameBuilder;
    private final ChatFrameBuilder chatFrameBuilder;

    private final ChatTokenHandler chatHandler;
    private final InstantMessageTokenHandler imHandler;
    private final LoginTokenHandler loginHandler;
    private final SkalholtTokenHandler skalholtHandler;
    private final NewsTokenHandler newsHandler;
    private final TosTokenHandler tosHandler;
    private final DodTokenHandler dodHandler;
    private final FileBrowserTokenHandler fileBrowserHandler;

    private final FileBrowserService fileBrowserService;

    private final ProtocolFrameDispatcher frameDispatcher;

    private XferTransferRegistry xferRegistry;
    private XferService xferService;

    private XferUploadRegistry xferUploadRegistry;
    private XferUploadService xferUploadService;

    private FileStorage fileStorage;

    private final boolean uploadTnEnabled;
    private final boolean uploadAckEnabled;

    private volatile long lastActivityNanos = System.nanoTime();

    public StatefulClientHandler(boolean verbose) {
        this(verbose, null, null, null, null);
    }

    public StatefulClientHandler(boolean verbose, UnifiedNewsService unifiedNewsService, UserRegistry userRegistry) {
        this(verbose, unifiedNewsService, userRegistry, null, null);
    }

    public StatefulClientHandler(boolean verbose,
                                 UnifiedNewsService unifiedNewsService,
                                 UserRegistry userRegistry,
                                 Properties configuration) {
        this(verbose, unifiedNewsService, userRegistry, configuration, null);
    }

    public StatefulClientHandler(boolean verbose,
                                 UnifiedNewsService unifiedNewsService,
                                 UserRegistry userRegistry,
                                 Properties configuration,
                                 FileStorage fileStorage) {
        this.verbose = verbose;

        // Core state / pacing
        this.sequenceManager = new SequenceManager();
        this.tcpAccumulator = new TcpFrameAccumulator("[conn " + connectionId + "] ");

        // Stable prefix used for several builders; note: prefix() may evolve post-auth.
        final String handlerPrefix = prefix();

        // Wire ACK manager -> pacer outbound hook without reordering initialization.
        final AtomicReference<AckWindowManager> ackManagerRef = new AtomicReference<>();
        final Consumer<byte[]> outboundHook = (_b) -> {
            AckWindowManager ackMgr = ackManagerRef.get();
            if (ackMgr != null) {
                ackMgr.onOutboundSentPiggybackingAck();
            }
        };

        this.pacer = new Pacer(sequenceManager, true, this.verbose, outboundHook, session.getDisplayName());
        this.ackWindowManager = new AckWindowManager(sequenceManager, pacer, verbose, handlerPrefix);
        ackManagerRef.set(this.ackWindowManager);

        // Configuration
        this.properties = (configuration != null) ? configuration : loadApplicationProperties();
        configurePacerFromProperties(this.pacer, this.properties);

        // FDO / DOD / Services
        this.fdoCompiler = new FdoCompiler(this.properties);
        this.authenticator = buildAuthenticator(this.properties);
        final DatabaseManager dbManager = DatabaseManager.getInstance(getDbPath(this.properties));
        this.preferencesService = new ScreennamePreferencesService(dbManager);

        this.fdoProcessor = new FdoProcessor(fdoCompiler, pacer, MAX_BURST_FRAMES);
        this.dodRequestHandler = new DodRequestHandler(fdoCompiler, new ArtService(), this.properties, preferencesService);

        this.unifiedNewsService = unifiedNewsService;
        this.userRegistry = userRegistry;
        this.unifiedStreamReassembler = new UnifiedStreamReassembler(this);

        // Xfer
        this.xferService = new XferService(fdoCompiler);
        this.xferRegistry = new XferTransferRegistry("pending");

        final long phaseTimeoutMs = Long.parseLong(this.properties.getProperty("upload.phase.timeout.ms", "30000"));
        this.uploadTnEnabled = Boolean.parseBoolean(this.properties.getProperty("upload.tn.enabled", "false"));
        this.uploadAckEnabled = Boolean.parseBoolean(this.properties.getProperty("upload.ack.enabled", "true"));

        this.fileStorage = (fileStorage != null) ? fileStorage : initFileStorage(this.properties);
        this.xferUploadService = new XferUploadService(this.fileStorage, (int) this.fileStorage.getMaxFileSizeBytes(), phaseTimeoutMs);
        this.xferUploadRegistry = new XferUploadRegistry("pending");

        // Frame builders / token handlers
        this.controlFrameBuilder = new ControlFrameBuilder(handlerPrefix, verbose, sequenceManager, pacer);
        this.chatFrameBuilder = new ChatFrameBuilder(handlerPrefix);

        this.chatHandler = new ChatTokenHandler(session, pacer, fdoCompiler, fdoProcessor, userRegistry, ChatBotRegistry.getInstance(), this.properties);
        this.imHandler = new InstantMessageTokenHandler(session, pacer, fdoCompiler, userRegistry, preferencesService, this.properties);
        this.loginHandler = new LoginTokenHandler(
                session, pacer, fdoCompiler, fdoProcessor, authenticator, userRegistry,
                dodRequestHandler, unifiedNewsService, this::handleDisconnect
        );
        this.skalholtHandler = new SkalholtTokenHandler(session, pacer, fdoCompiler, fdoProcessor, this.properties);
        this.newsHandler = new NewsTokenHandler(session, pacer, fdoProcessor, unifiedNewsService);
        this.tosHandler = new TosTokenHandler(session, pacer, fdoProcessor, this.properties);
        this.dodHandler = new DodTokenHandler(session, pacer, fdoCompiler, fdoProcessor, dodRequestHandler, controlFrameBuilder);

        // File browser (needs direct LocalFileSystemStorage for directory browsing)
        this.fileBrowserService = new FileBrowserService(
            StorageFactory.createLocalStorage(this.properties));
        this.fileBrowserHandler = new FileBrowserTokenHandler(
                session, pacer, fdoCompiler, fileBrowserService, xferService, this.fileStorage);

        this.frameDispatcher = new ProtocolFrameDispatcher(
                session, pacer, fdoCompiler, fdoProcessor, dodRequestHandler,
                xferService, xferRegistry, xferUploadService, xferUploadRegistry,
                chatHandler, imHandler, loginHandler, skalholtHandler, newsHandler, tosHandler, dodHandler,
                fileBrowserHandler, this::handleDisconnect
        );
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LoggerUtil.info(prefix() + "Connection ESTABLISHED | remote=" + ctx.channel().remoteAddress());
        ctx.channel().attr(XFER_REGISTRY_KEY).set(xferRegistry);
        ctx.channel().attr(XFER_UPLOAD_REGISTRY_KEY).set(xferUploadRegistry);
        ctx.channel().attr(HANDLER_KEY).set(this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LoggerUtil.info(prefix() + "Connection CLOSED | framesSent=" + sequenceManager.getLastDataTx()
                + " | framesReceived=" + sequenceManager.getLastClientTxSeq());

        if (userRegistry != null && session.isAuthenticated() && session.getUsername() != null) {
            boolean wasInChat = chatHandler.processChatDeparture();
            if (wasInChat) {
                LoggerUtil.info(prefix() + "User disconnected from chat - CB broadcast sent to remaining members");
            }
            userRegistry.unregister(session.getUsername());
        }

        // Clean up ephemeral guest names on disconnect
        if (session.isEphemeral() && session.getUsername() != null) {
            EphemeralUserManager ephemeralManager = authenticator.getEphemeralManager();
            if (ephemeralManager != null) {
                ephemeralManager.releaseGuestName(session.getUsername());
                LoggerUtil.info(prefix() + "Ephemeral session cleaned up: " + session.getUsername());
            }
        }

        // Token handler / registries cleanup
        skalholtHandler.cleanup();
        if (xferRegistry != null) {
            xferRegistry.close();
        }
        if (xferUploadRegistry != null) {
            xferUploadRegistry.close();
        }

        // TCP remainder diagnostics
        int discardedBytes = tcpAccumulator.clearAndReset();
        if (discardedBytes > 0) {
            LoggerUtil.warn(prefix() + String.format("Connection closed with %d bytes buffered in TCP frame buffer", discardedBytes));
        }

        // Pacing / ACK cleanup
        ackWindowManager.cancelPostAckDrain();
        pacer.setDrainsDeferred(false);
        pacer.close();

        // Sequence cleanup
        sequenceManager.cleanupStallDetection();

        // Compiler cleanup
        try {
            fdoCompiler.close();
        } catch (IOException e) {
            LoggerUtil.warn(prefix() + "Failed to close FdoCompiler: " + e.getMessage());
        }

        try {
            super.channelInactive(ctx);
        } catch (Exception e) {
            LoggerUtil.warn(prefix() + "Exception during channel cleanup: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LoggerUtil.error(prefix() + "Pipeline error: " + cause);
        ctx.close();
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) {
        if (!ctx.channel().isWritable()) {
            long bytesBeforeWritable = ctx.channel().bytesBeforeWritable();
            LoggerUtil.warn(prefix() + "Backpressure START | needToFlush=" + bytesBeforeWritable
                    + " bytes | pendingFrames=" + (pacer.hasPending() ? "YES" : "NO"));
        } else {
            long bytesBeforeUnwritable = ctx.channel().bytesBeforeUnwritable();
            LoggerUtil.info(prefix() + "Backpressure END | bufferHeadroom=" + bytesBeforeUnwritable + " bytes");
            ctx.flush();
            pacer.resume(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        final ByteBuf buf = (ByteBuf) msg;
        final byte[] in = new byte[buf.readableBytes()];
        try {
            buf.readBytes(in);
        } finally {
            buf.release();
        }

        try {
            byte[] data = tcpAccumulator.prepareDataForProcessing(in);
            int processed = splitAndDispatch(ctx, data);
            tcpAccumulator.bufferRemainder(data, processed);
        } catch (TcpBufferOverflowException e) {
            LoggerUtil.error(prefix() + String.format(
                    "TCP buffer overflow (%d bytes, %d attempts) - closing connection",
                    e.getBufferSize(), e.getAttemptCount()
            ));
            ctx.close();
        }
    }

    /**
     * Split TCP data into frames and dispatch each frame.
     *
     * @return number of bytes processed from {@code data}
     */
    private int splitAndDispatch(ChannelHandlerContext ctx, byte[] data) throws Exception {
        LoggerUtil.debug(() -> prefix() + String.format("splitAndDispatch: Received %d bytes", data.length));
        logInboundHexPreview(data);

        // Defer drains until after we finish parsing all available frames.
        pacer.setDrainsDeferred(true);

        int i = 0;
        try {
            int framesExtracted = 0;

            while (i + ProtocolConstants.MIN_FRAME_SIZE <= data.length) {
                if (i == 0 || (data[i] & ProtocolConstants.BYTE_MASK) == ProtocolConstants.AOL_FRAME_MAGIC_BYTE) {
                    LoggerUtil.debug(prefix() + String.format("Scanning at offset %d, byte=0x%02X", i, data[i] & 0xFF));
                }

                if ((data[i] & ProtocolConstants.BYTE_MASK) != ProtocolConstants.AOL_FRAME_MAGIC_BYTE) {
                    i++;
                    continue;
                }

                // 9-byte short control frame fast-path
                if (i + ProtocolConstants.SHORT_FRAME_SIZE <= data.length) {
                    final int type = data[i + ProtocolConstants.IDX_TYPE] & 0xFF;
                    final int declared = u16(
                            data[i + ProtocolConstants.IDX_LEN_HI],
                            data[i + ProtocolConstants.IDX_LEN_HI + 1]
                    );
                    if ((type & ProtocolConstants.AOL_ACK_TYPE_MASK) == ProtocolConstants.AOL_ACK_TYPE_BASE && declared == 3) {
                        LoggerUtil.debug(prefix() + String.format("Extracting SHORT control frame at offset %d (9 bytes)", i));
                        byte[] frame = Arrays.copyOfRange(data, i, i + ProtocolConstants.SHORT_FRAME_SIZE);
                        dispatchSingleFrame(ctx, frame);
                        i += ProtocolConstants.SHORT_FRAME_SIZE;
                        framesExtracted++;
                        continue;
                    }
                }

                // Need full header for normal frames
                if (i + ProtocolConstants.MIN_FULL_FRAME_SIZE > data.length) {
                    LoggerUtil.debug(prefix() + String.format(
                            "Breaking: Not enough data for full frame header at offset %d (need %d, have %d)",
                            i, ProtocolConstants.MIN_FULL_FRAME_SIZE, data.length - i
                    ));
                    break;
                }

                final int len = u16(data[i + ProtocolConstants.IDX_LEN_HI], data[i + ProtocolConstants.IDX_LEN_HI + 1]);
                final int total = 6 + len;

                LoggerUtil.debug(prefix() + String.format(
                        "Frame at offset %d: declared_len=%d, total_size=%d, available=%d",
                        i, len, total, data.length - i
                ));

                if (i + total > data.length) {
                    LoggerUtil.debug(prefix() + String.format(
                            "Breaking: Frame incomplete at offset %d (need %d, have %d)",
                            i, total, data.length - i
                    ));
                    break;
                }

                int withCr = total;
                if (i + total < data.length && data[i + total] == 0x0D) {
                    withCr = total + 1;
                    LoggerUtil.debug(prefix() + "Found CR terminator, frame size with CR: " + withCr);
                }

                LoggerUtil.debug(prefix() + String.format("Extracting FULL frame at offset %d (%d bytes)", i, withCr));
                byte[] frame = Arrays.copyOfRange(data, i, i + withCr);
                dispatchSingleFrame(ctx, frame);
                i += withCr;
                framesExtracted++;
            }

            LoggerUtil.debug(prefix() + String.format(
                    "splitAndDispatch complete: extracted %d frame(s), processed %d of %d bytes",
                    framesExtracted, i, data.length
            ));
        } finally {
            pacer.setDrainsDeferred(false);
            if (!pacer.isWaitingForAck() && pacer.hasPending()) {
                pacer.drainLimited(ctx, MAX_BURST_FRAMES);
            }
        }

        return i;
    }

    /**
     * Process a single frame:
     * <ul>
     *   <li>Update client sequence + ACK window tracking</li>
     *   <li>Handle short control frames</li>
     *   <li>Extract token and dispatch to {@link ProtocolFrameDispatcher}</li>
     * </ul>
     */
    private void dispatchSingleFrame(ChannelHandlerContext ctx, byte[] in) throws Exception {
        LoggerUtil.debug(() -> prefix() + String.format(
                "DISPATCH: Frame received | length=%d | magic=0x%02X | type=0x%02X | token_area=%02X%02X",
                in.length,
                in.length > 0 ? (in[0] & 0xFF) : 0,
                in.length > 1 ? (in[1] & 0xFF) : 0,
                in.length > 8 ? (in[8] & 0xFF) : 0,
                in.length > 9 ? (in[9] & 0xFF) : 0
        ));

        final int beforeOutstanding = sequenceManager.getOutstandingWindowFill();
        sequenceManager.updateClientSequence(in);
        sequenceManager.updateAckFromIncoming(in);
        final int afterOutstanding = sequenceManager.getOutstandingWindowFill();

        ackWindowManager.setWindowOpenedByPiggyback(afterOutstanding < beforeOutstanding);

        if (verbose && beforeOutstanding != afterOutstanding) {
            sequenceManager.logSenderWindowState("after ACK update");
        }

        ackWindowManager.maybeLogWindowChange(in, beforeOutstanding, afterOutstanding);
        ackWindowManager.handleAckRelatedPacerHints(ctx, in, beforeOutstanding, afterOutstanding);

        lastActivityNanos = System.nanoTime();

        if (controlFrameBuilder.handleShortControl9B(ctx, in, ackWindowManager)) {
            return;
        }

        final String token = FrameCodec.extractTokenAscii(in);
        if (token == null) {
            LoggerUtil.debug(() -> prefix() + String.format("TOKEN: null (frame length=%d)", in.length));
            handleNullToken(ctx, in);
            return;
        } else {
            LoggerUtil.info(String.format("[TOKEN] received | type:%s", token));
        }

        // Preserve existing guard: ignore specific 0x5A 9-byte keepalive frames
        if (PacketParser.isFiveA(in) && in.length == ProtocolConstants.SHORT_FRAME_SIZE) {
            return;
        }

        // Parse is kept (side-effects / validation), even if not directly used here.
        PacketParser.parse(in);

        final int type = in[ProtocolConstants.IDX_TYPE] & ProtocolConstants.BYTE_MASK;
        if (type == PacketType.A3.getValue()
                && (in[ProtocolConstants.IDX_TOKEN] & 0xFF) == 0x0C
                && (in[ProtocolConstants.IDX_TOKEN + 1] & 0xFF) == 0x03) {
            sequenceManager.initializeFromClientProbe(in);
        }

        frameDispatcher.dispatchToken(ctx, in, sequenceManager);
    }

    /**
     * Extract NUL-terminated ASCII string from byte array.
     * (Kept for compatibility; may be used by other call sites or future parsing enhancements.)
     */
    private String extractNulTerminatedString(byte[] data, int offset, int maxLen) {
        if (data == null || offset >= data.length) {
            return "";
        }
        int endOffset = Math.min(offset + maxLen, data.length);
        int len = 0;
        for (int i = offset; i < endOffset; i++) {
            if (data[i] == 0) {
                break;
            }
            len++;
        }
        return new String(data, offset, len, StandardCharsets.US_ASCII);
    }

    /**
     * Send post-login welcome screen with IDB atom streams.
     */
    private void handlePreload(ChannelHandlerContext ctx) {
        try {
            fdoProcessor.compileAndSend(
                    ctx,
                    ConfigureActiveUsernameFdoBuilder.forUser(session.getDisplayName()),
                    session,
                    "At",
                    -1,
                    "OPEN_WELCOME_WINDOW"
            );

            sendIdbAtomStreamResetAndSend(ctx, "69-421");

            sendIdbAtomStreamResetAndSend(ctx, "32-117");
            sendIdbAtomStreamResetAndSend(ctx, "32-5447");
            pacer.drainLimited(ctx, MAX_BURST_FRAMES);

            sendIdbAtomStreamResetAndSend(ctx, "32-168");
            sendIdbAtomStreamResetAndSend(ctx, "32-225");
            sendIdbAtomStreamResetAndSend(ctx, "69-420");

            pacer.drainLimited(ctx, MAX_BURST_FRAMES);

            openWelcomeScreen(ctx);
        } catch (Exception ex) {
            LoggerUtil.error(prefix() + "Welcome generation failed: " + ex.getMessage());
            throw new RuntimeException("Welcome generation failed", ex);
        }
    }

    /**
     * Send IDB atom stream reset+send for welcome screen preloading.
     */
    private void sendIdbAtomStreamResetAndSend(ChannelHandlerContext ctx, String gid) throws Exception {
        LoggerUtil.info(prefix() + "Sending IDB atom stream reset+send for: " + gid);
        List<FdoChunk> chunks = dodRequestHandler.generateIdbAtomStreamResetAndSend(gid, session.getDisplayName(), session.getPlatform());
        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "IDB_ATOM_RESET_" + gid, MAX_BURST_FRAMES, session.getDisplayName());
        LoggerUtil.info(prefix() + "IDB atom stream reset+send enqueued: " + gid);
    }

    /**
     * Open welcome screen with news headlines.
     */
    private void openWelcomeScreen(ChannelHandlerContext ctx) {
        try {
            String newsHeadline = unifiedNewsService.getTeaserHeadline(UnifiedNewsService.NewsCategory.GENERAL);
            String entertainmentHeadline = unifiedNewsService.getTeaserHeadline(UnifiedNewsService.NewsCategory.ENTERTAINMENT);
            String cryptoHeadline = unifiedNewsService.getHeadline(UnifiedNewsService.NewsCategory.CRYPTO);
            String sportsHeadline = unifiedNewsService.getTeaserHeadline(UnifiedNewsService.NewsCategory.SPORTS);
            String techHeadline = unifiedNewsService.getTeaserHeadline(UnifiedNewsService.NewsCategory.TECH);

            fdoProcessor.compileAndSend(
                    ctx,
                    WelcomeScreenFdoBuilder.create(
                            session.getDisplayName(),
                            techHeadline,
                            newsHeadline,
                            cryptoHeadline,
                            sportsHeadline,
                            entertainmentHeadline
                    ),
                    session,
                    "AT",
                    -1,
                    "WELCOME_SCREEN_DATA"
            );
        } catch (Exception ex) {
            LoggerUtil.error(prefix() + "Welcome generation failed: " + ex.getMessage());
            throw new RuntimeException("Welcome generation failed", ex);
        }
    }

    /**
     * Extract client IP address from channel context.
     */
    private String getClientIp(ChannelHandlerContext ctx) {
        try {
            SocketAddress remoteAddr = ctx.channel().remoteAddress();
            if (remoteAddr instanceof InetSocketAddress) {
                return ((InetSocketAddress) remoteAddr).getAddress().getHostAddress();
            }
        } catch (Exception e) {
            LoggerUtil.warn(prefix() + "Failed to extract client IP: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Extract server IP address from channel context.
     */
    private String getServerIp(ChannelHandlerContext ctx) {
        try {
            SocketAddress localAddr = ctx.channel().localAddress();
            if (localAddr instanceof InetSocketAddress) {
                return ((InetSocketAddress) localAddr).getAddress().getHostAddress();
            }
        } catch (Exception e) {
            LoggerUtil.warn(prefix() + "Failed to extract server IP: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Format INIT packet data for MOTD display.
     */
    private String formatInitPacketForMotd(ChannelHandlerContext ctx, SessionContext session) {
        if (session == null || !session.hasInitData()) {
            return "Client information not yet available";
        }

        InitPacketData initData = session.getInitPacketData();
        StringBuilder sb = new StringBuilder();
        String separator = "========================================================";

        sb.append(separator).append("\\r");
        sb.append("CLIENT INFORMATION").append("\\r");
        sb.append(separator).append("\\r");

        sb.append(formatField("Platform", initData.getPlatformName())).append("\\r");
        sb.append(formatField("Version", initData.getVersionString())).append("\\r");
        sb.append(formatField("Memory", String.format("Machine: %dMB, App: %dMB", initData.getMachineMemory(), initData.getAppMemory()))).append("\\r");

        if (initData.getProcessorType() != 0) {
            sb.append(formatField("Processor", initData.getProcessorTypeName())).append("\\r");
        }

        sb.append(formatField("Payload Size", initData.getActualPayloadSize() + " bytes")).append("\\r");

        String warning = initData.getWarningMessage();
        if (warning != null) {
            sb.append("\\r");
            sb.append(warning).append("\\r");
        }

        if (initData.isFullyParsed()) {
            sb.append("\\r");
            sb.append(separator).append("\\r");
            sb.append("EXTENDED INFO (Windows Layout)").append("\\r");
            sb.append(separator).append("\\r");

            sb.append(formatField("Resolution", initData.getResolutionString())).append("\\r");
            sb.append(formatField("Connection", initData.getConnectSpeedDescription())).append("\\r");
            sb.append(formatField("PC Type", String.format("0x%04X", initData.getPcType()))).append("\\r");
            sb.append(formatField("Release Date", String.format("%d/%d", initData.getReleaseMonth(), initData.getReleaseDay()))).append("\\r");
            sb.append(formatField("Windows Ver", String.format("0x%08X", initData.getWindowsVersion()))).append("\\r");
            sb.append(formatField("Processor", initData.getProcessorTypeName())).append("\\r");
            sb.append(formatField("Video Type", String.format("0x%02X", initData.getVideoType()))).append("\\r");
        }

        sb.append("\\r");
        sb.append(separator).append("\\r");
        sb.append("CONNECTION INFO").append("\\r");
        sb.append(separator).append("\\r");

        sb.append(formatField("Client IP", getClientIp(ctx))).append("\\r");
        sb.append(formatField("Server IP", getServerIp(ctx))).append("\\r");
        sb.append(formatField("Session Duration", session.getSessionDuration())).append("\\r");

        return sb.toString();
    }

    /**
     * Format field with dot-leader alignment for INIT packet display.
     */
    private String formatField(String label, String value) {
        int totalWidth = 56;
        int dotsNeeded = totalWidth - label.length() - value.length() - 2;
        if (dotsNeeded < 1) {
            dotsNeeded = 1;
        }
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < dotsNeeded; i++) {
            dots.append(".");
        }
        return label + " " + dots + " " + value;
    }

    /**
     * Send logout/disconnect sequence with default message.
     */
    public void handleDisconnect(ChannelHandlerContext ctx) {
        handleDisconnect(ctx, "Thank you for using Dialtone!");
    }

    /**
     * Send logout/disconnect sequence with custom message.
     */
    public void handleDisconnect(ChannelHandlerContext ctx, String message) {
        try {
            fdoProcessor.compileAndSend(
                    ctx,
                    LogoutFdoBuilder.withMessage(message != null ? message : "Goodbye!"),
                    session,
                    "At",
                    -1,
                    "LOGOUT"
            );
        } catch (Exception ex) {
            LoggerUtil.error(prefix() + "Failed logout: " + ex.getMessage());
        }
    }

    /**
     * Handle null-token frames (init packets, etc.).
     */
    private void handleNullToken(ChannelHandlerContext ctx, byte[] in) {
        if (PacketParser.isFiveA(in) && in.length == ProtocolConstants.SHORT_FRAME_SIZE) {
            return;
        }

        if (PacketParser.isFiveA(in)
                && in.length > ProtocolConstants.SHORT_FRAME_SIZE
                && (in[ProtocolConstants.IDX_TYPE] & ProtocolConstants.BYTE_MASK) == PacketType.A3.getValue()) {

            InitPacketData initData = InitPacketParser.parse(in);
            if (initData != null) {
                session.setInitPacketData(initData);
                LoggerUtil.info(prefix() + "INIT packet parsed: " + initData);
            }

            // Mac client detection (0xA3 init, token=0x0C03)
            if ((in[ProtocolConstants.IDX_TOKEN] & ProtocolConstants.BYTE_MASK) == 0x0C
                    && (in[ProtocolConstants.IDX_TOKEN + 1] & ProtocolConstants.BYTE_MASK) == 0x03) {
                LoggerUtil.info(prefix() + "Mac client detected (0xA3 init, token=0x0C03)");
                session.setPlatform(ClientPlatform.MAC);
                sendFiveAExact(ctx, ProtocolConstants.MAC_KEEPALIVE_PONG);
                sendFiveAExact(ctx, ProtocolConstants.MAC_HANDSHAKE);
                return;
            }

            // Windows client detection (0xA3 init, length=52)
            int payloadLen = u16(in[ProtocolConstants.IDX_LEN_HI], in[ProtocolConstants.IDX_LEN_LO]);
            if (payloadLen == 52) {
                LoggerUtil.info(prefix() + "Windows client detected (0xA3 init, length=52)");
                session.setPlatform(ClientPlatform.WINDOWS);
                sendFiveAExact(ctx, ProtocolConstants.WINDOWS_KEEPALIVE_PONG);
                sendFiveAExact(ctx, ProtocolConstants.WINDOWS_HANDSHAKE);
                return;
            }

            LoggerUtil.warn(prefix() + "Unknown 0xA3 init packet variant (length=" + in.length + ")");
            return;
        }

        LoggerUtil.warn(prefix() + String.format(
                "Unrecognized null-token frame | length=%d | magic=0x%02X | type=0x%02X | token_bytes=0x%02X%02X | printable=%s%s",
                in.length,
                in.length > 0 ? (in[0] & 0xFF) : 0,
                in.length > 1 ? (in[1] & 0xFF) : 0,
                in.length > 8 ? (in[8] & 0xFF) : 0,
                in.length > 9 ? (in[9] & 0xFF) : 0,
                in.length > 8 ? (isPrintableAscii(in[8]) ? (char) (in[8] & 0xFF) : "?") : "?",
                in.length > 9 ? (isPrintableAscii(in[9]) ? (char) (in[9] & 0xFF) : "?") : "?"
        ));
    }

    /**
     * Check if byte is printable ASCII.
     */
    private boolean isPrintableAscii(byte b) {
        int c = b & 0xFF;
        return c >= 0x20 && c <= 0x7E;
    }

    /**
     * Initialize telnet bridge for Skalholt connection.
     */
    public void initializeSkalholtTelnetBridge(ChannelHandlerContext ctx) {
        skalholtHandler.initializeSkalholtTelnetBridge(ctx);
    }

    /**
     * Get log prefix with connection ID and username (when authenticated).
     */
    private String prefix() {
        String username = session.getUsername();
        if (username != null && session.isAuthenticated()) {
            return "[conn " + connectionId + "][" + username + "] ";
        }
        return "[conn " + connectionId + "] ";
    }

    public SessionContext getSession() {
        return session;
    }

    /**
     * Convert two bytes to unsigned 16-bit integer (big-endian).
     */
    private static int u16(byte hi, byte lo) {
        return ((hi & 0xFF) << 8) | (lo & 0xFF);
    }

    /**
     * Send exact hex bytes for init packet handshake.
     */
    private void sendFiveAExact(ChannelHandlerContext ctx, String hexTemplate) {
        byte[] out = Hex.hexToBytes(hexTemplate);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(out));
        ackWindowManager.onOutboundSentPiggybackingAck();
    }

    /**
     * Handler for gracefully disconnecting remote sessions during single-session enforcement.
     */
    public static class RemoteDisconnectHandler implements com.dialtone.auth.SessionDisconnectHandler {
        @Override
        public CompletableFuture<Void> disconnectSession(UserRegistry.UserConnection connection, String message) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            ChannelHandlerContext ctx = connection.getContext();

            if (!ctx.channel().isActive()) {
                LoggerUtil.debug(() -> "Channel already inactive for user: " + connection.getUsername());
                future.complete(null);
                return future;
            }

            ctx.channel().eventLoop().execute(() -> {
                try {
                    StatefulClientHandler handler = ctx.pipeline().get(StatefulClientHandler.class);
                    if (handler != null) {
                        LoggerUtil.info("Disconnecting old session for user: " + connection.getUsername() + " - Reason: " + message);
                        handler.handleDisconnect(ctx, message);

                        try {
                            Pacer pacer = connection.getPacer();
                            pacer.setDrainsDeferred(false);
                            pacer.drainLimited(ctx, 10);
                            LoggerUtil.info("Logout frames drained for user: " + connection.getUsername());
                        } catch (Exception drainEx) {
                            LoggerUtil.error("Failed to drain logout frames for user: " + connection.getUsername()
                                    + " - " + drainEx.getMessage());
                        }

                        ctx.channel().eventLoop().schedule(() -> {
                            try {
                                ctx.close();
                                LoggerUtil.info("Old session closed for user: " + connection.getUsername());
                                future.complete(null);
                            } catch (Exception e) {
                                LoggerUtil.error("Failed to close channel for user: " + connection.getUsername()
                                        + " - " + e.getMessage());
                                future.completeExceptionally(e);
                            }
                        }, 2, TimeUnit.SECONDS);
                    } else {
                        LoggerUtil.warn("No StatefulClientHandler found for user: " + connection.getUsername() + " - forcing close");
                        ctx.close();
                        future.complete(null);
                    }
                } catch (Exception e) {
                    LoggerUtil.error("Failed to disconnect session for user: " + connection.getUsername() + " - " + e.getMessage());
                    try {
                        ctx.close();
                    } catch (Exception closeEx) {
                        LoggerUtil.error("Failed to force close channel: " + closeEx.getMessage());
                    }
                    future.completeExceptionally(e);
                }
            });

            return future;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers (no behavior changes; isolated for readability/consistency)
    // -------------------------------------------------------------------------

    /**
     * Load application.properties from classpath, applying supported system property overrides.
     */
    private static Properties loadApplicationProperties() {
        Properties props = new Properties();
        try (var in = StatefulClientHandler.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception e) {
            LoggerUtil.warn("Failed to load application.properties, using defaults: " + e.getMessage());
        }

        String mockEnabled = System.getProperty("atomforge.mock.enabled");
        if (mockEnabled != null) {
            props.setProperty("atomforge.mock.enabled", mockEnabled);
        }

        return props;
    }

    private static void configurePacerFromProperties(Pacer pacer, Properties props) {
        int interFrameDelay = Integer.parseInt(props.getProperty("pacer.inter.frame.delay.ms", "0"));
        pacer.setInterFrameDelayMs(interFrameDelay);
    }

    private static String getDbPath(Properties props) {
        return props.getProperty("db.path", DEFAULT_DB_PATH);
    }

    private static FallbackAuthenticator buildAuthenticator(Properties props) {
        // Create the ephemeral user manager (singleton per handler, shared with authenticator)
        EphemeralUserManager ephemeralManager = new EphemeralUserManager();

        // Check if ephemeral fallback is enabled (replaces old auth.pass.through)
        boolean ephemeralFallbackEnabled = Boolean.parseBoolean(
                props.getProperty("auth.ephemeral.fallback.enabled", "false"));

        // Create database authenticator
        com.dialtone.auth.DatabaseUserAuthenticator dbAuth =
                new com.dialtone.auth.DatabaseUserAuthenticator(getDbPath(props));

        // Create fallback authenticator that wraps database auth with ephemeral fallback
        return new FallbackAuthenticator(dbAuth, ephemeralManager, ephemeralFallbackEnabled);
    }

    private static FileStorage initFileStorage(Properties props) {
        try {
            return com.dialtone.storage.StorageFactory.createWithFallback(props);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize file storage", e);
        }
    }

    private void logInboundHexPreview(byte[] data) {
        if (!LoggerUtil.isDebugEnabled()) {
            return;
        }

        if (data.length == 0) {
            return;
        }

        if (data.length <= HEX_LOG_MAX_BYTES) {
            LoggerUtil.debug(() -> prefix() + "Data bytes:\n  " + formatHexLines(data, data.length));
            return;
        }

        LoggerUtil.debug(() -> prefix() + String.format(
                "Data too large to log (%d bytes), first %d: %s",
                data.length,
                HEX_LOG_LARGE_PREFIX_BYTES,
                formatHexSingleLine(data, HEX_LOG_LARGE_PREFIX_BYTES)
        ));
    }

    private static String formatHexLines(byte[] data, int maxBytes) {
        StringBuilder hex = new StringBuilder();
        int limit = Math.min(data.length, maxBytes);
        for (int j = 0; j < limit; j++) {
            if (j > 0 && j % 16 == 0) {
                hex.append("\n  ");
            }
            hex.append(String.format("%02X ", data[j] & 0xFF));
        }
        return hex.toString();
    }

    private static String formatHexSingleLine(byte[] data, int maxBytes) {
        StringBuilder hex = new StringBuilder();
        int limit = Math.min(data.length, maxBytes);
        for (int j = 0; j < limit; j++) {
            hex.append(String.format("%02X ", data[j] & 0xFF));
        }
        return hex.toString();
    }
}