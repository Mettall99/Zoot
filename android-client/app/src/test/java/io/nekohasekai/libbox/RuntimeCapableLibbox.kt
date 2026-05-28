package io.nekohasekai.libbox

object RuntimeCapableLibbox {
    @JvmStatic fun version(): String = "1.13.12-test"
}

object Libbox {
    @JvmStatic fun version(): String = "1.13.12-test"
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
