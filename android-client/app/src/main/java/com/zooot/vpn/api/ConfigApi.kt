package com.zooot.vpn.api
interface ConfigApi { suspend fun resolveToken(request: ResolveTokenRequest): ApiResult<ResolveTokenResponse> }
