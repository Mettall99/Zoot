package com.zooot.vpn.app
import com.zooot.vpn.api.*
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.selector.*
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.VpnConfig

class MainStateMachine(private val configApi: ConfigApi, private val adapter: FakeVpnProtocolAdapter = FakeVpnProtocolAdapter(), private val networkType: NetworkType = NetworkType.WIFI) {
var state: MainUiState = MainUiState(); private set
suspend fun onDeepLink(raw: String) { val token = DeepLinkParser.extractToken(raw); if (token.isNullOrBlank()) { state = state.copy(status = ConnectionState.Error("Ссылка недействительна"), errorMessage = "Ссылка недействительна"); return }; state = state.copy(token = token, status = ConnectionState.TokenReceived(token), errorMessage = null); loadConfig(token) }
suspend fun loadConfig(token: String) { state = state.copy(status = ConnectionState.LoadingConfig, errorMessage = null); when (val result = configApi.resolveToken(ResolveTokenRequest(token))) { is ApiResult.Success -> { val servers = result.data.servers; if (servers.isEmpty()) { state = state.copy(status = ConnectionState.Error("Нет доступных серверов"), errorMessage = "Нет доступных серверов"); return }; state = state.copy(status = ConnectionState.ConfigLoaded); state = state.copy(status = ConnectionState.SelectingProtocol); val domain = servers.map { s -> ServerCandidate(s.id, s.country, if (s.status == "online") ServerStatus.ONLINE else ServerStatus.OFFLINE, s.loadPercent, s.latencyMs, s.protocols.map { p -> ServerProtocol(Proto.valueOf(p.type.uppercase()), if (p.health == "healthy") HealthStatus.HEALTHY else HealthStatus.FAILED, p.configUrl) }) }; val country = domain.first().country; val selection = ProtocolSelector.select(domain, country, networkType, emptyMap()) ?: run { state = state.copy(status = ConnectionState.Error("Нет доступных протоколов"), errorMessage = "Нет доступных протоколов"); return }; state = state.copy(selectedCountry = country, selectedServer = selection.serverId, selectedProtocol = selection.protocol.name, selection = selection, status = ConnectionState.ReadyToConnect) }
is ApiResult.NetworkError -> state = state.copy(status = ConnectionState.Error("Не удалось подключиться к API"), errorMessage = "Не удалось подключиться к API")
is ApiResult.ParseError -> state = state.copy(status = ConnectionState.Error("Ошибка ответа сервера"), errorMessage = "Ошибка ответа сервера")
is ApiResult.HttpError -> { val message = if (result.code == 403) "Подписка неактивна" else "Ссылка недействительна"; state = state.copy(status = ConnectionState.Error(message), errorMessage = message) } } }
suspend fun connect() { val selection = state.selection ?: run { state = state.copy(status = ConnectionState.Error("Нет доступных протоколов"), errorMessage = "Нет доступных протоколов"); return }; state = state.copy(status = ConnectionState.Connecting); adapter.prepare(VpnConfig(selection.serverId, selection.configUrl)); adapter.connect(VpnConfig(selection.serverId, selection.configUrl)); state = state.copy(status = ConnectionState.Connected) }
suspend fun disconnect() { adapter.disconnect(); state = state.copy(status = ConnectionState.ReadyToConnect) }
}
