/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.skalholt;

import com.dialtone.skalholt.SkalholtAuthToken;
import com.dialtone.skalholt.SkalholtSseEvent;
import com.dialtone.skalholt.SkalholtSessionManager;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.MultiFrameStreamProcessor;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.dsl.builders.NoopFdoBuilder;
import com.dialtone.skalholt.fdo.SkalholtFdoBuilder;
import com.dialtone.skalholt.fdo.SkalholtMapFdoBuilder;
import com.dialtone.terminal.TelnetBridge;
import com.dialtone.terminal.TelnetLineFilter;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Handles Skalholt-related tokens: St (Skalholt command), Sl (Skalholt close).
 * Manages telnet bridge connection and multi-frame St accumulation.
 */
public class SkalholtTokenHandler implements TokenHandler {
    private static final int MAX_BURST_FRAMES = 10;

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final Properties properties;
    private final String logPrefix;

    // Multi-frame St accumulation: Keyed by Stream ID to track incomplete Skalholt command streams.
    private final Map<Integer, List<byte[]>> pendingStStreams = new ConcurrentHashMap<>();

    // Skalholt state
    private TelnetBridge telnetBridge;
    private SkalholtFdoBuilder skalholtFdoBuilder;

    // MAP window builder (69-421) - no server-side state tracking needed
    // FDO conditional logic handles window existence checks client-side
    private SkalholtMapFdoBuilder skalholtMapFdoBuilder;

    // Skalholt MUD API integration
    private SkalholtSessionManager skalholtSessionManager;

    // SSO credentials for EventStream authentication
    private String ssoBase64Credentials;

