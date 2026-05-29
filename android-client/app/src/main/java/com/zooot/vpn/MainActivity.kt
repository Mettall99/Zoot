package com.zooot.vpn

import android.content.Context
import android.net.VpnService
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.zooot.vpn.api.DeviceIdentity
import com.zooot.vpn.api.ApiHttpException
import com.zooot.vpn.api.DemoConfigUnavailableException
import com.zooot.vpn.api.ResolveTokenResult
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.OutlineShadowsocksProtocolAdapter
import com.zooot.vpn.vpn.protocol.ProtocolType
import com.zooot.vpn.vpn.protocol.VpnConfig
import com.zooot.vpn.vpn.protocol.VpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.WireGuardProtocolAdapter
import com.zooot.vpn.vpn.protocol.XrayRealityProtocolAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import com.google.gson.JsonParser

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ManualLinkFlow"
        private const val DEFAULT_DEBUG_BACKEND_URL = "http://31.59.45.197:8080"
    }
    private lateinit var wireGuardAdapter: WireGuardProtocolAdapter
    private lateinit var xrayRealityAdapter: XrayRealityProtocolAdapter
    private lateinit var outlineShadowsocksAdapter: OutlineShadowsocksProtocolAdapter
    private val fakeAdapter = FakeVpnProtocolAdapter(ProtocolType.AMNEZIAWG)
    private var selectedServer: UiServer? = null
    private var selectedProtocol: UiProtocolOption? = null
    private var resolveResult: ResolveTokenResult? = null
    private var session: ConnectionSession? = null
    private var timerJob: Job? = null
    private lateinit var trafficProvider: VpnTrafficStatsProvider

    private lateinit var startContainer: View
    private lateinit var serverSelectionContainer: View
    private lateinit var connectionContainer: View
    private lateinit var connectedContainer: View
    private lateinit var linkInputLayout: TextInputLayout
    private lateinit var linkInput: TextInputEditText
    private lateinit var serversList: LinearLayout
    private lateinit var continueButton: MaterialButton
    private lateinit var protocolsList: LinearLayout
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var connectedStatus: TextView
    private lateinit var connectedServer: TextView
    private lateinit var connectedProtocol: TextView
    private lateinit var connectedDuration: TextView
    private lateinit var connectedTrafficRx: TextView
    private lateinit var connectedTrafficTx: TextView

    private var currentState: AppUiState = AppUiState.StartState
    private var pendingVpnPermissionConnect = false
    private lateinit var connectButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) {
            Log.d(TAG, "vpn permission granted")
            if (pendingVpnPermissionConnect) connectInternal()
        } else {
            Log.d(TAG, "vpn permission denied")
            pendingVpnPermissionConnect = false
            setConnecting(false)
            showError("VPN permission is required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wireGuardAdapter = WireGuardProtocolAdapter(this)
        xrayRealityAdapter = XrayRealityProtocolAdapter(this)
        outlineShadowsocksAdapter = OutlineShadowsocksProtocolAdapter(this)
        trafficProvider = NoopTrafficStatsProvider
        bindViews()
        setupActions()

        val deepLink = intent?.dataString.orEmpty()
        if (deepLink.isNotBlank()) {
            linkInput.setText(deepLink)
            submitLink(deepLink, source = "deep-link")
        } else {
            showState(AppUiState.StartState)
        }
    }

    private fun bindViews() {
        startContainer = findViewById(R.id.startContainer)
        serverSelectionContainer = findViewById(R.id.serverSelectionContainer)
        connectionContainer = findViewById(R.id.connectionContainer)
        connectedContainer = findViewById(R.id.connectedContainer)
        linkInputLayout = findViewById(R.id.linkInputLayout)
        linkInput = findViewById(R.id.linkInput)
        serversList = findViewById(R.id.serversList)
        continueButton = findViewById(R.id.continueButton)
        protocolsList = findViewById(R.id.protocolsList)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorValue)
        connectedStatus = findViewById(R.id.connectedStatus)
        connectedServer = findViewById(R.id.connectedServer)
        connectedProtocol = findViewById(R.id.connectedProtocol)
        connectedDuration = findViewById(R.id.connectedDuration)
        connectedTrafficRx = findViewById(R.id.connectedTrafficRx)
        connectedTrafficTx = findViewById(R.id.connectedTrafficTx)
    }

    private fun setupActions() {
        continueButton.setOnClickListener {
            Log.d(TAG, "manual link submit clicked")
            submitLink(linkInput.text?.toString().orEmpty(), source = "manual")
        }
        findViewById<MaterialButton>(R.id.selectServerButton).setOnClickListener { showConnectionScreen() }
        findViewById<MaterialButton>(R.id.backToServersButton).setOnClickListener { showState(AppUiState.ServerSelectionState) }
        connectButton = findViewById(R.id.connectButton)
        disconnectButton = findViewById(R.id.disconnectButton)
        connectButton.setOnClickListener { connect() }
        disconnectButton.setOnClickListener { disconnect() }
    }

    private fun submitLink(rawLink: String, source: String) {
        if (rawLink.trim().startsWith("ss://", ignoreCase = true)) {
            submitShadowsocksLink(rawLink.trim())
            return
        }
        if (rawLink.trim().startsWith("vless://", ignoreCase = true)) {
            submitVlessLink(rawLink.trim())
            return
        }
        val parsed = LinkInputParser.validate(rawLink)
        if (!parsed.valid) {
            linkInputLayout.error = parsed.error
            if (source == "manual") showError(parsed.error ?: "Неверный формат ссылки")
            return
        }
        Log.d(TAG, "manual link validation ok")
        linkInputLayout.error = null
        val token = parsed.token!!
        Log.d(TAG, "manual token extracted")
        loadConfigFromToken(token)
    }

    private fun submitShadowsocksLink(rawLink: String) {
        clearError()
        val parsed = OutlineShadowsocksProtocolAdapter.tryParseConfig(rawLink)
        if (parsed.isFailure) {
            val message = parsed.exceptionOrNull()?.message ?: OutlineShadowsocksProtocolAdapter.INVALID_LINK_MESSAGE
            linkInputLayout.error = message
            showError(message)
            return
        }
        linkInputLayout.error = null
        val ss = parsed.getOrThrow()
        Log.d(TAG, "protocol selected=outline_shadowsocks server=${OutlineShadowsocksProtocolAdapter.maskedHost(ss.host)} port=${ss.port} method=${ss.method}")
        val server = UiServer(
            id = ss.name ?: "outline-shadowsocks",
            country = "Outline",
            city = ss.name ?: "Shadowsocks",
            serverIp = OutlineShadowsocksProtocolAdapter.maskedHost(ss.host),
            loadPercent = 0,
            latencyMs = 0,
            online = true,
            wireGuardConfig = null,
            wireGuardConfigSource = null,
            xrayRealityConfig = null,
            xrayRealityConfigSource = null,
            outlineShadowsocksConfig = rawLink,
            outlineShadowsocksConfigSource = "manual_ss_uri"
        )
        selectedServer = server
        selectedProtocol = UiProtocolOption.outlineShadowsocks(rawLink, "manual_ss_uri")
        showConnectionScreen()
    }

    private fun submitVlessLink(rawLink: String) {
        clearError()
        if (XrayRealityProtocolAdapter.parseConfig(rawLink) == null) {
            linkInputLayout.error = "Invalid VLESS Reality link"
            showError("Invalid VLESS Reality link")
            return
        }
        linkInputLayout.error = null
        Log.d(TAG, "protocol selected=xray_vless_reality")
        val server = UiServer(
            id = "vless-reality",
            country = "VLESS",
            city = "Reality",
            serverIp = "<redacted>",
            loadPercent = 0,
            latencyMs = 0,
            online = true,
            wireGuardConfig = null,
            wireGuardConfigSource = null,
            xrayRealityConfig = rawLink,
            xrayRealityConfigSource = "manual_vless_uri"
        )
        selectedServer = server
        selectedProtocol = UiProtocolOption.xrayReality(rawLink, "manual_vless_uri")
        showConnectionScreen()
    }

    private fun loadConfigFromToken(token: String) {
        clearError(); showState(AppUiState.ServerSelectionState)
        lifecycleScope.launch {
            setLoading(true)
            val deviceId = DeviceIdentity.getOrCreate(this@MainActivity)
            val backendUrl = readBackendUrl()
            Log.d(TAG, "resolve-token start backendUrl=$backendUrl hasDeviceId=${deviceId.isNotBlank()}")
            try {
                val result = withContext(Dispatchers.IO) {
                    ZootApiClient.resolveToken(token, deviceId, "Android device", backendUrl)
                }
                resolveResult = result
                val servers = result.servers.map { UiMapper.toUiServer(it) }
                Log.d(TAG, "resolve-token success servers=${servers.size}")
                if (servers.any { hasValidConfig(it.outlineShadowsocksConfig) }) {
                    Log.d(TAG, "protocol selected=outline_shadowsocks")
                }
                if (servers.none { it.hasAnyConnectableConfig() }) {
                    showState(AppUiState.StartState)
                    showError("Нет доступной VPN-конфигурации")
                    return@launch
                }
                selectedServer = ServerRecommendation.pick(servers)
                renderServers(servers)
            } catch (e: Exception) {
                Log.e(TAG, "resolve-token failed type=${e::class.java.simpleName} message=${safeErrorMessage(e)}")
                showState(AppUiState.StartState)
                showError(mapResolveError(e))
            } finally {
                setLoading(false)
            }
        }
    }
    private fun safeErrorMessage(e: Throwable): String = when (e) {
        is ApiHttpException -> "http_${e.code}"
        else -> e.message?.take(120).orEmpty()
    }

    private fun mapResolveError(e: Exception): String = when (e) {
        is DemoConfigUnavailableException -> e.message ?: "Outline Shadowsocks server is not configured"
        is ApiHttpException -> e.backendMessage() ?: if (e.code == 401 || e.code == 404) "Ссылка недействительна" else "Не удалось подключиться к серверу"
        is IOException -> "Не удалось подключиться к серверу"
        else -> "Не удалось подключиться к серверу"
    }

    private fun ApiHttpException.backendMessage(): String? = runCatching {
        val root = JsonParser.parseString(responseBody).takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val error = root.get("error")?.takeIf { it.isJsonObject }?.asJsonObject
        val message = error?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
            ?: root.get("message")?.takeIf { it.isJsonPrimitive }?.asString
        message?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun renderServers(servers: List<UiServer>) {
        serversList.removeAllViews()
        servers.forEach { s ->
            val card = layoutInflater.inflate(R.layout.item_simple_card, serversList, false) as MaterialCardView
            card.findViewById<TextView>(R.id.cardTitle).text = "${s.country} · ${s.city}"
            card.findViewById<TextView>(R.id.cardSubtitle).text = "${s.serverIp} • load ${s.loadPercent}% • ${if (s.online) "online" else "offline"}"
            card.isChecked = selectedServer?.id == s.id
            card.setOnClickListener { selectedServer = s; renderServers(servers) }
            serversList.addView(card)
        }
    }

    private fun showConnectionScreen() {
        val server = selectedServer ?: return showError("Выберите сервер")
        selectedProtocol = server.preferredProtocolOption()
        if (selectedProtocol?.enabled != true) return showError("Нет доступной VPN-конфигурации")
        renderProtocols(server)
        showState(AppUiState.ConnectionSetupState)
    }

    private fun renderProtocols(server: UiServer) {
        protocolsList.removeAllViews()
        listOf(
            UiProtocolOption.wireguard(server.wireGuardConfig, server.wireGuardConfigSource),
            UiProtocolOption.outlineShadowsocks(server.outlineShadowsocksConfig, server.outlineShadowsocksConfigSource),
            UiProtocolOption.xrayReality(server.xrayRealityConfig, server.xrayRealityConfigSource),
            UiProtocolOption.disabled("AmneziaWG", ProtocolType.AMNEZIAWG),
            UiProtocolOption.disabled("OpenVPN UDP", ProtocolType.OPENVPN_UDP),
            UiProtocolOption.disabled("OpenVPN TCP", ProtocolType.OPENVPN_TCP)
        ).forEach { p ->
            val card = layoutInflater.inflate(R.layout.item_simple_card, protocolsList, false) as MaterialCardView
            card.findViewById<TextView>(R.id.cardTitle).text = p.name
            card.findViewById<TextView>(R.id.cardSubtitle).text = if (p.enabled) {
                val src = p.configSource?.let { " · Config source: $it" } ?: ""
                "Доступно$src"
            } else if (p.type == ProtocolType.OUTLINE_SHADOWSOCKS) {
                "Outline Shadowsocks server is not configured"
            } else "Скоро"
            card.alpha = if (p.enabled) 1f else 0.6f
            if (p.enabled) card.setOnClickListener { selectedProtocol = p; renderProtocols(server) }
            card.isChecked = selectedProtocol?.name == p.name
            protocolsList.addView(card)
        }
    }

    private fun connect() {
        val protocol = selectedProtocol ?: return showError("Выберите протокол")
        if (!protocol.enabled || protocol.config.isNullOrBlank() || protocol.config.trim().lowercase() == "null") return showError("Config is not available for ${protocol.name}")
        Log.d(TAG, "vpn permission check")
        val intent = VpnService.prepare(this)
        if (intent != null) {
            Log.d(TAG, "VPN permission is required")
            pendingVpnPermissionConnect = true
            setConnecting(true)
            launchVpnPermission(intent)
            return
        }
        pendingVpnPermissionConnect = false
        connectInternal()
    }

    internal fun launchVpnPermission(intent: Intent) {
        vpnPermissionLauncher.launch(intent)
    }

    private fun connectInternal() {
        val server = selectedServer ?: return
        val protocol = selectedProtocol ?: return
        pendingVpnPermissionConnect = false
        lifecycleScope.launch {
            setConnecting(true)
            val adapter = adapterFor(protocol)
            Log.d(TAG, "vpn connect start protocol=${protocol.type.name.lowercase()}")
            val vpnConfig = VpnConfig(server.id, "", protocol.config)
            val prepareResult = withContext(Dispatchers.IO) { adapter.prepare(vpnConfig) }
            val result = if (prepareResult.ok) {
                withContext(Dispatchers.IO) { adapter.connect(vpnConfig) }
            } else {
                com.zooot.vpn.vpn.protocol.ConnectResult(false, prepareResult.message)
            }
            Log.d(TAG, "vpn connect result protocol=${protocol.type.name.lowercase()} success=${result.ok} message=${result.message.take(120)}")
            if (result.ok) {
                clearError()
                session = ConnectionSession(server, protocol.name, SystemClock.elapsedRealtime())
                renderConnectedSession(session!!)
                startTimer()
                showState(AppUiState.ConnectedState)
                Log.d(TAG, "connected screen rendered")
            } else {
                session = null
                showState(AppUiState.ConnectionSetupState)
                showError(connectionErrorMessage(protocol, result.message, server))
            }
            setConnecting(false)
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            Log.d(TAG, "disconnect start")
            setDisconnecting(true)
            val adapter: VpnProtocolAdapter = selectedProtocol?.let { adapterFor(it) } ?: fakeAdapter
            val result = withContext(Dispatchers.IO) { adapter.disconnect() }
            Log.d(TAG, "disconnect result success=${result.ok}")
            if (result.ok) {
                stopTimer()
                session = null
                showState(AppUiState.ConnectionSetupState)
                clearError()
                setDisconnectedUi()
            } else {
                if (session != null) showState(AppUiState.ConnectedState)
                showError(result.message)
            }
            setDisconnecting(false)
        }
    }

    private fun adapterFor(protocol: UiProtocolOption): VpnProtocolAdapter = when (protocol.type) {
        ProtocolType.WIREGUARD -> wireGuardAdapter
        ProtocolType.XRAY_VLESS_REALITY -> xrayRealityAdapter
        ProtocolType.OUTLINE_SHADOWSOCKS -> outlineShadowsocksAdapter
        else -> fakeAdapter
    }

    private fun connectionErrorMessage(protocol: UiProtocolOption, raw: String, server: UiServer): String =
        if (protocol.type == ProtocolType.WIREGUARD && hasValidConfig(server.xrayRealityConfig))
            "WireGuard handshake unstable. Try Reality/TCP fallback."
        else raw

    private fun hasValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"

    private fun startTimer() {
        timerJob?.cancel()
        Log.d(TAG, "connection timer started")
        timerJob = lifecycleScope.launch {
            while (true) {
                val s = session ?: break
                connectedDuration.text = TimerFormatter.formatElapsed(SystemClock.elapsedRealtime() - s.startedAtMs)
                val stats = trafficProvider.read()
                connectedTrafficRx.text = "↓ ${stats.rx}"
                connectedTrafficTx.text = "↑ ${stats.tx}"
                delay(1000)
            }
        }
    }
    private fun stopTimer() { timerJob?.cancel(); timerJob = null; Log.d(TAG, "connection timer stopped") }

    private fun showState(state: AppUiState) {
        currentState = state
        startContainer.visibility = if (state is AppUiState.StartState) View.VISIBLE else View.GONE
        serverSelectionContainer.visibility = if (state is AppUiState.ServerSelectionState) View.VISIBLE else View.GONE
        connectionContainer.visibility = if (state is AppUiState.ConnectionSetupState) View.VISIBLE else View.GONE
        connectedContainer.visibility = if (state is AppUiState.ConnectedState) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        when (currentState) {
            is AppUiState.ConnectionSetupState -> showState(AppUiState.ServerSelectionState)
            is AppUiState.ServerSelectionState -> showState(AppUiState.StartState)
            else -> super.onBackPressed()
        }
    }

    private fun showError(m: String) { errorCard.visibility = View.VISIBLE; errorText.text = m }
    private fun clearError() { errorCard.visibility = View.GONE; errorText.text = "" }

    private fun setLoading(isLoading: Boolean) {
        continueButton.isEnabled = !isLoading
        linkInput.isEnabled = !isLoading
        continueButton.text = if (isLoading) "Загрузка..." else "Продолжить"
    }

    private fun setConnecting(isConnecting: Boolean) {
        connectButton.isEnabled = !isConnecting
        connectButton.text = if (isConnecting) "Подключаемся..." else "Подключиться"
    }

    private fun setDisconnecting(isDisconnecting: Boolean) {
        disconnectButton.isEnabled = !isDisconnecting
        disconnectButton.text = if (isDisconnecting) "Отключаемся..." else "Отключиться"
    }

    private fun renderConnectedSession(active: ConnectionSession) {
        connectedStatus.text = "Подключено"
        connectedServer.text = "${active.server.country} · ${active.server.city} · ${active.server.serverIp}"
        connectedProtocol.text = active.protocol
        connectedDuration.text = "00:00:00"
        connectedTrafficRx.text = "↓ —"
        connectedTrafficTx.text = "↑ —"
    }

    private fun setDisconnectedUi() {
        connectedStatus.text = "Отключено"
        connectButton.text = "Подключиться"
    }

    private fun readBackendUrl(): String {
        val prefs = getSharedPreferences("zooot_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("backend_url", null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        return if (BuildConfig.DEBUG) DEFAULT_DEBUG_BACKEND_URL else ZootApiClient.backendBaseUrl
    }
}

sealed class AppUiState { object StartState: AppUiState(); object ServerSelectionState: AppUiState(); object ConnectionSetupState: AppUiState(); object ConnectedState: AppUiState() }
data class UiServer(val id: String, val country: String, val city: String, val serverIp: String, val loadPercent: Int, val latencyMs: Int, val online: Boolean, val wireGuardConfig: String?, val wireGuardConfigSource: String?, val xrayRealityConfig: String? = null, val xrayRealityConfigSource: String? = null, val outlineShadowsocksConfig: String? = null, val outlineShadowsocksConfigSource: String? = null) {
    fun hasAnyConnectableConfig(): Boolean = hasValidConfig(wireGuardConfig) || hasValidConfig(outlineShadowsocksConfig) || hasValidConfig(xrayRealityConfig)
    fun preferredProtocolOption(): UiProtocolOption = when {
        hasValidConfig(wireGuardConfig) -> UiProtocolOption.wireguard(wireGuardConfig, wireGuardConfigSource)
        hasValidConfig(outlineShadowsocksConfig) -> UiProtocolOption.outlineShadowsocks(outlineShadowsocksConfig, outlineShadowsocksConfigSource)
        else -> UiProtocolOption.xrayReality(xrayRealityConfig, xrayRealityConfigSource)
    }
    private fun hasValidConfig(config: String?): Boolean = !config.isNullOrBlank() && config.trim().lowercase() != "null"
}
data class UiProtocolOption(val name: String, val type: ProtocolType, val enabled: Boolean, val config: String?, val configSource: String?) { companion object { fun wireguard(config: String?, source: String?) = UiProtocolOption("WireGuard", ProtocolType.WIREGUARD, isValidConfig(config), config, source); fun xrayReality(config: String?, source: String?) = UiProtocolOption("VLESS Reality", ProtocolType.XRAY_VLESS_REALITY, isValidConfig(config), config, source); fun outlineShadowsocks(config: String?, source: String?) = UiProtocolOption("Outline Shadowsocks", ProtocolType.OUTLINE_SHADOWSOCKS, isValidConfig(config), config, source); fun disabled(name: String, type: ProtocolType)=UiProtocolOption(name,type,false,null,null); private fun isValidConfig(config: String?) = !config.isNullOrBlank() && config.trim().lowercase() != "null" } }
data class ConnectionSession(val server: UiServer, val protocol: String, val startedAtMs: Long)

data class LinkValidationResult(val valid: Boolean, val token: String? = null, val error: String? = null)
object LinkInputParser {
    fun validate(raw: String): LinkValidationResult {
        if (raw.isBlank()) return LinkValidationResult(false, error = "Введите ссылку подключения")
        if (!raw.startsWith("zoootconf://")) return LinkValidationResult(false, error = "Неверный формат ссылки")
        val token = parseToken(raw) ?: return LinkValidationResult(false, error = "Неверный формат ссылки")
        return LinkValidationResult(true, token)
    }
    fun parseToken(raw: String): String? = DeepLinkParser.extractToken(raw)
}
object LinkFlowContract {
    const val DEVICE_NAME = "Android device"
    const val DEBUG_MVP_BACKEND_URL = "http://31.59.45.197:8080"
}
object ServerRecommendation { fun pick(servers: List<UiServer>): UiServer? = servers.filter { it.online && it.hasAnyConnectableConfig() }.sortedWith(compareBy<UiServer>{it.loadPercent}.thenBy{it.latencyMs}).firstOrNull() ?: servers.firstOrNull() }
object TimerFormatter { fun formatElapsed(ms: Long): String { val total = ms/1000; val h=total/3600; val m=(total%3600)/60; val s=total%60; return "%02d:%02d:%02d".format(h,m,s) } }
object UiMapper { fun toUiServer(s: com.zooot.vpn.selector.ServerCandidate): UiServer { val wg = s.protocols.firstOrNull{it.type==com.zooot.vpn.selector.Proto.WIREGUARD && it.health!=com.zooot.vpn.selector.HealthStatus.FAILED}; val xray = s.protocols.firstOrNull{it.type==com.zooot.vpn.selector.Proto.XRAY_VLESS_REALITY && it.health!=com.zooot.vpn.selector.HealthStatus.FAILED}; val outline = s.protocols.firstOrNull{it.type==com.zooot.vpn.selector.Proto.OUTLINE_SHADOWSOCKS && it.health!=com.zooot.vpn.selector.HealthStatus.FAILED}; return UiServer(s.serverId,s.country,s.city,s.serverIp,s.loadPercent,s.latencyMs,s.status==com.zooot.vpn.selector.ServerStatus.ONLINE,wg?.config,wg?.configSource,xray?.config,xray?.configSource,outline?.config,outline?.configSource) } }

data class TrafficStats(val rx: String, val tx: String)
interface VpnTrafficStatsProvider { fun read(): TrafficStats }
object NoopTrafficStatsProvider: VpnTrafficStatsProvider { override fun read(): TrafficStats = TrafficStats("—", "—") }
