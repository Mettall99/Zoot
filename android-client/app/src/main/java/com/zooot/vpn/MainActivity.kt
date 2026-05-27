package com.zooot.vpn

import android.content.Context
import android.net.VpnService
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.zooot.vpn.api.ResolveTokenResult
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.selector.NetworkType
import com.zooot.vpn.selector.ProtocolSelector
import com.zooot.vpn.selector.Selection
import com.zooot.vpn.vpn.protocol.ConnectResult
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.ProtocolType
import com.zooot.vpn.vpn.protocol.VpnConfig
import com.zooot.vpn.vpn.protocol.VpnProtocolAdapter
import com.zooot.vpn.vpn.protocol.WireGuardProtocolAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }
    private lateinit var backendUrlInput: EditText
    private lateinit var tokenValue: TextView
    private lateinit var emailValue: TextView
    private lateinit var tariffValue: TextView
    private lateinit var countryValue: TextView
    private lateinit var cityValue: TextView
    private lateinit var serverIpValue: TextView
    private lateinit var protocolValue: TextView
    private lateinit var configStatusValue: TextView
    private lateinit var statusBadge: TextView
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorValue: TextView

    private var currentToken: String = ""
    private var currentBackendUrl: String = ""
    private var currentSelection: Selection? = null
    private var lastLoadFailed = false
    private lateinit var wireGuardAdapter: WireGuardProtocolAdapter
    private val fakeAdapter = FakeVpnProtocolAdapter(ProtocolType.AMNEZIAWG)
    private val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) { connectInternal() } else { showError("VPN permission denied") }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        wireGuardAdapter = WireGuardProtocolAdapter(this)

        currentBackendUrl = readBackendUrl()
        backendUrlInput.setText(currentBackendUrl)
        findViewById<MaterialButton>(R.id.saveUrlButton).setOnClickListener {
            val input = backendUrlInput.text.toString().trim()
            if (input.isNotBlank()) {
                currentBackendUrl = input
                saveBackendUrl(input)
            }
        }

        findViewById<MaterialButton>(R.id.loadConfigButton).setOnClickListener { loadConfig(auto = false) }
        findViewById<MaterialButton>(R.id.connectButton).setOnClickListener { connect() }
        findViewById<MaterialButton>(R.id.disconnectButton).setOnClickListener { disconnect() }
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener { if (lastLoadFailed) loadConfig(auto = false) }

        currentToken = DeepLinkParser.extractToken(intent?.dataString.orEmpty()).orEmpty()
        tokenValue.text = readable(currentToken)

        if (currentToken.isNotBlank()) loadConfig(auto = true) else updateStatus("Ready")
        clearError()
    }

    private fun bindViews() {
        backendUrlInput = findViewById(R.id.backendUrlInput)
        tokenValue = findViewById(R.id.tokenValue)
        emailValue = findViewById(R.id.emailValue)
        tariffValue = findViewById(R.id.tariffValue)
        countryValue = findViewById(R.id.countryValue)
        cityValue = findViewById(R.id.cityValue)
        serverIpValue = findViewById(R.id.serverIpValue)
        protocolValue = findViewById(R.id.protocolValue)
        configStatusValue = findViewById(R.id.configStatusValue)
        statusBadge = findViewById(R.id.statusBadge)
        errorCard = findViewById(R.id.errorCard)
        errorValue = findViewById(R.id.errorValue)
        render(UiState("", "", "", "", "", "", ""))
    }

    private fun loadConfig(auto: Boolean) {
        if (currentToken.isBlank()) return showError("Invalid token")
        updateStatus("Loading")
        clearError()
        Thread {
            try {
                val deviceId = com.zooot.vpn.api.DeviceIdentity.getOrCreate(this)
                val result = ZootApiClient.resolveToken(currentToken, deviceId, "Android device", currentBackendUrl)
                val state = mapConfig(result)
                runOnUiThread {
                    render(state)
                    updateStatus(if (auto) "ReadyToConnect" else "ReadyToConnect")
                    lastLoadFailed = false
                }
            } catch (e: Exception) {
                runOnUiThread {
                    lastLoadFailed = true
                    updateStatus("Error")
                    showError(mapErrorMessage(e.message.orEmpty()))
                }
            }
        }.start()
    }

    private fun connect() {
        val selection = currentSelection ?: return showError("No healthy protocols")
        if (selection.protocol == com.zooot.vpn.selector.Proto.WIREGUARD && !selection.config.isNullOrBlank()) {
            val intent = VpnService.prepare(this)
            if (intent != null) { vpnPermissionLauncher.launch(intent); return }
        }
        connectInternal()
    }

    private fun connectInternal() {
        val selection = currentSelection ?: return showError("No healthy protocols")
        lifecycleScope.launch {
            val adapter: VpnProtocolAdapter = if (selection.protocol == com.zooot.vpn.selector.Proto.WIREGUARD) wireGuardAdapter else fakeAdapter
            Log.d(TAG, "connect: selected protocol=${selection.protocol.name.lowercase()}, config available=${!selection.config.isNullOrBlank()}")
            val result: ConnectResult = adapter.connect(VpnConfig(selection.serverId, selection.configUrl, selection.config))
            Log.d(TAG, "connect: tunnel state result ok=${result.ok}")
            if (result.ok) {
                updateStatus("Connected")
                clearError()
            } else {
                updateStatus("Error")
                showError(result.message.ifBlank { "Connection failed" })
            }
        }
    }

    private fun disconnect() {
        lifecycleScope.launch {
            val sel = currentSelection
            val adapter: VpnProtocolAdapter = if (sel?.protocol == com.zooot.vpn.selector.Proto.WIREGUARD) wireGuardAdapter else fakeAdapter
            val result = adapter.disconnect()
            if (result.ok) {
                updateStatus("Disconnected")
                clearError()
            } else {
                updateStatus("Error")
                showError(result.message.ifBlank { "Disconnect failed" })
            }
        }
    }

    private fun mapConfig(result: ResolveTokenResult): UiState {
        val country = result.preferredCountry.ifBlank { result.servers.firstOrNull()?.country.orEmpty() }
        val selection = ProtocolSelector.select(result.servers, country, NetworkType.WIFI, emptyMap())
            ?: throw IllegalStateException("No healthy protocols")
        Log.d(TAG, "mapConfig: selected protocol=${selection.protocol.name.lowercase()}, wireguard_config_available=${!selection.config.isNullOrBlank()}")
        val server = result.servers.firstOrNull { it.serverId == selection.serverId }
            ?: throw IllegalStateException("No servers available")
        currentSelection = selection
        return UiState(
            result.userEmail.ifBlank { "demo@zooot.local" },
            result.tariffTitle.ifBlank { "Demo Monthly" },
            server.country,
            server.city.ifBlank { "Frankfurt" },
            server.serverIp,
            selection.protocol.name.lowercase(),
            if (selection.config.isNullOrBlank()) "missing" else "available (len=${selection.config.length})",
            !selection.config.isNullOrBlank()
        )
    }

    private fun render(state: UiState) {
        emailValue.text = readable(state.email)
        tariffValue.text = readable(state.tariff)
        countryValue.text = readable(state.country)
        cityValue.text = readable(state.city)
        serverIpValue.text = readable(state.serverIp)
        protocolValue.text = readable(state.protocol)
        configStatusValue.text = readable(state.configStatus)
    }

    private fun readable(value: String): String = value.ifBlank { getString(R.string.empty_value) }

    private fun updateStatus(status: String) {
        statusBadge.text = status
        when (status) {
            "Connected" -> statusBadge.setTextColor(getColor(R.color.success))
            "Disconnected" -> statusBadge.setTextColor(getColor(R.color.neutral))
            "Error" -> statusBadge.setTextColor(getColor(R.color.error))
            else -> statusBadge.setTextColor(getColor(R.color.primary))
        }
        statusBadge.setBackgroundResource(R.drawable.field_background)
    }

    private fun showError(message: String) {
        errorCard.visibility = View.VISIBLE
        errorValue.text = message
    }

    private fun clearError() {
        errorValue.text = ""
        errorCard.visibility = View.GONE
    }

    private fun mapErrorMessage(raw: String): String {
        val text = raw.lowercase()
        return when {
            "http 400" in text || "invalid" in text -> "Invalid token"
            "inactive" in text -> "Subscription inactive"
            "no servers" in text -> "No servers available"
            "no healthy" in text -> "No healthy protocols"
            "timeout" in text || "failed to connect" in text || "unreachable" in text -> "Network error"
            else -> "Unexpected response"
        }
    }

    private fun readBackendUrl(): String {
        val prefs = getSharedPreferences("zooot_prefs", Context.MODE_PRIVATE)
        return prefs.getString("backend_url", ZootApiClient.backendBaseUrl) ?: ZootApiClient.backendBaseUrl
    }

    private fun saveBackendUrl(url: String) {
        getSharedPreferences("zooot_prefs", Context.MODE_PRIVATE).edit().putString("backend_url", url).apply()
    }
}

data class UiState(
    val email: String,
    val tariff: String,
    val country: String,
    val city: String,
    val serverIp: String,
    val protocol: String,
    val configStatus: String,
    val configAvailable: Boolean = false
)
