/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.auth;

import com.dialtone.auth.AuthResult;
import com.dialtone.auth.FallbackAuthenticator;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.fdo.FdoVariableBuilder;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.fdo.dsl.builders.ConfigureActiveUsernameFdoBuilder;
import com.dialtone.fdo.dsl.builders.GuestLoginFdoBuilder;
import com.dialtone.fdo.dsl.builders.IncorrectLoginFdoBuilder;
import com.dialtone.fdo.dsl.builders.UsernameConfigFdoBuilder;
import com.dialtone.fdo.dsl.builders.WelcomeScreenFdoBuilder;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.protocol.dod.DodRequestHandler;
import com.dialtone.ai.UnifiedNewsService;
import com.dialtone.auth.UserRegistry;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Handles login-related tokens: Dd (login), Dg (guest login), ]K (preload).
 * Manages authentication flow, welcome screen initialization, and IDB atom streams.
 */
public class LoginTokenHandler implements TokenHandler {
    private static final int MAX_BURST_FRAMES = 10;

    private final SessionContext session;
    private final Pacer pacer;
    private final FdoCompiler fdoCompiler;
    private final FdoProcessor fdoProcessor;
    private final FallbackAuthenticator authenticator;
    private final UserRegistry userRegistry;
    private final DodRequestHandler dodRequestHandler;
    private final UnifiedNewsService unifiedNewsService;
    private final String logPrefix;

    // Reference to main handler for disconnect operations (needed for RemoteDisconnectHandler)
    private final DisconnectHandler disconnectHandler;

    /**
     * Interface for disconnect operations (to avoid circular dependency).
     */
    public interface DisconnectHandler {
        void handleDisconnect(ChannelHandlerContext ctx, String message);
    }

