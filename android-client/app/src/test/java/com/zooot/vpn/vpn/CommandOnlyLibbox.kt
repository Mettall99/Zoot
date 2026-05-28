package com.zooot.vpn.vpn

class CommandOnlySetupOptions

class CommandClient

class CommandServer

object CommandOnlyLibbox {
    @JvmStatic fun setup(options: CommandOnlySetupOptions) = Unit
    @JvmStatic fun checkConfig(config: String) = Unit
    @JvmStatic fun newCommandClient(path: String, maxLines: Long, handler: Any?): CommandClient = CommandClient()
    @JvmStatic fun newCommandServer(handler: Any?, maxLines: Long): CommandServer = CommandServer()
    @JvmStatic fun newStandaloneCommandClient(): CommandClient = CommandClient()
    @JvmStatic fun version(): String = "2.1.0-test"
}
