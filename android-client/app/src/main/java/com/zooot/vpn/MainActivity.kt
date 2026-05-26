package com.zooot.vpn

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.zooot.vpn.api.ResolveTokenResult
import com.zooot.vpn.api.ZootApiClient
import com.zooot.vpn.deeplink.DeepLinkParser
import com.zooot.vpn.selector.NetworkType
import com.zooot.vpn.selector.ProtocolSelector
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.Selection
import com.zooot.vpn.vpn.protocol.FakeVpnProtocolAdapter

class MainActivity : Activity() {
    private lateinit var backendUrlInput: EditText
    private lateinit var tokenValue: TextView
    private lateinit var emailValue: TextView
    private lateinit var tariffValue: TextView
    private lateinit var countryValue: TextView
    private lateinit var cityValue: TextView
    private lateinit var serverIpValue: TextView
    private lateinit var protocolValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var errorValue: TextView

    private var currentToken: String = ""
    private var currentBackendUrl: String = ""
    private var currentSelection: Selection? = null
    private var currentResult: ResolveTokenResult? = null
    private var lastLoadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentBackendUrl = readBackendUrl()
        setContentView(buildUi())
        backendUrlInput.setText(currentBackendUrl)

        currentToken = DeepLinkParser.extractToken(intent?.dataString.orEmpty()).orEmpty()
        tokenValue.text = currentToken.ifBlank { "none" }

        if (currentToken.isNotBlank()) {
            loadConfig(auto = true)
        } else {
            updateStatus("Ready")
        }
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        fun label(name: String): TextView = TextView(this).apply { text = name }
        fun value(default: String = "-"): TextView = TextView(this).apply { text = default }

        root.addView(TextView(this).apply { text = "Zooot VPN"; textSize = 24f })
        root.addView(label("Backend URL"))
        backendUrlInput = EditText(this)
        root.addView(backendUrlInput)

        val saveUrlButton = Button(this).apply {
            text = "Save URL"
            setOnClickListener {
                val input = backendUrlInput.text.toString().trim()
                if (input.isNotBlank()) {
                    currentBackendUrl = input
                    saveBackendUrl(input)
                }
            }
        }
        root.addView(saveUrlButton)

        root.addView(label("Token")); tokenValue = value(); root.addView(tokenValue)
        root.addView(label("User email")); emailValue = value(); root.addView(emailValue)
        root.addView(label("Tariff title")); tariffValue = value(); root.addView(tariffValue)
        root.addView(label("Country")); countryValue = value(); root.addView(countryValue)
        root.addView(label("City")); cityValue = value(); root.addView(cityValue)
        root.addView(label("Server IP")); serverIpValue = value(); root.addView(serverIpValue)
        root.addView(label("Selected protocol")); protocolValue = value(); root.addView(protocolValue)
        root.addView(label("Connection status")); statusValue = value("Ready"); root.addView(statusValue)
        root.addView(label("Error")); errorValue = value(); root.addView(errorValue)

        root.addView(Button(this).apply { text = "Load config"; setOnClickListener { loadConfig(auto = false) } })
        root.addView(Button(this).apply { text = "Connect"; setOnClickListener { connect() } })
        root.addView(Button(this).apply { text = "Disconnect"; setOnClickListener { disconnect() } })
        root.addView(Button(this).apply { text = "Retry"; setOnClickListener { if (lastLoadFailed) loadConfig(auto = false) } })
        return root
    }

    private fun loadConfig(auto: Boolean) {
        if (currentToken.isBlank()) {
            showError("Invalid token")
            return
        }
        updateStatus("Loading")
        clearError()

        Thread {
            try {
                val result = ZootApiClient.resolveToken(currentToken, currentBackendUrl)
                val mapping = mapConfig(result)
                runOnUiThread {
                    render(mapping)
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
        currentSelection ?: run {
            showError("No healthy protocols")
            return
        }
        FakeVpnProtocolAdapter().start(currentSelection!!.configUrl)
        updateStatus("Connected")
        clearError()
    }

    private fun disconnect() {
        FakeVpnProtocolAdapter().stop()
        updateStatus("Disconnected")
    }

    private fun mapConfig(result: ResolveTokenResult): UiState {
        val country = result.preferredCountry.ifBlank { result.servers.firstOrNull()?.country.orEmpty() }
        val selection = ProtocolSelector.select(result.servers, country, NetworkType.WIFI, emptyMap())
            ?: throw IllegalStateException("No healthy protocols")
        val server = result.servers.firstOrNull { it.serverId == selection.serverId }
            ?: throw IllegalStateException("No servers available")
        currentSelection = selection
        currentResult = result

        return UiState(
            email = result.userEmail.ifBlank { "demo@zooot.local" },
            tariff = result.tariffTitle.ifBlank { "Demo Monthly" },
            country = server.country,
            city = server.city.ifBlank { "Frankfurt" },
            serverIp = server.serverIp,
            protocol = selection.protocol.name.lowercase()
        )
    }

    private fun render(state: UiState) {
        emailValue.text = state.email
        tariffValue.text = state.tariff
        countryValue.text = state.country
        cityValue.text = state.city
        serverIpValue.text = state.serverIp
        protocolValue.text = state.protocol
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

    private fun updateStatus(status: String) { statusValue.text = status }
    private fun showError(message: String) { errorValue.text = message }
    private fun clearError() { errorValue.text = "-" }
}

data class UiState(
    val email: String,
    val tariff: String,
    val country: String,
    val city: String,
    val serverIp: String,
    val protocol: String
)
