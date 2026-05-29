package io.nekohasekai.libbox

object RuntimeCapableLibbox {
    @JvmStatic fun version(): String = "1.13.12-test"
}

object Libbox {
    @JvmStatic fun version(): String = "1.13.12-test"
    @JvmStatic fun newService(config: String, platform: PlatformInterface): BoxService = BoxService(config, platform)
    @JvmStatic fun newCommandServer(handler: CommandServerHandler, platform: PlatformInterface): CommandServer = CommandServer(handler, platform)
}

interface PlatformInterface
interface CommandServerHandler {
    fun serviceStop()
    fun serviceReload()
    fun getSystemProxyStatus(): Any?
    fun setSystemProxyEnabled(isEnabled: Boolean)
    fun writeDebugMessage(message: String?)
}

class OverrideOptions

class BoxService(private val config: String, private val platform: PlatformInterface) {
    var started = false
    fun start() {
        check(config.contains("tun-in")) { "config missing tun inbound" }
        started = true
    }
    fun needWIFIState(): Boolean = false
    fun close() { started = false }
}

class CommandServer(private val handler: CommandServerHandler, private val platform: PlatformInterface) {
    var started = false
    var serviceStarted = false
    fun start() { started = true }
    fun startOrReloadService(config: String, options: OverrideOptions) {
        check(started) { "command server was not started" }
        check(config.contains("tun-in")) { "config missing tun inbound" }
        serviceStarted = true
    }
    fun needWIFIState(): Boolean = false
    fun closeService() { serviceStarted = false }
    fun close() { started = false }
}
