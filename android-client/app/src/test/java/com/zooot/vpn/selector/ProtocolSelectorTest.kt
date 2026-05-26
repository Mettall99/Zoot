package com.zooot.vpn.selector

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolSelectorTest {
    @Test
    fun picksBestProtocolOnBestServer() {
        val servers = listOf(
            ServerCandidate("srv1", "NL", ServerStatus.ONLINE, 20, 80, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "u1"), ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "u2"))),
            ServerCandidate("srv2", "NL", ServerStatus.ONLINE, 50, 120, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "u3")))
        )
        val res = ProtocolSelector.select(servers, "NL", NetworkType.WIFI, emptyMap())
        assertEquals("srv1", res?.serverId)
        assertEquals(Proto.AMNEZIAWG, res?.protocol)
    }
}
