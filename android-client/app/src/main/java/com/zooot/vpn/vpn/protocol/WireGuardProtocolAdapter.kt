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

        internal fun mapConnectFailure(state: Tunnel.State?, error: Exception): ConnectResult {
            if (state == Tunnel.State.UP) return ConnectResult(true, "Connected")
            return ConnectResult(false, error.message?.take(120) ?: "WireGuard start failed")
        }
    }
    override val type: ProtocolType = ProtocolType.WIREGUARD
    private val backend = GoBackend(context)
    private val tunnel = SimpleTunnel("zooot")

    private fun isValidWireGuardConfig(raw: String?): Boolean = !raw.isNullOrBlank() && raw.trim().lowercase() != "null"

    override suspend fun prepare(config: VpnConfig): PrepareResult =
        if (!isValidWireGuardConfig(config.config)) PrepareResult(false, "WireGuard config missing") else PrepareResult(true, "Prepared")

    override suspend fun connect(config: VpnConfig): ConnectResult {
        val raw = config.config
        if (!isValidWireGuardConfig(raw)) return ConnectResult(false, "WireGuard config missing")
        val wireGuardConfig = raw!!
        Log.d(TAG, "connect: selected protocol=wireguard, config available=true")
        return try {
            val parsed = Config.parse(ByteArrayInputStream(wireGuardConfig.toByteArray()))
            val state = backend.setState(tunnel, Tunnel.State.UP, parsed)
            Log.d(TAG, "connect: tunnel state result=${state.name}")
            if (state == Tunnel.State.UP) ConnectResult(true, "Connected")
            else ConnectResult(false, "WireGuard start failed")
        } catch (e: Exception) {
            val state = runCatching { backend.getState(tunnel) }.getOrNull()
            Log.w(TAG, "connect: non-fatal wireguard exception with state=${state?.name ?: "unknown"}: ${e::class.java.simpleName}")
            mapConnectFailure(state, e)
        }
    }


    override suspend fun disconnect(): DisconnectResult = try {
        val state = backend.setState(tunnel, Tunnel.State.DOWN, null)
        Log.d(TAG, "disconnect: tunnel state result=${state.name}")
        DisconnectResult(state == Tunnel.State.DOWN, if (state == Tunnel.State.DOWN) "Disconnected" else "WireGuard stop failed")
    } catch (e: Exception) {
        DisconnectResult(false, e.message?.take(120) ?: "WireGuard stop failed")
    }

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult = HealthCheckResult(ok = isValidWireGuardConfig(config.config))

    private class SimpleTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) = Unit
    }
}
