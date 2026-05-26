package com.zooot.vpn.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {
    @Test
    fun parseRawToken() {
        assertEquals("TOKEN", DeepLinkParser.extractToken("zoootconf://TOKEN"))
    }

    @Test
    fun parseQueryToken() {
        assertEquals("TOKEN", DeepLinkParser.extractToken("zoootconf://connect?token=TOKEN"))
    }

    @Test
    fun parsePathToken() {
        assertEquals("TOKEN", DeepLinkParser.extractToken("zoootconf://connect/TOKEN"))
    }

    @Test
    fun rejectEmptyToken() {
        assertNull(DeepLinkParser.extractToken("zoootconf://connect?token="))
    }

    @Test
    fun rejectBlankToken() {
        assertNull(DeepLinkParser.extractToken("zoootconf://connect?token=   "))
    }

    @Test
    fun rejectUnsupportedScheme() {
        assertNull(DeepLinkParser.extractToken("http://connect?token=TOKEN"))
    }
}
