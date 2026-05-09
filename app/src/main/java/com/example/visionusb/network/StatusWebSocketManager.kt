package com.example.visionusb.network

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

class StatusWebSocketManager {

    private val client = OkHttpClient()
    private val running = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var currentWebSocket: WebSocket? = null

    fun start(
        onStatus: (String) -> Unit,
        onMetrics: (fps: String, latencyMs: String) -> Unit
    ) {
        if (running.getAndSet(true)) return

        Thread {
            while (running.get()) {
                try {
                    postStatus(onStatus, "接続試行中")

                    val request = Request.Builder()
                        .url(ServerConfig.wsUrl())
                        .build()

                    val listener = object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            postStatus(onStatus, "接続中")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val json = JSONObject(text)
                                val messageType = json.optString("type", "")
                                val rootPayload = json.optJSONObject("payload")
                                val payload = if (messageType == "status" && rootPayload != null) {
                                    rootPayload
                                } else {
                                    json
                                }

                                val fps = payload.optDouble("fps", 0.0)
                                val latency = payload.optDouble("latency_ms", 0.0)
                                mainHandler.post {
                                    onMetrics(
                                        String.format("%.1f", fps),
                                        String.format("%.1f", latency)
                                    )
                                }
                            } catch (_: Exception) {
                            }
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            postStatus(onStatus, "接続待ち")
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            postStatus(onStatus, "接続待ち")
                        }
                    }

                    currentWebSocket = client.newWebSocket(request, listener)
                    Thread.sleep(2000)
                } catch (_: Exception) {
                    postStatus(onStatus, "接続待ち")
                    try {
                        Thread.sleep(2000)
                    } catch (_: InterruptedException) {
                    }
                }
            }
        }.start()
    }

    fun stop() {
        running.set(false)
        try {
            currentWebSocket?.close(1000, null)
        } catch (_: Exception) {
        }
        currentWebSocket = null
    }

    private fun postStatus(onStatus: (String) -> Unit, status: String) {
        mainHandler.post {
            onStatus(status)
        }
    }
}
