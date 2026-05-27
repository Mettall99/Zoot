package com.zooot.vpn

import com.zooot.vpn.selector.HealthStatus
import com.zooot.vpn.selector.Proto
import com.zooot.vpn.selector.ServerCandidate
import com.zooot.vpn.selector.ServerProtocol
import com.zooot.vpn.selector.ServerStatus
import org.junit.Assert.*
import org.junit.Test

class NewFlowUnitTest {
    @Test fun manualLinkParsing() { assertEquals("demo-token", LinkInputParser.parseToken("zoootconf://demo-token")) }
    @Test fun invalidLinkValidation() { assertFalse(LinkInputParser.validate("http://bad").valid) }
    @Test fun deepLinkStartsResolveTokenFlowParserReady() { assertNotNull(LinkInputParser.parseToken("zoootconf://demo-token")) }
    @Test fun recommendedServerChoice() {
        val servers = listOf(
            UiMapper.toUiServer(ServerCandidate("1","DE",ServerStatus.ONLINE,80,40,listOf(ServerProtocol(Proto.WIREGUARD,HealthStatus.HEALTHY,"", "a")),"Frankfurt","1.1.1.1")),
            UiMapper.toUiServer(ServerCandidate("2","DE",ServerStatus.ONLINE,20,20,listOf(ServerProtocol(Proto.WIREGUARD,HealthStatus.HEALTHY,"", "b")),"Berlin","2.2.2.2"))
        )
        assertEquals("2", ServerRecommendation.pick(servers)?.id)
    }
    @Test fun timerFormattingWorks() { assertEquals("00:00:05", TimerFormatter.formatElapsed(5000)) }
}
