package com.zooot.vpn.api

data class ResolveTokenRequest(val token: String)
data class ResolveTokenResponse(val user: UserDto, val subscription: TariffDto?, val servers: List<ServerDto>)
data class UserDto(val id: String, val email: String)
data class TariffDto(val id: String, val name: String, val status: String)
data class ServerDto(val id: String, val country: String, val name: String, val status: String, val loadPercent: Int, val latencyMs: Int, val protocols: List<ProtocolDto>)
data class ProtocolDto(val type: String, val health: String, val configUrl: String)
