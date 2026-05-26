package com.zooot.vpn.api
sealed class ApiResult<out T> { data class Success<T>(val data: T): ApiResult<T>(); data class HttpError(val code: Int, val message: String): ApiResult<Nothing>(); data class NetworkError(val message: String): ApiResult<Nothing>(); data class ParseError(val message: String): ApiResult<Nothing>() }
