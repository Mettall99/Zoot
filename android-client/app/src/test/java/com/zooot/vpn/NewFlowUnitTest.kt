package com.zooot.vpn

import com.zooot.vpn.api.DemoConfigResolver
import com.zooot.vpn.api.DemoConfigUnavailableException
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.selector.HealthStatus
import com.zooot.vpn.selector.Proto
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.ServerProtocol
import com.zooot.vpn.selector.ServerStatus
import org.junit.Assert.*
import org.junit.Test
import com.google.gson.JsonParser

class NewFlowUnitTest {
    @Test fun manualLinkParsing() { assertEquals("demo-token", LinkInputParser.parseToken("zoootconf://demo-token")) }
    @Test fun manualAndDeepLinkUseSameParserPath() {
        val raw = "zoootconf://demo-token"
        assertEquals(LinkInputParser.parseToken(raw), LinkInputParser.validate(raw).token)
    }
    @Test fun resolveBodyContainsDeviceIdentityFields() {
        val body = ZootApiClient.buildResolveTokenBody("demo-token", "device-1", LinkFlowContract.DEVICE_NAME)
        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("device-1", json.get("device_id").asString)
        assertEquals(LinkFlowContract.DEVICE_NAME, json.get("device_name").asString)
    }

    @Test fun demoTokenResolvesToOutlineShadowsocksWhenDemoSsUriConfigured() {
        val uri = "ss://aes-256-gcm:demo-password@example.com:8388#Demo"
        val result = DemoConfigResolver.resolveDemoToken(uri)
        val protocol = result.servers.first().protocols.first()
        assertEquals(Proto.OUTLINE_SHADOWSOCKS, protocol.type)
        assertEquals(uri, protocol.config)
        assertEquals("zoootconf_demo", protocol.configSource)
    }

    @Test fun demoTokenReturnsExplicitErrorWhenDemoSsUriMissing() {
        val error = runCatching { DemoConfigResolver.resolveDemoToken("") }.exceptionOrNull()
        assertTrue(error is DemoConfigUnavailableException)
        assertEquals(DemoConfigResolver.NOT_CONFIGURED_MESSAGE, error!!.message)
    }

    @Test fun outlineShadowsocksIsAvailableOnlyWhenConfigExists() {
        val available = UiProtocolOption.outlineShadowsocks("ss://aes-256-gcm:demo-password@example.com:8388", "zoootconf_demo")
        val missing = UiProtocolOption.outlineShadowsocks("", "zoootconf_demo")
        assertTrue(available.enabled)
        assertFalse(missing.enabled)
    }

    @Test fun demoSsUriIsNotLeakedInErrorStrings() {
        val secret = "ss://aes-256-gcm:super-secret-password@example.com:8388?plugin=v2ray#Demo"
        val error = runCatching { DemoConfigResolver.resolveDemoToken(secret) }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains(secret))
        assertFalse(error.message.orEmpty().contains("super-secret-password"))
    }
    @Test fun defaultDebugBackendUrlIsMvpBackend() { assertEquals("http://31.59.45.197:8080", LinkFlowContract.DEBUG_MVP_BACKEND_URL) }
    @Test fun invalidLinkValidation() { val r = LinkInputParser.validate("http://bad"); assertFalse(r.valid); assertEquals("Неверный формат ссылки", r.error) }
    @Test fun invalidLinkDoesNotProduceTokenForNetworkCall() {
        val r = LinkInputParser.validate("invalid")
        assertFalse(r.valid)
        assertNull(r.token)
    }
    @Test fun deepLinkStartsResolveTokenFlowParserReady() { assertNotNull(LinkInputParser.parseToken("zoootconf://demo-token")) }
    @Test fun recommendedServerChoice() {
        val servers = listOf(
            UiMapper.toUiServer(ServerCandidate("1","DE",ServerStatus.ONLINE,80,40,listOf(ServerProtocol(Proto.WIREGUARD,HealthStatus.HEALTHY,"", "a")),"Frankfurt","1.1.1.1")),
            UiMapper.toUiServer(ServerCandidate("2","DE",ServerStatus.ONLINE,20,20,listOf(ServerProtocol(Proto.WIREGUARD,HealthStatus.HEALTHY,"", "b")),"Berlin","2.2.2.2"))
        )
        assertEquals("2", ServerRecommendation.pick(servers)?.id)
    }
    @Test fun nullStringWireGuardConfigNotRecommended() {
        val servers = listOf(
            UiServer("1","DE","Frankfurt","1.1.1.1",20,20,true,"null","demo_fallback"),
            UiServer("2","DE","Berlin","2.2.2.2",30,30,true,"","device")
        )
        assertEquals("1", ServerRecommendation.pick(servers)?.id)
    }
    @Test fun timerFormattingWorks() { assertEquals("00:00:05", TimerFormatter.formatElapsed(5000)) }
    @Test fun realTokenResolveBodyContainsOnlyRawToken() {
        val body = ZootApiClient.buildVpnResolveBody("real-token")
        val json = JsonParser.parseString(body).asJsonObject
        assertEquals("real-token", json.get("token").asString)
        assertFalse(json.has("device_id"))
    }

    @Test fun backendOutlineShadowsocksResponseCanSelectOutlineAdapter() {
        val ss = "ss://aes-256-gcm:demo-password@example.com:8388#Server"
        val servers = listOf(
            ServerCandidate("outline-main-1", "DE", ServerStatus.ONLINE, 0, 0, listOf(ServerProtocol(Proto.OUTLINE_SHADOWSOCKS, HealthStatus.HEALTHY, "", ss, null, "server_generated")), "Frankfurt", "")
        )
        val selection = com.zooot.vpn.selector.ProtocolSelector.select(servers, "DE", com.zooot.vpn.selector.NetworkType.WIFI, emptyMap())
        assertEquals(Proto.OUTLINE_SHADOWSOCKS, selection?.protocol)
        assertEquals(ss, selection?.config)
    }

    @Test fun directSsAndVlessSchemesStillResolveToAdapters() {
        assertEquals(com.zooot.vpn.vpn.protocol.ProtocolType.OUTLINE_SHADOWSOCKS, com.zooot.vpn.vpn.protocol.ProtocolSchemeSelector.typeForConnectionString("ss://aes-256-gcm:pass@example.com:8388"))
        assertEquals(com.zooot.vpn.vpn.protocol.ProtocolType.XRAY_VLESS_REALITY, com.zooot.vpn.vpn.protocol.ProtocolSchemeSelector.typeForConnectionString("vless://uuid@example.com:443"))
    }
}
