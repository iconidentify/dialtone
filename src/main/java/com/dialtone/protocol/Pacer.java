/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.state.SequenceManager;
import com.dialtone.utils.LoggerUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Paces outbound frames, enforces the 0x10 (16) DATA in-flight window, and handles heartbeats.
 *
 * Key points:
 *  - drainsDeferred prevents writes during a read batch.
 *  - drainLimited() supports post-ACK micro-bursts.
 *  - onA4WindowOpenNoDrain() cancels heartbeat and avoids immediate drain.
 *  - Single instance reused for the whole connection; queues can be cleared via clearPending().
 */
public class Pacer {

	private static final class FrameChunk {
		final ByteBuf buffer;
		final String label;
		FrameChunk(ByteBuf buffer, String label) {
			this.buffer = buffer;
			this.label = label;
		}
	}

	private static final int HEARTBEAT_TIMEOUT_SECONDS = 12;
	private static final int HEARTBEAT_MAX_ATTEMPTS = 10;

	private final Deque<FrameChunk> pending = new ArrayDeque<>();
	private final AtomicBoolean needAck = new AtomicBoolean(false);
	private final AtomicBoolean needResume = new AtomicBoolean(false);
	private final SequenceManager sequenceManager;
	private final boolean verbose;
	private final java.util.function.Consumer<byte[]> outboundHook;
	private final String username;

	private ScheduledFuture<?> heartbeatFuture;
	private int heartbeatAttempts = 0;

	// Prevent writes while the handler is in channelRead
	private volatile boolean drainsDeferred = false;

	// Configurable delay between DATA frames (0 = disabled)
	// Helps slow clients (e.g., Mac) keep up with fast native FDO compiler
	// Default 5ms to prevent P3 window violations on Mac client during login burst
	private int interFrameDelayMs = 5;

	public Pacer(SequenceManager sequenceManager, boolean isServer) {
		this(sequenceManager, isServer, false, null, null);
	}

	public Pacer(SequenceManager sequenceManager, boolean isServer, boolean verbose) {
		this(sequenceManager, isServer, verbose, null, null);
	}

	public Pacer(SequenceManager sequenceManager, boolean isServer, boolean verbose,
				 java.util.function.Consumer<byte[]> outboundHook,
				 String username) {
		this.sequenceManager = sequenceManager;
		this.verbose = verbose;
		this.outboundHook = outboundHook;
		this.username = username;
	}

	private String logPrefix() {
		return username != null ? "[" + username + "][Pacer] " : "[Pacer] ";
	}

	// ======== 9B helpers used by handler ========
	public void onA4WindowOpenNoDrain() {
		LoggerUtil.debug(logPrefix() + "Short ACK (0xA4) received; NOT draining yet (deferred). Pending: " + pending.size());
		cancelHeartbeat();
		heartbeatAttempts = 0;
		needAck.set(false);
	}

	public void onA5KeepAliveNoDrain() {
		LoggerUtil.debug(logPrefix() + "Keepalive (0xA5) received; no drain.");
	}

	// ======== Queueing ========
	/** FIFO enqueue. Caller must release their original buffer; pacer retains a copy. */
	public void enqueue(ByteBuf frame, String label) {
		pending.add(new FrameChunk(frame.retain(), label));
	}

	/** Priority enqueue at head. */
	public void enqueuePriority(ByteBuf frame, String label) {
		pending.addFirst(new FrameChunk(frame.retain(), label));
	}

	/**
	 * Safely enqueue a frame with automatic buffer management.
	 * Creates buffer, enqueues with retain(), and releases caller's reference.
	 *
	 * @param ctx channel context for buffer allocation
	 * @param data frame data to send
	 * @param label debug label
	 */
	public void enqueueSafe(ChannelHandlerContext ctx, byte[] data, String label) {
		ByteBuf buf = ctx.alloc().buffer(data.length).writeBytes(data);
		try {
			enqueue(buf, label);
		} finally {
			buf.release();
		}
	}

	/**
	 * Safely enqueue a priority frame with automatic buffer management.
	 * Creates buffer, enqueues with retain(), and releases caller's reference.
	 *
	 * @param ctx channel context for buffer allocation
	 * @param data frame data to send
	 * @param label debug label
	 */
	public void enqueuePrioritySafe(ChannelHandlerContext ctx, byte[] data, String label) {
		ByteBuf buf = ctx.alloc().buffer(data.length).writeBytes(data);
		try {
			enqueuePriority(buf, label);
		} finally {
			buf.release();
		}
	}

