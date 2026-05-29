package com.zooot.vpn.vpn.protocol

enum class ProtocolType { WIREGUARD, AMNEZIAWG, XRAY_VLESS_REALITY, OUTLINE_SHADOWSOCKS, OPENVPN_UDP, OPENVPN_TCP }

data class VpnConfig(val serverId: String, val configUrl: String, val config: String? = null)

data class PrepareResult(val ok: Boolean, val message: String = "")
data class ConnectResult(val ok: Boolean, val message: String = "")
data class DisconnectResult(val ok: Boolean, val message: String = "")
data class HealthCheckResult(val ok: Boolean, val latencyMs: Int? = null)

interface VpnProtocolAdapter {
    val type: ProtocolType
    suspend fun prepare(config: VpnConfig): PrepareResult
    suspend fun connect(config: VpnConfig): ConnectResult
    suspend fun disconnect(): DisconnectResult
    suspend fun healthCheck(config: VpnConfig): HealthCheckResult
}
