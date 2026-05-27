package com.zooot.vpn.selector

import kotlin.test.Test
import kotlin.test.assertEquals

class WireGuardMvpSelectionTest {
    @Test
    fun wireguardWithConfigPreferredOverAmneziaForMvp() {
        val servers = listOf(
            ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(
                ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, ""),
                ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "", "[Interface]\nPrivateKey=hidden")
            ))
        )
        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())
        assertEquals(Proto.WIREGUARD, sel?.protocol)
    }

    @Test
    fun nullStringConfigDoesNotSelectWireGuard() {
        val servers = listOf(ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "", "null"))))
        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())
        assertEquals(null, sel)
    }
}
