package com.zooot.vpn.vpn.protocol

import android.content.Context
import com.zooot.vpn.vpn.ZootVpnService

class SingBoxRealityCore(
    private val contextProvider: () -> Context? = { currentApplicationContext() }
) : RealityCore {
    constructor(context: Context) : this({ context.applicationContext })

    override fun isBundled(): Boolean = runCatching {
        Class.forName(LIBBOX_CLASS_NAME)
        Class.forName(LIBBOX_SERVICE_CLASS_NAME)
        Class.forName(LIBBOX_PLATFORM_INTERFACE_CLASS_NAME)
    }.isSuccess

    override fun missingDependencyName(): String = LIBBOX_DEPENDENCY_NAME

    override fun start(singBoxConfigJson: String) {
        if (!isBundled()) throw IllegalStateException(XrayRealityProtocolAdapter.CORE_MISSING_MESSAGE)
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
        const val LIBBOX_DEPENDENCY_NAME = "net.clever-vpn:libbox-android:2.1.0"
        const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
        const val LIBBOX_SERVICE_CLASS_NAME = "io.nekohasekai.libbox.BoxService"
        const val LIBBOX_PLATFORM_INTERFACE_CLASS_NAME = "io.nekohasekai.libbox.PlatformInterface"
        private const val START_TIMEOUT_MS = 10_000L
        private const val STOP_TIMEOUT_MS = 5_000L

        private fun currentApplicationContext(): Context? = runCatching {
            Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null)?.let { it as Context }?.applicationContext
        }.getOrNull()
    }
}
