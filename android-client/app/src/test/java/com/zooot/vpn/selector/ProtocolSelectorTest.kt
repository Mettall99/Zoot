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

class XrayRealitySelectionTest {
    @Test
    fun doesNotSelectXrayRealityWithNullOrEmptyConfig() {
        val servers = listOf(
            ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(
                ServerProtocol(Proto.XRAY_VLESS_REALITY, HealthStatus.HEALTHY, "", null, 443),
                ServerProtocol(Proto.XRAY_VLESS_REALITY, HealthStatus.HEALTHY, "", "", 443)
            ))
        )

        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())
        assertEquals(null, sel)
    }

    @Test
    fun prefersRealityFallbackWhenWireGuardPreviouslyFailed() {
        val servers = listOf(
            ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(
                ServerProtocol(Proto.WIREGUARD, HealthStatus.HEALTHY, "", "[Interface]\nPrivateKey=hidden", 51821),
                ServerProtocol(Proto.XRAY_VLESS_REALITY, HealthStatus.HEALTHY, "", "{\"protocol\":\"xray_vless_reality\",\"port\":443}", 443)
            ))
        )
        val history = mapOf(HistoryKey(NetworkType.WIFI, "srv1", Proto.WIREGUARD) to HistoryVal(success = false, failurePenalty = 100))

        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, history)
        assertEquals(Proto.XRAY_VLESS_REALITY, sel?.protocol)
    }
}

class OutlineShadowsocksSelectionTest {
    @Test
    fun doesNotSelectOutlineShadowsocksWithoutConfig() {
        val servers = listOf(
            ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(
                ServerProtocol(Proto.OUTLINE_SHADOWSOCKS, HealthStatus.HEALTHY, "", "", 8388),
                ServerProtocol(Proto.OUTLINE_SHADOWSOCKS, HealthStatus.HEALTHY, "", null, 8388)
            ))
        )

        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())

        assertEquals(null, sel)
    }

    @Test
    fun selectsOutlineShadowsocksWithConfig() {
        val servers = listOf(
            ServerCandidate("srv1", "DE", ServerStatus.ONLINE, 20, 20, listOf(
                ServerProtocol(Proto.OUTLINE_SHADOWSOCKS, HealthStatus.HEALTHY, "", "ss://aes-256-gcm:pass@example.com:8388", 8388)
            ))
        )

        val sel = ProtocolSelector.select(servers, "DE", NetworkType.WIFI, emptyMap())

        assertEquals(Proto.OUTLINE_SHADOWSOCKS, sel?.protocol)
    }
}
