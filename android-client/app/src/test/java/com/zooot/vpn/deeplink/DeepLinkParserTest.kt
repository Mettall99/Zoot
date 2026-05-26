package com.zooot.vpn.deeplink

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinkParserTest {
    @Test fun parseRawToken() { assertEquals("abc", DeepLinkParser.extractToken("zoootconf://abc")) }
    @Test fun parseQueryToken() { assertEquals("abc", DeepLinkParser.extractToken("zoootconf://connect?token=abc")) }
    @Test fun parsePathToken() { assertEquals("abc", DeepLinkParser.extractToken("zoootconf://connect/abc")) }
    @Test fun rejectUnsupported() { assertNull(DeepLinkParser.extractToken("http://x")) }
    @Test fun rejectEmpty() { assertNull(DeepLinkParser.extractToken("zoootconf://")) }
}
