package com.zooot.vpn.api

import com.zooot.vpn.BuildConfig
import com.zooot.vpn.selector.HealthStatus
import com.zooot.vpn.selector.Proto
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.ServerProtocol
import com.zooot.vpn.selector.ServerStatus
import com.zooot.vpn.vpn.protocol.OutlineShadowsocksProtocolAdapter
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object ZootApiClient {
    private fun isValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"

    private const val TAG = "ZootApiClient"
    

    private fun safeLogDebug(message: String) {
        runCatching { Log.d(TAG, sanitizeForLogs(message)) }
    }

    val backendBaseUrl: String = BuildConfig.BACKEND_BASE_URL

    fun resolveToken(token: String, deviceId: String, deviceName: String, backendUrl: String = backendBaseUrl): ResolveTokenResult {
        safeLogDebug("zoootconf resolve start")
        if (token == DemoConfigResolver.DEMO_TOKEN) {
            return DemoConfigResolver.resolveDemoToken().also {
                safeLogDebug("zoootconf resolve success protocol=outline_shadowsocks")
            }
        }
        val body = buildResolveTokenBody(token, deviceId, deviceName)
        val response = postJson("$backendUrl/api/v1/config/resolve-token", body)
        return parseResult(response)
    }
    fun buildResolveTokenBody(token: String, deviceId: String, deviceName: String): String =
        JsonObject().apply {
            addProperty("token", token)
            addProperty("device_id", deviceId)
            addProperty("device_name", deviceName)
        }.toString()

    private fun postJson(endpoint: String, body: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) throw ApiHttpException(code, text)
        return text
    }

    private fun parseResult(rawJson: String): ResolveTokenResult {
        val root = JsonParser.parseString(rawJson).takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
        val preferredCountry = root.optString("preferred_country", "")
        val userEmail = root.optObject("user")?.optString("email", "").orEmpty()
        val tariffTitle = root.optObject("tariff")?.optString("title", "").orEmpty()
        val serversJson = root.optArray("servers") ?: JsonArray()
        val servers = mutableListOf<ServerCandidate>()
        safeLogDebug("resolve-token parse: servers_count=${serversJson.size()}")
        for (i in 0 until serversJson.size()) {
            val server = serversJson[i].takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val serverCountry = server.optString("country", preferredCountry)
            val serverId = server.optString("id", server.optString("name", "unknown"))
            val loadPercent = server.optInt("load_percent", 50)
            val latencyMs = server.optInt("latency_ms", 80)
            val city = server.optString("city", "")
            val ip = server.optString("ip", server.optString("host", ""))

            val protocols = mutableListOf<ServerProtocol>()
            val protocolItems = server.optArray("protocols") ?: JsonArray()
            val protocolTypes = mutableListOf<String>()
            for (p in 0 until protocolItems.size()) {
                val item = protocolItems[p]
                when (item) {
                    is JsonElement -> if (item.isJsonPrimitive && item.asJsonPrimitive.isString) {
                        val proto = protoFromApi(item.asString) ?: continue
                        protocols += ServerProtocol(proto, HealthStatus.HEALTHY, "", null, null, null)
                        protocolTypes += proto.name.lowercase()
                    } else if (item.isJsonObject) {
                        val obj = item.asJsonObject
                        val protoName = obj.optString("protocol", obj.optString("type", ""))
                        val proto = protoFromApi(protoName) ?: continue
                        val rawConfig = if (obj.hasNonNull("config")) obj.optString("config", "") else ""
                        val config = rawConfig.takeIf { isValidConfig(it) } ?: ""
                        val healthRaw = obj.optString("health_status", obj.optString("health", "healthy"))
                        val health = if (healthRaw.lowercase() == "failed") HealthStatus.FAILED else HealthStatus.HEALTHY
                        val configUrl = obj.optString("config_url", "")
                        val port = if (obj.hasNonNull("port")) obj.optInt("port", 0) else null
                        val configSource = obj.optString("config_source", "").ifBlank { null }
                        val configAvailable = isValidConfig(config)
                        if (proto == Proto.XRAY_VLESS_REALITY) {
                            val flags = xrayRealitySafeFlags(config)
                            safeLogDebug("resolve-token parse: protocol=xray_vless_reality config_source=${configSource ?: "none"} config_available=$configAvailable has_host=${flags.hasHost} has_port=${flags.hasPort} has_server_name=${flags.hasServerName} has_flow=${flags.hasFlow}")
                        } else {
                            safeLogDebug("resolve-token parse: protocol=${proto.name.lowercase()} config_source=${configSource ?: "none"} config_available=$configAvailable")
                        }
                        protocols += ServerProtocol(proto, health, configUrl, config, port, configSource)
                        protocolTypes += proto.name.lowercase()
                    }
                }
            }
            if (protocols.isEmpty()) protocols += ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "")
            val wgHasConfig = protocols.any { it.type == Proto.WIREGUARD && isValidConfig(it.config) }
            val xrayHasConfig = protocols.any { it.type == Proto.XRAY_VLESS_REALITY && isValidConfig(it.config) }
            safeLogDebug("resolve-token parse: protocols_count=${protocolItems.size()} wireguard_config_available=$wgHasConfig xray_config_available=$xrayHasConfig")

            servers += ServerCandidate(serverId, serverCountry, ServerStatus.ONLINE, loadPercent, latencyMs, protocols, city, ip)
        }
        return ResolveTokenResult(preferredCountry, userEmail, tariffTitle, servers)
    }

    private data class XraySafeFlags(val hasHost: Boolean, val hasPort: Boolean, val hasServerName: Boolean, val hasFlow: Boolean)

    private fun xrayRealitySafeFlags(config: String?): XraySafeFlags {
        if (!isValidConfig(config)) return XraySafeFlags(false, false, false, false)
        return runCatching {
            val root = JsonParser.parseString(config).takeIf { it.isJsonObject }?.asJsonObject
            if (root != null) {
                XraySafeFlags(
                    hasHost = root.optString("host", "").isNotBlank(),
                    hasPort = root.optInt("port", 0) > 0,
                    hasServerName = root.optString("serverName", root.optString("server_name", "")).isNotBlank(),
                    hasFlow = root.optString("flow", "").isNotBlank()
                )
            } else {
                XraySafeFlags(
                    hasHost = config!!.contains("@") && config.contains(":"),
                    hasPort = Regex(":\\d+").containsMatchIn(config),
                    hasServerName = config.contains("sni="),
                    hasFlow = config.contains("flow=")
                )
            }
        }.getOrDefault(XraySafeFlags(false, false, false, false))
    }

    private fun sanitizeForLogs(message: String): String = message
        .replace(Regex("ss://[^\\s,;)]*", RegexOption.IGNORE_CASE), "ss://<redacted>")
        .replace(Regex("(?i)(password|passwd|token)[=:/][^\\s,;)]*"), "${'$'}1=<redacted>")
        .take(200)

    private fun protoFromApi(name: String): Proto? = when (name.lowercase()) {
        "amneziawg" -> Proto.AMNEZIAWG
        "xray_vless_reality" -> Proto.XRAY_VLESS_REALITY
        "outline_shadowsocks", "shadowsocks_outline", "shadowsocks" -> Proto.OUTLINE_SHADOWSOCKS
        "wireguard" -> Proto.WIREGUARD
        "openvpn_udp" -> Proto.OPENVPN_UDP
        "openvpn_tcp" -> Proto.OPENVPN_TCP
        else -> null
    }
}

