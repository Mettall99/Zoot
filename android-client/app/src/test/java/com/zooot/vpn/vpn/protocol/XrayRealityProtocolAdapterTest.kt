package com.zooot.vpn.vpn.protocol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import com.google.gson.JsonParser
import org.junit.Test

class XrayRealityProtocolAdapterTest {
    private val validJson = """
        {"protocol":"xray_vless_reality","host":"vpn.example.com","port":443,"uuid":"11111111-1111-1111-1111-111111111111","public_key":"PUBKEY_VALUE_1234567890","short_id":"abcdef1234567890","server_name":"www.cloudflare.com","flow":"xtls-rprx-vision","fingerprint":"chrome"}
    """.trimIndent()

    @Test
    fun parseConfig_returnsClientConfig_forValidJson() {
        val parsed = XrayRealityProtocolAdapter.parseConfig(validJson)

        assertNotNull(parsed)
        assertEquals("vpn.example.com", parsed!!.host)
        assertEquals(443, parsed.port)
        assertEquals("www.cloudflare.com", parsed.serverName)
        assertEquals("xtls-rprx-vision", parsed.flow)
    }

    @Test
    fun parseConfig_returnsNull_forNullOrEmptyConfig() {
        assertEquals(null, XrayRealityProtocolAdapter.parseConfig(null))
        assertEquals(null, XrayRealityProtocolAdapter.parseConfig(""))
        assertEquals(null, XrayRealityProtocolAdapter.parseConfig("null"))
    }

    @Test
    fun buildSingBoxConfig_containsTunInboundAndVlessRealityOutbound() {
        val parsed = XrayRealityProtocolAdapter.parseConfig(validJson)!!

        val config = XrayRealityProtocolAdapter.buildSingBoxConfig(parsed)

        assertTrue(config.contains("\"type\":\"tun\""))
        assertTrue(config.contains("\"tag\":\"tun-in\""))
        assertTrue(config.contains("\"type\":\"vless\""))
        assertTrue(config.contains("\"reality\""))
        assertTrue(config.contains("\"public_key\""))
        assertTrue(config.contains("\"short_id\""))

        val root = JsonParser.parseString(config).asJsonObject
        val tunInbound = root.getAsJsonArray("inbounds").first { it.asJsonObject.get("type").asString == "tun" }.asJsonObject
        val vlessOutbound = root.getAsJsonArray("outbounds").first { it.asJsonObject.get("type").asString == "vless" }.asJsonObject
        assertEquals("tun", tunInbound.get("type").asString)
        assertEquals("vless", vlessOutbound.get("type").asString)
        assertTrue(vlessOutbound.get("server_port").asJsonPrimitive.isNumber)
        assertTrue(vlessOutbound.has("flow"))
        assertTrue(vlessOutbound.get("tls").isJsonObject)
        assertTrue(vlessOutbound.getAsJsonObject("tls").get("reality").isJsonObject)
        assertTrue(vlessOutbound.getAsJsonObject("tls").get("utls").isJsonObject)
    }

    @Test
    fun prepare_returnsFailure_whenCoreDependencyMissing() = runBlocking {
        val result = XrayRealityProtocolAdapter(MissingRealityCore()).prepare(VpnConfig("srv1", "", validJson))

        assertFalse(result.ok)
        assertEquals(XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE, result.message)
    }

    @Test
    fun prepare_returnsExplicitUnsupportedCoreError_whenRuntimeApiMissing() = runBlocking {
        val result = XrayRealityProtocolAdapter(UnsupportedRealityCore()).prepare(VpnConfig("srv1", "", validJson))

        assertFalse(result.ok)
        assertEquals(XrayRealityProtocolAdapter.UNSUPPORTED_CORE_MESSAGE, result.message)
    }

    @Test
    fun prepareAndConnect_returnSuccess_whenCoreAvailable() = runBlocking {
        val core = FakeRealityCore(bundled = true)
        val adapter = XrayRealityProtocolAdapter(core)

        val prepare = adapter.prepare(VpnConfig("srv1", "", validJson))
        val connect = adapter.connect(VpnConfig("srv1", "", validJson))

        assertTrue(prepare.ok)
        assertTrue(connect.ok)
        assertTrue(core.startedConfig!!.contains("vless"))
        assertTrue(core.startedConfig!!.contains("11111111-1111-1111-1111-111111111111"))
    }

