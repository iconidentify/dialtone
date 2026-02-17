/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.state;

import com.dialtone.aol.core.FrameCodec;
import com.dialtone.aol.core.ProtocolConstants;
import com.dialtone.utils.LoggerUtil;

import java.util.Arrays;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages sequence numbers and packet restamping for AOL3 protocol.
 * Extracted from StatefulClientHandler to improve code organization.
 */
public class SequenceManager {

	// ======== Sequence State ========
	// Server TX (next DATA to send), last DATA TX actually sent, and last client TX observed
	// CRITICAL: volatile ensures memory visibility between ACK update thread and drain thread
	private volatile int serverTx = 0x10;     // Start at 0x10 instead of 0x11
	private volatile int lastDataTx = 0x10;
	private volatile int lastClientTxSeq = 0x10;

	// Highest server TX acknowledged by the client (from inbound frames' RX)
	private volatile int lastAckedServerTx = 0x10;
	private boolean serverTxInitialized = false;

	// Linearized counters for wraparound-safe window calculations
	// These monotonically increase (no wrap), eliminating modular arithmetic bugs
	private final java.util.concurrent.atomic.AtomicInteger txLinearized = new java.util.concurrent.atomic.AtomicInteger(0);
	private final java.util.concurrent.atomic.AtomicInteger rxLinearized = new java.util.concurrent.atomic.AtomicInteger(0);

	// Telemetry: track peak in-flight and violation counts
	private final java.util.concurrent.atomic.AtomicInteger peakInflight = new java.util.concurrent.atomic.AtomicInteger(0);
	private final java.util.concurrent.atomic.AtomicInteger violationCount = new java.util.concurrent.atomic.AtomicInteger(0);

	// Strict start mode: conservative sequencing for the first N DATA frames
	private boolean strictStartActive = false;
	private int strictStartFramesRemaining = 0;

	// Startup seeding fields from A3 / 0x0C03
	private int startupClientTx = -1;
	private boolean haveSentFirstData = false;
	private int nextServerDataTx = -1;
	private boolean startupSeededOnce = false;

	// ACK debouncing to prevent ping-pong loops
	private int lastAckedByClientTx = -1;
	private int lastAckedByClientRx = -1;

	// ======== Stall detection (window full for too long) ========
	private static final long STALL_TIMEOUT_MS = 5000L; // 5 seconds
	private long stallStartTime = 0L;
	private ScheduledFuture<?> stallCheckTask;
	private java.util.concurrent.ScheduledExecutorService stallExecutor;

	// ======== Public API ========

	/**
	 * Wraps sequence numbers to valid range: [0x10, 0x7F] (ring size 0x70).
	 */
	public int wrapTx(int tx) {
		if (tx < 0x10) tx = 0x10;
		if (tx > 0x7F) tx = 0x10 + ((tx - 0x10) % 0x70);
		return tx;
	}

	/**
	 * Updates client sequence numbers from incoming packet.
	 */
	public void updateClientSequence(byte[] in) {
		if (isFiveA(in) && in.length >= 6) {
			int prevLastSeen = lastClientTxSeq;
			lastClientTxSeq = in[5] & 0xFF;

			// Log receiver window state when receiving ALL frames (not just DATA)
			// This is critical for tracking non-DATA frames like NX (type 0xA0)
			int type = in.length >= 7 ? (in[7] & 0xFF) : -1;
			if (prevLastSeen != lastClientTxSeq) {
				String frameType = (type == com.dialtone.protocol.PacketType.DATA.getValue())
					? "DATA"
					: String.format("0x%02X", type);
				LoggerUtil.debug(String.format("[WINDOW] Receiver %s: lastSeen 0x%02X -> 0x%02X",
						frameType, prevLastSeen & 0xFF, lastClientTxSeq & 0xFF));
			}

			// Initialize server TX sequence ONLY on first client packet, before any DATA sent.
			// Prevents mid-session re-initialization that could corrupt serverTx from client's piggyback ACK.
			if (!serverTxInitialized && !haveSentFirstData) {
				int clientRx = in[6] & 0xFF;
				serverTx = wrapTx(clientRx + 1);
				serverTxInitialized = true;
				LoggerUtil.debug(String.format(
					"[SequenceManager] Initialized serverTx=0x%02X from first client packet (clientRx=0x%02X)",
					serverTx, clientRx));
			} else if (!serverTxInitialized && haveSentFirstData) {
				// Guard: Should never happen - log error if we somehow lost initialization flag mid-session
				LoggerUtil.error(String.format(
					"[SEQUENCE BUG] serverTxInitialized=false but haveSentFirstData=true! " +
					"This indicates a sequence state corruption. NOT re-initializing from clientRx=0x%02X | " +
					"current: serverTx=0x%02X lastDataTx=0x%02X",
					in[6] & 0xFF, serverTx & 0xFF, lastDataTx & 0xFF));
			}
		}
	}

