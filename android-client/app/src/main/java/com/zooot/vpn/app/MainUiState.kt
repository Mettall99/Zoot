package com.zooot.vpn.app
import com.zooot.vpn.selector.Selection

data class MainUiState(val backendUrl: String = DebugConfig.BACKEND_BASE_URL, val token: String? = null, val selectedCountry: String? = null, val selectedServer: String? = null, val selectedProtocol: String? = null, val status: ConnectionState = ConnectionState.Idle, val errorMessage: String? = null, val selection: Selection? = null)
