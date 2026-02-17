/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.news;

import com.dialtone.fdo.FdoProcessor;
import com.dialtone.fdo.FdoStreamExtractor;
import com.dialtone.fdo.dsl.builders.NewsStoryFdoBuilder;
import com.dialtone.protocol.Pacer;
import com.dialtone.protocol.SessionContext;
import com.dialtone.protocol.core.TokenHandler;
import com.dialtone.ai.UnifiedNewsService;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Handles news-related tokens: NX (news story request).
 */
public class NewsTokenHandler implements TokenHandler {
    private final SessionContext session;
    private final Pacer pacer;
    private final FdoProcessor fdoProcessor;
    private final UnifiedNewsService unifiedNewsService;
    private final String logPrefix;

    public NewsTokenHandler(SessionContext session, Pacer pacer, FdoProcessor fdoProcessor,
                           UnifiedNewsService unifiedNewsService) {
        this.session = session;
        this.pacer = pacer;
        this.fdoProcessor = fdoProcessor;
        this.unifiedNewsService = unifiedNewsService;
        this.logPrefix = "[" + (session.getDisplayName() != null ? session.getDisplayName() : "unknown") + "] ";
    }

    @Override
    public boolean canHandle(String token) {
        return "NX".equals(token);
    }

    @Override
    public void handle(ChannelHandlerContext ctx, byte[] frame, SessionContext session) throws Exception {
        handleNewsStoryRequest(ctx, frame);
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
     * Handle NX token request for full news story display.
     */
    public void handleNewsStoryRequest(ChannelHandlerContext ctx, byte[] nxFrame) {
        try {
            String identifier = extractNxIdentifier(nxFrame);
            int categoryId = Integer.parseInt(identifier);

            String windowTitle;
            String headline;
            String fullReport;

            switch (categoryId) {
                case 1: // News
                    com.dialtone.ai.UnifiedNewsContent news = unifiedNewsService.getLatestContent(UnifiedNewsService.NewsCategory.GENERAL);
                    windowTitle = "News Update";
                    headline = news != null ? news.getTeaserHeadline() : "News";
                    fullReport = news != null ? news.getReport() : "News content is currently unavailable. Please try again later.";
                    break;

                case 2: // Sports
                    com.dialtone.ai.UnifiedNewsContent sports = unifiedNewsService.getLatestContent(UnifiedNewsService.NewsCategory.SPORTS);
                    windowTitle = "Sports Update";
                    headline = sports != null ? sports.getTeaserHeadline() : "Sports";
                    fullReport = sports != null ? sports.getReport() : "Sports content is currently unavailable. Please try again later.";
                    break;

                case 3: // Financial
                    com.dialtone.ai.UnifiedNewsContent financial = unifiedNewsService.getLatestContent(UnifiedNewsService.NewsCategory.CRYPTO);
                    windowTitle = "Financial Update";
                    headline = financial != null ? financial.getHeadline() : "Financial News";
                    fullReport = financial != null ? financial.getReport() : "Financial news is currently unavailable. Please try again later.";
                    break;

                case 4: // Entertainment
                    com.dialtone.ai.UnifiedNewsContent entertainment = unifiedNewsService.getLatestContent(UnifiedNewsService.NewsCategory.ENTERTAINMENT);
                    windowTitle = "Entertainment Update";
                    headline = entertainment != null ? entertainment.getTeaserHeadline() : "Entertainment";
                    fullReport = entertainment != null ? entertainment.getReport() : "Entertainment news is currently unavailable. Please try again later.";
                    break;

                case 5: // Tech
                    com.dialtone.ai.UnifiedNewsContent tech = unifiedNewsService.getLatestContent(UnifiedNewsService.NewsCategory.TECH);
                    windowTitle = "Tech Update";
                    headline = tech != null ? tech.getTeaserHeadline() : "Tech News";
                    fullReport = tech != null ? tech.getReport() : "Tech news is currently unavailable. Please try again later.";
                    break;

                default:
                    LoggerUtil.warn(logPrefix + "Unknown NX category identifier: " + categoryId);
                    windowTitle = "Story";
                    headline = "Unknown Category";
                    fullReport = "The requested content category is not recognized.";
                    break;
            }

            LoggerUtil.info(logPrefix + String.format("NX request for category %d (%s)", categoryId, windowTitle));

            // Format today's date and build content
            String todaysDate = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE MM/dd/yy"));
            String contentHtml = headline + "\r\r" + fullReport;

            fdoProcessor.compileAndSend(ctx,
                    NewsStoryFdoBuilder.create(windowTitle, todaysDate, contentHtml),
                    session, "AT", -1, "NEWS_STORY");

        } catch (Exception ex) {
            LoggerUtil.error(logPrefix + "Failed to handle NX news story request: " + ex.getMessage());
            // Send a control ACK to prevent client from hanging
            try {
                byte[] resp = buildShortControl(0x24);
                pacer.enqueuePrioritySafe(ctx, resp, "NX_ERROR_ACK");
            } catch (Exception ackEx) {
                LoggerUtil.error(logPrefix + "Failed to send NX error ACK: " + ackEx.getMessage());
            }
        }
    }

    /**
     * Extract numeric identifier from NX frame using native FdoStream API.
     */
    private String extractNxIdentifier(byte[] nxFrame) throws Exception {
        String identifier = FdoStreamExtractor.extractNxIdentifier(nxFrame);
        if (identifier == null) {
            throw new IllegalArgumentException("No identifier found in NX frame");
        }
        LoggerUtil.info(logPrefix + "Extracted NX identifier: '" + identifier + "'");
        return identifier;
    }

    /**
     * Build short control frame.
     */
    private byte[] buildShortControl(int type) {
        byte[] b = new byte[com.dialtone.aol.core.ProtocolConstants.SHORT_FRAME_SIZE];
        b[0] = (byte) com.dialtone.aol.core.ProtocolConstants.AOL_FRAME_MAGIC;
        b[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_HI] = 0x00;
        b[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_LO] = 0x03;
        b[com.dialtone.aol.core.ProtocolConstants.IDX_TYPE] = (byte) (type & 0xFF);
        b[com.dialtone.aol.core.ProtocolConstants.IDX_TOKEN] = 0x0D;
        return b;
    }
}