	/**
	 * Updates the last-acked-by-client server TX based on inbound frame RX field.
	 * Should be called for every inbound 0x5A frame (client → server).
	 *
	 * NOW: Always updates (no seqAhead guard), increments rxLinearized on wrap.
	 */
	public void updateAckFromIncoming(byte[] in) {
		if (!isFiveA(in) || in.length < 7) return;
		int rx = in[6] & 0xFF;

		// Accept only valid P3 range 0x10..0x7F
		if (rx < 0x10 || rx > 0x7F) return;

		int prevLastAcked = lastAckedServerTx;

		// Update ACK value only when it advances (wrap or ahead).
		// This prevents backward movement while still handling wraparound correctly.
		if (rx != lastAckedServerTx) {
			// Detect wrap: 0x7F → 0x10 (or nearby values indicating wrap)
			boolean wrapDetected = false;
			boolean shouldUpdate = false;

			if (prevLastAcked == 0x7F && rx == 0x10) {
				wrapDetected = true;
				shouldUpdate = true;
				rxLinearized.addAndGet(0x70);  // Ring size
				LoggerUtil.debug(String.format(
					"[SequenceManager] RX wrapped 0x7F->0x10, rxLinearized now %d",
					rxLinearized.get()));
			} else if (seqAhead(rx, prevLastAcked)) {
				// Normal advancement - increment rxLinearized by distance
				shouldUpdate = true;
				int dist = (rx - prevLastAcked);
				if (dist < 0) dist += 0x70;
				rxLinearized.addAndGet(dist);
			}

			// Only update lastAckedServerTx if ACK actually advanced (prevents backward movement)
			if (shouldUpdate) {
				lastAckedServerTx = rx;

				// Log sender window state when ACK advances
				LoggerUtil.debug(String.format("[WINDOW] Sender ACK: lastAcked 0x%02X -> 0x%02X | rxLin=%d",
						prevLastAcked & 0xFF, lastAckedServerTx & 0xFF, rxLinearized.get()));
			}
		}

		// If strict-start is active and we've sent the first DATA, disable strict-start
		// once the client's RX has caught up to (or moved past) our last DATA TX.
		if (strictStartActive && haveSentFirstData) {
			int lastData = lastDataTx & 0xFF;
			if (rx == lastData || seqAhead(rx, lastData)) {
				strictStartActive = false;
				strictStartFramesRemaining = 0;
			}
		}
	}

	/** Returns the last server TX that the client has acknowledged. */
	public int getLastAckedServerTx() { return lastAckedServerTx; }

