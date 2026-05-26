package com.zooot.vpn.api

import com.zooot.vpn.BuildConfig
import com.zooot.vpn.selector.HealthStatus
import com.zooot.vpn.selector.Proto
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.ServerProtocol
import com.zooot.vpn.selector.ServerStatus
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ZootApiClient {
    val backendBaseUrl: String = BuildConfig.BACKEND_BASE_URL

    fun resolveToken(token: String): List<ServerCandidate> {
        val body = JSONObject().put("token", token).toString()
        val response = postJson("$backendBaseUrl/api/v1/config/resolve-token", body)
        return parseServers(response)
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
        if (code !in 200..299) {
            throw IllegalStateException("Backend HTTP $code: $text")
        }
        return text
    }

    private fun parseServers(rawJson: String): List<ServerCandidate> {
        val root = JSONObject(rawJson)
        val preferredCountry = root.optString("preferred_country", "")
        val serversJson = root.optJSONArray("servers") ?: JSONArray()
        val servers = mutableListOf<ServerCandidate>()
        for (i in 0 until serversJson.length()) {
            val server = serversJson.getJSONObject(i)
            val serverCountry = server.optString("country", preferredCountry)
            val serverId = server.optString("id", server.optString("name", "unknown"))
            val loadPercent = server.optInt("load_percent", 50)
            val latencyMs = server.optInt("latency_ms", 80)

            val protocols = mutableListOf<ServerProtocol>()
            val protocolItems = server.optJSONArray("protocols") ?: JSONArray()
            for (p in 0 until protocolItems.length()) {
                val protoName = protocolItems.getString(p)
                val proto = protoFromApi(protoName) ?: continue
                protocols += ServerProtocol(proto, HealthStatus.HEALTHY, "")
            }
            if (protocols.isEmpty()) {
                protocols += ServerProtocol(Proto.AMNEZIAWG, HealthStatus.HEALTHY, "")
            }

            servers += ServerCandidate(
                serverId = serverId,
                country = serverCountry,
                status = ServerStatus.ONLINE,
                loadPercent = loadPercent,
                latencyMs = latencyMs,
                protocols = protocols
            )
        }
        return servers
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
