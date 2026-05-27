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
import java.net.HttpURLConnection
import java.net.URL

object ZootApiClient {
    val backendBaseUrl: String = BuildConfig.BACKEND_BASE_URL

    fun resolveToken(token: String, backendUrl: String = backendBaseUrl): ResolveTokenResult {
        val body = JSONObject().put("token", token).toString()
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
            for (p in 0 until protocolItems.length()) {
                val item = protocolItems.get(p)
                when (item) {
                    is String -> {
                        val proto = protoFromApi(item) ?: continue
                        protocols += ServerProtocol(proto, HealthStatus.HEALTHY, "", null)
                    }
                    is JSONObject -> {
                        val protoName = item.optString("protocol", item.optString("type", ""))
                        val proto = protoFromApi(protoName) ?: continue
                        val config = item.optString("config", "").ifBlank { null }
                        val health = if (item.optString("health", "healthy").lowercase() == "failed") HealthStatus.FAILED else HealthStatus.HEALTHY
                        val configUrl = item.optString("config_url", "")
                        protocols += ServerProtocol(proto, health, configUrl, config)
                    }
                }
            }
            if (protocols.isEmpty()) protocols += ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "")

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