	/**
	 * Returns the number of outstanding DATA frames (sent but not yet acknowledged),
	 * now using HYBRID approach: wrapped calculation (primary), linearized (telemetry).
	 */
	public int getOutstandingWindowFill() {
		// Get raw sequence values
		int sent = lastDataTx & 0xFF;
		int ackd = lastAckedServerTx & 0xFF;

		// PRIMARY: Use traditional wrapped calculation (test-compatible)
		int ring = 0x70;
		if (seqAhead(ackd, sent)) {
			return 0;  // ACK ahead of sent (startup edge case)
		}
		int dist = (sent - ackd);
		if (dist < 0) dist += ring;
		int outstanding = dist;

		// TELEMETRY: Track with linearized counters for monitoring
		int linearizedOutstanding = txLinearized.get() - rxLinearized.get();
		if (linearizedOutstanding < 0) linearizedOutstanding = 0;

		// CRITICAL: Validate sequence numbers are in valid P3 range [0x10, 0x7F]
		if (sent < 0x10 || sent > 0x7F) {
			LoggerUtil.error(String.format(
				"[SequenceManager] INVALID lastDataTx=0x%02X (must be 0x10-0x7F). " +
				"This indicates a sequence numbering bug.",
				sent));
		}
		if (ackd < 0x10 || ackd > 0x7F) {
			LoggerUtil.error(String.format(
				"[SequenceManager] INVALID lastAckedServerTx=0x%02X (must be 0x10-0x7F). " +
				"This indicates a sequence numbering bug.",
				ackd));
		}

		// Telemetry: Track peak in-flight (must be effectively final for lambda)
		final int outstandingForLambda = outstanding;
		peakInflight.updateAndGet(cur -> Math.max(cur, outstandingForLambda));

		// CRITICAL ASSERTION: Outstanding should NEVER exceed the P3 window limit
		if (outstanding > 0x10) {
			int violations = violationCount.incrementAndGet();
			LoggerUtil.error(String.format(
				"[VIOLATION #%d] P3 WINDOW EXCEEDED: outstanding=%d (max 16) | " +
				"tx_lin=%d rx_lin=%d | tx=0x%02X rx=0x%02X",
				violations, outstanding,
				txLinearized.get(), rxLinearized.get(),
				sent, ackd));
		}

		// Stall detection: window is full when outstanding >= MAX_WINDOW (16)
		int maxWindow = 0x10;
		boolean windowFull = outstanding >= maxWindow;

		if (windowFull) {
			// Start stall timer if not already running
			if (stallStartTime == 0L) {
				stallStartTime = System.currentTimeMillis();
				startStallCheck();
				LoggerUtil.debug(String.format("[STALL] Window full at %d/%d, starting stall timer", outstanding, maxWindow));
			}
		} else {
			// Reset stall timer when window opens
			if (stallStartTime != 0L) {
				stallStartTime = 0L;
				cancelStallCheck();
				LoggerUtil.debug(String.format("[STALL] Window opened at %d/%d, stall timer reset", outstanding, maxWindow));
			}
		}

		return outstanding;
	}

	/**
	 * Logs current sender window state for debugging.
	 */
	public void logSenderWindowState(String context) {
		int outstanding = getOutstandingWindowFill();
		int nextTx = serverTx & 0xFF;
		int lastAcked = lastAckedServerTx & 0xFF;
		int winSize = 0x10; // MAX_WINDOW from Pacer

		LoggerUtil.debug(String.format("[WINDOW] Sender %s: nextTx=0x%02X lastAcked=0x%02X outstanding=%d/%d",
				context, nextTx, lastAcked, outstanding, winSize));
	}

	/**
	 * Logs current receiver window state for debugging.
	 */
	public void logReceiverWindowState(String context) {
		int lastSeen = lastClientTxSeq & 0xFF;
		int lastAckSent = lastAckedByClientTx & 0xFF;
		int winSize = 0x10; // MAX_WINDOW from Pacer

		LoggerUtil.debug(String.format("[WINDOW] Receiver %s: lastSeen=0x%02X lastAckSent=0x%02X winSize=%d",
				context, lastSeen, lastAckSent, winSize));
	}

	/**
	 * Cleans up stall detection resources. Call on channelInactive.
	 */
	public void cleanupStallDetection() {
		cancelStallCheck();
		stallStartTime = 0L;
	}

	// ======== Restamping ========

