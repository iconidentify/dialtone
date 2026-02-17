/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.core;

import com.dialtone.protocol.Pacer;
import com.dialtone.state.SequenceManager;
import com.dialtone.utils.LoggerUtil;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages ACK handling, window state tracking, and post-ACK drain scheduling.
 * Extracted from StatefulClientHandler to separate protocol core concerns.
 */
public class AckWindowManager {
    private final SequenceManager sequenceManager;
    private final Pacer pacer;
    private final boolean verbose;
    private final String logPrefix;

    private boolean windowOpenedByPiggyback = false;
    private ScheduledFuture<?> postAckDrainFuture;
    private volatile boolean deferredDrainArmed = false;

    public AckWindowManager(SequenceManager sequenceManager, Pacer pacer, boolean verbose, String logPrefix) {
        this.sequenceManager = sequenceManager;
        this.pacer = pacer;
        this.verbose = verbose;
        this.logPrefix = logPrefix;
    }

    /**
     * Handle ACK-related pacer hints based on window state changes.
     */
    public void handleAckRelatedPacerHints(ChannelHandlerContext ctx, byte[] in, int beforeOutstanding, int afterOutstanding) {
        if (!pacer.isWaitingForAck()) return;
        if (afterOutstanding < beforeOutstanding) {
            pacer.onPiggybackAck(ctx, beforeOutstanding - afterOutstanding);
        } else if (afterOutstanding < 0x10 && afterOutstanding != beforeOutstanding) {
            if (verbose)
                LoggerUtil.info(logPrefix + "Window opened by inbound RX (" + beforeOutstanding + "->" + afterOutstanding + ") – resuming pacer");
            pacer.resume(ctx);
        }
    }

    /**
     * Log window state changes if significant.
     */
    public void maybeLogWindowChange(byte[] in, int beforeOutstanding, int afterOutstanding) {
        if (afterOutstanding != beforeOutstanding) {
            LoggerUtil.debug(logPrefix + String.format(
                    "RX ack: outstanding %d -> %d (type=0x%02X token=%s)",
                    beforeOutstanding, afterOutstanding,
                    in.length > 7 ? (in[7] & 0xFF) : 0,
                    com.dialtone.aol.core.FrameCodec.extractTokenAscii(in)));
            if (pacer.isWaitingForAck() && afterOutstanding >= 0x10 &&
                    containsAscii(in, "SC") && sequenceManager.getOutstandingWindowFill() >= 0x10) {
                LoggerUtil.info(logPrefix + "Priority waiting but window full at 16/16 – immediate heartbeat sent");
            }
        }
    }

    /**
     * Check if frame contains ASCII string.
     */
    private boolean containsAscii(byte[] in, String search) {
        if (in == null || in.length < search.length()) return false;
        String frameStr = new String(in, java.nio.charset.StandardCharsets.US_ASCII);
        return frameStr.contains(search);
    }

    /**
     * Called when outbound frame is sent with piggyback ACK.
     */
    public void onOutboundSentPiggybackingAck() {
        if (verbose) sequenceManager.logReceiverWindowState("after sending ACK");
    }

    /**
     * Cancel any scheduled post-ACK drain.
     */
    public void cancelPostAckDrain() {
        if (postAckDrainFuture != null) {
            postAckDrainFuture.cancel(false);
            postAckDrainFuture = null;
        }
        deferredDrainArmed = false;
    }

    /**
     * Get current window opened by piggyback state.
     */
    public boolean isWindowOpenedByPiggyback() {
        return windowOpenedByPiggyback;
    }

    /**
     * Set window opened by piggyback state.
     */
    public void setWindowOpenedByPiggyback(boolean value) {
        this.windowOpenedByPiggyback = value;
    }
}
