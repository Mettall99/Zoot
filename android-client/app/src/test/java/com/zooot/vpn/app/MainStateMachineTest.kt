package com.zooot.vpn.app

import com.zooot.vpn.api.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

class MainStateMachineTest {
    @Test
    fun tokenToReady() = runBlocking {
        val api = object: ConfigApi { override suspend fun resolveToken(request: ResolveTokenRequest) = ApiResult.Success(ResolveTokenResponse(UserDto("u","e"), null, listOf(ServerDto("s1","Germany","s","online",10,30,listOf(ProtocolDto("wireguard","healthy","cfg")))))) }
        val sm = MainStateMachine(api)
        sm.onDeepLink("zoootconf://demo-token")
        assertTrue(sm.state.status is ConnectionState.ReadyToConnect)
    }
}