    public LoginTokenHandler(SessionContext session, Pacer pacer, FdoCompiler fdoCompiler,
                            FdoProcessor fdoProcessor, FallbackAuthenticator authenticator,
                            UserRegistry userRegistry, DodRequestHandler dodRequestHandler,
                            UnifiedNewsService unifiedNewsService, DisconnectHandler disconnectHandler) {
        this.session = session;
        this.pacer = pacer;
        this.fdoCompiler = fdoCompiler;
        this.fdoProcessor = fdoProcessor;
        this.authenticator = authenticator;
        this.userRegistry = userRegistry;
        this.dodRequestHandler = dodRequestHandler;
        this.unifiedNewsService = unifiedNewsService;
        this.disconnectHandler = disconnectHandler;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "Dd".equals(token) || "Dg".equals(token) || "]K".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        String token = extractToken(frame);
        if (token == null) {
            return;
        }

        switch (token) {
            case "Dd":
                handleDdMultiFrame(ctx, frame);
                break;
            case "Dg":
                handleDgGuestLogin(ctx, frame);
                break;
            case "]K":
                handlePreload(ctx);
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
     * Handle Dd (login) token with multi-frame support.
     */
    public void handleDdMultiFrame(ChannelHandlerContext ctx, byte[] ddFrame) {
        try {
            LoginCredentials credentials = extractLoginCredentials(ddFrame);
            byte[] uiInit = com.dialtone.aol.core.Hex.hexToBytes("5A4AAA000B121020615420010112000D");
            pacer.enqueueSafe(ctx, uiInit, "LOGIN_INIT");
            // Check for Windows guest login flow
            if (credentials.username().equalsIgnoreCase("Guest")) {

                LoggerUtil.info(logPrefix + "Guest login detected - sending guest_login.fdo.txt");
                sendGuestLoginForm(ctx);
                return;
            }

            // Use FallbackAuthenticator with full result info
            AuthResult authResult = authenticator.authenticateWithResult(
                    credentials.username(), credentials.password());

            if (!authResult.isSuccess()) {
                LoggerUtil.warn(logPrefix + "Authentication failed for user: " + credentials.username());
                handleAuthenticationFailure(ctx);
                return;
            }

            // Set session state from auth result
            // For ephemeral users, the screenname is the generated guest name
            String effectiveScreenname = authResult.getScreenname();
            session.setUsername(effectiveScreenname);
            session.setEphemeral(authResult.isEphemeral());

            if (!authResult.isEphemeral()) {
                session.setPassword(credentials.password());  // Store for Skalholt SSO (registered users only)
            } else {
                // Generate a secure random password for ephemeral guest users
                // This is needed for Skalholt SSO authentication
                String guestPassword = generateGuestPassword();
                session.setPassword(guestPassword);
                LoggerUtil.debug(logPrefix + "Generated password for ephemeral guest: " + effectiveScreenname);
            }

            session.setAuthenticated(true);

            if (authResult.isEphemeral()) {
                LoggerUtil.info(logPrefix + "Ephemeral guest session created: '" + effectiveScreenname +
                               "' (attempted: " + credentials.username() + ")");
            } else {
                LoggerUtil.info(logPrefix + "User '" + effectiveScreenname + "' authenticated successfully");
            }

            // Register user in UserRegistry with detected platform and single-session enforcement
            // Fallback to singleton if field is null (robustness for edge cases)
            UserRegistry registry = userRegistry != null ? userRegistry : UserRegistry.getInstance();
            if (userRegistry == null) {
                LoggerUtil.warn(logPrefix + "userRegistry field was null, falling back to singleton");
            }

            // Create disconnect handler for single-session enforcement
            // Note: RemoteDisconnectHandler is an inner class of StatefulClientHandler
            // For now, we'll need to pass a reference or keep it in the main handler
            com.dialtone.auth.SessionDisconnectHandler sessionDisconnectHandler =
                    new RemoteDisconnectHandlerAdapter(disconnectHandler);

            // Register with disconnect handler - old sessions will be gracefully disconnected
            // Use effectiveScreenname (may be generated guest name for ephemeral users)
            UserRegistry.UserConnection oldConnection = registry.register(
                    effectiveScreenname,
                    ctx,
                    pacer,
                    session.getPlatform(),
                    sessionDisconnectHandler
            );

            if (oldConnection != null) {
                LoggerUtil.info(logPrefix + "Replaced existing session for user: " + effectiveScreenname);
            }

            // For ephemeral guests, use "TryAnyPass" instead of temp name to indicate any login works
            String configUsername = authResult.isEphemeral() ? "TryAnyPass" : effectiveScreenname;
            UsernameConfigFdoBuilder usernameBuilder = UsernameConfigFdoBuilder.forUser(configUsername);
            String fdoSource = usernameBuilder.toSource(RenderingContext.DEFAULT);
            java.util.List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "At", -1);

            LoggerUtil.info(logPrefix + "Login (Dd) username-config P3: " +
                    (chunks != null ? chunks.size() : 0) + " chunks");

            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "LOGIN_CFG", MAX_BURST_FRAMES,
                    session.getDisplayName());

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed Dd login processing: " + ex.getMessage());
            handleAuthenticationFailure(ctx);
        }
    }

