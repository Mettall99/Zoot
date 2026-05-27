package com.zooot.vpn.api

import com.zooot.vpn.selector.Proto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun parsesProtocolObjectsWithPortAndHealthStatus() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":[{"type":"wireguard","port":51821,"health_status":"healthy","config":"[Interface]\\nPrivateKey = hidden"},{"type":"amneziawg","port":51820,"health_status":"failed"}]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertEquals(51821, wg.port)
        assertNotNull(wg.config)
        val amnezia = result.servers.first().protocols.first { it.type == Proto.AMNEZIAWG }
        assertEquals(com.zooot.vpn.selector.HealthStatus.FAILED, amnezia.health)
    }

    @Test
    fun parsesLegacyProtocolStringFormat() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":["wireguard"]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertNull(wg.port)
    }

    @Test
    fun nullConfigParsedAsUnavailable() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":[{"type":"wireguard","config":null}]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertEquals("", wg.config)
    }

    @Test
    fun stringNullConfigTreatedAsUnavailable() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":[{"type":"wireguard","config":"null"}]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertEquals("", wg.config)
    }
    @Test
    fun missingConfigFieldParsedAsUnavailable() {
        val raw = """
            {"preferred_country":"DE","servers":[{"id":"srv1","country":"DE","protocols":[{"type":"wireguard"}]}]}
        """.trimIndent()
        val method = ZootApiClient::class.java.getDeclaredMethod("parseResult", String::class.java)
        method.isAccessible = true
        val result = method.invoke(ZootApiClient, raw) as ResolveTokenResult
        val wg = result.servers.first().protocols.first { it.type == Proto.WIREGUARD }
        assertEquals("", wg.config)
    }

}
