package com.zooot.vpn.vpn.protocol

import com.wireguard.android.backend.Tunnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WireGuardProtocolAdapterTest {
    @Test
    fun mapConnectFailure_returnsSuccess_whenStateUpEvenWithException() {
        val result = WireGuardProtocolAdapter.mapConnectFailure(
            Tunnel.State.UP,
            IllegalStateException("mkdir /data/data/com.wireguard.android: permission denied")
        )

        assertTrue(result.ok)
        assertEquals("Connected", result.message)
    }

    @Test
    fun mapConnectFailure_returnsFailure_whenStateNotUp() {
        val result = WireGuardProtocolAdapter.mapConnectFailure(
            Tunnel.State.DOWN,
            IllegalStateException("WireGuard start failed")
        )

        assertFalse(result.ok)
        assertEquals("WireGuard start failed", result.message)
    }
}
