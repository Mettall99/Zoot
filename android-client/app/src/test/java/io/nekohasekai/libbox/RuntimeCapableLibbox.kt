package io.nekohasekai.libbox

object RuntimeCapableLibbox {
    @JvmStatic fun version(): String = "1.13.12-test"
}

object CommandRuntimeState {
    var throwOnStartOrReload: RuntimeException? = null
    var closeServiceCalls = 0
    var closeCalls = 0
    var startOrReloadCalls = 0
    fun reset() {
        throwOnStartOrReload = null
        closeServiceCalls = 0
        closeCalls = 0
        startOrReloadCalls = 0
    }
}

object CommandServerOnlyLibbox {
    @JvmStatic fun version(): String = "1.13.12-command-runtime-test"
    @JvmStatic fun newCommandServer(handler: CommandServerHandler, platform: PlatformInterface): CommandServer = CommandServer(handler, platform)
}

object CommandServerWithoutReloadLibbox {
    @JvmStatic fun version(): String = "1.13.12-command-no-reload-test"
    @JvmStatic fun newCommandServer(handler: CommandServerHandler, platform: PlatformInterface): CommandServerWithoutReload = CommandServerWithoutReload(handler, platform)
}

object Libbox {
    @JvmStatic fun version(): String = "1.13.12-test"
    @JvmStatic fun newService(config: String, platform: PlatformInterface): BoxService = BoxService(config, platform)
    @JvmStatic fun newCommandServer(handler: CommandServerHandler, platform: PlatformInterface): CommandServer = CommandServer(handler, platform)
}

interface PlatformInterface {
    fun openTun(options: TunOptions): Int
}

class TunOptions
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
        platform.openTun(TunOptions())
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
        CommandRuntimeState.startOrReloadCalls++
        CommandRuntimeState.throwOnStartOrReload?.let { throw it }
        platform.openTun(TunOptions())
        serviceStarted = true
    }
    fun needWIFIState(): Boolean = false
    fun closeService() { CommandRuntimeState.closeServiceCalls++; serviceStarted = false }
    fun close() { CommandRuntimeState.closeCalls++; started = false }
}

class CommandServerWithoutReload(private val handler: CommandServerHandler, private val platform: PlatformInterface) {
    fun start() = Unit
    fun close() = Unit
}
