package com.zooot.vpn.api

import com.zooot.vpn.BuildConfig
import com.zooot.vpn.selector.HealthStatus
import com.zooot.vpn.selector.Proto
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.ServerProtocol
import com.zooot.vpn.selector.ServerStatus
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL

object ZootApiClient {
    private fun isValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"

    private const val TAG = "ZootApiClient"
    val backendBaseUrl: String = BuildConfig.BACKEND_BASE_URL

    fun resolveToken(token: String, deviceId: String, deviceName: String, backendUrl: String = backendBaseUrl): ResolveTokenResult {
        val body = JSONObject().put("token", token).put("device_id", deviceId).put("device_name", deviceName).toString()
        val response = postJson("$backendUrl/api/v1/config/resolve-token", body)
        return parseResult(response)
    }

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
        if (code !in 200..299) throw IllegalStateException("Backend HTTP $code: $text")
        return text
    }

    private fun parseResult(rawJson: String): ResolveTokenResult {
        val root = JSONObject(rawJson)
        val preferredCountry = root.optString("preferred_country", "")
        val userEmail = root.optJSONObject("user")?.optString("email", "").orEmpty()
        val tariffTitle = root.optJSONObject("tariff")?.optString("title", "").orEmpty()
        val serversJson = root.optJSONArray("servers") ?: JSONArray()
        val servers = mutableListOf<ServerCandidate>()
        Log.d(TAG, "resolve-token parse: servers_count=${serversJson.length()}")
        for (i in 0 until serversJson.length()) {
            val server = serversJson.getJSONObject(i)
            val serverCountry = server.optString("country", preferredCountry)
            val serverId = server.optString("id", server.optString("name", "unknown"))
            val loadPercent = server.optInt("load_percent", 50)
            val latencyMs = server.optInt("latency_ms", 80)
            val city = server.optString("city", "")
            val ip = server.optString("ip", server.optString("host", ""))

            val protocols = mutableListOf<ServerProtocol>()
            val protocolItems = server.optJSONArray("protocols") ?: JSONArray()
            val protocolTypes = mutableListOf<String>()
            for (p in 0 until protocolItems.length()) {
                val item = protocolItems.get(p)
                when (item) {
                    is String -> {
                        val proto = protoFromApi(item) ?: continue
                        protocols += ServerProtocol(proto, HealthStatus.HEALTHY, "", null, null)
                        protocolTypes += proto.name.lowercase()
                    }
                    is JSONObject -> {
                        val protoName = item.optString("protocol", item.optString("type", ""))
                        val proto = protoFromApi(protoName) ?: continue
                        val rawConfig = if (item.has("config") && !item.isNull("config")) item.optString("config", "") else ""
                        val config = rawConfig.takeIf { isValidConfig(it) } ?: ""
                        val healthRaw = item.optString("health_status", item.optString("health", "healthy"))
                        val health = if (healthRaw.lowercase() == "failed") HealthStatus.FAILED else HealthStatus.HEALTHY
                        val configUrl = item.optString("config_url", "")
                        val port = if (item.has("port") && !item.isNull("port")) item.optInt("port") else null
                        protocols += ServerProtocol(proto, health, configUrl, config, port)
                        protocolTypes += proto.name.lowercase()
                    }
                }
            }
            if (protocols.isEmpty()) protocols += ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "")
            val wgHasConfig = protocols.any { it.type == Proto.WIREGUARD && isValidConfig(it.config) }
            Log.d(TAG, "resolve-token parse: server_id=$serverId protocols_count=${protocolItems.length()} protocol_types=$protocolTypes wireguard_config_available=$wgHasConfig")

            servers += ServerCandidate(serverId, serverCountry, ServerStatus.ONLINE, loadPercent, latencyMs, protocols, city, ip)
        }
        return ResolveTokenResult(preferredCountry, userEmail, tariffTitle, servers)
    }

    private fun protoFromApi(name: String): Proto? = when (name.lowercase()) {
        "amneziawg" -> Proto.AMNEZIAWG
        "xray_vless_reality" -> Proto.XRAY_VLESS_REALITY
        "wireguard" -> Proto.WIREGUARD
        "openvpn_udp" -> Proto.OPENVPN_UDP
        "openvpn_tcp" -> Proto.OPENVPN_TCP
        else -> null
    }
}

data class ResolveTokenResult(
    val preferredCountry: String,
    val userEmail: String,
    val tariffTitle: String,
    val servers: List<ServerCandidate>
)
