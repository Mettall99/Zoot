package com.zooot.vpn.vpn

import io.nekohasekai.libbox.CommandServerHandler
import io.nekohasekai.libbox.OverrideOptions

object CommandServerNoOpenTunLibbox {
    @JvmStatic fun version(): String = "1.13.12-command-no-open-tun-test"
    @JvmStatic fun newCommandServer(handler: CommandServerHandler, platform: NoOpenTunPlatformInterface): CommandServerNoOpenTun = CommandServerNoOpenTun(handler, platform)
}

interface NoOpenTunPlatformInterface {
    fun writeLog(message: String?)
}

class CommandServerNoOpenTun(private val handler: CommandServerHandler, private val platform: NoOpenTunPlatformInterface) {
    fun start() = Unit
    fun startOrReloadService(config: String, options: OverrideOptions) = Unit
    fun close() = Unit
}