	/**
	 * Send a control frame immediately without queueing.
	 * This bypasses the sliding window and deferred drain logic.
	 * Use ONLY for critical control frames (f2, ff, 9B ACKs) that must be sent
	 * even when the sliding window is full.
	 *
	 * @param ctx channel context
	 * @param data control frame data (will be stamped before sending)
	 * @param label debug label
	 * @return true if sent successfully, false if channel not writable/active
	 */
	public boolean sendControlFrameImmediately(ChannelHandlerContext ctx, byte[] data, String label) {
		// Check channel health
		if (!ctx.channel().isActive()) {
			LoggerUtil.error(logPrefix() + "Cannot send control frame - channel INACTIVE | label=" + label);
			return false;
		}

		if (!ctx.channel().isWritable()) {
			LoggerUtil.warn(logPrefix() + "Cannot send control frame - channel NOT writable | label=" + label);
			return false;
		}

		// Apply sequence stamping for control frames
		byte[] stamped = sequenceManager.restampNonDataHeader(data);

		// Log the send
		if (verbose) {
			int tx = stamped[com.dialtone.aol.core.ProtocolConstants.IDX_TX] & 0xFF;
			LoggerUtil.debug(() -> String.format(
				logPrefix() + "Sending control frame IMMEDIATELY | label=%s | tx=0x%02X",
				label, tx));
		}

		// Send immediately
		ByteBuf buf = ctx.alloc().buffer().writeBytes(stamped);
		ChannelFuture future = ctx.writeAndFlush(buf);

		// Track completion
		future.addListener(f -> {
			if (!f.isSuccess()) {
				LoggerUtil.error(logPrefix() + "Immediate control frame FAILED | label=" + label +
					" | cause=" + f.cause().getMessage());
			} else if (verbose) {
				LoggerUtil.debug(() -> logPrefix() + "Immediate control frame sent | label=" + label);
			}
		});

		if (outboundHook != null) outboundHook.accept(stamped);

		return true;
	}

	/** Drop all queued frames (used when switching sequences) without destroying the pacer. */
	public void clearPending() {
		while (!pending.isEmpty()) {
			FrameChunk fc = pending.poll();
			try {
				if (fc != null && fc.buffer != null) fc.buffer.release();
			} catch (Throwable t) {
				LoggerUtil.warn(logPrefix() + "Failed to release buffer during clearPending: " + t.getMessage());
			}
		}
		needAck.set(false);
		needResume.set(false);
		cancelHeartbeat();
		heartbeatAttempts = 0;
	}

	/** Resume after channel writability returns. */
	public void resume(ChannelHandlerContext ctx) {
		if (needResume.compareAndSet(true, false)) {
			drain(ctx);
		}
	}

	public void setDrainsDeferred(boolean deferred) {
		this.drainsDeferred = deferred;
	}

	public boolean isDrainsDeferred() { return drainsDeferred; }

	/**
	 * Set inter-frame delay in milliseconds.
	 * When > 0, introduces a delay between each DATA frame to help slow clients keep up.
	 * This is useful when the native FDO compiler generates frames faster than clients can process.
	 *
	 * @param delayMs delay in milliseconds (0 = disabled)
	 */
	public void setInterFrameDelayMs(int delayMs) {
		this.interFrameDelayMs = Math.max(0, delayMs);
		if (this.interFrameDelayMs > 0) {
			LoggerUtil.info(logPrefix() + "Inter-frame delay enabled: " + this.interFrameDelayMs + "ms");
		}
	}

	public int getInterFrameDelayMs() {
		return interFrameDelayMs;
	}

	// ======== Draining ========
	/** Full drain (until window/backpressure stops us). */
	public void drain(ChannelHandlerContext ctx) {
		drainInternal(ctx, Integer.MAX_VALUE);
	}

	/** Drain up to at most maxDataFrames DATA frames (micro-burst). */
	public void drainLimited(ChannelHandlerContext ctx, int maxDataFrames) {
		drainInternal(ctx, Math.max(0, maxDataFrames));
	}