private fun JsonObject.hasNonNull(name: String): Boolean = has(name) && !get(name).isJsonNull
private fun JsonObject.optString(name: String, fallback: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: fallback
private fun JsonObject.optInt(name: String, fallback: Int): Int =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt ?: fallback
private fun JsonObject.optArray(name: String): JsonArray? =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
private fun JsonObject.optObject(name: String): JsonObject? =
    get(name)?.takeIf { it.isJsonObject }?.asJsonObject

class ApiHttpException(val code: Int, body: String): IOException("Backend HTTP $code: $body")

data class ResolveTokenResult(
    val preferredCountry: String,
    val userEmail: String,
    val tariffTitle: String,
    val servers: List<ServerCandidate>
)


class DemoConfigUnavailableException(message: String = DemoConfigResolver.NOT_CONFIGURED_MESSAGE) : IllegalStateException(message)

object DemoConfigResolver {
    const val DEMO_TOKEN = "demo-token"
    const val NOT_CONFIGURED_MESSAGE = "Outline Shadowsocks server is not configured"
    private const val CONFIG_SOURCE = "zoootconf_demo"

    fun resolveDemoToken(demoSsUri: String = BuildConfig.ZOOOT_DEMO_SS_URI): ResolveTokenResult {
        val ssUri = demoSsUri.trim()
        val available = ssUri.isNotBlank() && OutlineShadowsocksProtocolAdapter.tryParseConfig(ssUri).isSuccess
        safeLogAvailability(available)
        if (!available) throw DemoConfigUnavailableException()

        return ResolveTokenResult(
            preferredCountry = "Outline",
            userEmail = "demo@zooot.local",
            tariffTitle = "Outline Shadowsocks demo",
            servers = listOf(
                ServerCandidate(
                    serverId = "outline-shadowsocks-demo",
                    country = "Outline",
                    status = ServerStatus.ONLINE,
                    loadPercent = 0,
                    latencyMs = 0,
                    protocols = listOf(
                        ServerProtocol(
                            type = Proto.OUTLINE_SHADOWSOCKS,
                            health = HealthStatus.HEALTHY,
                            configUrl = "",
                            config = ssUri,
                            port = null,
                            configSource = CONFIG_SOURCE
                        )
                    ),
                    city = "Shadowsocks",
                    serverIp = "<redacted>"
                )
            )
        )
    }

    private fun safeLogAvailability(available: Boolean) {
        runCatching { Log.d("ZootApiClient", "outline shadowsocks config available=$available") }
    }
}
