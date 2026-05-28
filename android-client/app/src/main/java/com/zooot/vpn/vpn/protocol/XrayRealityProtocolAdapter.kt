package com.zooot.vpn.vpn.protocol

class XrayRealityProtocolAdapter : VpnProtocolAdapter {
    override val type: ProtocolType = ProtocolType.XRAY_VLESS_REALITY

    private fun hasValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"

    override suspend fun prepare(config: VpnConfig): PrepareResult =
        if (hasValidConfig(config.config)) PrepareResult(true, "Prepared")
        else PrepareResult(false, "Reality/TCP config is not available")

    override suspend fun connect(config: VpnConfig): ConnectResult =
        if (!hasValidConfig(config.config)) ConnectResult(false, "Reality/TCP config is not available")
        else ConnectResult(false, "Reality/TCP fallback config is available, but the Android Xray core adapter is not bundled yet")

    override suspend fun disconnect(): DisconnectResult = DisconnectResult(true, "Disconnected")

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult = HealthCheckResult(ok = hasValidConfig(config.config))
}