	private void drainInternal(ChannelHandlerContext ctx, int maxDataFrames) {
		if (isDrainsDeferred()) {
			LoggerUtil.debug(logPrefix() + "Drain DEFERRED - frames NOT sent | pending=" + pending.size() + " | reason=drainsDeferred");
			return;
		}
		if (pending.isEmpty()) return;

		// CRITICAL FIX: Check window BEFORE starting drain, not just per-frame.
		// Without this, multiple drainLimited() calls could each send 8+ frames,
		// violating the 16-frame window and crashing Mac clients.
		int preCheckOutstanding = sequenceManager.getOutstandingWindowFill();
		if (preCheckOutstanding >= 8) {
			// Already at or beyond throttle threshold - don't send anything
			LoggerUtil.debug(logPrefix() + String.format(
				"Drain BLOCKED - already at throttle threshold | outstanding=%d/16 | pending=%d",
				preCheckOutstanding, pending.size()));
			if (!needAck.get()) {
				needAck.set(true);
				scheduleHeartbeatIfNeeded(ctx);
			}
			return;
		}

		// Limit maxDataFrames to not exceed throttle threshold (8)
		// This prevents a single drain from sending more frames than the window allows
		int availableBeforeThrottle = 8 - preCheckOutstanding;
		int effectiveMaxFrames = Math.min(maxDataFrames, availableBeforeThrottle);
		if (effectiveMaxFrames <= 0) {
			LoggerUtil.debug(logPrefix() + String.format(
				"Drain BLOCKED - no room before throttle | outstanding=%d | maxRequested=%d",
				preCheckOutstanding, maxDataFrames));
			return;
		}

		// CRITICAL: Check channel health before attempting to send
		boolean channelActive = ctx.channel().isActive();
		boolean channelWritable = ctx.channel().isWritable();

		if (!channelActive) {
			LoggerUtil.error(logPrefix() + "Channel INACTIVE - cannot send | pending=" + pending.size());
			return;
		}

		if (!channelWritable) {
			long bytesBeforeWritable = ctx.channel().bytesBeforeWritable();
			LoggerUtil.warn(logPrefix() + "Channel NOT writable - backpressure detected | pending=" + pending.size() + " | needToFlush=" + bytesBeforeWritable + " bytes");
			return;
		}

		if (verbose) {
			int pendingCount = pending.size();
			int finalEffectiveMax = effectiveMaxFrames;
			LoggerUtil.debug(() -> logPrefix() + "Starting drain | pending=" + pendingCount +
				" | maxDataFrames=" + maxDataFrames + " | effectiveMax=" + finalEffectiveMax);
		}

		int framesSent = 0;     // DATA frames only
		int burstBytes = 0;
		boolean hitWindowLimit = false;

		while (!pending.isEmpty() && ctx.channel().isWritable()) {
			// Enforce batch limit using effectiveMaxFrames (already capped by throttle threshold)
			if (framesSent >= effectiveMaxFrames) {
				LoggerUtil.debug(String.format(
					logPrefix() + "Batch limit reached: %d/%d DATA frames sent (capped by throttle), pausing drain. Remaining pending: %d",
					framesSent, effectiveMaxFrames, pending.size()));
				hitWindowLimit = true;  // Trigger ACK wait so remaining frames get sent after ACK
				break;
			}

			FrameChunk chunk = pending.peek();
			int sz = chunk.buffer.readableBytes();

			// Peek type
			byte[] preview = new byte[Math.min(sz, 12)];
			chunk.buffer.getBytes(0, preview);
			boolean isAol = (preview.length >= 8 && preview[0] == 0x5A);
			int type = isAol ? (preview[com.dialtone.aol.core.ProtocolConstants.IDX_TYPE] & 0xFF) : -1;
			boolean isData = isAol && type == com.dialtone.protocol.PacketType.DATA.getValue();

			// Window slots available?
			int outstanding = sequenceManager.getOutstandingWindowFill();
			if (outstanding < 0) {
				// Negative means wraparound calculation error or race condition
				LoggerUtil.warn(logPrefix() + "Negative outstanding=" + outstanding + ", treating as 0");
				outstanding = 0;
			}

			// CRITICAL: Check for window violation (outstanding > 16)
			if (isData && outstanding > 0x10) {
				LoggerUtil.error(String.format(
					logPrefix() + "[CIRCUIT BREAKER] P3 WINDOW VIOLATED: outstanding=%d exceeds max 16! " +
					"This indicates a protocol bug that can crash clients. Blocking further sends.",
					outstanding));
				// Don't close connection yet - just stop sending until ACKs arrive
				hitWindowLimit = true;
				break;
			}

			int availableSlots = 0x10 - outstanding;

			// Throttle at 50% capacity (8/16) to give client time to send ACKs
			// This prevents hitting the hard limit during high-throughput bursts
			// Lowered from 75% (12/16) to fix Mac client crashes during login burst
			if (isData && outstanding >= 8 && availableSlots <= 8) {
				LoggerUtil.debug(String.format(
					logPrefix() + "Throttling at %d/16 (50%% capacity) to allow client ACK time | pending=%d",
					outstanding, pending.size()));
				hitWindowLimit = true;
				break;
			}

			if (isData && availableSlots <= 0) { hitWindowLimit = true; break; }

			byte[] bytes = new byte[sz];
			chunk.buffer.getBytes(0, bytes);

			if (isData && sz >= com.dialtone.aol.core.ProtocolConstants.MIN_FULL_FRAME_SIZE) {
				// Restamp using the latest client TX/RX known to the sequence manager
				bytes = sequenceManager.restamp(bytes, true);

				// Defensive: fix declared length & CRC if needed
				int declaredLen = ((bytes[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_HI] & 0xFF) << 8)
						|  (bytes[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_LO] & 0xFF);
				int expectedLen = bytes.length - 6;
				if (declaredLen != expectedLen) {
					bytes[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_HI] = (byte)((expectedLen >>> 8) & 0xFF);
					bytes[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_LO] = (byte)(expectedLen & 0xFF);
					int crc = com.dialtone.aol.core.Crc16Ibm.compute(
							bytes,
							com.dialtone.aol.core.ProtocolConstants.IDX_LEN_HI,
							bytes.length - (com.dialtone.aol.core.ProtocolConstants.IDX_MAGIC + 4));
					bytes[com.dialtone.aol.core.ProtocolConstants.IDX_CRC_HI] = (byte)((crc >>> 8) & 0xFF);
					bytes[com.dialtone.aol.core.ProtocolConstants.IDX_CRC_LO] = (byte)(crc & 0xFF);
				}

				if (verbose) {
					int tx = bytes[com.dialtone.aol.core.ProtocolConstants.IDX_TX] & 0xFF;
					int rx = bytes[com.dialtone.aol.core.ProtocolConstants.IDX_RX] & 0xFF;
					String tok = com.dialtone.aol.core.FrameCodec.extractTokenAscii(bytes);
					int finalOutstanding = outstanding;
					LoggerUtil.debug(() -> String.format(
							logPrefix() + "DATA: token=%s tx=0x%02X rx=0x%02X outstanding=%d",
							tok, tx, rx, finalOutstanding));
				}
			} else if (isAol && !isData) {
				// Log TX state before/after control frame stamping for debugging.
				if (verbose) {
					LoggerUtil.debug(() -> String.format(
						logPrefix() + "BEFORE restamp control: lastDataTx=0x%02X lastAckedServerTx=0x%02X",
						sequenceManager.getLastDataTx() & 0xFF,
						sequenceManager.getLastAckedServerTx() & 0xFF));
				}

				// Restamp header for BOTH short and full-length control frames (e.g., AT)
				bytes = sequenceManager.restampNonDataHeader(bytes);

				int stampedControlTx = bytes[com.dialtone.aol.core.ProtocolConstants.IDX_TX] & 0xFF;
				int expectedTx = sequenceManager.getLastDataTx() & 0xFF;
				if (stampedControlTx != expectedTx) {
					LoggerUtil.error(String.format(
						logPrefix() + "[PACER TX CORRUPTION] Control frame TX MISMATCH after restamp! " +
						"stamped=0x%02X expected=0x%02X (lastDataTx)",
						stampedControlTx, expectedTx));
				} else if (verbose) {
					LoggerUtil.debug(() -> String.format(
						logPrefix() + "AFTER restamp control: tx=0x%02X (correct, matches lastDataTx)",
						stampedControlTx));
				}
			}

			ByteBuf buf = ctx.alloc().buffer().writeBytes(bytes);
			ChannelFuture future = ctx.writeAndFlush(buf);

			// Track write completion - catches silent failures
			String label = chunk.label;
			future.addListener(f -> {
				if (!f.isSuccess()) {
					LoggerUtil.error(logPrefix() + "Write FAILED | label=" + label + " | cause=" + f.cause().getMessage());
				} else if (verbose) {
					LoggerUtil.debug(() -> logPrefix() + "Write completed | label=" + label);
				}
			});

			if (outboundHook != null) outboundHook.accept(bytes);

			if (isData) {
				// The restamp() method updates lastDataTx and txLinearized directly during stamping.
				// This eliminates the read-back pattern that could propagate TX corruption.
				if (verbose) sequenceManager.logSenderWindowState("after sending DATA");
				framesSent++;

				// Per-frame window recalculation catches mid-burst violations.
				// This handles edge cases where window calculation changes between pre-send check and actual send.
				// NOTE: We only check for hard violations (>16), not throttle (>=12), to avoid false positives.
				int postSendOutstanding = sequenceManager.getOutstandingWindowFill();
				if (postSendOutstanding > 0x10) {
					LoggerUtil.error(String.format(
						logPrefix() + "[POST-SEND VIOLATION] Window exceeded after DATA send: outstanding=%d (max 16) | " +
						"This indicates a race condition or calculation bug.",
						postSendOutstanding));
					hitWindowLimit = true;
					break;
				}
			}

			pending.poll();
			burstBytes += sz;
			chunk.buffer.release();

			// Apply inter-frame delay if configured (helps slow clients keep up)
			if (interFrameDelayMs > 0 && isData && !pending.isEmpty()) {
				try {
					Thread.sleep(interFrameDelayMs);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					LoggerUtil.warn(logPrefix() + "Inter-frame delay interrupted");
					break;
				}
			}
		}

		int outstandingNow = Math.max(0, sequenceManager.getOutstandingWindowFill());

		LoggerUtil.debug(String.format(
			logPrefix() + "Drain complete | dataFrames=%d | totalBytes=%d | remaining=%d | outstanding=%d/16 | channelWritable=%s",
			framesSent, burstBytes, pending.size(), outstandingNow, ctx.channel().isWritable()));

		// Check for window violation after drain
		if (outstandingNow > 0x10) {
			LoggerUtil.error(String.format(
				logPrefix() + "[WINDOW VIOLATION] Post-drain outstanding=%d exceeds max 16! " +
				"Sent %d frames but window was already at limit. This is a bug.",
				outstandingNow, framesSent));
		}

		if ((hitWindowLimit || (outstandingNow >= 0x10 && !pending.isEmpty()))) {
			needAck.set(true);
			LoggerUtil.debug(logPrefix() + "Window limit reached (" + outstandingNow + "/16 DATA outstanding). Waiting for ACK");
			scheduleHeartbeatIfNeeded(ctx);
		} else if (!pending.isEmpty() && !ctx.channel().isWritable()) {
			needResume.set(true);
			LoggerUtil.debug(logPrefix() + "Backpressure detected, will resume when writable");
		}
	}

