package com.zooot.vpn.api

import com.zooot.vpn.selector.Proto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ProtocolConfigParsingTest {
    @Test
    fun parsesProtocolConfigField() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":[{"protocol":"wireguard","config":"[Interface]\\nPrivateKey = hidden"}]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertNotNull(wg.config)
        assertEquals(true, wg.config!!.contains("[Interface]"))
    }
}