    /**
     * Handle Dg (guest login) token.
     */
    public void handleDgGuestLogin(ChannelHandlerContext ctx, byte[] dgFrame) {
        try {
            LoginCredentials credentials = extractLoginCredentials(dgFrame);
            // Credentials are already trimmed in extractLoginCredentials()

            // Use FallbackAuthenticator with full result info
            AuthResult authResult = authenticator.authenticateWithResult(
                    credentials.username(), credentials.password());

            if (!authResult.isSuccess()) {
                LoggerUtil.warn(logPrefix + "Guest authentication failed for user: " + credentials.username());
                handleAuthenticationFailure(ctx);
                return;
            }

            // Set session state from auth result
            // For ephemeral users, the screenname is the generated guest name
            String effectiveScreenname = authResult.getScreenname();
            session.setUsername(effectiveScreenname);
            session.setEphemeral(authResult.isEphemeral());

            if (!authResult.isEphemeral()) {
                session.setPassword(credentials.password());  // Store for Skalholt SSO (registered users only)
            } else {
                // Generate a secure random password for ephemeral guest users
                // This is needed for Skalholt SSO authentication
                String guestPassword = generateGuestPassword();
                session.setPassword(guestPassword);
                LoggerUtil.debug(logPrefix + "Generated password for ephemeral guest: " + effectiveScreenname);
            }

            session.setAuthenticated(true);

            if (authResult.isEphemeral()) {
                LoggerUtil.info(logPrefix + "Ephemeral guest session created: '" + effectiveScreenname +
                               "' (attempted: " + credentials.username() + ")");
            } else {
                LoggerUtil.info(logPrefix + "Guest user '" + effectiveScreenname + "' authenticated successfully");
            }

            // Register user in UserRegistry
            UserRegistry registry = userRegistry != null ? userRegistry : UserRegistry.getInstance();
            if (userRegistry == null) {
                LoggerUtil.warn(logPrefix + "userRegistry field was null, falling back to singleton");
            }

            // Create disconnect handler for single-session enforcement
            com.dialtone.auth.SessionDisconnectHandler sessionDisconnectHandler =
                    new RemoteDisconnectHandlerAdapter(disconnectHandler);

            // Register with disconnect handler
            // Use effectiveScreenname (may be generated guest name for ephemeral users)
            UserRegistry.UserConnection oldConnection = registry.register(
                    effectiveScreenname,
                    ctx,
                    pacer,
                    session.getPlatform(),
                    sessionDisconnectHandler
            );

            if (oldConnection != null) {
                LoggerUtil.info(logPrefix + "Replaced existing session for guest user: " + effectiveScreenname);
            }

            // For ephemeral guests, use "TryAnyPass" instead of temp name to indicate any login works
            String configUsername = authResult.isEphemeral() ? "TryAnyPass" : effectiveScreenname;
            UsernameConfigFdoBuilder usernameBuilder = UsernameConfigFdoBuilder.forUser(configUsername);
            String fdoSource = usernameBuilder.toSource(RenderingContext.DEFAULT);
            java.util.List<FdoChunk> chunks = fdoCompiler.compileFdoScriptToP3Chunks(fdoSource, "At", -1);

            LoggerUtil.info(logPrefix + "Guest login (Dg) username-config P3: " +
                    (chunks != null ? chunks.size() : 0) + " chunks");

            P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "GUEST_LOGIN_CFG", MAX_BURST_FRAMES,
                    session.getDisplayName());

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed Dg guest login processing: " + ex.getMessage());
            handleAuthenticationFailure(ctx);
        }
    }

    /**
     * Handle ]K (preload) token - welcome screen initialization.
     */
    public void handlePreload(ChannelHandlerContext ctx) {
        try {
            // Type-safe DSL builder - no string paths, compile-time checked
            fdoProcessor.compileAndSend(ctx,
                    ConfigureActiveUsernameFdoBuilder.forUser(session.getDisplayName()),
                    session, "At", -1, "OPEN_WELCOME_WINDOW");

            // Proactively reset and send welcome window atom streams
            // This deletes the old IDB objects and sends the new compiled atom stream data
            // NOTE: We batch these with drain boundaries to prevent P3 window violations
            // that crash Mac clients (strict 16-frame sliding window enforcement)

            // First batch: 2 IDB atom streams
            sendIdbAtomStreamResetAndSend(ctx, "32-117");
            sendIdbAtomStreamResetAndSend(ctx, "32-5447");

            // Force drain to give client ACK opportunity (fixes Mac client crash)
            pacer.drainLimited(ctx, MAX_BURST_FRAMES);

            // Second batch: 2 more IDB atom streams
            sendIdbAtomStreamResetAndSend(ctx, "32-168");
            sendIdbAtomStreamResetAndSend(ctx, "32-225");
            sendIdbAtomStreamResetAndSend(ctx, "69-420");
            sendIdbAtomStreamResetAndSend(ctx, "69-421");

            // Force drain again before welcome screen
            pacer.drainLimited(ctx, MAX_BURST_FRAMES);

            openWelcomeScreen(ctx);
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Welcome generation failed: " + ex.getMessage());
            throw new RuntimeException("Welcome generation failed", ex);
        }
    }

    /**
     * Extract login credentials from Dd/Dg frame.
     */
    private LoginCredentials extractLoginCredentials(byte[] ddFrame) throws Exception {
        // Use native FdoStream extraction (no HTTP decompilation needed)
        return FdoStreamExtractor.extractLoginCredentials(ddFrame);
    }

    /**
     * Handle authentication failure.
     */
    private void handleAuthenticationFailure(ChannelHandlerContext ctx) {
        LoggerUtil.error(logPrefix + "Authentication failure - sending incorrect login alert");
        try {
            fdoProcessor.compileAndSend(
                ctx,
                IncorrectLoginFdoBuilder.INSTANCE,
                session,
                "At",
                -1,
                "INCORRECT_LOGIN"
            );
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send incorrect login FDO: " + ex.getMessage());
        }
    }

    /**
     * Send guest login form.
     */
    private void sendGuestLoginForm(ChannelHandlerContext ctx) {
        try {
            fdoProcessor.compileAndSend(ctx, GuestLoginFdoBuilder.INSTANCE, session, "at", -1, "GUEST_LOGIN_FORM");
        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to send guest login form: " + ex.getMessage());
            handleAuthenticationFailure(ctx);
        }
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

            fdoProcessor.compileAndSend(ctx,
                    WelcomeScreenFdoBuilder.create(
                            session.getDisplayName(),
                            techHeadline,
                            newsHeadline,
                            cryptoHeadline,
                            sportsHeadline,
                            entertainmentHeadline),
                    session, "AT", -1, "WELCOME_SCREEN_DATA");

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Welcome generation failed: " + ex.getMessage());
            throw new RuntimeException("Welcome generation failed", ex);
        }
    }

    /**
     * Sends an IDB reset+send for an atom stream.
     */
    private void sendIdbAtomStreamResetAndSend(ChannelHandlerContext ctx, String gid) throws Exception {
        LoggerUtil.info(logPrefix + "Sending IDB atom stream reset+send for: " + gid);

        java.util.List<FdoChunk> chunks = dodRequestHandler.generateIdbAtomStreamResetAndSend(
            gid, session.getDisplayName(), session.getPlatform());

        P3ChunkEnqueuer.enqueue(ctx, pacer, chunks, "IDB_ATOM_RESET_" + gid, MAX_BURST_FRAMES,
            session.getDisplayName());

        LoggerUtil.info(logPrefix + "IDB atom stream reset+send enqueued: " + gid);
    }

    /**
     * Generate a secure random password for ephemeral guest users.
     * 
     * <p>Generates a password suitable for Skalholt SSO authentication.
     * Password is 12-16 characters long using alphanumeric characters
     * and safe special characters.</p>
     * 
     * @return A secure random password string
     */
    private String generateGuestPassword() {
        SecureRandom random = new SecureRandom();
        
        // Character set: alphanumeric + safe special chars (no quotes, spaces, or problematic chars)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        
        // Generate password length between 12 and 16 characters
        int length = 12 + random.nextInt(5);
        
        StringBuilder password = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            password.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return password.toString();
    }

    /**
     * Adapter to convert DisconnectHandler interface to SessionDisconnectHandler.
     * This allows LoginTokenHandler to work with the disconnect functionality
     * without creating a circular dependency.
     */
    private static class RemoteDisconnectHandlerAdapter implements com.dialtone.auth.SessionDisconnectHandler {
        private final DisconnectHandler disconnectHandler;

        public RemoteDisconnectHandlerAdapter(DisconnectHandler disconnectHandler) {
            this.disconnectHandler = disconnectHandler;
        }

        @Override
        public java.util.concurrent.CompletableFuture<Void> disconnectSession(
                UserRegistry.UserConnection connection, String message) {
            java.util.concurrent.CompletableFuture<Void> future = new java.util.concurrent.CompletableFuture<>();

            io.netty.channel.ChannelHandlerContext ctx = connection.getContext();

            // Skip if channel already closed
            if (!ctx.channel().isActive()) {
                LoggerUtil.debug(() -> "Channel already inactive for user: " + connection.getUsername());
                future.complete(null);
                return future;
            }

            // Execute on the channel's event loop to ensure thread safety
            ctx.channel().eventLoop().execute(() -> {
                try {
                    // Call the disconnect handler
                    disconnectHandler.handleDisconnect(ctx, message);

                    // Schedule channel close after delay to ensure FDO sent
                    ctx.channel().eventLoop().schedule(() -> {
                        try {
                            ctx.close();
                            LoggerUtil.info("Old session closed for user: " + connection.getUsername());
                            future.complete(null);
                        } catch (Exception e) {
                            LoggerUtil.error("Failed to close old session for user: " +
                                    connection.getUsername() + " - " + e.getMessage());
                            future.completeExceptionally(e);
                        }
                    }, 2, java.util.concurrent.TimeUnit.SECONDS);

                } catch (Exception e) {
                    LoggerUtil.error("Failed to disconnect old session for user: " +
                            connection.getUsername() + " - " + e.getMessage());
                    // Force close on error
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
}
