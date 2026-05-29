package com.zooot.vpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.zooot.vpn.vpn.LibboxRuntimeSupport
import com.zooot.vpn.vpn.ZootVpnService
import java.net.URLDecoder
import java.util.Base64

class OutlineShadowsocksProtocolAdapter(
    private val core: ShadowsocksCore = SingBoxShadowsocksCore()
) : VpnProtocolAdapter {
    constructor(context: Context) : this(SingBoxShadowsocksCore(context))

    override val type: ProtocolType = ProtocolType.OUTLINE_SHADOWSOCKS

    companion object {
        private const val TAG = "OutlineShadowsocks"
        const val PLUGINS_UNSUPPORTED_MESSAGE = "Shadowsocks plugins are not supported yet"
        const val INVALID_LINK_MESSAGE = "Invalid Shadowsocks link"
        val UNSUPPORTED_CORE_MESSAGE = LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE
        private val SUPPORTED_METHODS = setOf("chacha20-ietf-poly1305", "aes-256-gcm", "aes-128-gcm")

        internal fun parseConfig(raw: String?): ShadowsocksClientConfig? = tryParseConfig(raw).getOrNull()

        internal fun tryParseConfig(raw: String?): Result<ShadowsocksClientConfig> = runCatching {
            val value = raw?.trim().orEmpty()
            require(value.isNotBlank() && value.lowercase() != "null" && value.startsWith("ss://", ignoreCase = true)) {
                INVALID_LINK_MESSAGE
            }
            parseUri(value)
        }

        internal fun parseUri(uri: String): ShadowsocksClientConfig {
            require(uri.startsWith("ss://", ignoreCase = true)) { INVALID_LINK_MESSAGE }
            val withoutScheme = uri.substringAfter("://")
            val fragment = withoutScheme.substringAfter("#", "")
            val beforeFragment = withoutScheme.substringBefore("#")
            val query = beforeFragment.substringAfter("?", "")
            if (queryHasPlugin(query)) throw IllegalArgumentException(PLUGINS_UNSUPPORTED_MESSAGE)
            val mainPart = beforeFragment.substringBefore("?")
            require(mainPart.isNotBlank()) { INVALID_LINK_MESSAGE }

            val parsed = if (mainPart.contains("@")) {
                val credentialsPart = mainPart.substringBeforeLast("@")
                val authorityPart = mainPart.substringAfterLast("@")
                parseSplitCredentials(credentialsPart, authorityPart)
            } else {
                parseDecodedFullUri(mainPart)
            }
            val config = parsed.copy(name = decode(fragment).ifBlank { null })
            config.validate()
            return config
        }

        internal fun buildSingBoxConfig(client: ShadowsocksClientConfig): String = JsonObject().apply {
            add("log", JsonObject().apply { addProperty("level", "info") })
            add("inbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "tun")
                    addProperty("tag", "tun-in")
                    addProperty("interface_name", "zooot0")
                    add("address", JsonArray().apply { add("172.19.0.1/30") })
                    addProperty("mtu", 9000)
                    addProperty("auto_route", true)
                    addProperty("strict_route", false)
                    addProperty("stack", "system")
                })
            })
            add("outbounds", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "shadowsocks")
                    addProperty("tag", "shadowsocks-out")
                    addProperty("server", client.host)
                    addProperty("server_port", client.port)
                    addProperty("method", client.method)
                    addProperty("password", client.password)
                })
            })
            add("route", JsonObject().apply {
                addProperty("final", "shadowsocks-out")
                addProperty("auto_detect_interface", true)
            })
        }.toString()

        internal fun shadowsocksFailureMessage(reason: String): String =
            "Shadowsocks runtime failed: ${reason.ifBlank { "unknown startup error" }}"

        internal fun safeExceptionMessage(e: Throwable): String = sanitizeForLogs(e.message)
        internal fun safeLogMessage(message: String): String = sanitizeForLogs(message)
        internal fun maskedHost(host: String): String = when {
            host.isBlank() -> "<empty>"
            host.length <= 2 -> "**"
            host.contains(":") -> "<ipv6>"
            else -> host.take(1) + "***" + host.takeLast(1)
        }

        private fun parseSplitCredentials(credentialsPart: String, authorityPart: String): ShadowsocksClientConfig {
            val decodedCredentials = decodeBase64OrNull(credentialsPart)?.let(::decode) ?: decode(credentialsPart)
            if (decodedCredentials.contains("plugin=", ignoreCase = true) || decodedCredentials.contains("plugin_opts=", ignoreCase = true)) {
                throw IllegalArgumentException(PLUGINS_UNSUPPORTED_MESSAGE)
            }
            val method = decodedCredentials.substringBefore(":", "")
            val password = decodedCredentials.substringAfter(":", "")
            require(method.isNotBlank() && password.isNotBlank()) { INVALID_LINK_MESSAGE }
            val (host, port) = parseHostPort(authorityPart)
            return ShadowsocksClientConfig(method = method.lowercase(), password = password, host = host, port = port, name = null)
        }

        private fun parseDecodedFullUri(encodedPart: String): ShadowsocksClientConfig {
            val decoded = decodeBase64OrThrow(encodedPart)
            if (decoded.contains("plugin=", ignoreCase = true) || decoded.contains("plugin_opts=", ignoreCase = true)) {
                throw IllegalArgumentException(PLUGINS_UNSUPPORTED_MESSAGE)
            }
            val credentials = decoded.substringBeforeLast("@", "")
            val authority = decoded.substringAfterLast("@", "")
            require(credentials.isNotBlank() && authority.isNotBlank()) { INVALID_LINK_MESSAGE }
            return parseSplitCredentials(credentials, authority)
        }

        private fun parseHostPort(authorityPart: String): Pair<String, Int> {
            val authority = decode(authorityPart).trim()
            require(authority.isNotBlank()) { INVALID_LINK_MESSAGE }
            val host = if (authority.startsWith("[")) authority.substringAfter("[").substringBefore("]") else authority.substringBeforeLast(":", "")
            val portRaw = if (authority.startsWith("[")) authority.substringAfter("]:", "") else authority.substringAfterLast(":", "")
            require(host.isNotBlank()) { INVALID_LINK_MESSAGE }
            val port = portRaw.toIntOrNull() ?: throw IllegalArgumentException(INVALID_LINK_MESSAGE)
            return host to port
        }

        private fun queryHasPlugin(query: String): Boolean = query.split("&")
            .filter { it.isNotBlank() }
            .any {
                val key = decode(it.substringBefore("=", it)).lowercase()
                key == "plugin" || key == "plugin_opts"
            }

        private fun decodeBase64OrThrow(value: String): String = decodeBase64OrNull(value)
            ?: throw IllegalArgumentException(INVALID_LINK_MESSAGE)

        private fun decodeBase64OrNull(value: String): String? {
            val normalized = value.trim().replace('-', '+').replace('_', '/')
            val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
            return runCatching { String(Base64.getDecoder().decode(padded), Charsets.UTF_8) }.getOrNull()
        }

        private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

        private fun sanitizeForLogs(message: String?): String = LibboxRuntimeSupport.sanitize(message)
            .replace(Regex("ss://[^\\s,;)]*", RegexOption.IGNORE_CASE), "ss://<redacted>")
            .replace(Regex("""(?i)"password"\s*:\s*"[^"]*"""), "\"password\":\"<redacted>\"")
            .replace(Regex("(?i)(password|method|server|host)[=:/][^\\s,;)]*"), "${'$'}1=<redacted>")
            .take(160)
    }

    override suspend fun prepare(config: VpnConfig): PrepareResult {
        safeLogDebug("protocol selected=outline_shadowsocks")
        safeLogDebug("shadowsocks uri parse start")
        val parsed = tryParseConfigForAdapter(config.config) ?: return PrepareResult(false, INVALID_LINK_MESSAGE)
        logConfigAvailability(parsed)
        core.unavailableReason()?.let { reason ->
            safeLogWarn("prepare: protocol=outline_shadowsocks core_start_success=false unavailable_core=${safeLogMessage(reason)}")
            return PrepareResult(false, reason)
        }
        return PrepareResult(true, "Prepared")
    }

    override suspend fun connect(config: VpnConfig): ConnectResult {
        safeLogDebug("protocol selected=outline_shadowsocks")
        safeLogDebug("shadowsocks uri parse start")
        val parsed = tryParseConfigForAdapter(config.config) ?: return ConnectResult(false, INVALID_LINK_MESSAGE)
        logConfigAvailability(parsed)
        core.unavailableReason()?.let { reason ->
            safeLogWarn("connect: protocol=outline_shadowsocks core_start_success=false unavailable_core=${safeLogMessage(reason)}")
            return ConnectResult(false, reason)
        }
        return try {
            val singBoxConfig = buildSingBoxConfig(parsed)
            safeLogDebug("shadowsocks config generated method=${parsed.method} server=${maskedHost(parsed.host)} port=${parsed.port}")
            safeLogDebug("runtime start called")
            core.start(singBoxConfig)
            val running = core.isRunning()
            safeLogDebug("running=$running lastError=none")
            if (running) ConnectResult(true, "Connected") else ConnectResult(false, shadowsocksFailureMessage("service did not report running"))
        } catch (e: Exception) {
            val reason = safeExceptionMessage(e)
            safeLogWarn("connect: protocol=outline_shadowsocks core_start_success=false exception=${e::class.java.simpleName} lastError=$reason running=false")
            ConnectResult(false, shadowsocksFailureMessage(reason))
        }
    }

    override suspend fun disconnect(): DisconnectResult = try {
        core.stop()
        safeLogDebug("disconnect: protocol=outline_shadowsocks running=false")
        DisconnectResult(true, "Disconnected")
    } catch (e: Exception) {
        safeLogWarn("disconnect: protocol=outline_shadowsocks exception=${e::class.java.simpleName} lastError=${safeExceptionMessage(e)}")
        DisconnectResult(false, "Shadowsocks stop failed")
    }

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult = HealthCheckResult(ok = parseConfig(config.config) != null)

    private fun tryParseConfigForAdapter(raw: String?): ShadowsocksClientConfig? {
        val result = tryParseConfig(raw)
        return result.onSuccess { safeLogDebug("shadowsocks uri parse success") }
            .onFailure { safeLogWarn("shadowsocks uri parse failure lastError=${safeExceptionMessage(it)}") }
            .getOrNull()
    }

    private fun logConfigAvailability(config: ShadowsocksClientConfig) {
        safeLogDebug("config: protocol=outline_shadowsocks method=${config.method} server=${maskedHost(config.host)} port=${config.port} name_present=${!config.name.isNullOrBlank()}")
    }

    private fun safeLogDebug(message: String) { runCatching { Log.d(TAG, safeLogMessage(message)) } }
    private fun safeLogWarn(message: String) { runCatching { Log.w(TAG, safeLogMessage(message)) } }
}

