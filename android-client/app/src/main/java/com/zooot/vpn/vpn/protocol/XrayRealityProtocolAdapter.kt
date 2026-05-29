package com.zooot.vpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zooot.vpn.vpn.LibboxRuntimeSupport
import java.net.URLDecoder

class XrayRealityProtocolAdapter(
    private val core: RealityCore = SingBoxRealityCore()
) : VpnProtocolAdapter {
    constructor(context: Context) : this(SingBoxRealityCore(context))
    override val type: ProtocolType = ProtocolType.XRAY_VLESS_REALITY

    companion object {
        private const val TAG = "XrayRealityAdapter"
        const val CORE_MISSING_MESSAGE = "Reality core is not bundled in this build"
        val UNSUPPORTED_CORE_MESSAGE = LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE

        private fun hasValidConfig(config: String?): Boolean =
            !config.isNullOrBlank() && config.trim().lowercase() != "null"

        internal fun parseConfig(raw: String?): XrayRealityClientConfig? =
            if (!hasValidConfig(raw)) null else runCatching {
                val trimmed = raw!!.trim()
                if (trimmed.startsWith("vless://", ignoreCase = true)) parseVlessUri(trimmed)
                else parseJsonConfig(trimmed)
            }.getOrNull()

        internal fun buildSingBoxConfig(client: XrayRealityClientConfig): String =
            JsonObject().apply {
                add("log", JsonObject().apply { addProperty("level", "warn") })
                add("inbounds", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "tun")
                        addProperty("tag", "tun-in")
                        addProperty("interface_name", "zooot-reality")
                        add("inet4_address", com.google.gson.JsonArray().apply { add("172.19.0.1/30") })
                        addProperty("auto_route", true)
                        addProperty("strict_route", true)
                        addProperty("stack", "system")
                        addProperty("sniff", true)
                    })
                })
                add("outbounds", com.google.gson.JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("type", "vless")
                        addProperty("tag", "reality-out")
                        addProperty("server", client.host)
                        addProperty("server_port", client.port)
                        addProperty("uuid", client.uuid)
                        addProperty("flow", client.flow.orEmpty())
                        add("tls", JsonObject().apply {
                            addProperty("enabled", true)
                            addProperty("server_name", client.serverName)
                            add("utls", JsonObject().apply {
                                addProperty("enabled", true)
                                addProperty("fingerprint", client.fingerprint.ifBlank { "chrome" })
                            })
                            add("reality", JsonObject().apply {
                                addProperty("enabled", true)
                                addProperty("public_key", client.publicKey)
                                addProperty("short_id", client.shortId)
                            })
                        })
                    })
                })
                add("route", JsonObject().apply {
                    addProperty("final", "reality-out")
                    addProperty("auto_detect_interface", true)
                })
            }.toString()

        internal fun safeExceptionMessage(e: Throwable): String =
            e.message?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
                ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|vless://)[^\\s,;)]*"), "\$1=<redacted>")
                ?.replace(Regex("[A-Za-z0-9_-]{20,}"), "<redacted>")
                ?.take(160)
                ?: ""

        internal fun realityFailureMessage(reason: String): String = "Reality failed: ${reason.ifBlank { "unknown startup error" }}"

        private fun parseJsonConfig(raw: String): XrayRealityClientConfig {
            val obj = JsonParser.parseString(raw).asJsonObject
            val protocol = obj.optString("protocol", "xray_vless_reality")
            require(protocol == "xray_vless_reality") { "Unsupported Reality protocol" }
            return XrayRealityClientConfig(
                host = obj.requiredString("host"),
                port = obj.optInt("port", 443),
                uuid = obj.requiredString("uuid"),
                publicKey = obj.optString("publicKey", obj.optString("public_key", "")),
                shortId = obj.optString("shortId", obj.optString("short_id", "")),
                serverName = obj.optString("serverName", obj.optString("server_name", "")),
                flow = obj.optString("flow", "").ifBlank { null },
                fingerprint = obj.optString("fingerprint", "chrome").ifBlank { "chrome" }
            ).also { it.validate() }
        }

        private fun parseVlessUri(uri: String): XrayRealityClientConfig {
            val withoutScheme = uri.removePrefix("vless://")
            val userAndRest = withoutScheme.split("@", limit = 2)
            require(userAndRest.size == 2) { "Invalid VLESS URI" }
            val uuid = decode(userAndRest[0])
            val authorityAndQuery = userAndRest[1].split("?", limit = 2)
            val authority = authorityAndQuery[0].substringBefore("#")
            val host = decode(authority.substringBeforeLast(":"))
            val port = authority.substringAfterLast(":", "443").substringBefore("#").toInt()
            val query = authorityAndQuery.getOrNull(1).orEmpty().substringBefore("#")
            val params = query.split("&").filter { it.contains("=") }.associate {
                val (k, v) = it.split("=", limit = 2)
                decode(k) to decode(v)
            }
            return XrayRealityClientConfig(
                host = host,
                port = port,
                uuid = uuid,
                publicKey = params["pbk"].orEmpty(),
                shortId = params["sid"].orEmpty(),
                serverName = params["sni"].orEmpty(),
                flow = params["flow"]?.ifBlank { null },
                fingerprint = params["fp"]?.ifBlank { "chrome" } ?: "chrome"
            ).also { it.validate() }
        }

        private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
    }

    override suspend fun prepare(config: VpnConfig): PrepareResult {
        val parsed = parseConfig(config.config)
            ?: run {
                safeLogDebug("prepare: protocol=xray_vless_reality config_available=false")
                return PrepareResult(false, "Reality/TCP config is not available")
            }
        logConfigAvailability(parsed, configAvailable = true)
        core.unavailableReason()?.let { reason ->
            safeLogWarn("prepare: protocol=xray_vless_reality config_available=true core_start_success=false core_name=${core::class.java.simpleName} unavailable_core=$reason")
            return PrepareResult(false, reason)
        }
        return PrepareResult(true, "Prepared")
    }

    override suspend fun connect(config: VpnConfig): ConnectResult {
        val parsed = parseConfig(config.config)
            ?: run {
                safeLogDebug("connect: protocol=xray_vless_reality config_available=false")
                return ConnectResult(false, "Reality/TCP config is not available")
            }
        logConfigAvailability(parsed, configAvailable = true)
        core.unavailableReason()?.let { reason ->
            safeLogWarn("connect: protocol=xray_vless_reality config_available=true core_start_success=false core_name=${core::class.java.simpleName} unavailable_core=$reason")
            return ConnectResult(false, reason)
        }
        return try {
            val singBoxConfig = buildSingBoxConfig(parsed)
            core.start(singBoxConfig)
            val running = core.isRunning()
            safeLogDebug("connect: protocol=xray_vless_reality config_available=true core_name=${core::class.java.simpleName} core_start_success=$running")
            if (running) ConnectResult(true, "Connected") else ConnectResult(false, realityFailureMessage("service did not report running"))
        } catch (e: Exception) {
            val reason = safeExceptionMessage(e)
            safeLogWarn("connect: protocol=xray_vless_reality core_name=${core::class.java.simpleName} core_start_success=false exception=${e::class.java.simpleName} message=$reason")
            ConnectResult(false, realityFailureMessage(reason))
        }
    }

    override suspend fun disconnect(): DisconnectResult = try {
        core.stop()
        DisconnectResult(true, "Disconnected")
    } catch (e: Exception) {
        safeLogWarn("disconnect: protocol=xray_vless_reality core_name=${core::class.java.simpleName} exception=${e::class.java.simpleName} message=${safeExceptionMessage(e)}")
        DisconnectResult(false, "Reality stop failed")
    }

    override suspend fun healthCheck(config: VpnConfig): HealthCheckResult = HealthCheckResult(ok = parseConfig(config.config) != null)

    private fun safeLogDebug(message: String) { runCatching { Log.d(TAG, message) } }
    private fun safeLogWarn(message: String) { runCatching { Log.w(TAG, message) } }

    private fun logConfigAvailability(config: XrayRealityClientConfig, configAvailable: Boolean) {
        safeLogDebug(
            "config: protocol=xray_vless_reality config_available=$configAvailable " +
                "has_host=${config.host.isNotBlank()} has_port=${config.port > 0} " +
                "has_server_name=${config.serverName.isNotBlank()} has_flow=${!config.flow.isNullOrBlank()}"
        )
    }
}

