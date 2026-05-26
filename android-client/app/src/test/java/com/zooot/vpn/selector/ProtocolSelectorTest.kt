package com.zooot.vpn.selector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProtocolSelectorTest {
    @Test fun choosesOnlineServerOnly() {
        val servers = listOf(
            ServerCandidate("off", "DE", ServerStatus.OFFLINE, 1, 1, listOf(ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "x"))),
            ServerCandidate("on", "DE", ServerStatus.ONLINE, 50, 100, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "y")))
        )
        assertEquals("on", ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())?.serverId)
    }

    @Test fun ignoresFailedProtocol() {
        val servers = listOf(ServerCandidate("s", "DE", ServerStatus.ONLINE, 10, 10, listOf(
            ServerProtocol(Proto.AMNEZIAWG, HealthStatus.FAILED, "a"),
            ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "w")
        )))
        assertEquals(Proto.WIREGUARD, ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())?.protocol)
    }

    @Test fun accountsForFailurePenaltyAndLatencyAndLoad() {
        val servers = listOf(
            ServerCandidate("s1", "DE", ServerStatus.ONLINE, 20, 300, listOf(ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "a"))),
            ServerCandidate("s2", "DE", ServerStatus.ONLINE, 20, 60, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "w")))
        )
        val history = mapOf(HistoryKey(NetworkType.WIFI, "s1", Proto.AMNEZIAWG) to HistoryVal(success = true, failurePenalty = 90))
        assertEquals("s2", ProtocolSelector.select(servers, "DE", NetworkType.WIFI, history)?.serverId)
    }

    @Test fun respectsSelectedCountry() {
        val servers = listOf(ServerCandidate("s", "NL", ServerStatus.ONLINE, 10, 10, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "w"))))
        assertNull(ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap()))
    }

    @Test fun fallbackToNextServerWhenNoHealthyProtocols() {
        val servers = listOf(
            ServerCandidate("s1", "DE", ServerStatus.ONLINE, 10, 10, listOf(ServerProtocol(Proto.AMNEZIAWG, HealthStatus.FAILED, "a"))),
            ServerCandidate("s2", "DE", ServerStatus.ONLINE, 15, 20, listOf(ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "w")))
        )
        assertEquals("s2", ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())?.serverId)
    }
}
