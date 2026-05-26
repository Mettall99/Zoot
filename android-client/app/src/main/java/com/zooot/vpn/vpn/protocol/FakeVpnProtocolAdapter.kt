package com.zooot.vpn.vpn.protocol

class FakeVpnProtocolAdapter : VpnProtocolAdapter {
    override val type = ProtocolType.WIREGUARD
    override suspend fun prepare(config: VpnConfig) = PrepareResult(true)
    override suspend fun connect(config: VpnConfig) = ConnectResult(true)
    override suspend fun disconnect() = DisconnectResult(true)
    override suspend fun healthCheck(config: VpnConfig) = HealthCheckResult(true, 50)
}
