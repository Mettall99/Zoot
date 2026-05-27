package com.zooot.vpn

import android.content.Context
import android.net.VpnService
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
import com.zooot.vpn.api.ResolveTokenResult
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.ProtocolType
import com.zooot.vpn.vpn.protocol.VpnConfig
import com.zooot.vpn.vpn.protocol.VpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.WireGuardProtocolAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "ManualLinkFlow"
        private const val DEFAULT_DEBUG_BACKEND_URL = "http://31.59.45.197:8080"
    }
    private lateinit var wireGuardAdapter: WireGuardProtocolAdapter
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
    private lateinit var protocolsList: LinearLayout
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var connectedDuration: TextView
    private lateinit var connectedTrafficRx: TextView
    private lateinit var connectedTrafficTx: TextView

    private var currentState: AppUiState = AppUiState.StartState
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) connectInternal() else showError("VPN permission denied")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        wireGuardAdapter = WireGuardProtocolAdapter(this)
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
        protocolsList = findViewById(R.id.protocolsList)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorValue)
        connectedDuration = findViewById(R.id.connectedDuration)
        connectedTrafficRx = findViewById(R.id.connectedTrafficRx)
        connectedTrafficTx = findViewById(R.id.connectedTrafficTx)
    }

    private fun setupActions() {
        findViewById<MaterialButton>(R.id.continueButton).setOnClickListener {
            Log.d(TAG, "manual link submit clicked")
            submitLink(linkInput.text?.toString().orEmpty(), source = "manual")
        }
        findViewById<MaterialButton>(R.id.selectServerButton).setOnClickListener { showConnectionScreen() }
        findViewById<MaterialButton>(R.id.backToServersButton).setOnClickListener { showState(AppUiState.ServerSelectionState) }
        findViewById<MaterialButton>(R.id.connectButton).setOnClickListener { connect() }
        findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener { disconnect() }
    }

    private fun submitLink(rawLink: String, source: String) {
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

    private fun loadConfigFromToken(token: String) {
        clearError(); showState(AppUiState.ServerSelectionState)
        lifecycleScope.launch {
            val deviceId = DeviceIdentity.getOrCreate(this@MainActivity)
            val backendUrl = readBackendUrl()
            Log.d(TAG, "resolve-token start backendUrl=$backendUrl hasDeviceId=${deviceId.isNotBlank()}")
            try {
                val result = ZootApiClient.resolveToken(token, deviceId, "Android device", backendUrl)
                resolveResult = result
                val servers = result.servers.map { UiMapper.toUiServer(it) }
                Log.d(TAG, "resolve-token success servers=${servers.size}")
                if (servers.none { !it.wireGuardConfig.isNullOrBlank() && it.wireGuardConfig.trim().lowercase() != "null" }) {
                    showState(AppUiState.StartState)
                    showError("Нет доступной WireGuard-конфигурации")
                    return@launch
                }
                selectedServer = ServerRecommendation.pick(servers)
                renderServers(servers)
            } catch (e: Exception) {
                Log.e(TAG, "resolve-token failed type=${e::class.java.simpleName} message=${safeErrorMessage(e)}")
                showState(AppUiState.StartState)
                showError(mapResolveError(e))
            }
        }
    }
    private fun safeErrorMessage(e: Throwable): String = when (e) {
        is ApiHttpException -> "http_${e.code}"
        else -> e.message?.take(120).orEmpty()
    }

    private fun mapResolveError(e: Exception): String = when (e) {
        is ApiHttpException -> if (e.code == 401 || e.code == 404) "Ссылка недействительна" else "Не удалось подключиться к серверу"
        is IOException -> "Не удалось подключиться к серверу"
        else -> "Не удалось подключиться к серверу"
    }

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
        selectedProtocol = UiProtocolOption.wireguard(server.wireGuardConfig)
        if (!selectedProtocol!!.enabled) return showError("Нет доступной WireGuard-конфигурации")
        renderProtocols(server)
        showState(AppUiState.ConnectionSetupState)
    }

    private fun renderProtocols(server: UiServer) {
        protocolsList.removeAllViews()
        listOf(
            UiProtocolOption.wireguard(server.wireGuardConfig), UiProtocolOption.disabled("AmneziaWG"), UiProtocolOption.disabled("VLESS Reality"),
            UiProtocolOption.disabled("OpenVPN UDP"), UiProtocolOption.disabled("OpenVPN TCP")
        ).forEach { p ->
            val card = layoutInflater.inflate(R.layout.item_simple_card, protocolsList, false) as MaterialCardView
            card.findViewById<TextView>(R.id.cardTitle).text = p.name
            card.findViewById<TextView>(R.id.cardSubtitle).text = if (p.enabled) "Доступно" else "Скоро"
            card.alpha = if (p.enabled) 1f else 0.6f
            if (p.enabled) card.setOnClickListener { selectedProtocol = p; renderProtocols(server) }
            card.isChecked = selectedProtocol?.name == p.name
            protocolsList.addView(card)
        }
    }

    private fun connect() {
        val server = selectedServer ?: return showError("Выберите сервер")
        val protocol = selectedProtocol ?: return showError("Выберите протокол")
        if (protocol.name != "WireGuard" || protocol.config.isNullOrBlank() || protocol.config.trim().lowercase() == "null") return showError("WireGuard config is not available")
        val intent = VpnService.prepare(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else connectInternal()
    }

    private fun connectInternal() {
        val server = selectedServer ?: return
        val protocol = selectedProtocol ?: return
        lifecycleScope.launch {
            val result = wireGuardAdapter.connect(VpnConfig(server.id, "", protocol.config))
            if (result.ok) {
                session = ConnectionSession(server, protocol.name, SystemClock.elapsedRealtime())
                startTimer()
                showState(AppUiState.ConnectedState)
            } else showError(result.message)
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            val adapter: VpnProtocolAdapter = if (selectedProtocol?.name == "WireGuard") wireGuardAdapter else fakeAdapter
            val result = adapter.disconnect()
            if (result.ok) {
                stopTimer()
                showState(AppUiState.ConnectionSetupState)
            } else showError(result.message)
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            while (true) {
                val s = session ?: break
                connectedDuration.text = TimerFormatter.formatElapsed(SystemClock.elapsedRealtime() - s.startedAtMs)
                val stats = trafficProvider.read()
                connectedTrafficRx.text = stats.rx
                connectedTrafficTx.text = stats.tx
                delay(1000)
            }
        }
    }
    private fun stopTimer() { timerJob?.cancel() }

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

    private fun readBackendUrl(): String {
        val prefs = getSharedPreferences("zooot_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("backend_url", null)?.trim().orEmpty()
        if (saved.isNotBlank()) return saved
        return if (BuildConfig.DEBUG) DEFAULT_DEBUG_BACKEND_URL else ZootApiClient.backendBaseUrl
    }
}

sealed class AppUiState { object StartState: AppUiState(); object ServerSelectionState: AppUiState(); object ConnectionSetupState: AppUiState(); object ConnectedState: AppUiState() }
data class UiServer(val id: String, val country: String, val city: String, val serverIp: String, val loadPercent: Int, val latencyMs: Int, val online: Boolean, val wireGuardConfig: String?)
data class UiProtocolOption(val name: String, val enabled: Boolean, val config: String?) { companion object { fun wireguard(config: String?) = UiProtocolOption("WireGuard", isValidWireGuardConfig(config), config); fun disabled(name: String)=UiProtocolOption(name,false,null); private fun isValidWireGuardConfig(config: String?) = !config.isNullOrBlank() && config.trim().lowercase() != "null" } }
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
object ServerRecommendation { fun pick(servers: List<UiServer>): UiServer? = servers.filter { it.online && !it.wireGuardConfig.isNullOrBlank() && it.wireGuardConfig.trim().lowercase() != "null" }.sortedWith(compareBy<UiServer>{it.loadPercent}.thenBy{it.latencyMs}).firstOrNull() ?: servers.firstOrNull() }
object TimerFormatter { fun formatElapsed(ms: Long): String { val total = ms/1000; val h=total/3600; val m=(total%3600)/60; val s=total%60; return "%02d:%02d:%02d".format(h,m,s) } }
object UiMapper { fun toUiServer(s: com.zooot.vpn.selector.ServerCandidate)= UiServer(s.serverId,s.country,s.city,s.serverIp,s.loadPercent,s.latencyMs,s.status==com.zooot.vpn.selector.ServerStatus.ONLINE,s.protocols.firstOrNull{it.type==com.zooot.vpn.selector.Proto.WIREGUARD && it.health!=com.zooot.vpn.selector.HealthStatus.FAILED}?.config) }

data class TrafficStats(val rx: String, val tx: String)
interface VpnTrafficStatsProvider { fun read(): TrafficStats }
object NoopTrafficStatsProvider: VpnTrafficStatsProvider { override fun read(): TrafficStats = TrafficStats("—", "—") }