	/**
	 * Restamps packet with current sequence numbers and CRC.
	 */
	public byte[] restamp(byte[] template, boolean advanceSeq) {
		byte[] out = Arrays.copyOf(template, template.length);

		// Initialize before stamping the header if not already done
		if (!serverTxInitialized) {
			serverTx = wrapTx(lastClientTxSeq + 1);
			serverTxInitialized = true;
		}

		if (out.length > 7 && ProtocolConstants.isAolFrame(out)) {
			// Determine frame type
			int type = out[7] & 0xFF;
			boolean isData = (type == com.dialtone.protocol.PacketType.DATA.getValue());

			// Default: DATA uses next server TX; control reuses last DATA TX
			int usedTx = isData ? serverTx : lastDataTx;

			// Length is declared as total minus header (including trailing 0x0D if present in buffer)
			int computedLen = out.length - 6;
			out[3] = (byte) (computedLen >>> 8);
			out[4] = (byte) (computedLen);

			// Special handling for the very first DATA after client probe: TX=(clientTx+1), RX=clientTx
			if (isData && !haveSentFirstData && nextServerDataTx >= 0 && startupClientTx >= 0) {
				usedTx = wrapTx(nextServerDataTx);
				out[5] = (byte) usedTx;          // first DATA TX
				out[6] = (byte) lastClientTxSeq; // always the latest client TX seen
				haveSentFirstData = true;
			} else {
				out[5] = (byte) (isData ? serverTx : lastDataTx);
				out[6] = (byte) lastClientTxSeq;
			}

			// Guardrails
			if ((out[6] & 0xFF) != lastClientTxSeq) {
				LoggerUtil.warn(String.format("RX stamp mismatch: out[6]=0x%02X lastClient=0x%02X",
						out[6] & 0xFF, lastClientTxSeq));
			}
			if ((out[5] & 0xFF) < 0x10 || (out[5] & 0xFF) > 0x7F) {
				LoggerUtil.warn(String.format("TX stamp out of range: out[5]=0x%02X (should be 0x10-0x7F)",
						out[5] & 0xFF));
			}

			ensureLengthMatches(out);
			FrameCodec.recomputeHeaderCrc(out);

			assert (out[6] & 0xFF) == lastClientTxSeq : "RX advanced ahead of client";
			assert (out[5] & 0xFF) >= 0x10 && (out[5] & 0xFF) <= 0x7F : "TX out of valid range";

			if (isData && advanceSeq) {
				int prevTx = lastDataTx;
				lastDataTx = usedTx;
				serverTx = wrapTx(usedTx + 1);

				// Update linearized counter directly here to eliminate read-back pattern.
				// This prevents corruption from reading back a potentially wrong TX value from the sent frame.
				int dist = (lastDataTx - prevTx);
				if (dist < 0) dist += 0x70;  // Handle wrap
				if (dist > 0) {
					txLinearized.addAndGet(dist);
				}

				// Consume strict-start budget if active
				if (strictStartActive && strictStartFramesRemaining > 0) {
					strictStartFramesRemaining--;
					if (strictStartFramesRemaining == 0) {
						strictStartActive = false;
					}
				}
			}
		}

		return out;
	}

	/**
	 * Restamps a non-DATA (short control, e.g., 9B) frame header with coherent TX/RX/length/CRC.
	 * TX is stamped with the last DATA TX we actually sent; RX with the last client TX we observed.
	 * Declared length is forced to (total bytes - 6). CRC is recomputed over header/payload.
	 */
	public byte[] restampNonDataHeader(byte[] template) {
		byte[] out = Arrays.copyOf(template, template.length);
		if (!ProtocolConstants.isAolFrame(out) || out.length < ProtocolConstants.MIN_FRAME_SIZE) return out;
		int computedLen = out.length - 6;
		if (computedLen < 0) computedLen = 0;
		out[3] = (byte) ((computedLen >>> 8) & 0xFF);
		out[4] = (byte) (computedLen & 0xFF);
		out[5] = (byte) (lastDataTx & 0xFF);
		out[6] = (byte) (lastClientTxSeq & 0xFF);

		// Validate control frames ALWAYS use lastDataTx (never serverTx or lastAckedServerTx).
		// This catches the P3 TX corruption bug where control frames incorrectly use ACKed sequence.
		int stampedTx = out[5] & 0xFF;
		int stampedRx = out[6] & 0xFF;
		int expectedTx = lastDataTx & 0xFF;

		// Log what RX value we're stamping for diagnostics
		LoggerUtil.debug(String.format("[RESTAMP CONTROL] Stamping control frame: tx=0x%02X (lastDataTx) rx=0x%02X (lastClientTxSeq)",
				stampedTx, stampedRx));
		if (stampedTx != expectedTx) {
			LoggerUtil.error(String.format(
				"[TX CORRUPTION] Control frame stamped with WRONG TX: stamped=0x%02X expected=0x%02X (lastDataTx) | " +
				"serverTx=0x%02X lastAckedServerTx=0x%02X | This violates P3 protocol and will crash clients!",
				stampedTx, expectedTx, serverTx & 0xFF, lastAckedServerTx & 0xFF));
		}
		assert stampedTx == expectedTx : String.format(
			"Control TX corruption: stamped=0x%02X expected=0x%02X", stampedTx, expectedTx);

		FrameCodec.recomputeHeaderCrc(out);
		return out;
	}