    public SkalholtTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                                FdoProcessor fdoProcessor, Properties properties) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fdoProcessor = fdoProcessor;
        this.properties = properties;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "St".equals(token) || "Sl".equals(token)
            || "MP".equals(token) || "MC".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        switch (token) {
            case "St":
                handleStToken(ctx, frame);
                break;
            case "Sl":
                handleSlToken(ctx);
                break;
            case "MP":
                handleMpToken(ctx);
                break;
            case "MC":
                handleMcToken(ctx);
                break;
        }
    }

    /**
     * Extract token from frame (simplified - assumes standard frame format).
     */
    private String extractToken(byte[] frame) {
        if (frame.length < 10) {
            return null;
        }
        return new String(new byte[]{frame[8], frame[9]}, StandardCharsets.US_ASCII);
    }

    /**
     * Handle St (Skalholt command) token with multi-frame support.
     */
    public void handleStToken(ChannelHandlerContext ctx, byte[] in) {
        try {
            // Extract Stream ID from frame header (bytes 10-11)
            int streamId = MultiFrameStreamProcessor.extractStreamId(in);
            LoggerUtil.debug(() -> logPrefix + String.format("St frame with Stream ID: 0x%04X", streamId));

            // Check if this frame has uni_end_stream (signals completion)
            if (MultiFrameStreamProcessor.isUniEndStream(in, fdoCompiler)) {
                LoggerUtil.debug(() -> logPrefix + String.format("St with uni_end_stream for Stream ID 0x%04X", streamId));

                // Check if we have accumulated data for this stream
                List<byte[]> accumulatedFrames = pendingStStreams.get(streamId);

                // Log multi-frame status at DEBUG
                if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                    LoggerUtil.debug(() -> logPrefix + String.format("Multi-frame St: %d accumulated + 1 final = %d total for Stream 0x%04X",
                            accumulatedFrames.size(), accumulatedFrames.size() + 1, streamId));
                }

                String skalholtCommand;
                if (accumulatedFrames != null && !accumulatedFrames.isEmpty()) {
                    // Multi-frame: combine ALL frames (accumulated + final) and extract command
                    List<byte[]> allFrames = new ArrayList<>(accumulatedFrames);
                    allFrames.add(in);  // Add final frame with uni_end_stream

                    // Extract command from ALL frames combined using shared utility
                    skalholtCommand = MultiFrameStreamProcessor.extractDeDataFromMultiFrame(allFrames, fdoCompiler, "St");

                    // Clear accumulation for this stream
                    pendingStStreams.remove(streamId);
                } else {
                    // Single-frame: extract de_data using shared utility
                    LoggerUtil.debug(() -> logPrefix + "Single-frame St (has uni_end_stream)");
                    skalholtCommand = MultiFrameStreamProcessor.extractDeDataFromSingleFrame(in, fdoCompiler, "St");
                }

                // Get or create SkalholtFdoBuilder for this connection
                if (skalholtFdoBuilder == null) {
                    skalholtFdoBuilder = new SkalholtFdoBuilder();
                }

                // If telnet bridge is connected, forward command to telnet server
                if (telnetBridge != null && telnetBridge.isConnected()) {
                    boolean sent = telnetBridge.sendCommand(skalholtCommand);
                    if (sent) {
                        LoggerUtil.info(String.format("[SKALHOLT] command forwarded to telnet | user:%s | command:'%s'",
                                session.getDisplayName(), skalholtCommand));
                    } else {
                        LoggerUtil.warn(String.format("[SKALHOLT] failed to forward command to telnet | user:%s | command:'%s'",
                                session.getDisplayName(), skalholtCommand));
                        // Fallback: echo command locally
                        String fdoSource = skalholtFdoBuilder.addLine(skalholtCommand);
                        List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                                fdoSource, "At", FdoCompiler.AUTO_GENERATE_STREAM_ID);
                        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "ST_SKALHOLT", MAX_BURST_FRAMES,
                                session.getDisplayName());
                    }
                }
            } else {
                // No uni_end_stream: this is part of multi-frame sequence
                LoggerUtil.debug(() -> logPrefix + String.format("St fragment for Stream ID 0x%04X (waiting for uni_end_stream)", streamId));

                // Accumulate this frame for this Stream ID
                pendingStStreams.computeIfAbsent(streamId, k -> new ArrayList<>()).add(
                        Arrays.copyOf(in, in.length)
                );

                // Log accumulation state at DEBUG
                LoggerUtil.debug(() -> logPrefix + String.format("Accumulated St frames: Stream 0x%04X now has %d frame(s)",
                        streamId, pendingStStreams.get(streamId).size()));
            }
        } catch (Exception e) {
            // Enhanced exception logging with full stack trace and context
            LoggerUtil.error(logPrefix + String.format(
                    "EXCEPTION in St processing | type=%s | message=%s",
                    e.getClass().getSimpleName(),
                    e.getMessage()));
            // Log stack trace manually
            LoggerUtil.error(logPrefix + "Stack trace:");
            for (StackTraceElement element : e.getStackTrace()) {
                LoggerUtil.error(logPrefix + "  at " + element.toString());
            }
            if (e.getCause() != null) {
                LoggerUtil.error(logPrefix + "Caused by: " + e.getCause().getClass().getSimpleName() + ": " + e.getCause().getMessage());
            }
            // Don't rethrow - let protocol continue (same as other handlers)
        }
    }

    /**
     * Handle Sl (Skalholt close) token - user closed the Skalholt window.
     */
    public void handleSlToken(ChannelHandlerContext ctx) {
        try {
            LoggerUtil.info(logPrefix + "Skalholt close token (Sl) received from user: " + session.getDisplayName());

            // Close telnet bridge if connected
            if (telnetBridge != null) {
                LoggerUtil.info(logPrefix + "Closing telnet bridge due to Skalholt close");
                telnetBridge.close();
                telnetBridge = null;
            }

            // Cleanup Skalholt session
            if (skalholtSessionManager != null) {
                LoggerUtil.info(logPrefix + "Cleaning up Skalholt session due to Skalholt close");
                skalholtSessionManager.cleanup();
                skalholtSessionManager = null;
            }

            // Clear Skalholt FDO builders
            skalholtFdoBuilder = null;
            skalholtMapFdoBuilder = null;

            // Clear any pending St streams
            if (!pendingStStreams.isEmpty()) {
                LoggerUtil.debug(logPrefix + "Clearing " + pendingStStreams.size() + " pending St stream(s) on Skalholt close");
                pendingStStreams.clear();
            }

            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "KK_UNKNOWN_ACK");

        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Error handling Skalholt close (Sl): " + e.getMessage());
            // Don't rethrow - let protocol continue
        }
    }

    /**
     * Handle MP (Map open) token - user clicked Map button in toolbar.
     * Opens 69-421 window. FDO uses conditional to handle already-open case.
     */
    public void handleMpToken(ChannelHandlerContext ctx) {
        try {
            LoggerUtil.info(logPrefix + "Map open token (MP) received");

            if (skalholtMapFdoBuilder == null) {
                skalholtMapFdoBuilder = new SkalholtMapFdoBuilder();
            }

            String fdoSource = skalholtMapFdoBuilder.openMapWindow();
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                    fdoSource, "At", FdoCompiler.AUTO_GENERATE_STREAM_ID);
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "MP_MAP_OPEN", MAX_BURST_FRAMES,
                    session.getDisplayName());

            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "MP_ACK");

            // Request initial map data from Skalholt API (delay to allow player login to complete)
            ctx.channel().eventLoop().schedule(this::sendSkalholtSeedRequest, 1, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Error handling Map open: " + e.getMessage());
        }
    }

    /**
     * Sends a seed request to the Skalholt API to trigger initial DRAW_MAP event.
     * This populates the map window with current room data.
     */
    private void sendSkalholtSeedRequest() {
        if (ssoBase64Credentials == null) {
            LoggerUtil.warn(logPrefix + "Cannot send seed request: no SSO credentials");
            return;
        }

        String skalholtHost = properties.getProperty("skalholt.api.host", "localhost");
        int skalholtPort;
        try {
            skalholtPort = Integer.parseInt(properties.getProperty("skalholt.api.port", "8163"));
        } catch (NumberFormatException e) {
            skalholtPort = 8163;
        }

        String seedUrl = "http://" + skalholtHost + ":" + skalholtPort + "/api/seed";
        LoggerUtil.info(logPrefix + "Sending seed request to: " + seedUrl);

        // Run HTTP request in background thread to not block the event loop
        new Thread(() -> {
            try (org.apache.hc.client5.http.impl.classic.CloseableHttpClient httpClient =
                    org.apache.hc.client5.http.impl.classic.HttpClients.createDefault()) {

                org.apache.hc.client5.http.classic.methods.HttpPost request =
                        new org.apache.hc.client5.http.classic.methods.HttpPost(seedUrl);
                request.setHeader("Authorization", "Basic " + ssoBase64Credentials);

                httpClient.execute(request, response -> {
                    int status = response.getCode();
                    if (status == 200) {
                        LoggerUtil.info(logPrefix + "Seed request successful - DRAW_MAP event should follow");
                    } else {
                        LoggerUtil.warn(logPrefix + "Seed request returned status: " + status);
                    }
                    return null;
                });
            } catch (Exception e) {
                LoggerUtil.error(logPrefix + "Seed request failed: " + e.getMessage());
            }
        }, "SkalholtSeedRequest").start();
    }

    /**
     * Handle MC (Map close) token - user closed Map window.
     * No state tracking needed - FDO conditional handles window existence.
     */
    public void handleMcToken(ChannelHandlerContext ctx) {
        LoggerUtil.info(logPrefix + "Map close token (MC) received");
        try {
            fdoProcessor.compileAndSend(ctx, NoopFdoBuilder.INSTANCE, session, "At", -1, "MP_ACK");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Handle DRAW_MAP event from Skalholt - update map window content.
     * FDO conditional logic handles "window not open" case client-side.
     */
    private void handleMapDrawEvent(ChannelHandlerContext ctx, SkalholtSseEvent event) {
        event.getDrawMapPayload().ifPresent(mapEvent -> {
            ctx.channel().eventLoop().execute(() -> {
                try {
                    String mapData = mapEvent.getMap();
                    LoggerUtil.debug(logPrefix + "Sending map update FDO");

                    if (skalholtMapFdoBuilder == null) {
                        skalholtMapFdoBuilder = new SkalholtMapFdoBuilder();
                    }

                    // FDO includes conditional to skip if window is closed
                    String fdoSource = skalholtMapFdoBuilder.updateMapContent(mapData);
                    List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                            fdoSource, "At", FdoCompiler.AUTO_GENERATE_STREAM_ID);
                    P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "MAP_UPDATE", MAX_BURST_FRAMES,
                            session.getDisplayName());

                    pacer.drainLimited(ctx, 2);

                } catch (Exception e) {
                    LoggerUtil.error(logPrefix + "Error updating map: " + e.getMessage());
                }
            });
        });
    }

    /**
     * Initialize Skalholt telnet bridge connection.
     */
    public void initializeSkalholtTelnetBridge(ChannelHandlerContext ctx) {
        // Create callback to send lines from telnet to client
        Consumer<String> lineCallback = (line) -> {
            // Schedule on Netty event loop thread for proper drain context
            ctx.channel().eventLoop().execute(() -> {
                try {
                    // Add line to Skalholt and compile FDO
                    String fdoSource = skalholtFdoBuilder.addLine(line);
                    List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                            fdoSource, "At", FdoCompiler.AUTO_GENERATE_STREAM_ID);

                    // Enqueue chunks
                    P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "ST_SKALHOLT", MAX_BURST_FRAMES,
                            session.getDisplayName());

                    // Explicitly drain since we're coming from background thread
                    // This ensures frames are sent even if internal drain logic doesn't trigger
                    pacer.drainLimited(ctx, 2);

                    LoggerUtil.debug(logPrefix + String.format(
                            "[SKALHOLT] line received | user:%s | line:'%s' | chunks:%d",
                            session.getDisplayName(), line, chunks != null ? chunks.size() : 0));
                } catch (Exception e) {
                    LoggerUtil.error(logPrefix + "Error sending telnet line to client: " + e.getMessage());
                }
            });
        };

        // Get telnet configuration from properties
        String telnetHost = properties.getProperty("telnet.host", "xterm.bonenet.ai");
        int telnetPort;
        try {
            telnetPort = Integer.parseInt(properties.getProperty("telnet.port", "8080"));
        } catch (NumberFormatException e) {
            LoggerUtil.warn(logPrefix + "Invalid telnet.port in properties, using default 8080");
            telnetPort = 8080;
        }
        int telnetTimeout;
        try {
            telnetTimeout = Integer.parseInt(properties.getProperty("telnet.timeout.ms", "30000"));
        } catch (NumberFormatException e) {
            LoggerUtil.warn(logPrefix + "Invalid telnet.timeout.ms in properties, using default 30000");
            telnetTimeout = 30000;
        }

        // Create SkalholtFdoBuilder if not already created
        if (skalholtFdoBuilder == null) {
            skalholtFdoBuilder = new SkalholtFdoBuilder();
        }

        // Initialize Skalholt session manager
        skalholtSessionManager = createSkalholtSessionManager();

        // Wire up DRAW_MAP event handler for map window updates
        skalholtSessionManager.setEventHandler(event -> {
            if (event.isMapDraw()) {
                handleMapDrawEvent(ctx, event);
            }
        });

        // Create line filter for Skalholt SSO response handling
        TelnetLineFilter skalholtSsoFilter = createSkalholtSsoFilter(ctx);

        // Create and connect telnet bridge with configured host/port and filter
        telnetBridge = new TelnetBridge(telnetHost, telnetPort, telnetTimeout, lineCallback, skalholtSsoFilter);
        boolean connected = telnetBridge.connect();

        if (connected) {
            LoggerUtil.info(logPrefix + "Skalholt telnet bridge connected for user: " + session.getDisplayName());

            // Send SSO AUTH command immediately after connect (bannerless port)
            boolean authSent = sendSkalholtSsoAuth();
            if (authSent) {
                // Start EventStream immediately - bannerless port doesn't send AUTH response
                // We use the same credentials we just sent for SSO
                LoggerUtil.info(logPrefix + "SSO AUTH sent, starting EventStream immediately");
                if (ssoBase64Credentials != null && skalholtSessionManager != null) {
                    skalholtSessionManager.onAuthTokenReceived(ssoBase64Credentials);
                    skalholtSessionManager.startEventStream();
                }
            } else {
                LoggerUtil.error(logPrefix + "Failed to send SSO AUTH - closing telnet bridge");
                telnetBridge.close();
                telnetBridge = null;
                sendSkalholtAuthError(ctx, "Failed to authenticate with game server");
            }
        } else {
            LoggerUtil.warn(logPrefix + "Skalholt telnet bridge initialization failed for user: " + session.getDisplayName());
            telnetBridge = null; // Clean up failed bridge
        }
    }

    /**
     * Creates the SkalholtSessionManager from configuration.
     */
    private SkalholtSessionManager createSkalholtSessionManager() {
        boolean enabled = Boolean.parseBoolean(properties.getProperty("skalholt.eventstream.enabled", "true"));
        if (!enabled) {
            LoggerUtil.info(logPrefix + "Skalholt EventStream is disabled");
            return SkalholtSessionManager.disabled();
        }

        String skalholtHost = properties.getProperty("skalholt.api.host", "xterm.bonenet.ai");
        int skalholtPort;
        try {
            skalholtPort = Integer.parseInt(properties.getProperty("skalholt.api.port", "8163"));
        } catch (NumberFormatException e) {
            LoggerUtil.warn(logPrefix + "Invalid skalholt.api.port in properties, using default 8163");
            skalholtPort = 8163;
        }
        int skalholtTimeout;
        try {
            skalholtTimeout = Integer.parseInt(properties.getProperty("skalholt.api.timeout.ms", "10000"));
        } catch (NumberFormatException e) {
            LoggerUtil.warn(logPrefix + "Invalid skalholt.api.timeout.ms in properties, using default 10000");
            skalholtTimeout = 10000;
        }

        LoggerUtil.info(logPrefix + String.format("Skalholt API configured: %s:%d", skalholtHost, skalholtPort));
        return new SkalholtSessionManager(skalholtHost, skalholtPort, skalholtTimeout, true);
    }

    /**
     * Creates a TelnetLineFilter that handles Skalholt SSO error responses.
     * The bannerless port doesn't send AUTH success - we start EventStream immediately after sending AUTH.
     * This filter only handles AUTH FAILED errors.
     */
    private TelnetLineFilter createSkalholtSsoFilter(ChannelHandlerContext ctx) {
        return line -> {
            // Handle AUTH FAILED errors
            if (line != null && line.startsWith("AUTH FAILED:")) {
                String reason = line.substring("AUTH FAILED:".length()).trim();
                LoggerUtil.error(logPrefix + "Skalholt SSO failed: " + reason);
                ctx.channel().eventLoop().execute(() -> {
                    sendSkalholtAuthError(ctx, "Authentication failed: " + reason);
                    if (telnetBridge != null) {
                        telnetBridge.close();
                        telnetBridge = null;
                    }
                    if (skalholtSessionManager != null) {
                        skalholtSessionManager.cleanup();
                    }
                });
                return null;  // Suppress from display
            }

            return line;  // Pass through all other lines
        };
    }

    /**
     * Sends the SSO AUTH command to Skalholt.
     * Format: AUTH <base64(username:password)> <secret>
     *
     * @return true if command was sent successfully
     */
    private boolean sendSkalholtSsoAuth() {
        if (telnetBridge == null || !telnetBridge.isConnected()) {
            LoggerUtil.error(logPrefix + "Cannot send SSO AUTH: telnet bridge not connected");
            return false;
        }

        String username = session.getUsername();
        String password = session.getPassword();
        if (username == null || password == null) {
            LoggerUtil.error(logPrefix + "Cannot send SSO AUTH: missing credentials in session");
            return false;
        }

        String ssoSecret = properties.getProperty("skalholt.sso.secret");
        if (ssoSecret == null || ssoSecret.isBlank()) {
            LoggerUtil.error(logPrefix + "Cannot send SSO AUTH: skalholt.sso.secret not configured");
            return false;
        }

        // Build: AUTH <base64(user:pass)> <secret>
        String credentials = username + ":" + password;
        String base64 = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // Store credentials for EventStream authentication
        this.ssoBase64Credentials = base64;

        String authCommand = "AUTH " + base64 + " " + ssoSecret;

        LoggerUtil.info(logPrefix + "Sending SSO AUTH for user: " + username);
        return telnetBridge.sendCommand(authCommand);
    }

    /**
     * Sends an authentication error message to the client via FDO.
     */
    private void sendSkalholtAuthError(ChannelHandlerContext ctx, String error) {
        try {
            if (skalholtFdoBuilder == null) {
                skalholtFdoBuilder = new SkalholtFdoBuilder();
            }
            String fdoSource = skalholtFdoBuilder.addLine("*** ERROR: " + error + " ***");
            List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(
                    fdoSource, "At", FdoCompiler.AUTO_GENERATE_STREAM_ID);
            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "ST_SSO_ERROR", MAX_BURST_FRAMES,
                    session.getDisplayName());
            pacer.drainLimited(ctx, 2);
        } catch (Exception e) {
            LoggerUtil.error(logPrefix + "Failed to send SSO auth error: " + e.getMessage());
        }
    }

    /**
     * Cleanup Skalholt resources (called on channel inactive).
     */
    public void cleanup() {
        if (telnetBridge != null) {
            telnetBridge.close();
            telnetBridge = null;
        }
        if (skalholtSessionManager != null) {
            skalholtSessionManager.cleanup();
            skalholtSessionManager = null;
        }
        skalholtFdoBuilder = null;
        skalholtMapFdoBuilder = null;
        ssoBase64Credentials = null;
        pendingStStreams.clear();
    }

    /**
     * Get pending St streams map (for integration with StatefulClientHandler during migration).
     */
    public Map<Integer, List<byte[]>> getPendingStStreams() {
        return pendingStStreams;
    }

    /**
     * Get telnet bridge (for integration with StatefulClientHandler during migration).
     */
    public TelnetBridge getTelnetBridge() {
        return telnetBridge;
    }
}
