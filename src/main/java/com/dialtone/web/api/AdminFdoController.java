/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.auth.UserRegistry;
import com.dialtone.db.models.User;
import com.dialtone.fdo.spi.FdoCompilationException;
import com.dialtone.fdo.FdoChunk;
import com.dialtone.fdo.FdoCompiler;
import com.dialtone.protocol.P3ChunkEnqueuer;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.AdminAuditService;
import com.dialtone.web.services.AdminSecurityService;
import io.javalin.http.Context;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.dialtone.web.api.AdminControllerUtils.*;

/**
 * Admin controller for FDO Workbench functionality.
 *
 * Provides admin-only endpoints for:
 * - Listing connected screennames
 * - Compiling and sending FDO scripts to connected clients
 *
 * All operations require admin authentication and are audit logged.
 */
public class AdminFdoController {
    private final AdminSecurityService adminSecurityService;
    private final AdminAuditService adminAuditService;
    private final CsrfProtectionService csrfService;
    private final FdoCompiler fdoCompiler;
    private final UserRegistry userRegistry;

    // Maximum burst frames for pacing (same as StatefulClientHandler)
    private static final int MAX_BURST_FRAMES = 16;

    public AdminFdoController(AdminSecurityService adminSecurityService,
                              AdminAuditService adminAuditService,
                              CsrfProtectionService csrfService,
                              FdoCompiler fdoCompiler) {
        this.adminSecurityService = adminSecurityService;
        this.adminAuditService = adminAuditService;
        this.csrfService = csrfService;
        this.fdoCompiler = fdoCompiler;
        this.userRegistry = UserRegistry.getInstance();
    }

    /**
     * Gets list of currently connected screennames.
     * GET /api/admin/fdo/connected-screennames
     */
    public void getConnectedScreennames(Context ctx) {
        try {
            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Get all connected usernames from UserRegistry
            List<String> connectedScreennames = userRegistry.getOnlineUsernames();

            ConnectedScreennamesResponse response = new ConnectedScreennamesResponse(
                connectedScreennames,
                connectedScreennames.size()
            );
            ctx.json(response);

            LoggerUtil.debug(String.format("Admin %s retrieved connected screennames list (%d online)",
                admin.xUsername(), connectedScreennames.size()));

        } catch (Exception e) {
            LoggerUtil.error("Failed to get connected screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve connected screennames"));
        }
    }

    /**
     * Compiles and sends FDO script to a connected client.
     * POST /api/admin/fdo/send
     *
     * Request body:
     * {
     *   "screenname": "Bobby",
     *   "fdoScript": "man_create_window(...)",
     *   "token": "AT",      // optional, defaults to "AT"
     *   "streamId": -1      // optional, -1 = auto-generate
     * }
     */
    public void sendFdo(Context ctx) {
        try {
            if (!validateCsrf(ctx, csrfService, "send FDO")) return;

            Optional<User> adminOpt = getAdminUser(ctx, adminSecurityService);
            if (adminOpt.isEmpty()) return;
            User admin = adminOpt.get();

            if (!checkRateLimit(ctx, admin, adminSecurityService)) return;

            // Parse request body
            SendFdoRequest request = ctx.bodyAsClass(SendFdoRequest.class);

            // Validate required fields
            if (request.screenname == null || request.screenname.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            if (request.fdoScript == null || request.fdoScript.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("FDO script is required"));
                return;
            }

            // Get the user connection
            UserRegistry.UserConnection connection = userRegistry.getConnection(request.screenname);
            if (connection == null) {
                ctx.status(404).json(SharedErrorResponse.notFound(
                    "Screenname '" + request.screenname + "' is not currently connected"));
                return;
            }

            if (!connection.isActive()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Screenname '" + request.screenname + "' connection is not active"));
                return;
            }

            // Use defaults if not provided
            String token = (request.token != null && !request.token.isBlank()) ? request.token : "AT";
            int streamId = (request.streamId != null) ? request.streamId : FdoCompiler.AUTO_GENERATE_STREAM_ID;

            // Resolve stream ID (auto-generate if requested)
            streamId = FdoCompiler.resolveStreamId(streamId);

            // Compile FDO script
            long startTime = System.currentTimeMillis();
            List<FdoChunk> chunks;
            try {
                chunks = fdoCompiler.compileFdoScriptToP3Chunks(request.fdoScript, token, streamId);
            } catch (FdoCompilationException e) {
                LoggerUtil.warn("FDO compilation failed: " + e.getMessage());
                ctx.status(400).json(SharedErrorResponse.badRequest(e.getMessage()));
                return;
            }

            if (chunks == null || chunks.isEmpty()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "FDO compilation produced no chunks"));
                return;
            }

            long compilationTime = System.currentTimeMillis() - startTime;

            // Calculate total bytes
            int totalBytes = chunks.stream()
                .mapToInt(chunk -> chunk.getBinaryData().length)
                .sum();

            // Send chunks to client using P3ChunkEnqueuer
            String label = "FDO_WORKBENCH_" + System.currentTimeMillis();
            P3ChunkEnqueuer.enqueue(
                connection.getContext(),
                connection.getPacer(),
                chunks,
                label,
                MAX_BURST_FRAMES,
                request.screenname
            );

            // Drain the pacer to send immediately (since we're not in splitAndDispatch context)
            connection.getPacer().drainLimited(connection.getContext(), MAX_BURST_FRAMES);

            // Build response
            CompilationStats stats = new CompilationStats(
                chunks.size(),
                totalBytes,
                compilationTime
            );

            SendFdoResponse response = new SendFdoResponse(
                true,
                request.screenname,
                stats,
                String.format("FDO sent successfully (%d chunks, %d bytes)", chunks.size(), totalBytes)
            );
            ctx.json(response);

            // Audit log
            Map<String, Object> details = new HashMap<>();
            details.put("target_screenname", request.screenname);
            details.put("chunk_count", chunks.size());
            details.put("total_bytes", totalBytes);
            details.put("compilation_time_ms", compilationTime);
            details.put("token", token);
            details.put("stream_id", streamId);
            details.put("fdo_preview", request.fdoScript.substring(0, Math.min(100, request.fdoScript.length())));

            logAdminAction(adminAuditService, admin, "SEND_FDO", null, null, details, ctx);

            LoggerUtil.info(String.format("Admin %s sent FDO to %s: %d chunks, %d bytes, %dms compilation",
                admin.xUsername(), request.screenname, chunks.size(), totalBytes, compilationTime));

        } catch (Exception e) {
            LoggerUtil.error("Failed to send FDO: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to send FDO: " + e.getMessage()));
        }
    }

    // Request/Response DTOs

    public static class SendFdoRequest {
        public String screenname;
        public String fdoScript;
        public String token;
        public Integer streamId;
    }

    public record ConnectedScreennamesResponse(List<String> screennames, int count) {}

    public record CompilationStats(int chunkCount, int totalBytes, long compilationTimeMs) {}

    public record SendFdoResponse(boolean success, String screenname, CompilationStats compilationStats, String message) {}
}