	// ======== Accessors ========

	/** Gets the last client TX sequence. */
	public int getLastClientTxSeq() {
		return lastClientTxSeq;
	}

	/** Gets the last data TX sequence. */
	public int getLastDataTx() {
		return lastDataTx;
	}

	/**
	 * Updates the last-sent DATA TX to the provided value and synchronizes
	 * the server TX to the next value so subsequent DATA frames will be
	 * numbered consecutively. Intended to be called after an actual send.
	 *
	 * Increments txLinearized counter by the distance advanced (handles wraps).
	 */
	public void setLastSentDataTx(int tx) {
		int prevTx = this.lastDataTx;
		this.lastDataTx = wrapTx(tx);
		this.serverTx = wrapTx(this.lastDataTx + 1);
		this.serverTxInitialized = true;

		// Increment linearized counter by distance advanced
		int dist = (this.lastDataTx - prevTx);
		if (dist < 0) dist += 0x70;  // Handle wrap
		if (dist > 0) {
			txLinearized.addAndGet(dist);
		}
	}

	/** Gets the last ACKed client TX (for debouncing A5). */
	public int getLastAckedByClientTx() {
		return lastAckedByClientTx;
	}

	/** Sets the last ACKed client TX (for debouncing A5). */
	public void setLastAckedByClientTx(int lastAckedByClientTx) {
		this.lastAckedByClientTx = lastAckedByClientTx;
	}

	/** Gets the last ACKed client RX (for debouncing A5). */
	public int getLastAckedByClientRx() {
		return lastAckedByClientRx;
	}

	/** Sets the last ACKed client RX (for debouncing A5). */
	public void setLastAckedByClientRx(int lastAckedByClientRx) {
		this.lastAckedByClientRx = lastAckedByClientRx;
	}

	public boolean isStartupSeeded() { return startupSeededOnce; }
	public int getStartupClientTx() { return startupClientTx; }
	public void setStartupClientTx(int v) { this.startupClientTx = v; }
	public boolean haveSentFirstData() { return haveSentFirstData; }
	public void setHaveSentFirstData(boolean v) { this.haveSentFirstData = v; }
	public int getNextServerDataTx() { return nextServerDataTx; }
	public void setNextServerDataTx(int v) { this.nextServerDataTx = wrapTx(v); }
	public void setLastClientTxSeq(int v) { this.lastClientTxSeq = wrapTx(v); }
	public void setLastDataTx(int v) {
		int prevTx = this.lastDataTx;
		this.lastDataTx = wrapTx(v);
		this.serverTx = wrapTx(v + 1);

		// Sync linearized counter (used by tests and initialization)
		// Calculate how many frames advanced from prev to new
		int diff = (this.lastDataTx - prevTx);
		if (diff < 0) diff += 0x70;  // Handle wrap
		if (diff > 0) {
			txLinearized.addAndGet(diff);
		}
	}

