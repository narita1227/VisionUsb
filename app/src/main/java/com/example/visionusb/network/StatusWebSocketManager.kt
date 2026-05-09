package com.example.visionusb.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class StatusWebSocketManager {

    companion object {
        private const val TAG = "StatusWebSocketManager"
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 15000L
    }

    private val client = OkHttpClient()
    private val running = AtomicBoolean(false)
    private val connected = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingMeta = ArrayDeque<Pair<String, String>>()
    private val pendingMetaLock = Any()

    @Volatile
    private var currentWebSocket: WebSocket? = null

    @Volatile
    private var workerThread: Thread? = null

    fun start(
        onStatus: (String) -> Unit,
        onFrame: (bitmap: Bitmap, fps: String, latencyMs: String) -> Unit
    ) {
        if (running.getAndSet(true)) return

        workerThread = Thread {
            var reconnectDelayMs = RECONNECT_BASE_DELAY_MS
            while (running.get()) {
                try {
                    if (connected.get()) {
                        Thread.sleep(500)
                        continue
                    }

                    val targetUrl = ServerConfig.wsUrl()
                    postStatus(onStatus, "接続試行中")
                    closeCurrentSocketQuietly()

                    val request = Request.Builder()
                        .url(targetUrl)
                        .build()

                    val listener = object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            connected.set(true)
                            reconnectDelayMs = RECONNECT_BASE_DELAY_MS
                            postStatus(onStatus, "接続中")
                            Log.i(TAG, "WebSocket connected")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val json = JSONObject(text)
                                when (json.optString("type", "")) {
                                    "frame_meta" -> {
                                        val payload = json.optJSONObject("payload") ?: return
                                        val fps = String.format("%.1f", payload.optDouble("fps", 0.0))
                                        val latency = String.format("%.1f", payload.optDouble("latency_ms", 0.0))
                                        synchronized(pendingMetaLock) {
                                            pendingMeta.addLast(fps to latency)
                                            if (pendingMeta.size > 8) {
                                                pendingMeta.removeFirst()
                                            }
                                        }
                                    }

                                    // Legacy status-only path.
                                    "status" -> {
                                        val payload = json.optJSONObject("payload") ?: return
                                        val fps = String.format("%.1f", payload.optDouble("fps", 0.0))
                                        val latency = String.format("%.1f", payload.optDouble("latency_ms", 0.0))
                                        synchronized(pendingMetaLock) {
                                            pendingMeta.addLast(fps to latency)
                                            if (pendingMeta.size > 8) {
                                                pendingMeta.removeFirst()
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to parse ws message", e)
                            }
                        }

                        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                            try {
                                val raw = bytes.toByteArray()
                                val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return

                                val (fps, latency) = synchronized(pendingMetaLock) {
                                    if (pendingMeta.isEmpty()) {
                                        "-" to "-"
                                    } else {
                                        pendingMeta.removeFirst()
                                    }
                                }

                                mainHandler.post {
                                    onFrame(bitmap, fps, latency)
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to decode binary frame", e)
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            connected.set(false)
                            currentWebSocket = null
                            postStatus(onStatus, "接続待ち")
                            Log.i(TAG, "WebSocket closed code=$code reason=$reason")
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            connected.set(false)
                            currentWebSocket = null
                            postStatus(onStatus, "接続待ち")
                            Log.w(TAG, "WebSocket failure", t)
                        }
                    }

                    currentWebSocket = client.newWebSocket(request, listener)
                    Thread.sleep(reconnectDelayMs)
                    reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
                } catch (e: Exception) {
                    connected.set(false)
                    Log.w(TAG, "Reconnect loop failure", e)
                    postStatus(onStatus, "接続待ち")
                    Thread.sleep(reconnectDelayMs)
                    reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
                }
            }
        }.apply {
            name = "StatusWebSocketWorker"
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        connected.set(false)
        workerThread?.interrupt()
        workerThread = null
        closeCurrentSocketQuietly()
    }

    private fun closeCurrentSocketQuietly() {
        try {
            currentWebSocket?.close(1000, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to close websocket", e)
        }
        currentWebSocket = null
    }

    private fun postStatus(onStatus: (String) -> Unit, status: String) {
        mainHandler.post {
            onStatus(status)
        }
    }
}
