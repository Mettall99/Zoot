package com.zooot.vpn.vpn.protocol

class FakeVpnProtocolAdapter(override val type: ProtocolType) : VpnProtocolAdapter {
    override suspend fun prepare(config: VpnConfig): PrepareResult =
        PrepareResult(ok = true, message = "Prepared")

    override suspend fun connect(config: VpnConfig): ConnectResult =
        ConnectResult(ok = true, message = "Connected")

    override suspend fun disconnect(): DisconnectResult =
        DisconnectResult(ok = true, message = "Disconnected")

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult =
        HealthCheckResult(ok = true, latencyMs = 42)
}
