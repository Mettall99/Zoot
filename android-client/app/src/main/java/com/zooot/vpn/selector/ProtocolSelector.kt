package com.zooot.vpn.selector

enum class ServerStatus { ONLINE, OFFLINE, MAINTENANCE }
enum class HealthStatus { HEALTHY, FAILED }
enum class NetworkType { WIFI, MOBILE }
enum class Proto { AMNEZIAWG, XRAY_VLESS_REALITY, OUTLINE_SHADOWSOCKS, WIREGUARD, OPENVPN_UDP, OPENVPN_TCP }

data class ServerProtocol(val type: Proto, val health: HealthStatus, val configUrl: String, val config: String? = null, val port: Int? = null, val configSource: String? = null)
data class ServerCandidate(val serverId: String, val country: String, val status: ServerStatus, val loadPercent: Int, val latencyMs: Int, val protocols: List<ServerProtocol>, val city: String = "", val serverIp: String = "")
data class HistoryKey(val network: NetworkType, val serverId: String, val proto: Proto)
data class HistoryVal(val success: Boolean, val failurePenalty: Int = 0)
data class Selection(val serverId: String, val protocol: Proto, val configUrl: String, val score: Int, val config: String? = null, val port: Int? = null)

object ProtocolSelector {
    private fun hasValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"
    private fun requiresConfig(proto: Proto): Boolean = proto == Proto.WIREGUARD || proto == Proto.XRAY_VLESS_REALITY || proto == Proto.OUTLINE_SHADOWSOCKS
    private val protocolScore = mapOf(Proto.WIREGUARD to 55, Proto.OUTLINE_SHADOWSOCKS to 52, Proto.XRAY_VLESS_REALITY to 50, Proto.AMNEZIAWG to 45, Proto.OPENVPN_UDP to 25, Proto.OPENVPN_TCP to 15)

    fun select(servers: List<ServerCandidate>, country: String, network: NetworkType, history: Map<HistoryKey, HistoryVal>): Selection? {
        val stableWireGuard = servers.asSequence()
            .filter { it.country == country && it.status == ServerStatus.ONLINE }
            .flatMap { server ->
                server.protocols.asSequence()
                    .filter { it.type == Proto.WIREGUARD && it.health != HealthStatus.FAILED && hasValidConfig(it.config) }
                    .filter { history[HistoryKey(network, server.serverId, Proto.WIREGUARD)]?.success != false }
                    .map { sp -> Selection(server.serverId, sp.type, sp.configUrl, Int.MAX_VALUE, sp.config, sp.port) }
            }
            .firstOrNull()
        if (stableWireGuard != null) return stableWireGuard
        val filtered = servers.filter { it.country == country && it.status == ServerStatus.ONLINE }
            .sortedWith(compareBy<ServerCandidate> { it.loadPercent }.thenBy { it.latencyMs })

        var best: Selection? = null
        for (server in filtered) {
            for (sp in server.protocols.filter { it.health != HealthStatus.FAILED && (!requiresConfig(it.type) || hasValidConfig(it.config)) }) {
                val key = HistoryKey(network, server.serverId, sp.type)
                val h = history[key]
                val historyScore = when (h?.success) { true -> 20; false -> -30; null -> 0 }
                val loadScore = when {
                    server.loadPercent < 30 -> 25
                    server.loadPercent <= 60 -> 15
                    server.loadPercent <= 80 -> 5
                    else -> -20
                }
                val latencyScore = when {
                    server.latencyMs < 70 -> 25
                    server.latencyMs <= 150 -> 15
                    server.latencyMs <= 250 -> 5
                    else -> -10
                }
                val score = (protocolScore[sp.type] ?: 0) + loadScore + latencyScore + historyScore - (h?.failurePenalty ?: 0)
                val candidate = Selection(server.serverId, sp.type, sp.configUrl, score, sp.config, sp.port)
                if (best == null || candidate.score > best.score) best = candidate
            }
            if (best != null) return best
        }
        return null
    }
}
