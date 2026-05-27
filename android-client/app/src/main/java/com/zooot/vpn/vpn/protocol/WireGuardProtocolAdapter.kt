package com.zooot.vpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class WireGuardProtocolAdapter(
    context: Context
) : VpnProtocolAdapter {
    companion object {
        private const val TAG = "WireGuardAdapter"
    }
    override val type: ProtocolType = ProtocolType.WIREGUARD
    private val backend = GoBackend(context)
    private val tunnel = SimpleTunnel("zooot")

    override suspend fun prepare(config: VpnConfig): PrepareResult =
        if (config.config.isNullOrBlank()) PrepareResult(false, "WireGuard config missing") else PrepareResult(true, "Prepared")

    override suspend fun connect(config: VpnConfig): ConnectResult {
        val raw = config.config
        if (raw.isNullOrBlank()) return ConnectResult(false, "WireGuard config missing")
        Log.d(TAG, "connect: selected protocol=wireguard, config available=true")
        var state: Tunnel.State? = null
        return try {
            val parsed = Config.parse(ByteArrayInputStream(raw.toByteArray()))
            state = backend.setState(tunnel, Tunnel.State.UP, parsed)
            Log.d(TAG, "connect: tunnel state result=${state?.name ?: "unknown"}")
            ConnectResult(true, "Connected")
        } catch (e: Exception) {
            state = runCatching { backend.getState(tunnel) }.getOrNull()
            Log.w(TAG, "connect: non-fatal wireguard exception with state=${state?.name ?: "unknown"}: ${e::class.java.simpleName}")
            if (state == Tunnel.State.UP) {
                ConnectResult(true, "Connected")
            } else {
                ConnectResult(false, e.message?.take(120) ?: "WireGuard start failed")
            }
        }
    }

    override suspend fun disconnect(): DisconnectResult = try {
        val state = backend.setState(tunnel, Tunnel.State.DOWN, null)
        Log.d(TAG, "disconnect: tunnel state result=${state.name}")
        DisconnectResult(true, "Disconnected")
    } catch (e: Exception) {
        DisconnectResult(false, e.message?.take(120) ?: "WireGuard stop failed")
    }

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult = HealthCheckResult(ok = !config.config.isNullOrBlank())

    private class SimpleTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) = Unit
    }
}
