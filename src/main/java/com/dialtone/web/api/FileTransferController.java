/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.web.api;

import com.dialtone.auth.UserRegistry;
import com.dialtone.db.models.Screenname;
import com.dialtone.db.models.User;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.protocol.xfer.XferTransferState;
import com.dialtone.protocol.xfer.XferUploadRegistry;
import com.dialtone.protocol.xfer.XferUploadService;
import com.dialtone.protocol.xfer.XferUploadState;
import com.dialtone.storage.FileMetadata;
import com.dialtone.storage.FileStorage;
import com.dialtone.utils.LoggerUtil;
import com.dialtone.web.security.CsrfProtectionService;
import com.dialtone.web.services.ScreennameService;
import io.javalin.http.Context;
import io.javalin.http.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Controller for file transfer functionality.
 *
 * <p>Allows authenticated users to:
 * <ul>
 *   <li>Download: Upload files via web and send them to connected AOL clients</li>
 *   <li>Upload: Request files from connected AOL clients</li>
 * </ul>
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/transfer/connected-screennames - List user's connected screennames</li>
 *   <li>GET /api/transfer/config - Get transfer configuration</li>
 *   <li>POST /api/transfer/upload - Upload file and initiate download to client</li>
 *   <li>POST /api/transfer/request-upload - Request file upload from client</li>
 *   <li>GET /api/transfer/uploads/{screenname} - List uploaded files for screenname</li>
 *   <li>GET /api/transfer/uploads/{screenname}/{filename} - Download an uploaded file</li>
 * </ul>
 */
public class FileTransferController {
    private final ScreennameService screennameService;
    private final CsrfProtectionService csrfService;
    private final XferService xferService;
    private final UserRegistry userRegistry;
    private final FileStorage fileStorage;
    private final long maxFileSizeBytes;

    /** Default max file size: 100 MB */
    private static final long DEFAULT_MAX_FILE_SIZE_MB = 100;

    public FileTransferController(ScreennameService screennameService,
                                   CsrfProtectionService csrfService,
                                   XferService xferService,
                                   FileStorage fileStorage,
                                   Properties config) {
        this.screennameService = screennameService;
        this.csrfService = csrfService;
        this.xferService = xferService;
        this.fileStorage = fileStorage;
        this.userRegistry = UserRegistry.getInstance();

        // Use max file size from storage or config
        long maxMb = DEFAULT_MAX_FILE_SIZE_MB;
        if (fileStorage != null) {
            maxMb = fileStorage.getMaxFileSizeBytes() / (1024 * 1024);
        }
        String configValue = config.getProperty("transfer.max.file.size.mb");
        if (configValue != null) {
            try {
                maxMb = Long.parseLong(configValue.trim());
            } catch (NumberFormatException e) {
                LoggerUtil.warn("Invalid transfer.max.file.size.mb value: " + configValue + ", using default: " + DEFAULT_MAX_FILE_SIZE_MB);
            }
        }
        this.maxFileSizeBytes = maxMb * 1024 * 1024;

        // Storage is already initialized by StorageFactory

        LoggerUtil.info("FileTransferController initialized (max file size: " + maxMb + " MB)");
    }

    /**
     * Gets list of user's screennames that are currently connected.
     * GET /api/transfer/connected-screennames
     *
     * <p>Only returns screennames that:
     * <ol>
     *   <li>Belong to the authenticated user</li>
     *   <li>Are currently connected to the AOL server</li>
     * </ol>
     */
    public void getConnectedScreennames(Context ctx) {
        try {
            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            LoggerUtil.info(String.format("User %d querying connected screennames", user.id()));

            // Get user's screennames from database
            List<Screenname> userScreennames = screennameService.getScreennamesForUser(user.id());

            LoggerUtil.info(String.format("Found %d screennames in database for user %d: %s",
                userScreennames.size(), user.id(),
                userScreennames.stream().map(Screenname::screenname).toList()));

            // Log all currently connected users for comparison
            LoggerUtil.info("All connected users in registry: " + userRegistry.getOnlineUsernames());

            // Filter to only those currently connected
            List<ConnectedScreennameInfo> connected = new ArrayList<>();
            for (Screenname sn : userScreennames) {
                UserRegistry.UserConnection connection = userRegistry.getConnection(sn.screenname());
                LoggerUtil.info(String.format("Checking screenname '%s': connection=%s, active=%s",
                    sn.screenname(),
                    connection != null ? "found" : "NOT FOUND",
                    connection != null ? connection.isActive() : "N/A"));
                if (connection != null && connection.isActive()) {
                    ConnectedScreennameInfo info = new ConnectedScreennameInfo(
                        sn.screenname(),
                        connection.getPlatform().name().toLowerCase(),
                        true
                    );
                    LoggerUtil.info(String.format("Adding connected screenname: screenname='%s', platform='%s', isOnline=%s",
                        info.screenname(), info.platform(), info.isOnline()));
                    connected.add(info);
                }
            }

            ConnectedScreennamesResponse response = new ConnectedScreennamesResponse(connected, connected.size());
            LoggerUtil.info(String.format("Returning response with %d screennames: %s",
                response.count(),
                connected.stream().map(c -> c.screenname() + "/" + c.platform()).toList()));
            ctx.json(response);

            LoggerUtil.info(String.format("User %d has %d/%d screennames connected",
                user.id(), connected.size(), userScreennames.size()));

        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.error("Failed to get screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve screennames"));
        } catch (Exception e) {
            LoggerUtil.error("Unexpected error getting connected screennames: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("An unexpected error occurred"));
        }
    }

