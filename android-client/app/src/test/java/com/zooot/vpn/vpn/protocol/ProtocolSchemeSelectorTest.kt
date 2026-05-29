package com.zooot.vpn.vpn.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolSchemeSelectorTest {
    @Test
    fun ssUriSelectsOutlineShadowsocksAdapterType() {
        assertEquals(ProtocolType.OUTLINE_SHADOWSOCKS, ProtocolSchemeSelector.typeForConnectionString("ss://aes-256-gcm:pass@example.com:8388"))
    }

    @Test
    fun vlessUriSelectsVlessRealityAdapterType() {
        assertEquals(ProtocolType.XRAY_VLESS_REALITY, ProtocolSchemeSelector.typeForConnectionString("vless://uuid@example.com:443"))
    }

    @Test
    fun zoootconfDemoTokenWithResolvedOutlineProtocolSelectsOutlineShadowsocks() {
        assertEquals(
            ProtocolType.OUTLINE_SHADOWSOCKS,
            ProtocolSchemeSelector.typeForResolvedConfig("zoootconf://demo-token", ProtocolType.OUTLINE_SHADOWSOCKS)
        )
    }

    @Test
    fun unsupportedSchemeReturnsExplicitError() {
        val error = runCatching { ProtocolSchemeSelector.typeForConnectionString("trojan://example.com") }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Unsupported VPN link scheme", error!!.message)
    }
}
