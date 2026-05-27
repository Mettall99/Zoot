package com.zooot.vpn.selector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun demoBackendFieldsCanBeMappedForUi() {
        val servers = listOf(
            ServerCandidate(
                serverId = "srv-frankfurt",
                country = "DE",
                status = ServerStatus.ONLINE,
                loadPercent = 10,
                latencyMs = 30,
                protocols = listOf(
                    ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "", "[Interface]\nPrivateKey=hidden", 51821)
                ),
                city = "Frankfurt",
                serverIp = "31.59.45.197/32"
            )
        )

        val selection = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())
        assertEquals("srv-frankfurt", selection?.serverId)
        assertEquals(Proto.WIREGUARD, selection?.protocol)
        assertEquals("DE", servers.first().country)
        assertEquals("Frankfurt", servers.first().city)
        assertEquals("31.59.45.197/32", servers.first().serverIp)
        assertFalse((selection?.config ?: "").contains("PublicKey"))
    }
}
