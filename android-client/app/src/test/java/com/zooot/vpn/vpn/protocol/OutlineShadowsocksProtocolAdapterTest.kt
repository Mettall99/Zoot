package com.zooot.vpn.vpn.protocol

import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OutlineShadowsocksProtocolAdapterTest {
    @Test
    fun parseSip002Base64Credentials() {
        val uri = "ss://${b64("chacha20-ietf-poly1305:secret-password")}@vpn.example.com:8388#Office"

        val parsed = OutlineShadowsocksProtocolAdapter.parseUri(uri)

        assertEquals("chacha20-ietf-poly1305", parsed.method)
        assertEquals("secret-password", parsed.password)
        assertEquals("vpn.example.com", parsed.host)
        assertEquals(8388, parsed.port)
        assertEquals("Office", parsed.name)
    }

    @Test
    fun parseUserinfoFormat() {
        val uri = "ss://aes-256-gcm:secret-password@vpn.example.com:8388#Office"

        val parsed = OutlineShadowsocksProtocolAdapter.parseUri(uri)

        assertEquals("aes-256-gcm", parsed.method)
        assertEquals("secret-password", parsed.password)
        assertEquals("vpn.example.com", parsed.host)
        assertEquals(8388, parsed.port)
        assertEquals("Office", parsed.name)
    }

    @Test
    fun parseOutlineStyleBase64FullUri() {
        val uri = "ss://${b64("aes-128-gcm:secret-password@vpn.example.com:8388")}#Office"

        val parsed = OutlineShadowsocksProtocolAdapter.parseUri(uri)

        assertEquals("aes-128-gcm", parsed.method)
        assertEquals("secret-password", parsed.password)
        assertEquals("vpn.example.com", parsed.host)
        assertEquals(8388, parsed.port)
        assertEquals("Office", parsed.name)
    }

    @Test
    fun parseFailsForInvalidBase64() {
        val failure = OutlineShadowsocksProtocolAdapter.tryParseConfig("ss://not-base64#Office").exceptionOrNull()

        assertNotNull(failure)
        assertEquals(OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE, failure!!.message)
    }

    @Test
    fun parseFailsForMissingHost() {
        val failure = OutlineShadowsocksProtocolAdapter.tryParseConfig("ss://aes-256-gcm:secret-password@:8388#Office").exceptionOrNull()

        assertNotNull(failure)
        assertEquals(OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE, failure!!.message)
    }

    @Test
    fun parseFailsForMissingPort() {
        val failure = OutlineShadowsocksProtocolAdapter.tryParseConfig("ss://aes-256-gcm:secret-password@vpn.example.com#Office").exceptionOrNull()

        assertNotNull(failure)
        assertEquals(OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE, failure!!.message)
    }

    @Test
    fun parseFailsForUnsupportedMethod() {
        val failure = OutlineShadowsocksProtocolAdapter.tryParseConfig("ss://rc4-md5:secret-password@vpn.example.com:8388#Office").exceptionOrNull()

        assertNotNull(failure)
        assertEquals("Unsupported Shadowsocks method: rc4-md5", failure!!.message)
    }

    @Test
    fun parseFailsForPlugin() {
        val failure = OutlineShadowsocksProtocolAdapter.tryParseConfig("ss://aes-256-gcm:secret-password@vpn.example.com:8388?plugin=v2ray-plugin#Office").exceptionOrNull()

        assertNotNull(failure)
        assertEquals(OutlineShadowsocksProtocolAdapter.PLUGINS_UNSUPPORTED_MESSAGE, failure!!.message)
    }

    @Test
    fun passwordDoesNotAppearInSafeLogOrErrorMessage() {
        val password = "secret-password"
        val message = OutlineShadowsocksProtocolAdapter.safeExceptionMessage(
            IllegalStateException("ss://aes-256-gcm:$password@vpn.example.com:8388 password=$password")
        )

        assertFalse(message.contains(password))
    }

    @Test
    fun buildSingBoxConfig_containsTunInboundAndShadowsocksOutbound() {
        val client = ShadowsocksClientConfig(
            method = "chacha20-ietf-poly1305",
            password = "secret-password",
            host = "vpn.example.com",
            port = 8388,
            name = "Office"
        )

        val config = OutlineShadowsocksProtocolAdapter.buildSingBoxConfig(client)
        val root = JsonParser.parseString(config).asJsonObject
        val tunInbound = root.getAsJsonArray("inbounds").first { it.asJsonObject.get("type").asString == "tun" }.asJsonObject
        val shadowsocksOutbound = root.getAsJsonArray("outbounds").first { it.asJsonObject.get("type").asString == "shadowsocks" }.asJsonObject

        assertEquals("tun", tunInbound.get("type").asString)
        assertEquals("tun-in", tunInbound.get("tag").asString)
        assertEquals("shadowsocks", shadowsocksOutbound.get("type").asString)
        assertTrue(shadowsocksOutbound.get("server_port").asJsonPrimitive.isNumber)
        assertEquals("chacha20-ietf-poly1305", shadowsocksOutbound.get("method").asString)
        assertEquals("secret-password", shadowsocksOutbound.get("password").asString)
        assertEquals("shadowsocks-out", root.getAsJsonObject("route").get("final").asString)
        assertFalse(OutlineShadowsocksProtocolAdapter.safeLogMessage(config).contains("secret-password"))
    }

    @Test
    fun connectPassesGeneratedConfigToCore() = runBlocking {
        val core = FakeShadowsocksCore()
        val adapter = OutlineShadowsocksProtocolAdapter(core)

        val result = adapter.connect(VpnConfig("srv1", "", "ss://aes-256-gcm:secret-password@vpn.example.com:8388#Office"))

        assertTrue(result.ok)
        assertNotNull(core.startedConfig)
        assertTrue(core.startedConfig!!.contains("\"type\":\"shadowsocks\""))
    }

    private fun b64(value: String): String = Base64.getEncoder().withoutPadding().encodeToString(value.toByteArray())

    private class FakeShadowsocksCore : ShadowsocksCore {
        var startedConfig: String? = null
        private var running = false
        override fun isBundled(): Boolean = true
        override fun start(singBoxConfigJson: String) {
            startedConfig = singBoxConfigJson
            running = true
        }
        override fun stop() { running = false }
        override fun isRunning(): Boolean = running
    }
}
