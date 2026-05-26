package com.zooot.vpn.deeplink

object DeepLinkParser {
    fun extractToken(uri: String): String? {
        if (!uri.startsWith("zoootconf://")) return null
        val payload = uri.removePrefix("zoootconf://")

        return when {
            payload.startsWith("connect?token=") -> payload.substringAfter("token=").trim().ifBlank { null }
            payload.startsWith("connect/") -> payload.substringAfter("connect/").trim().ifBlank { null }
            payload.startsWith("connect") -> null
            else -> payload.trim().ifBlank { null }
        }
    }
}
