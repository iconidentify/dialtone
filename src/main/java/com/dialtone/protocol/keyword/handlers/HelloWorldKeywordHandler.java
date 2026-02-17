/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.keyword.handlers;

import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.StatefulClientHandler;
import com.dialtone.protocol.keyword.KeywordHandler;
import com.dialtone.protocol.xfer.XferService;
import com.dialtone.protocol.xfer.XferTransferRegistry;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;

/**
 * Keyword handler for MVP file transfer demonstration.
 *
 * <p><b>Keyword:</b> "hello world" (case-insensitive)
 *
 * <p><b>Functionality:</b>
 * <ul>
 *   <li>Triggers a server-to-client file transfer</li>
 *   <li>Sends a tiny test file (tiny.txt containing "tiny\n")</li>
 *   <li>Demonstrates the XFER protocol with proper xG handshake</li>
 * </ul>
 *
 * <p><b>Protocol Flow:</b>
 * <ol>
 *   <li>Handler calls initiateTransfer() - sends xfer atoms, tj, tf</li>
 *   <li>Client opens destination file and sends xG token</li>
 *   <li>StatefulClientHandler receives xG and calls resumeAfterXg()</li>
 *   <li>Data frames (F9) are sent to complete the transfer</li>
 * </ol>
 *
 * <p><b>Test File:</b>
 * <ul>
 *   <li>Filename: tiny.txt</li>
 *   <li>Contents: "tiny\n" (5 bytes)</li>
 * </ul>
 */
public class HelloWorldKeywordHandler implements KeywordHandler {

    private static final String KEYWORD = "hello world";
    private static final String DESCRIPTION = "Sends a test file (tiny.txt) to demonstrate file transfer";

    /** The test filename for MVP file transfer */
    private static final String TINY_FILENAME = "tiny.txt";

    /** The test file contents: "tiny\n" (5 bytes) */
    private static final byte[] TINY_FILE_DATA = "tiny\n".getBytes(StandardCharsets.US_ASCII);

    private final XferService xferService;

    /**
     * Creates a new HelloWorldKeywordHandler.
     *
     * @param xferService the XFER service for file transfer
     */
    public HelloWorldKeywordHandler(XferService xferService) {
        if (xferService == null) {
            throw new IllegalArgumentException("XferService cannot be null");
        }
        this.xferService = xferService;
    }

    @Override
    public String getKeyword() {
        return KEYWORD;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void handle(String keyword, SessionContext session,
                       ChannelHandlerContext ctx, Pacer pacer) throws Exception {
        String username = session != null ? session.getDisplayName() : "unknown";

        LoggerUtil.info(String.format(
            "[%s] Received 'hello world' keyword - initiating MVP file transfer",
            username));

        // Get the XferTransferRegistry from channel attributes
        XferTransferRegistry registry = ctx.channel().attr(StatefulClientHandler.XFER_REGISTRY_KEY).get();
        if (registry == null) {
            LoggerUtil.error(String.format(
                "[%s] Cannot initiate file transfer - XferTransferRegistry not found on channel",
                username));
            throw new IllegalStateException("XferTransferRegistry not available");
        }

        // Initiate file transfer (sends atoms + tj + tf, then waits for xG)
        xferService.initiateTransfer(ctx, pacer, TINY_FILENAME, TINY_FILE_DATA, session, registry);

        LoggerUtil.info(String.format(
            "[%s] MVP file transfer initiated (awaiting xG): %s (%d bytes)",
            username, TINY_FILENAME, TINY_FILE_DATA.length));
    }
}