    /**
     * Upload a file and initiate transfer to a connected screenname.
     * POST /api/transfer/upload (multipart/form-data)
     *
     * <p>Form fields:
     * <ul>
     *   <li>file - The file to upload</li>
     *   <li>screenname - The target screenname (must belong to user and be connected)</li>
     * </ul>
     */
    public void uploadFile(Context ctx) {
        try {
            // CSRF Protection
            csrfService.requireValidCsrfToken(ctx);

            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            // Get form fields
            String targetScreenname = ctx.formParam("screenname");
            UploadedFile uploadedFile = ctx.uploadedFile("file");

            // Validate inputs
            if (targetScreenname == null || targetScreenname.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            if (uploadedFile == null) {
                ctx.status(400).json(SharedErrorResponse.badRequest("File is required"));
                return;
            }

            // Validate file size
            long fileSize = uploadedFile.size();
            if (fileSize > maxFileSizeBytes) {
                long maxMb = maxFileSizeBytes / (1024 * 1024);
                ctx.status(413).json(SharedErrorResponse.badRequest(
                    String.format("File size (%d MB) exceeds maximum allowed size (%d MB)",
                        fileSize / (1024 * 1024), maxMb)));
                return;
            }

            if (fileSize == 0) {
                ctx.status(400).json(SharedErrorResponse.badRequest("File is empty"));
                return;
            }

            // Verify screenname belongs to user
            if (!isUserScreenname(user.id(), targetScreenname)) {
                ctx.status(403).json(SharedErrorResponse.forbidden("You can only send files to your own screennames"));
                return;
            }

            // Get connection for target screenname
            UserRegistry.UserConnection connection = userRegistry.getConnection(targetScreenname);
            if (connection == null || !connection.isActive()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Screenname '" + targetScreenname + "' is not currently connected"));
                return;
            }

            // Get XferTransferRegistry from channel attributes
            XferTransferRegistry registry = connection.getContext().channel()
                .attr(StatefulClientHandler.XFER_REGISTRY_KEY).get();
            if (registry == null) {
                LoggerUtil.error("XferTransferRegistry not found for screenname: " + targetScreenname);
                ctx.status(500).json(SharedErrorResponse.serverError("Transfer service unavailable for this connection"));
                return;
            }

            // Check if there's already a pending transfer
            if (registry.hasTransferAwaitingXg()) {
                ctx.status(409).json(SharedErrorResponse.conflict(
                    "A file transfer is already in progress for this screenname. Please wait."));
                return;
            }

            // Read file data
            String filename = sanitizeFilename(uploadedFile.filename());
            byte[] fileData;
            try (InputStream is = uploadedFile.content()) {
                fileData = is.readAllBytes();
            }

            LoggerUtil.info(String.format("User %d uploading file '%s' (%d bytes) to screenname '%s'",
                user.id(), filename, fileData.length, targetScreenname));

            // Create a session context for the transfer
            SessionContext session = new SessionContext();
            session.setUsername(targetScreenname);
            session.setAuthenticated(true);

            // Initiate transfer via XFER protocol
            XferTransferState state = xferService.initiateTransfer(
                connection.getContext(),
                connection.getPacer(),
                filename,
                fileData,
                session,
                registry
            );

            // Drain the pacer to send immediately (since we're not in splitAndDispatch context)
            connection.getPacer().drainLimited(connection.getContext(), 16);

            // Build success response
            TransferResponse response = new TransferResponse(
                true,
                state.getTransferId(),
                String.format("File transfer initiated. Awaiting client acknowledgment."),
                filename,
                fileData.length,
                targetScreenname
            );
            ctx.json(response);

            LoggerUtil.info(String.format("File transfer initiated: %s -> %s (%s, %d bytes)",
                user.id(), targetScreenname, filename, fileData.length));

        } catch (CsrfProtectionService.CsrfValidationException e) {
            LoggerUtil.warn("CSRF validation failed for file upload: " + e.getMessage());
            ctx.status(403).json(SharedErrorResponse.csrfFailed("Invalid or missing CSRF token"));

        } catch (Exception e) {
            LoggerUtil.error("Failed to upload file: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to initiate file transfer: " + e.getMessage()));
        }
    }

