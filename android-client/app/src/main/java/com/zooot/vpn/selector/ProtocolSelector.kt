package com.zooot.vpn.selector

enum class ServerStatus { ONLINE, OFFLINE, MAINTENANCE }
enum class HealthStatus { HEALTHY, FAILED }
enum class NetworkType { WIFI, MOBILE }
enum class Proto { AMNEZIAWG, XRAY_VLESS_REALITY, WIREGUARD, OPENVPN_UDP, OPENVPN_TCP }

data class ServerProtocol(val type: Proto, val health: HealthStatus, val configUrl: String)
data class ServerCandidate(val serverId: String, val country: String, val status: ServerStatus, val loadPercent: Int, val latencyMs: Int, val protocols: List<ServerProtocol>)
data class HistoryKey(val network: NetworkType, val serverId: String, val proto: Proto)
data class HistoryVal(val success: Boolean, val failurePenalty: Int = 0)
data class Selection(val serverId: String, val protocol: Proto, val configUrl: String, val score: Int)

object ProtocolSelector {
    private val protocolScore = mapOf(Proto.AMNEZIAWG to 50, Proto.XRAY_VLESS_REALITY to 45, Proto.WIREGUARD to 35, Proto.OPENVPN_UDP to 25, Proto.OPENVPN_TCP to 15)

    fun select(servers: List<ServerCandidate>, country: String, network: NetworkType, history: Map<HistoryKey, HistoryVal>): Selection? {
        val filtered = servers.filter { it.country == country && it.status == ServerStatus.ONLINE }
            .sortedWith(compareBy<ServerCandidate> { it.loadPercent }.thenBy { it.latencyMs })

        for (server in filtered) {
            val bestForServer = server.protocols.filter { it.health != HealthStatus.FAILED }
                .map { sp ->
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
                    Selection(server.serverId, sp.type, sp.configUrl, score)
                }.maxByOrNull { it.score }

            if (bestForServer != null) return bestForServer
        }
        return null
    }
}
