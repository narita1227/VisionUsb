package com.example.visionusb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.visionusb.network.ServerConfig
import com.example.visionusb.network.StatusWebSocketManager
import com.example.visionusb.ui.camera.MjpegBackground

class MainActivity : ComponentActivity() {

    private val wsManager = StatusWebSocketManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var statusText by remember { mutableStateOf("未接続") }
            var fps by remember { mutableStateOf("-") }
            var latencyMs by remember { mutableStateOf("-") }

            LaunchedEffect(Unit) {
                wsManager.start(
                    onStatus = { status -> statusText = status },
                    onMetrics = { f, l ->
                        fps = f
                        latencyMs = l
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                MjpegBackground(
                    url = ServerConfig.mjpegUrl(),
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "状態: $statusText", color = Color.White)
                    Text(text = "FPS: $fps", color = Color.White)
                    Text(text = "遅延(ms): $latencyMs", color = Color.White)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wsManager.stop()
    }
}