    /**
     * Get max file size in bytes.
     * Exposed for frontend to display limit.
     */
    public void getConfig(Context ctx) {
        try {
            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            ctx.json(new TransferConfig(maxFileSizeBytes / (1024 * 1024)));
        } catch (Exception e) {
            LoggerUtil.error("Failed to get transfer config: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to retrieve configuration"));
        }
    }

    /**
     * Check if a screenname belongs to the specified user.
     */
    private boolean isUserScreenname(int userId, String screenname) {
        try {
            List<Screenname> userScreennames = screennameService.getScreennamesForUser(userId);
            return userScreennames.stream()
                .anyMatch(sn -> sn.screenname().equalsIgnoreCase(screenname));
        } catch (ScreennameService.ScreennameServiceException e) {
            LoggerUtil.error("Failed to verify screenname ownership: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sanitize filename to prevent path traversal and special characters.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "upload.bin";
        }

        // Remove path separators and parent directory references
        String sanitized = filename
            .replaceAll("[/\\\\]", "")   // Remove / and \
            .replaceAll("\\.\\.", "")     // Remove ..
            .trim();

        // If nothing left, use default
        if (sanitized.isEmpty()) {
            return "upload.bin";
        }

        // Truncate if too long
        if (sanitized.length() > 64) {
            // Keep extension if present
            int dotIndex = sanitized.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < sanitized.length() - 1) {
                String ext = sanitized.substring(dotIndex);
                String name = sanitized.substring(0, Math.min(60, dotIndex));
                sanitized = name + ext;
            } else {
                sanitized = sanitized.substring(0, 64);
            }
        }

        return sanitized;
    }

    /**
     * Extract authenticated user from request context.
     */
    private User getAuthenticatedUser(Context ctx) {
        User user = ctx.attribute("user");
        if (user == null) {
            ctx.status(401).json(SharedErrorResponse.unauthorized("Authentication required"));
            return null;
        }
        return user;
    }

    // =========================================================================
    // Upload Endpoints (client to server file transfer)
    // =========================================================================

    /**
     * Request a file upload from a connected screenname.
     * POST /api/transfer/request-upload
     *
     * <p>Initiates the server-driven upload protocol:
     * <ol>
     *   <li>Server sends th token to prompt file picker</li>
     *   <li>Client opens file picker dialog</li>
     *   <li>Client sends TH_OUT with selected filename</li>
     *   <li>Server sends td to request file stats</li>
     *   <li>Client sends TD_OUT with file size</li>
     *   <li>Server sends tf (0x80) to start upload</li>
     *   <li>Client streams file data (xd/xb/xe)</li>
     *   <li>Server sends fX result</li>
     * </ol>
     *
     * <p>Form fields:
     * <ul>
     *   <li>screenname - The target screenname (must belong to user and be connected)</li>
     * </ul>
     */
    public void requestUpload(Context ctx) {
        try {
            // CSRF Protection
            csrfService.requireValidCsrfToken(ctx);

            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            // Get form fields
            String targetScreenname = ctx.formParam("screenname");
            if (targetScreenname == null || targetScreenname.isBlank()) {
                // Try JSON body
                try {
                    var body = ctx.bodyAsClass(RequestUploadRequest.class);
                    targetScreenname = body.screenname();
                } catch (Exception e) {
                    ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                    return;
                }
            }

            // Validate screenname belongs to user
            if (!isUserScreenname(user.id(), targetScreenname)) {
                ctx.status(403).json(SharedErrorResponse.forbidden(
                    "You can only request uploads from your own screennames"));
                return;
            }

            // Get connection for target screenname
            UserRegistry.UserConnection connection = userRegistry.getConnection(targetScreenname);
            if (connection == null || !connection.isActive()) {
                ctx.status(400).json(SharedErrorResponse.badRequest(
                    "Screenname '" + targetScreenname + "' is not currently connected"));
                return;
            }

            // Get XferUploadRegistry from channel attributes
            XferUploadRegistry uploadRegistry = connection.getContext().channel()
                .attr(StatefulClientHandler.XFER_UPLOAD_REGISTRY_KEY).get();
            if (uploadRegistry == null) {
                LoggerUtil.error("XferUploadRegistry not found for screenname: " + targetScreenname);
                ctx.status(500).json(SharedErrorResponse.serverError(
                    "Upload service unavailable for this connection"));
                return;
            }

            // Check if there's already a pending upload
            if (uploadRegistry.hasActiveUpload()) {
                ctx.status(409).json(SharedErrorResponse.conflict(
                    "A file upload is already in progress for this screenname. Please wait."));
                return;
            }

            // Create XferUploadService for this request
            // Note: We need to get the service from the handler context
            // For now, we'll create a temporary one - this could be improved
            // by storing the service in the channel attribute
            XferUploadService uploadService = new XferUploadService(
                fileStorage,
                (int) maxFileSizeBytes,
                30000L
            );

            // Create session context
            SessionContext session = new SessionContext();
            session.setUsername(targetScreenname);
            session.setAuthenticated(true);

            // Initiate upload via XFER protocol
            XferUploadState state = uploadService.initiateUpload(
                connection.getContext(),
                connection.getPacer(),
                session,
                uploadRegistry
            );

            // Drain the pacer to send immediately
            connection.getPacer().drainLimited(connection.getContext(), 16);

            // Build success response
            UploadRequestResponse response = new UploadRequestResponse(
                true,
                state.getUploadId(),
                "Upload request sent. Waiting for client to select a file.",
                targetScreenname
            );
            ctx.json(response);

            LoggerUtil.info(String.format("Upload request initiated: user %d -> %s (upload: %s)",
                user.id(), targetScreenname, state.getUploadId()));

        } catch (CsrfProtectionService.CsrfValidationException e) {
            LoggerUtil.warn("CSRF validation failed for upload request: " + e.getMessage());
            ctx.status(403).json(SharedErrorResponse.csrfFailed("Invalid or missing CSRF token"));

        } catch (Exception e) {
            LoggerUtil.error("Failed to request upload: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to initiate upload request: " + e.getMessage()));
        }
    }

    /**
     * List uploaded files for a screenname.
     * GET /api/transfer/uploads/{screenname}
     */
    public void listUploads(Context ctx) {
        try {
            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            String screenname = ctx.pathParam("screenname");
            if (screenname == null || screenname.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            // Validate screenname belongs to user
            if (!isUserScreenname(user.id(), screenname)) {
                ctx.status(403).json(SharedErrorResponse.forbidden(
                    "You can only view uploads from your own screennames"));
                return;
            }

            // Check if file storage is available
            if (fileStorage == null) {
                ctx.status(503).json(SharedErrorResponse.serverError("Upload storage not configured"));
                return;
            }

            // List files
            List<FileMetadata> files = fileStorage.list(FileStorage.Scope.USER, screenname);
            List<UploadedFileInfo> fileInfos = files.stream()
                .map(f -> new UploadedFileInfo(
                    f.filename(),
                    f.sizeBytes(),
                    f.getSizeFormatted(),
                    f.getLastModifiedMs()
                ))
                .collect(Collectors.toList());

            ctx.json(new UploadedFilesResponse(fileInfos, fileInfos.size(), screenname));

        } catch (Exception e) {
            LoggerUtil.error("Failed to list uploads: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to list uploads: " + e.getMessage()));
        }
    }

    /**
     * Download an uploaded file.
     * GET /api/transfer/uploads/{screenname}/{filename}
     */
    public void downloadUpload(Context ctx) {
        try {
            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            String screenname = ctx.pathParam("screenname");
            String filename = ctx.pathParam("filename");

            if (screenname == null || screenname.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            if (filename == null || filename.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Filename is required"));
                return;
            }

            // Validate screenname belongs to user
            if (!isUserScreenname(user.id(), screenname)) {
                ctx.status(403).json(SharedErrorResponse.forbidden(
                    "You can only download files from your own screennames"));
                return;
            }

            // Check if file storage is available
            if (fileStorage == null) {
                ctx.status(503).json(SharedErrorResponse.serverError("Upload storage not configured"));
                return;
            }

            // Check if file exists
            if (!fileStorage.exists(FileStorage.Scope.USER, screenname, filename)) {
                ctx.status(404).json(SharedErrorResponse.notFound("File not found: " + filename));
                return;
            }

            // Get metadata for content type
            Optional<FileMetadata> metadataOpt = fileStorage.getMetadata(FileStorage.Scope.USER, screenname, filename);
            String contentType = "application/octet-stream";
            if (metadataOpt.isPresent()) {
                FileMetadata meta = metadataOpt.get();
                if (meta.contentType() != null && !meta.contentType().isEmpty()) {
                    contentType = meta.contentType();
                } else if (meta.path() != null) {
                    // Try to probe content type from path
                    String probed = Files.probeContentType(meta.path());
                    if (probed != null) {
                        contentType = probed;
                    }
                }
            }

            ctx.contentType(contentType);
            ctx.header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            ctx.result(fileStorage.read(FileStorage.Scope.USER, screenname, filename));

            LoggerUtil.info(String.format("User %d downloading file: %s/%s", user.id(), screenname, filename));

        } catch (IOException e) {
            LoggerUtil.error("Failed to download file: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to download file: " + e.getMessage()));
        } catch (Exception e) {
            LoggerUtil.error("Failed to download file: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to download file: " + e.getMessage()));
        }
    }

    /**
     * Delete an uploaded file.
     * DELETE /api/transfer/uploads/{screenname}/{filename}
     */
    public void deleteUpload(Context ctx) {
        try {
            // CSRF Protection
            csrfService.requireValidCsrfToken(ctx);

            User user = getAuthenticatedUser(ctx);
            if (user == null) return;

            String screenname = ctx.pathParam("screenname");
            String filename = ctx.pathParam("filename");

            if (screenname == null || screenname.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Screenname is required"));
                return;
            }

            if (filename == null || filename.isBlank()) {
                ctx.status(400).json(SharedErrorResponse.badRequest("Filename is required"));
                return;
            }

            // Validate screenname belongs to user
            if (!isUserScreenname(user.id(), screenname)) {
                ctx.status(403).json(SharedErrorResponse.forbidden(
                    "You can only delete files from your own screennames"));
                return;
            }

            // Check if file storage is available
            if (fileStorage == null) {
                ctx.status(503).json(SharedErrorResponse.serverError("Upload storage not configured"));
                return;
            }

            // Delete file
            boolean deleted = fileStorage.delete(FileStorage.Scope.USER, screenname, filename);
            if (!deleted) {
                ctx.status(404).json(SharedErrorResponse.notFound("File not found: " + filename));
                return;
            }

            ctx.json(new DeleteResponse(true, "File deleted: " + filename));

            LoggerUtil.info(String.format("User %d deleted file: %s/%s", user.id(), screenname, filename));

        } catch (CsrfProtectionService.CsrfValidationException e) {
            LoggerUtil.warn("CSRF validation failed for delete: " + e.getMessage());
            ctx.status(403).json(SharedErrorResponse.csrfFailed("Invalid or missing CSRF token"));

        } catch (IOException e) {
            LoggerUtil.error("Failed to delete file: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to delete file: " + e.getMessage()));
        } catch (Exception e) {
            LoggerUtil.error("Failed to delete file: " + e.getMessage());
            ctx.status(500).json(SharedErrorResponse.serverError("Failed to delete file: " + e.getMessage()));
        }
    }

    // DTO Records

    public record ConnectedScreennameInfo(String screenname, String platform, boolean isOnline) {}

    public record ConnectedScreennamesResponse(List<ConnectedScreennameInfo> screennames, int count) {}

    public record TransferResponse(boolean success, String transferId, String message,
                                    String filename, long fileSize, String screenname) {}

    public record TransferConfig(long maxFileSizeMb) {}

    // Upload DTOs

    public record RequestUploadRequest(String screenname) {}

    public record UploadRequestResponse(boolean success, String uploadId, String message, String screenname) {}

    public record UploadedFileInfo(String filename, long sizeBytes, String sizeFormatted, long lastModifiedMs) {}

    public record UploadedFilesResponse(List<UploadedFileInfo> files, int count, String screenname) {}

    public record DeleteResponse(boolean success, String message) {}
}