data class ShadowsocksClientConfig(
    val method: String,
    val password: String,
    val host: String,
    val port: Int,
    val name: String? = null
) {
    fun validate() {
        require(method.isNotBlank()) { OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE }
        require(method in setOf("chacha20-ietf-poly1305", "aes-256-gcm", "aes-128-gcm")) { "Unsupported Shadowsocks method: $method" }
        require(password.isNotBlank()) { OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE }
        require(host.isNotBlank()) { OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE }
        require(port in 1..65535) { OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE }
    }
}

interface ShadowsocksCore {
    fun isBundled(): Boolean
    fun unavailableReason(): String? = if (isBundled()) null else OutlineShadowsocksProtocolAdapter.UNSUPPORTED_CORE_MESSAGE
    fun start(singBoxConfigJson: String)
    fun stop()
    fun isRunning(): Boolean
}

class SingBoxShadowsocksCore(
    private val contextProvider: () -> Context? = { currentApplicationContext() }
) : ShadowsocksCore {
    constructor(context: Context) : this({ context.applicationContext })

    override fun isBundled(): Boolean = inspect().libboxPresent && runCatching {
        Class.forName(SingBoxRealityCore.LIBBOX_PLATFORM_INTERFACE_CLASS_NAME)
    }.isSuccess

    override fun unavailableReason(): String? {
        val inspection = inspect()
        if (!inspection.libboxPresent) return OutlineShadowsocksProtocolAdapter.UNSUPPORTED_CORE_MESSAGE
        if (!isBundled()) return OutlineShadowsocksProtocolAdapter.UNSUPPORTED_CORE_MESSAGE
        inspection.unsupportedReason?.let { reason ->
            LibboxRuntimeSupport.logInspection(inspection) { message -> safeLogDebug(message) }
            return reason
        }
        return null
    }

    override fun start(singBoxConfigJson: String) {
        unavailableReason()?.let { throw IllegalStateException(it) }
        val context = contextProvider() ?: throw IllegalStateException("Android application context is not available")
        ZootVpnService.startShadowsocks(context, singBoxConfigJson)
        if (!ZootVpnService.awaitShadowsocksRunning(START_TIMEOUT_MS)) {
            val error = ZootVpnService.lastShadowsocksError()
            throw IllegalStateException(error ?: "sing-box Shadowsocks service did not report running")
        }
    }

    override fun stop() {
        contextProvider()?.let { ZootVpnService.stopShadowsocks(it) }
        ZootVpnService.awaitShadowsocksStopped(STOP_TIMEOUT_MS)
    }

    override fun isRunning(): Boolean = ZootVpnService.isShadowsocksRunning()

    companion object {
        private const val START_TIMEOUT_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 5_000L
        private fun inspect() = ZootVpnService.inspectLibboxRuntime()
        private fun safeLogDebug(message: String) { runCatching { Log.d("SingBoxShadowsocksCore", LibboxRuntimeSupport.sanitize(message)) } }
        private fun currentApplicationContext(): Context? = runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)?.let { it as Context }?.applicationContext
        }.getOrNull()
    }
}
