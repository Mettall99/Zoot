package com.zooot.vpn.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiStateMappingTest {
    @Test
    fun selectedServerHasExpectedCityAndProtocol() {
        val servers = listOf(
            ServerCandidate(
                serverId = "srv-frankfurt",
                country = "DE",
                status = ServerStatus.ONLINE,
                loadPercent = 10,
                latencyMs = 30,
                protocols = listOf(ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "")),
                city = "Frankfurt",
                serverIp = "1.2.3.4"
            )
        )

        val selection = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())
        assertEquals("srv-frankfurt", selection?.serverId)
        assertEquals(Proto.AMNEZIAWG, selection?.protocol)
        assertEquals("Frankfurt", servers.first().city)
    }

    @Test
    fun noServersReturnsNullSelection() {
        val selection = ProtocolSelector.select(emptyList(), "DE", NetworkType.WIFI, emptyMap())
        assertNull(selection)
    }
}