    @Test
    fun safeExceptionMessage_redactsSensitiveValues() {
        val uuid = "11111111-1111-1111-1111-111111111111"
        val publicKey = "PUBKEY_VALUE_1234567890"
        val shortId = "abcdef1234567890abcdef"
        val sanitized = XrayRealityProtocolAdapter.safeExceptionMessage(
            IllegalStateException("failed uuid=$uuid key=$publicKey sid=$shortId")
        )

        assertFalse(sanitized.contains(uuid))
        assertFalse(sanitized.contains(publicKey))
        assertFalse(sanitized.contains(shortId))
    }

    @Test
    fun connect_returnsUnavailable_forInvalidConfig() = runBlocking {
        val core = FakeRealityCore(bundled = true)
        val adapter = XrayRealityProtocolAdapter(core)

        val result = adapter.connect(VpnConfig("srv1", "", null))

        assertFalse(result.ok)
        assertEquals("Reality/TCP config is not available", result.message)
        assertEquals(null, core.startedConfig)
    }

    @Test
    fun missingRealityCore_returnsFailureMessage() {
        val core = MissingRealityCore()

        assertFalse(core.isBundled())
        assertEquals(XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE, assertFailsWithMessage { core.start("{}") })
    }

    @Test
    fun safeExceptionMessage_doesNotExposeSensitiveValues() {
        val uuid = "11111111-1111-1111-1111-111111111111"
        val publicKey = "PUBKEY_VALUE_1234567890"
        val token = "token_abcdefghijklmnopqrstuvwxyz1234567890"

        val sanitized = XrayRealityProtocolAdapter.safeExceptionMessage(
            IllegalStateException("uuid=$uuid public_key=$publicKey token=$token")
        )

        assertFalse(sanitized.contains(uuid))
        assertFalse(sanitized.contains(publicKey))
        assertFalse(sanitized.contains(token))
    }


    @Test
    fun connect_returnsDetailedRealityFailure_whenCoreThrows() = runBlocking {
        val core = FailingRealityCore(IllegalStateException(XrayRealityProtocolAdapter.UNSUPPORTED_CORE_MESSAGE))
        val adapter = XrayRealityProtocolAdapter(core)

        val result = adapter.connect(VpnConfig("srv1", "", validJson))

        assertFalse(result.ok)
        assertEquals("Reality failed: ${XrayRealityProtocolAdapter.UNSUPPORTED_CORE_MESSAGE}", result.message)
    }

    @Test
    fun connect_returnsDetailedRealityFailure_whenCoreDoesNotRun() = runBlocking {
        val core = FakeRealityCore(bundled = true, runningAfterStart = false)
        val adapter = XrayRealityProtocolAdapter(core)

        val result = adapter.connect(VpnConfig("srv1", "", validJson))

        assertFalse(result.ok)
        assertEquals("Reality failed: service did not report running", result.message)
    }

    private fun assertFailsWithMessage(block: () -> Unit): String {
        return try {
            block()
            ""
        } catch (e: IllegalStateException) {
            e.message.orEmpty()
        }
    }

    private class FakeRealityCore(private val bundled: Boolean, private val runningAfterStart: Boolean = true) : RealityCore {
        var startedConfig: String? = null
        private var running = false
        override fun isBundled(): Boolean = bundled
        override fun start(singBoxConfigJson: String) {
            startedConfig = singBoxConfigJson
            running = runningAfterStart
        }
        override fun stop() { running = false }
        override fun isRunning(): Boolean = running
    }

    private class UnsupportedRealityCore : RealityCore {
        override fun isBundled(): Boolean = true
        override fun unavailableReason(): String = XrayRealityProtocolAdapter.UNSUPPORTED_CORE_MESSAGE
        override fun start(singBoxConfigJson: String) = error("unsupported core should not be started")
        override fun stop() = Unit
        override fun isRunning(): Boolean = false
    }

    private class FailingRealityCore(private val failure: RuntimeException) : RealityCore {
        override fun isBundled(): Boolean = true
        override fun start(singBoxConfigJson: String) { throw failure }
        override fun stop() = Unit
        override fun isRunning(): Boolean = false
    }
}
