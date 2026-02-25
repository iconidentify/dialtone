/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.skalholt;

import com.dialtone.utils.LoggerUtil;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * SSE (Server-Sent Events) client for the Skalholt MUD EventStream.
 *
 * <p>Connects to the /api/events endpoint and streams events to registered callbacks.
 * Uses HTTP Basic Authentication with the token captured from telnet.
 */
public class SkalholtEventStreamClient implements AutoCloseable {

    private static final String EVENTS_PATH = "/api/events";
    private static final String SSE_DATA_PREFIX = "data:";
    private static final int DEFAULT_TIMEOUT_MS = 30000;

    private final String host;
    private final int port;
    private final SkalholtAuthToken authToken;
    private final Consumer<SkalholtSseEvent> eventCallback;
    private final Consumer<Throwable> errorCallback;
    private final int timeoutMs;

    private final CloseableHttpClient httpClient;
    private Thread readerThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);

    /**
     * Creates a new SkalholtEventStreamClient.
     *
     * @param host Skalholt API host
     * @param port Skalholt API port
     * @param authToken authentication token
     * @param eventCallback callback for received events
     * @param errorCallback callback for errors (may be null)
     */
    public SkalholtEventStreamClient(String host, int port, SkalholtAuthToken authToken,
                                     Consumer<SkalholtSseEvent> eventCallback,
                                     Consumer<Throwable> errorCallback) {
        this(host, port, authToken, eventCallback, errorCallback, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Creates a new SkalholtEventStreamClient with custom timeout.
     *
     * @param host Skalholt API host
     * @param port Skalholt API port
     * @param authToken authentication token
     * @param eventCallback callback for received events
     * @param errorCallback callback for errors (may be null)
     * @param timeoutMs connection timeout in milliseconds
     */
    public SkalholtEventStreamClient(String host, int port, SkalholtAuthToken authToken,
                                     Consumer<SkalholtSseEvent> eventCallback,
                                     Consumer<Throwable> errorCallback,
                                     int timeoutMs) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host cannot be null or blank");
        }
        if (authToken == null) {
            throw new IllegalArgumentException("Auth token cannot be null");
        }
        if (eventCallback == null) {
            throw new IllegalArgumentException("Event callback cannot be null");
        }
        this.host = host;
        this.port = port;
        this.authToken = authToken;
        this.eventCallback = eventCallback;
        this.errorCallback = errorCallback;
        this.timeoutMs = timeoutMs;

        // Create httpClient once with configured timeout (reused across reconnects)
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(timeoutMs))
                .setResponseTimeout(Timeout.DISABLED) // SSE streams indefinitely
                .build();
        this.httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * Connects to the EventStream and starts receiving events.
     * This method returns immediately; events are delivered asynchronously.
     */
    public void connect() {
        if (connected.get()) {
            LoggerUtil.warn("[SkalholtEventStream] Already connected");
            return;
        }

        shouldReconnect.set(true);
        startReaderThread();
    }

    /**
     * Starts the background reader thread.
     */
    private void startReaderThread() {
        LoggerUtil.info("[SkalholtEventStream] Starting reader thread for " + authToken.getUsername());
        readerThread = new Thread(() -> {
            LoggerUtil.info("[SkalholtEventStream] Reader thread running");
            while (shouldReconnect.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    connectAndRead();
                } catch (Exception e) {
                    connected.set(false);
                    if (shouldReconnect.get()) {
                        LoggerUtil.warn("[SkalholtEventStream] Connection error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        notifyError(e);
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            connected.set(false);
            LoggerUtil.info("[SkalholtEventStream] Reader thread stopped");
        }, "SkalholtEventStream-" + authToken.getUsername());

        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Establishes connection and reads events until disconnected.
     */
    private void connectAndRead() throws IOException {
        String url = "http://" + host + ":" + port + EVENTS_PATH;
        LoggerUtil.info("[SkalholtEventStream] Connecting to " + url);
        LoggerUtil.info("[SkalholtEventStream] Using auth header: " + authToken.getBasicAuthHeader().substring(0, 15) + "...");

        HttpGet request = new HttpGet(url);
        request.setHeader("Authorization", authToken.getBasicAuthHeader());
        request.setHeader("Accept", "text/event-stream");
        request.setHeader("Cache-Control", "no-cache");

        LoggerUtil.info("[SkalholtEventStream] Executing HTTP request...");

        httpClient.execute(request, (ClassicHttpResponse response) -> {
            int status = response.getCode();
            LoggerUtil.info("[SkalholtEventStream] Got HTTP response: " + status);

            if (status != 200) {
                String body = EntityUtils.toString(response.getEntity());
                LoggerUtil.error("[SkalholtEventStream] Error response body: " + body);
                throw new IOException("EventStream returned status " + status + ": " + body);
            }

            connected.set(true);
            LoggerUtil.info("[SkalholtEventStream] SSE connection established for player: " + authToken.getUsername());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8))) {

                String line;
                StringBuilder eventData = new StringBuilder();
                int lineCount = 0;

                while ((line = reader.readLine()) != null && shouldReconnect.get()) {
                    lineCount++;
                    // Log first few lines to see what we're receiving
                    if (lineCount <= 10) {
                        LoggerUtil.info("[SkalholtEventStream] Received line " + lineCount + ": " +
                            (line.length() > 80 ? line.substring(0, 80) + "..." : line));
                    }

                    if (line.isEmpty()) {
                        // Empty line marks end of event
                        if (eventData.length() > 0) {
                            LoggerUtil.info("[SkalholtEventStream] Processing complete event, size=" + eventData.length());
                            processEventData(eventData.toString());
                            eventData.setLength(0);
                        }
                    } else if (line.startsWith(SSE_DATA_PREFIX)) {
                        // SSE data line
                        String data = line.substring(SSE_DATA_PREFIX.length()).trim();
                        if (eventData.length() > 0) {
                            eventData.append("\n");
                        }
                        eventData.append(data);
                    }
                    // Ignore other SSE fields (event:, id:, retry:) for now
                }

                LoggerUtil.info("[SkalholtEventStream] Read loop ended after " + lineCount + " lines");
            }

            return null;
        });
    }

    /**
     * Processes received SSE data and dispatches to callback.
     */
    private void processEventData(String data) {
        // Skip known non-JSON markers
        if (data == null || data.isBlank() || "EOM".equals(data.trim())) {
            return;
        }

        try {
            SkalholtSseEvent event = SkalholtSseEvent.fromJson(data);
            LoggerUtil.debug("[SkalholtEventStream] Received event: " + event.getSkalholtEventType() + " for " + event.getPlayerId());
            eventCallback.accept(event);
        } catch (Exception e) {
            LoggerUtil.warn("[SkalholtEventStream] Failed to parse event: " + e.getMessage());
            LoggerUtil.debug("[SkalholtEventStream] Raw data: " + data);
            notifyError(e);
        }
    }

    /**
     * Notifies error callback if present.
     */
    private void notifyError(Throwable error) {
        if (errorCallback != null) {
            try {
                errorCallback.accept(error);
            } catch (Exception e) {
                LoggerUtil.error("[SkalholtEventStream] Error in error callback: " + e.getMessage());
            }
        }
    }

    /**
     * Checks if the client is currently connected.
     *
     * @return true if connected and receiving events
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Disconnects from the EventStream.
     */
    public void disconnect() {
        shouldReconnect.set(false);
        connected.set(false);

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
            try {
                readerThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        closeHttpClient();
        LoggerUtil.info("[SkalholtEventStream] Disconnected");
    }

    /**
     * Closes the HTTP client.
     */
    private void closeHttpClient() {
        try {
            httpClient.close();
        } catch (IOException e) {
            LoggerUtil.warn("[SkalholtEventStream] Error closing HTTP client: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    /**
     * Gets the username associated with this client.
     *
     * @return the username
     */
    public String getUsername() {
        return authToken.getUsername();
    }
}
