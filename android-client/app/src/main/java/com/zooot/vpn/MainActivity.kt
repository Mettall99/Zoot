package com.zooot.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zooot.vpn.deeplink.DeepLinkParser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = DeepLinkParser.extractToken(intent?.dataString.orEmpty()).orEmpty()
        setContent { MainScreen(token) }
    }
}

@Composable
fun MainScreen(token: String) {
    var status by remember { mutableStateOf("Disconnected") }
    Column(Modifier.padding(16.dp)) {
        Text("Zooot VPN")
        Text("Status: $status")
        Text("Token: ${if (token.isBlank()) "-" else token}")
        Text("Server: demo-server")
        Text("Protocol: wireguard")
        Button(onClick = { status = "Connecting" }) { Text("Connect") }
        Button(onClick = { status = "Disconnected" }) { Text("Disconnect") }
    }
}
