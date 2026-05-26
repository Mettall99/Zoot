package com.zooot.vpn.api

object ZootApiClient {
    fun resolveConfigEndpoint(token: String): String =
        "https://api.zooot.local/v1/mobile/config/$token"
}
