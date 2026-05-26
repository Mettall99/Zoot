package com.zooot.vpn.api
class ApiClient(private val baseUrl: String) : ConfigApi { override suspend fun resolveToken(request: ResolveTokenRequest): ApiResult<ResolveTokenResponse> = ApiResult.NetworkError("Не удалось подключиться к API") }
