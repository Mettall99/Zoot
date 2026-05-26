package com.zooot.vpn

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.selector.NetworkType
import com.zooot.vpn.selector.ProtocolSelector
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.ProtocolType

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            this.text = "Zooot VPN\nStatus: Loading..."
            textSize = 18f
            setPadding(32, 32, 32, 32)
        }
        setContentView(text)

        val token = DeepLinkParser.extractToken(intent?.dataString.orEmpty())
        if (token.isNullOrBlank()) {
            text.text = "Zooot VPN\nbackend URL: ${ZootApiClient.backendBaseUrl}\ntoken: none\nstatus: Ready"
            return
        }

        Thread {
            try {
                val servers = ZootApiClient.resolveToken(token)
                val country = servers.firstOrNull()?.country ?: "DE"
                val selection = ProtocolSelector.select(servers, country, NetworkType.WIFI, emptyMap())
                val protocol = selection?.protocol?.name?.lowercase() ?: "amneziawg"
                val server = selection?.serverId ?: "Frankfurt"

                val fakeAdapter = FakeVpnProtocolAdapter(type = protocolTypeFromSelector(protocol))
                val status = if (fakeAdapter.type == ProtocolType.AMNEZIAWG) "Connected" else "Ready"

                runOnUiThread {
                    text.text = """
                        Zooot VPN
                        backend URL: ${ZootApiClient.backendBaseUrl}
                        token: $token
                        country: $country
                        server: $server
                        protocol: $protocol
                        status: $status
                    """.trimIndent()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    text.text = "Zooot VPN\nbackend URL: ${ZootApiClient.backendBaseUrl}\ntoken: $token\nstatus: Error: ${e.message}"
                }
            }
        }.start()
    }

    private fun protocolTypeFromSelector(protocol: String): ProtocolType = when (protocol) {
        "amneziawg" -> ProtocolType.AMNEZIAWG
        "xray_vless_reality" -> ProtocolType.XRAY_VLESS_REALITY
        "wireguard" -> ProtocolType.WIREGUARD
        "openvpn_udp" -> ProtocolType.OPENVPN_UDP
        "openvpn_tcp" -> ProtocolType.OPENVPN_TCP
        else -> ProtocolType.AMNEZIAWG
    }
}