	/**
	 * Seeds sequence state from the client's 0xA3 probe with token 0x0C03.
	 * - Sets lastClientTxSeq from the frame header TX
	 * - Does not touch lastDataTx yet (first 9B should mirror client's TX/RX)
	 * - Precomputes first server DATA TX as clientTx+1 and initializes serverTx
	 * - Activates strict-start for the first N DATA frames
	 */
	public void initializeFromClientProbe(byte[] in) {
		if (startupSeededOnce || haveSentFirstData) return; // seed only once, before first DATA
		if (!ProtocolConstants.isAolFrame(in) || in.length < 7) return;
		int clientTx = in[ProtocolConstants.IDX_TX] & 0xFF;
		this.lastClientTxSeq = clientTx;
		this.startupClientTx = clientTx;
		this.haveSentFirstData = false;
		this.nextServerDataTx = wrapTx(clientTx + 1);
		this.serverTx = this.nextServerDataTx;
		this.serverTxInitialized = true;
		this.lastAckedServerTx = (this.lastDataTx & 0xFF);
		this.strictStartActive = true;
		this.strictStartFramesRemaining = 3;
		this.startupSeededOnce = true;
	}

	// ======== Internals ========

	/** Returns true if a is ahead of b in the P3 ring order. */
	private static boolean seqAhead(int a, int b) {
		if (a == b) return false;
		int ring = 0x70;
		int da = (a - 0x10) % ring; if (da < 0) da += ring;
		int db = (b - 0x10) % ring; if (db < 0) db += ring;
		int diff = da - db; if (diff < 0) diff += ring;
		// consider ahead if within first half of ring
		return diff > 0 && diff < (ring / 2);
	}

	// Accept short 9B too
	private static boolean isFiveA(byte[] b) {
		return b != null && b.length >= 1 && b[0] == (byte) 0x5A;
	}

	/**
	 * Ensures packet length matches declared length.
	 */
	private static void ensureLengthMatches(byte[] out) {
		if (!ProtocolConstants.isAolFrame(out) || out.length < ProtocolConstants.MIN_FULL_FRAME_SIZE) return;
		int declared = ((out[3] & 0xFF) << 8) | (out[4] & 0xFF);
		int expected = out.length - 6;
		if (declared != expected) {
			LoggerUtil.debug(String.format("Length mismatch: declared=0x%04X expected=0x%04X; fixing.", declared, expected));
			out[3] = (byte) ((expected >> 8) & 0xFF);
			out[4] = (byte) (expected & 0xFF);
		}
	}

	/**
	 * Starts stall detection timer when window becomes full.
	 */
	private void startStallCheck() {
		if (stallCheckTask != null && !stallCheckTask.isDone()) {
			return; // Already running
		}
		if (stallExecutor == null || stallExecutor.isShutdown()) {
			stallExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "seq-stall-check");
				t.setDaemon(true);
				return t;
			});
		}
		stallCheckTask = stallExecutor.scheduleAtFixedRate(
				this::checkStallTimeout, STALL_TIMEOUT_MS, STALL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Cancels the stall detection timer and shuts down executor.
	 */
	private void cancelStallCheck() {
		if (stallCheckTask != null) {
			stallCheckTask.cancel(false);
			stallCheckTask = null;
		}
		if (stallExecutor != null && !stallExecutor.isShutdown()) {
			stallExecutor.shutdownNow();
			stallExecutor = null;
		}
	}

	/**
	 * Checks if stall timeout has been exceeded and logs stall message.
	 */
	private void checkStallTimeout() {
		if (stallStartTime == 0L) {
			cancelStallCheck();
			return;
		}

		long stalledMs = System.currentTimeMillis() - stallStartTime;
		if (stalledMs >= STALL_TIMEOUT_MS) {
			int outstanding = getOutstandingWindowFill();
			int nextTx = serverTx & 0xFF;
			int lastAcked = lastAckedServerTx & 0xFF;
			int lastData = lastDataTx & 0xFF;
			int lastClientRx = lastClientTxSeq & 0xFF;

			LoggerUtil.warn(String.format(
					"[STALL] TX stalled waiting for ACK: outstanding=%d/%d nextTx=0x%02X lastAcked=0x%02X lastDataTx=0x%02X lastClientRx=0x%02X stalled=%dms",
					outstanding, 0x10, nextTx, lastAcked, lastData, lastClientRx, stalledMs));
		}
	}
}