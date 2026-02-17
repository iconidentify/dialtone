/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.spi;

import com.dialtone.fdo.FdoChunk;

import java.io.Closeable;
import java.util.List;

/**
 * Service interface for FDO compilation.
 *
 * <p>Implementations of this interface provide FDO compilation capabilities
 * through different backends (HTTP API, native Java library, etc.).</p>
 *
 * <p>The compilation produces FdoChunk objects that are ready for P3 frame wrapping
 * via P3ChunkEnqueuer.</p>
 */
public interface FdoCompilationService extends Closeable {

    /**
     * Compile FDO source to binary data.
     *
     * @param fdoSource the FDO source text to compile
     * @return compiled binary data
     * @throws FdoCompilationException if compilation fails
     */
    byte[] compile(String fdoSource) throws FdoCompilationException;

    /**
     * Compile FDO source to P3 protocol chunks.
     *
     * <p>Each chunk is sized appropriately for P3 framing (max 119 bytes payload).
     * Chunks include the token bytes at the beginning and optional Stream ID metadata.</p>
     *
     * @param fdoSource the FDO source text to compile
     * @param token P3 token (e.g., "AT", "at")
     * @param streamId Stream ID for P3 frames (0 for no Stream ID, or 0x0001-0xFFFE)
     * @return list of FdoChunk objects ready for P3 frame wrapping
     * @throws FdoCompilationException if compilation fails
     */
    List<FdoChunk> compileToChunks(String fdoSource, String token, int streamId)
            throws FdoCompilationException;

    /**
     * Get the backend name for logging and diagnostics.
     *
     * @return backend identifier (e.g., "http", "java")
     */
    String getBackendName();

    /**
     * Check if the backend is healthy and ready to serve requests.
     *
     * @return true if healthy, false otherwise
     */
    boolean isHealthy();
}
