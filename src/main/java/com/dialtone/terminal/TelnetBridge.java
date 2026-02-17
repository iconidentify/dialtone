/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.terminal;

import com.dialtone.utils.LoggerUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages a telnet connection to an external server and bridges communication
 * between the telnet server and the Dialtone client.
 * 
 * <p>Responsibilities:
 * <ul>
 *   <li>Establish and maintain socket connection to telnet server</li>
 *   <li>Read telnet output in background thread</li>
 *   <li>Strip ANSI color codes from telnet output</li>
 *   <li>Split output into lines and forward to client via callback</li>
 *   <li>Forward commands from client to telnet server</li>
 *   <li>Handle connection errors and cleanup</li>
 * </ul>
 */
public class TelnetBridge {

    private final String host;
    private final int port;
    private final int timeoutMs;
    private final java.util.function.Consumer<String> lineCallback;
    private final TelnetLineFilter lineFilter;

    private Socket socket;
    private OutputStream outputStream;
    private Thread readingThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new TelnetBridge with specified host, port, and line filter.
     *
     * @param host Telnet server hostname
     * @param port Telnet server port
     * @param timeoutMs Socket timeout in milliseconds
     * @param lineCallback Callback to invoke for each line received from telnet server
     * @param lineFilter Optional filter to apply to each line before callback (may be null)
     */
    public TelnetBridge(String host, int port, int timeoutMs,
                        java.util.function.Consumer<String> lineCallback,
                        TelnetLineFilter lineFilter) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
        this.lineCallback = lineCallback;
        this.lineFilter = lineFilter;
    }

    /**
     * Establishes connection to telnet server and starts reading thread.
     * 
     * @return true if connection was successful, false otherwise
     */
    public boolean connect() {
        if (closed.get()) {
            LoggerUtil.warn("[TelnetBridge] Cannot connect: bridge is closed");
            return false;
        }

        if (connected.get()) {
            LoggerUtil.warn("[TelnetBridge] Already connected to " + host + ":" + port);
            return true;
        }

        try {
            LoggerUtil.info("[TelnetBridge] Connecting to " + host + ":" + port);
            socket = new Socket(host, port);
            socket.setSoTimeout(timeoutMs);
            outputStream = socket.getOutputStream();
            
            connected.set(true);
            LoggerUtil.info("[TelnetBridge] Connected to " + host + ":" + port);

            // Read any immediately available data (initial prompt from server)
            // This captures prompts that arrive right after connection, before the read thread starts
            readInitialData();

            // Start reading thread
            startReading();
            return true;

        } catch (IOException e) {
            LoggerUtil.error("[TelnetBridge] Failed to connect to " + host + ":" + port + ": " + e.getMessage());
            close();
            return false;
        }
    }

    /**
     * Reads any immediately available data from the telnet connection.
     * This captures initial prompts that arrive right after connection.
     */
    private void readInitialData() {
        try {
            InputStream inputStream = socket.getInputStream();
            if (inputStream.available() > 0) {
                // Read available bytes (up to 4096 to avoid reading too much)
                byte[] buffer = new byte[Math.min(inputStream.available(), 4096)];
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead > 0) {
                    String initialData = new String(buffer, 0, bytesRead, StandardCharsets.US_ASCII);
                    LoggerUtil.debug("[TelnetBridge] Initial data available: " + bytesRead + " bytes");
                    LoggerUtil.debug("[TelnetBridge] Initial data content: '" + initialData + "'");
                    
                    // Strip ANSI codes
                    String cleanData = AnsiColorStripper.stripAnsiCodes(initialData);
                    
                    // Split by newlines and process each line
                    if (cleanData != null && !cleanData.isEmpty() && lineCallback != null) {
                        String[] lines = cleanData.split("\\r?\\n", -1);
                        for (String line : lines) {
                            // Apply filter if present
                            String filteredLine = line;
                            if (lineFilter != null) {
                                filteredLine = lineFilter.filter(line);
                                if (filteredLine == null) {
                                    LoggerUtil.debug("[TelnetBridge] Initial line suppressed by filter: '" + line + "'");
                                    continue;
                                }
                            }
                            // Process all lines, including blank lines (empty strings represent blank lines)
                            LoggerUtil.debug("[TelnetBridge] Processing initial line: '" + filteredLine + "' (blank=" + filteredLine.isEmpty() + ")");
                            try {
                                lineCallback.accept(filteredLine);
                            } catch (Exception e) {
                                LoggerUtil.error("[TelnetBridge] Error in initial line callback: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            LoggerUtil.warn("[TelnetBridge] Error reading initial data: " + e.getMessage());
            // Don't fail connection if initial read fails - continue with normal reading
        }
    }

    /**
     * Starts background thread to read from telnet socket.
     */
    private void startReading() {
        if (readingThread != null && readingThread.isAlive()) {
            LoggerUtil.warn("[TelnetBridge] Reading thread already running");
            return;
        }

        readingThread = new Thread(() -> {
            try (InputStream inputStream = socket.getInputStream();
                 BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.US_ASCII))) {

                String line;
                while (connected.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        line = reader.readLine();
                        if (line == null) {
                            // End of stream - connection closed by server
                            LoggerUtil.info("[TelnetBridge] Telnet server closed connection");
                            break;
                        }

                        // Log raw line from telnet (before ANSI stripping) for debugging
                        LoggerUtil.debug("[TelnetBridge] Raw line from telnet: '" + line + "' (length: " + line.length() + ")");

                        // Strip ANSI color codes
                        String cleanLine = AnsiColorStripper.stripAnsiCodes(line);

                        // Log cleaned line for debugging
                        if (!line.equals(cleanLine)) {
                            LoggerUtil.debug("[TelnetBridge] After ANSI strip: '" + cleanLine + "' (length: " + cleanLine.length() + ")");
                            // Check if newlines were affected
                            int rawNewlines = countNewlines(line);
                            int cleanNewlines = countNewlines(cleanLine);
                            if (rawNewlines != cleanNewlines) {
                                LoggerUtil.warn("[TelnetBridge] WARNING: Newline count changed! Raw: " + rawNewlines + ", Clean: " + cleanNewlines);
                            }
                        }

                        // Apply filter if present
                        String filteredLine = cleanLine;
                        if (lineFilter != null && cleanLine != null) {
                            filteredLine = lineFilter.filter(cleanLine);
                            if (filteredLine == null) {
                                LoggerUtil.debug("[TelnetBridge] Line suppressed by filter: '" + cleanLine + "'");
                                continue;
                            }
                        }

                        // Forward line to client via callback
                        // Allow empty strings to pass through (they represent blank lines)
                        // readLine() strips newlines, so blank lines become empty strings
                        if (filteredLine != null && lineCallback != null) {
                            if (filteredLine.isEmpty()) {
                                LoggerUtil.debug("[TelnetBridge] Processing blank line (empty string)");
                            }
                            try {
                                lineCallback.accept(filteredLine);
                            } catch (Exception e) {
                                LoggerUtil.error("[TelnetBridge] Error in line callback: " + e.getMessage());
                            }
                        }

                    } catch (SocketTimeoutException e) {
                        // Timeout is expected - continue reading
                        continue;
                    }
                }

            } catch (IOException e) {
                if (connected.get()) {
                    LoggerUtil.error("[TelnetBridge] Error reading from telnet: " + e.getMessage());
                }
            } finally {
                connected.set(false);
                LoggerUtil.info("[TelnetBridge] Reading thread stopped");
            }
        }, "TelnetBridge-Reader-" + host + "-" + port);

        readingThread.setDaemon(true);
        readingThread.start();
        LoggerUtil.debug("[TelnetBridge] Reading thread started");
    }

    /**
     * Sends a command to the telnet server.
     * 
     * @param command Command to send (newline will be appended automatically)
     * @return true if command was sent successfully, false otherwise
     */
    public synchronized boolean sendCommand(String command) {
        if (!connected.get() || closed.get() || outputStream == null) {
            LoggerUtil.warn("[TelnetBridge] Cannot send command: not connected");
            return false;
        }

        if (command == null) {
            LoggerUtil.warn("[TelnetBridge] Cannot send null command");
            return false;
        }

        try {
            byte[] commandBytes = (command + "\n").getBytes(StandardCharsets.US_ASCII);
            outputStream.write(commandBytes);
            outputStream.flush();
            
            LoggerUtil.debug("[TelnetBridge] Sent command: '" + command + "'");
            return true;

        } catch (IOException e) {
            LoggerUtil.error("[TelnetBridge] Failed to send command '" + command + "': " + e.getMessage());
            connected.set(false);
            return false;
        }
    }

    /**
     * Checks if the telnet bridge is currently connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return connected.get() && !closed.get();
    }

    /**
     * Closes the telnet connection and cleans up resources.
     */
    public void close() {
        if (closed.getAndSet(true)) {
            // Already closed
            return;
        }

        LoggerUtil.info("[TelnetBridge] Closing connection to " + host + ":" + port);

        connected.set(false);

        // Interrupt reading thread
        if (readingThread != null && readingThread.isAlive()) {
            readingThread.interrupt();
            try {
                readingThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                LoggerUtil.warn("[TelnetBridge] Interrupted while waiting for reading thread to finish");
                Thread.currentThread().interrupt();
            }
        }

        // Close socket (this will cause readLine() to throw IOException if it's blocking)
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                LoggerUtil.warn("[TelnetBridge] Error closing socket: " + e.getMessage());
            }
        }

        // Close output stream if still open
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                LoggerUtil.warn("[TelnetBridge] Error closing output stream: " + e.getMessage());
            }
        }

        LoggerUtil.info("[TelnetBridge] Connection closed");
    }

    /**
     * Count newline characters in a string for debugging.
     */
    private static int countNewlines(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                count++;
            }
        }
        return count;
    }
}
