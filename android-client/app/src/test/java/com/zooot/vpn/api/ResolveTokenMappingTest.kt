package com.zooot.vpn.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveTokenMappingTest {
    @Test
    fun keepsServerAndProtocolFields() {
        val response = ResolveTokenResponse(
            user = UserDto("u1", "demo@zooot.local"),
            subscription = TariffDto("t1", "Demo", "active"),
            servers = listOf(ServerDto("s1", "Germany", "Germany #1", "online", 20, 40, listOf(ProtocolDto("amneziawg", "healthy", "https://cfg"))))
        )
        assertEquals("Germany", response.servers.first().country)
        assertEquals("amneziawg", response.servers.first().protocols.first().type)
    }
}
