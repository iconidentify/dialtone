/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.protocol;

import com.dialtone.utils.LoggerUtil;
import java.io.ByteArrayOutputStream;

/**
 * Handles TCP-level frame accumulation for partial frames that arrive
 * split across multiple TCP segments.
 *
 * This is distinct from protocol-level multi-frame stream accumulation
 * (handled by MultiFrameStreamAccumulator). TCP accumulation deals with
 * incomplete P3 frames at the byte level, while stream accumulation deals
 * with complete frames that span multiple logical protocol messages.
 *
 * <p>This class encapsulates the complexity of:
 * <ul>
 *   <li>Buffer size limits to prevent DOS attacks</li>
 *   <li>Accumulation attempt counting to prevent infinite buffering</li>
 *   <li>Prepending buffered bytes to new TCP segments</li>
 *   <li>Storing unprocessed remainder bytes for next segment</li>
 * </ul>
 *
 * <h3>Usage Pattern:</h3>
 * <pre>
 * TcpFrameAccumulator accumulator = new TcpFrameAccumulator("conn-123");
 *
 * // On each channelRead:
 * try {
 *     byte[] dataToProcess = accumulator.prepareDataForProcessing(newBytes);
 *     int bytesProcessed = splitAndDispatch(ctx, dataToProcess);
 *     accumulator.bufferRemainder(dataToProcess, bytesProcessed);
 * } catch (TcpBufferOverflowException e) {
 *     LoggerUtil.error("Buffer overflow - closing connection");
 *     ctx.close();
 * }
 *
 * // On channelInactive:
 * int discarded = accumulator.clearAndReset();
 * if (discarded > 0) {
 *     LoggerUtil.warn("Connection closed with buffered data");
 * }
 * </pre>
 *
 * <h3>Thread Safety:</h3>
 * This class is <strong>NOT thread-safe</strong>. It is designed for use by
 * a single Netty channel handler thread, which aligns with Netty's
 * single-threaded channel processing model.
 *
 * <h3>Memory Management:</h3>
 * This class assumes ownership of input byte arrays is transferred to the caller
 * after method return (standard Netty pattern). No defensive copying is performed.
 *
 * @author Claude Code
 * @see com.dialtone.protocol.stream.MultiFrameStreamAccumulator
 */
public class TcpFrameAccumulator {

    private static final int MAX_TCP_BUFFER_SIZE = 65536;  // 64KB max to prevent DOS
    private static final int MAX_ACCUMULATION_ATTEMPTS = 10;  // Prevent infinite buffering of corrupt data

    private ByteArrayOutputStream tcpFrameBuffer = null;
    private int tcpBufferAccumulationCount = 0;
    private final String logPrefix;

    /**
     * Creates accumulator with logging prefix for diagnostic context.
     *
     * @param logPrefix Prefix to use in diagnostic log messages (e.g., connection identifier)
     */
    public TcpFrameAccumulator(String logPrefix) {
        this.logPrefix = logPrefix != null ? logPrefix : "";
    }

    /**
     * Prepares data for processing by prepending any buffered bytes from previous
     * TCP segments. This handles the case where P3 frame boundaries don't align
     * with TCP segment boundaries.
     *
     * <p>The method enforces accumulation attempt limits and buffer size limits
     * to prevent resource exhaustion attacks.
     *
     * @param newBytes Newly arrived TCP segment data
     * @return Combined data ready for frame extraction (buffered + new bytes)
     * @throws TcpBufferOverflowException if accumulation limits exceeded
     */
    public byte[] prepareDataForProcessing(byte[] newBytes) throws TcpBufferOverflowException {
        // Fast path: no buffered data, return new bytes as-is
        if (tcpFrameBuffer == null || tcpFrameBuffer.size() == 0) {
            return newBytes;
        }

        LoggerUtil.debug(() -> logPrefix + String.format(
            "TCP accumulation: Prepending %d buffered bytes to %d new bytes",
            tcpFrameBuffer.size(), newBytes.length));

        // Check accumulation attempts to prevent infinite buffering of corrupt data
        tcpBufferAccumulationCount++;
        if (tcpBufferAccumulationCount > MAX_ACCUMULATION_ATTEMPTS) {
            LoggerUtil.error(logPrefix + "TCP buffer: Too many accumulation attempts - discarding buffer");
            tcpFrameBuffer = null;
            tcpBufferAccumulationCount = 0;
            return newBytes;  // Continue with just new data
        }

        // Combine buffered + new data
        // Note: ByteArrayOutputStream.write() never actually throws IOException
        try {
            tcpFrameBuffer.write(newBytes);
            byte[] combinedData = tcpFrameBuffer.toByteArray();

            // Check combined size before returning
            if (combinedData.length > MAX_TCP_BUFFER_SIZE) {
                throw new TcpBufferOverflowException(
                    "TCP buffer overflow after combining data",
                    combinedData.length,
                    tcpBufferAccumulationCount);
            }

            tcpFrameBuffer = null;  // Clear buffer after successful combination
            return combinedData;

        } catch (TcpBufferOverflowException e) {
            // Re-throw TcpBufferOverflowException for proper error handling
            throw e;
        } catch (Exception e) {
            // This should never happen with ByteArrayOutputStream, but handle defensively
            LoggerUtil.error(logPrefix + "Unexpected error combining buffered data: " + e.getMessage());
            tcpFrameBuffer = null;
            tcpBufferAccumulationCount = 0;
            return newBytes;
        }
    }