data class XrayRealityClientConfig(
    val host: String,
    val port: Int,
    val uuid: String,
    val publicKey: String,
    val shortId: String,
    val serverName: String,
    val flow: String?,
    val fingerprint: String
) {
    fun validate() {
        require(host.isNotBlank()) { "Reality host is missing" }
        require(port in 1..65535) { "Reality port is invalid" }
        require(uuid.isNotBlank()) { "Reality uuid is missing" }
        require(publicKey.isNotBlank()) { "Reality public key is missing" }
        require(shortId.isNotBlank()) { "Reality short id is missing" }
        require(serverName.isNotBlank()) { "Reality server name is missing" }
    }
}

interface RealityCore {
    fun isBundled(): Boolean
    fun missingDependencyName(): String = ""
    fun unavailableReason(): String? = if (isBundled()) null else XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE
    fun start(singBoxConfigJson: String)
    fun stop()
    fun isRunning(): Boolean
}

class MissingRealityCore : RealityCore {
    override fun isBundled(): Boolean = false
    override fun missingDependencyName(): String = SingBoxRealityCore.LIBBOX_DEPENDENCY_NAME
    override fun start(singBoxConfigJson: String) {
        throw IllegalStateException(XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE)
    }
    override fun stop() = Unit
    override fun isRunning(): Boolean = false
}

private fun JsonObject.requiredString(name: String): String = optString(name, "").also { require(it.isNotBlank()) { "$name is missing" } }
private fun JsonObject.optString(name: String, fallback: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: fallback
private fun JsonObject.optInt(name: String, fallback: Int): Int =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: fallback
