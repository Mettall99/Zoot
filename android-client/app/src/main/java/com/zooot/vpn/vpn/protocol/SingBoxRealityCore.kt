package com.zooot.vpn.vpn.protocol

import android.content.Context
import android.util.Log
import com.zooot.vpn.vpn.LibboxRuntimeSupport
import com.zooot.vpn.vpn.ZootVpnService

class SingBoxRealityCore(
    private val contextProvider: () -> Context? = { currentApplicationContext() }
) : RealityCore {
    constructor(context: Context) : this({ context.applicationContext })

    override fun isBundled(): Boolean = inspect().libboxPresent && runCatching {
        Class.forName(LIBBOX_PLATFORM_INTERFACE_CLASS_NAME)
    }.isSuccess

    override fun missingDependencyName(): String = LIBBOX_DEPENDENCY_NAME

    override fun unavailableReason(): String? {
        val inspection = inspect()
        if (!inspection.libboxPresent) return XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE
        if (!isBundled()) return XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE
        inspection.unsupportedReason?.let { reason ->
            LibboxRuntimeSupport.logInspection(inspection) { message -> safeLogDebug(message) }
            return reason
        }
        return null
    }

    override fun start(singBoxConfigJson: String) {
        unavailableReason()?.let { throw IllegalStateException(it) }
        val context = contextProvider() ?: throw IllegalStateException("Android application context is not available")
        ZootVpnService.startReality(context, singBoxConfigJson)
        if (!ZootVpnService.awaitRealityRunning(START_TIMEOUT_MS)) {
            val error = ZootVpnService.lastRealityError()
            throw IllegalStateException(error ?: "sing-box Reality service did not report running")
        }
    }

    override fun stop() {
        contextProvider()?.let { ZootVpnService.stopReality(it) }
        ZootVpnService.awaitRealityStopped(STOP_TIMEOUT_MS)
    }

    override fun isRunning(): Boolean = ZootVpnService.isRealityRunning()

    companion object {
        const val LIBBOX_DEPENDENCY_NAME = "official sing-box Android libbox AAR (io.nekohasekai.libbox)"
        const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
        const val LIBBOX_PLATFORM_INTERFACE_CLASS_NAME = "io.nekohasekai.libbox.PlatformInterface"
        private const val START_TIMEOUT_MS = 15_000L
        private const val STOP_TIMEOUT_MS = 5_000L

        private fun inspect() = ZootVpnService.inspectLibboxRuntime()
        private fun safeLogDebug(message: String) { runCatching { Log.d("SingBoxRealityCore", LibboxRuntimeSupport.sanitize(message)) } }

        private fun currentApplicationContext(): Context? = runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)?.let { it as Context }?.applicationContext
        }.getOrNull()
    }
}