    /**
     * Buffers unprocessed remainder bytes for the next TCP segment.
     * This is called after frame processing to store any partial frame
     * data that couldn't be processed in this segment.
     *
     * @param allData The complete data that was processed (from prepareDataForProcessing)
     * @param bytesProcessed Number of bytes successfully consumed by frame processing
     * @throws TcpBufferOverflowException if remainder size exceeds buffer limits
     */
    public void bufferRemainder(byte[] allData, int bytesProcessed) throws TcpBufferOverflowException {
        if (bytesProcessed < 0 || bytesProcessed > allData.length) {
            throw new IllegalArgumentException(
                String.format("Invalid bytesProcessed: %d (data length: %d)",
                    bytesProcessed, allData.length));
        }

        int remainingBytes = allData.length - bytesProcessed;

        if (remainingBytes > 0) {
            LoggerUtil.debug(() -> logPrefix + String.format(
                "TCP accumulation: Buffering %d unprocessed bytes for next segment", remainingBytes));

            // Check size limit before buffering
            if (remainingBytes > MAX_TCP_BUFFER_SIZE) {
                throw new TcpBufferOverflowException(
                    "Remainder data exceeds buffer size limit",
                    remainingBytes,
                    tcpBufferAccumulationCount);
            }

            // Create new buffer and store remainder
            tcpFrameBuffer = new ByteArrayOutputStream(remainingBytes);
            tcpBufferAccumulationCount = 0;  // Reset counter on new buffer

            // Note: ByteArrayOutputStream.write() never actually throws IOException
            tcpFrameBuffer.write(allData, bytesProcessed, remainingBytes);

        } else {
            // All data processed, reset accumulation counter
            tcpBufferAccumulationCount = 0;
            // Note: keep tcpFrameBuffer as-is (null or empty) for next cycle
        }
    }

    /**
     * Clears all buffered state and resets counters.
     * Should be called on channel inactive to prevent memory leaks
     * and provide diagnostic information about discarded data.
     *
     * @return Number of bytes discarded (useful for logging)
     */
    public int clearAndReset() {
        int discardedBytes = 0;

        if (tcpFrameBuffer != null) {
            discardedBytes = tcpFrameBuffer.size();
            tcpFrameBuffer = null;
        }

        tcpBufferAccumulationCount = 0;

        return discardedBytes;
    }

    /**
     * Checks if there are buffered bytes waiting for the next TCP segment.
     *
     * @return true if bytes are buffered, false otherwise
     */
    public boolean hasBufferedData() {
        return tcpFrameBuffer != null && tcpFrameBuffer.size() > 0;
    }

    /**
     * Gets current buffer size for diagnostics and monitoring.
     *
     * @return Number of bytes currently buffered (0 if no buffer)
     */
    public int getBufferedByteCount() {
        return tcpFrameBuffer != null ? tcpFrameBuffer.size() : 0;
    }

    /**
     * Gets accumulation attempt count for diagnostics and monitoring.
     * This count resets when a new buffer is created or all data is processed.
     *
     * @return Current accumulation attempt count
     */
    public int getAccumulationCount() {
        return tcpBufferAccumulationCount;
    }
}