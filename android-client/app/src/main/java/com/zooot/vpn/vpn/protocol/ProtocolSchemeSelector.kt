package com.zooot.vpn.vpn.protocol

object ProtocolSchemeSelector {
    fun typeForConnectionString(raw: String): ProtocolType = when {
        raw.trim().startsWith("ss://", ignoreCase = true) -> ProtocolType.OUTLINE_SHADOWSOCKS
        raw.trim().startsWith("vless://", ignoreCase = true) -> ProtocolType.XRAY_VLESS_REALITY
        else -> throw IllegalArgumentException("Unsupported VPN link scheme")
    }

    fun typeForResolvedConfig(raw: String, resolvedProtocol: ProtocolType? = null): ProtocolType = when {
        raw.trim().startsWith("ss://", ignoreCase = true) -> ProtocolType.OUTLINE_SHADOWSOCKS
        raw.trim().startsWith("vless://", ignoreCase = true) -> ProtocolType.XRAY_VLESS_REALITY
        raw.trim().startsWith("zoootconf://demo-token", ignoreCase = true) && resolvedProtocol == ProtocolType.OUTLINE_SHADOWSOCKS -> ProtocolType.OUTLINE_SHADOWSOCKS
        else -> throw IllegalArgumentException("Unsupported VPN link scheme")
    }
}
