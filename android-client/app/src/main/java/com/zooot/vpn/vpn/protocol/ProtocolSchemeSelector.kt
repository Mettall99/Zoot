package com.zooot.vpn.vpn.protocol

object ProtocolSchemeSelector {
    fun typeForConnectionString(raw: String): ProtocolType = when {
        raw.trim().startsWith("ss://", ignoreCase = true) -> ProtocolType.OUTLINE_SHADOWSOCKS
        raw.trim().startsWith("vless://", ignoreCase = true) -> ProtocolType.XRAY_VLESS_REALITY
        else -> throw IllegalArgumentException("Unsupported VPN link scheme")
    }
}
