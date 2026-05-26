package com.zooot.vpn.deeplink

import java.net.URI

object DeepLinkParser {
    private const val MIN_TOKEN_LENGTH = 3

    fun extractToken(uri: String): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.scheme != "zoootconf") return null

        val hostToken = parsed.host?.trim().orEmpty()
        val pathToken = parsed.path?.trim('/',' ')?.trim().orEmpty()
        val queryToken = parsed.rawQuery
            ?.split('&')
            ?.firstOrNull { it.startsWith("token=") }
            ?.substringAfter('=')
            ?.trim()
            .orEmpty()

        val token = when {
            queryToken.isNotEmpty() -> queryToken
            pathToken.isNotEmpty() && parsed.host == "connect" -> pathToken
            hostToken.isNotEmpty() && parsed.host != "connect" -> hostToken
            else -> ""
        }.trim()

        return token.takeIf { it.isNotBlank() && it.length >= MIN_TOKEN_LENGTH }
    }
}
