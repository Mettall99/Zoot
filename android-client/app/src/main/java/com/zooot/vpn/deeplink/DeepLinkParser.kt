package com.zooot.vpn.deeplink

object DeepLinkParser {
    fun extractToken(uri: String): String? {
        return when {
            uri.startsWith("zoootconf://connect?token=") -> uri.substringAfter("token=").ifBlank { null }
            uri.startsWith("zoootconf://") -> uri.removePrefix("zoootconf://").ifBlank { null }
            else -> null
        }
    }
}
