/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol.core;

import com.dialtone.aol.core.Hex;
import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.protocol.PacketParser;
import com.dialtone.protocol.PacketType;
import com.dialtone.protocol.Pacer;
import com.dialtone.state.SequenceManager;
import com.dialtone.utils.LoggerUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

/**
 * Utility class for building control frames (short control, 9B frames, etc.).
 */
public class ControlFrameBuilder {
    private final String logPrefix;
    private final boolean verbose;
    private final SequenceManager sequenceManager;
    private final Pacer pacer;

    public ControlFrameBuilder(String logPrefix, boolean verbose, SequenceManager sequenceManager, Pacer pacer) {
        this.logPrefix = logPrefix;
        this.verbose = verbose;
        this.sequenceManager = sequenceManager;
        this.pacer = pacer;
    }

    /**
     * Build short control frame.
     */
    public byte[] buildShortControl(int type) {
        byte[] b = new byte[ProtocolConstants.SHORT_FRAME_SIZE];
        b[0] = (byte) ProtocolConstants.AOL_FRAME_MAGIC;
        b[ProtocolConstants.IDX_LEN_HI] = 0x00;
        b[ProtocolConstants.IDX_LEN_LO] = 0x03;
        b[ProtocolConstants.IDX_TYPE] = (byte) (type & 0xFF);
        b[ProtocolConstants.IDX_TOKEN] = 0x0D;
        return b;
    }

    /**
     * Send exact 5A frame from hex template (for init handshakes).
     */
    public void sendFiveAExact(ChannelHandlerContext ctx, String hexTemplate) {
        byte[] out = Hex.hexToBytes(hexTemplate);
        ctx.writeAndFlush(Unpooled.wrappedBuffer(out));
    }

    /**
     * Handle short control 9B frames (ACKs, heartbeats, etc.).
     */
    public boolean handleShortControl9B(ChannelHandlerContext ctx, byte[] in, AckWindowManager ackManager) {
        if (!PacketParser.isFiveA(in) || in.length != ProtocolConstants.SHORT_FRAME_SIZE) return false;

        int type = in[ProtocolConstants.IDX_TYPE] & ProtocolConstants.BYTE_MASK;

        if (PacketType.isAolAcknowledgmentType(type, PacketType.A4)) {
            if (verbose) {
                LoggerUtil.debug(logPrefix + String.format(
                        "Short control 0x%02X at tx=0x%02X rx=0x%02X piggyFreed=%s",
                        type, sequenceManager.getLastDataTx(), sequenceManager.getLastClientTxSeq(),
                        ackManager.isWindowOpenedByPiggyback()));
            }
            pacer.onA4WindowOpenNoDrain();
            ackManager.setWindowOpenedByPiggyback(false);
            return true;
        }

        if (PacketType.isAolAcknowledgmentType(type, PacketType.A5)) {
            pacer.onA5KeepAliveNoDrain();
            return true;
        }

        if (type == 0xA6) {
            // Heartbeat - respond with ACK
            LoggerUtil.info(logPrefix + "Ping received (0xA6) - queuing ACK (0x24) for drain");
            byte[] pong = buildShortControl(0x24);
            pacer.enqueuePrioritySafe(ctx, pong, "PING_PONG");
            if (verbose) {
                int pendingAfter = pacer.getPendingCount();
                LoggerUtil.debug(() -> logPrefix + "Pong queued | pendingAfter=" + pendingAfter + " | drainsDeferred=" + pacer.isDrainsDeferred());
            }
            return true;
        }

        PacketType responseType = PacketType.getAcknowledgmentResponseType(type);
        if (responseType != null) {
            byte[] responseFrame = buildShortControl(responseType.getValue());
            pacer.enqueuePrioritySafe(ctx, responseFrame, "9B_ECHO");
        }
        return true;
    }

    /**
     * Send error acknowledgment frame.
     */
    public void sendErrorAck(ChannelHandlerContext ctx, String reason) {
        byte[] ack = buildShortControl(0x24);
        pacer.enqueuePrioritySafe(ctx, ack, reason);
    }
}
