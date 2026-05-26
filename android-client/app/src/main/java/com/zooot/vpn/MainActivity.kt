package com.zooot.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val deeplinkToken = intent?.dataString?.let(DeepLinkParser::extractToken)

        setContent {
            MaterialTheme {
                val status = remember { mutableStateOf("Disconnected") }
                Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    Text("Zooot Android Client")
                    Text("Token: ${deeplinkToken ?: "not provided"}")
                    Text("Status: ${status.value}")
                    Button(onClick = {
                        val token = deeplinkToken ?: "demo-token"
                        val endpoint = ZootApiClient.resolveConfigEndpoint(token)
                        status.value = "Connected (fake): $endpoint"
                    }) {
                        Text("Connect (fake)")
                    }
                }
            }
        }
    }
}