	public boolean isComplete() {
		return pending.isEmpty() && !needAck.get();
	}

	/** Are we throttled waiting for client ACK? */
	public boolean isWaitingForAck() { return needAck.get(); }

	/** True if there are unsent frames in the queue. */
	public boolean hasPending() {
		return !pending.isEmpty();
	}

	/** Get pending queue size for logging/diagnostics. */
	public int getPendingCount() {
		return pending.size();
	}

	/** Piggyback ACK inside a full frame freed slots. */
	public void onPiggybackAck(ChannelHandlerContext ctx, int freed) {
		if (!needAck.get()) return;
		needAck.set(false);
		cancelHeartbeat();
		heartbeatAttempts = 0;
		LoggerUtil.debug(logPrefix() + "ACK via piggyback: freed=" + freed + ", resuming");
		drain(ctx);
	}

	// ======== Heartbeat ========
	private void scheduleHeartbeatIfNeeded(ChannelHandlerContext ctx) {
		if (!needAck.get()) return;
		if (heartbeatFuture != null && !heartbeatFuture.isDone()) return;
		if (heartbeatAttempts >= HEARTBEAT_MAX_ATTEMPTS) return;

		heartbeatFuture = ctx.executor().schedule(() -> {
			if (!needAck.get()) return;
			byte[] hb = buildHeartbeat();
			if (hb != null) {
				enqueuePrioritySafe(ctx, hb, "HEARTBEAT");
				drain(ctx);
				heartbeatAttempts++;
				if (needAck.get() && heartbeatAttempts < HEARTBEAT_MAX_ATTEMPTS) {
					scheduleHeartbeatIfNeeded(ctx);
				}
			}
		}, HEARTBEAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private void cancelHeartbeat() {
		if (heartbeatFuture != null) {
			heartbeatFuture.cancel(false);
			heartbeatFuture = null;
		}
	}

	private byte[] buildHeartbeat() {
		try {
			byte[] b = new byte[com.dialtone.aol.core.ProtocolConstants.SHORT_FRAME_SIZE];
			b[0] = (byte) com.dialtone.aol.core.ProtocolConstants.MAGIC;
			b[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_HI] = 0x00;
			b[com.dialtone.aol.core.ProtocolConstants.IDX_LEN_LO] = 0x03;
			b[com.dialtone.aol.core.ProtocolConstants.IDX_TYPE] =
					(byte) com.dialtone.protocol.PacketType.HEARTBEAT.getValue();
			b[com.dialtone.aol.core.ProtocolConstants.IDX_TOKEN] = 0x0D;
			return b;
		} catch (Throwable t) {
			return null;
		}
	}

	// ======== Cleanup ========
	public void close() {
		clearPending();
	}
}