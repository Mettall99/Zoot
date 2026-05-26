package com.zooot.vpn

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.zooot.vpn.deeplink.DeepLinkParser

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val token = DeepLinkParser.extractToken(intent?.dataString.orEmpty())
        val text = TextView(this).apply {
            this.text = "Zooot VPN\nToken: ${token ?: "none"}\nStatus: Ready"
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }

        setContentView(text)
    }
}
