package com.zooot.vpn.vpn

import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object LibboxRuntimeSupport {
    const val UNSUPPORTED_CORE_MESSAGE = "Current libbox dependency does not expose VPN runtime API"
    const val COMMAND_SERVER_BACKEND = "sing-box libbox CommandServer"
    private const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
    private const val BOX_SERVICE_CLASS_NAME = "io.nekohasekai.libbox.BoxService"
    private const val COMMAND_SERVER_CLASS_NAME = "io.nekohasekai.libbox.CommandServer"
    private const val COMMAND_SERVER_HANDLER_CLASS_NAME = "io.nekohasekai.libbox.CommandServerHandler"
    private const val OVERRIDE_OPTIONS_CLASS_NAME = "io.nekohasekai.libbox.OverrideOptions"
    private const val TAG = "LibboxRuntimeSupport"

    fun inspect(
        classLoader: ClassLoader? = Thread.currentThread().contextClassLoader,
        libboxClassName: String = LIBBOX_CLASS_NAME,
        boxServiceClassName: String = BOX_SERVICE_CLASS_NAME,
        commandServerClassName: String = COMMAND_SERVER_CLASS_NAME,
        commandServerHandlerClassName: String = COMMAND_SERVER_HANDLER_CLASS_NAME,
        overrideOptionsClassName: String = OVERRIDE_OPTIONS_CLASS_NAME
    ): LibboxRuntimeInspection {
        val libboxClass = runCatching { Class.forName(libboxClassName, false, classLoader) }
            .getOrElse { return LibboxRuntimeInspection(false, null, emptyList(), false, false, null, "${it::class.java.simpleName}: ${sanitize(it.message)}") }
        val version = runCatching { libboxClass.getMethod("version").invoke(null)?.toString() }.getOrNull()
        val methods = publicMethods(libboxClass)
        val hasBoxService = runCatching { Class.forName(boxServiceClassName, false, classLoader) }.isSuccess
        val commandServerSupported = commandServerRuntimeSupported(
            classLoader,
            commandServerClassName,
            commandServerHandlerClassName,
            overrideOptionsClassName
        )
        val backend = if (commandServerSupported) COMMAND_SERVER_BACKEND else null
        val unsupportedReason = if (commandServerSupported) null else UNSUPPORTED_CORE_MESSAGE
        return LibboxRuntimeInspection(true, version, methods, hasBoxService, commandServerSupported, backend, unsupportedReason)
    }

    fun logInspection(inspection: LibboxRuntimeInspection, logger: (String) -> Unit = { Log.d(TAG, it) }) {
        logger("core version=${sanitize(inspection.version ?: "unavailable")}")
        logger("runtime backend=${inspection.backendName ?: "unsupported"}")
        if (!inspection.runtimeSupported) {
            logger("Libbox runtime API missing: ${inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE} box_service_present=${inspection.boxServicePresent}")
            inspection.publicMethods.forEach { logger("Libbox public method: $it") }
        }
    }

    fun start(
        config: String,
        platform: Any,
        classLoader: ClassLoader? = Thread.currentThread().contextClassLoader,
        logger: (String) -> Unit = { Log.d(TAG, it) },
        libboxClassName: String = LIBBOX_CLASS_NAME,
        commandServerClassName: String = COMMAND_SERVER_CLASS_NAME,
        commandServerHandlerClassName: String = COMMAND_SERVER_HANDLER_CLASS_NAME,
        overrideOptionsClassName: String = OVERRIDE_OPTIONS_CLASS_NAME
    ): SingBoxRuntimeHandle {
        val inspection = inspect(
            classLoader = classLoader,
            libboxClassName = libboxClassName,
            commandServerClassName = commandServerClassName,
            commandServerHandlerClassName = commandServerHandlerClassName,
            overrideOptionsClassName = overrideOptionsClassName
        )
        logInspection(inspection, logger)
        if (!inspection.runtimeSupported) throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
        return CommandServerRuntimeHandle(config, platform, classLoader, logger, commandServerClassName, commandServerHandlerClassName, overrideOptionsClassName)
    }

    fun sanitize(message: String?): String = message
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
        ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|vless://)[^\\s,;)]*"), "\$1=<redacted>")
        ?.replace(Regex("[A-Za-z0-9_-]{20,}"), "<redacted>")
        ?.take(160)
        ?: ""

    private fun commandServerRuntimeSupported(classLoader: ClassLoader?, commandServerClassName: String, handlerClassName: String, overrideOptionsClassName: String): Boolean = runCatching {
        val commandServer = Class.forName(commandServerClassName, false, classLoader)
        val handler = Class.forName(handlerClassName, false, classLoader)
        val platform = Class.forName("io.nekohasekai.libbox.PlatformInterface", false, classLoader)
        val overrideOptions = Class.forName(overrideOptionsClassName, false, classLoader)
        commandServer.getConstructor(handler, platform)
        commandServer.getMethod("start")
        commandServer.getMethod("startOrReloadService", String::class.java, overrideOptions)
        commandServer.getMethod("closeService")
        commandServer.getMethod("close")
        true
    }.getOrDefault(false)

    private fun publicMethods(libboxClass: Class<*>): List<LibboxMethodDiagnostic> =
        libboxClass.methods
            .filter { Modifier.isPublic(it.modifiers) }
            .sortedWith(compareBy<Method> { it.name }.thenBy { it.parameterTypes.joinToString(",") { type -> type.name } })
            .map { method ->
                LibboxMethodDiagnostic(
                    name = method.name,
                    parameterTypeNames = method.parameterTypes.map { it.name },
                    returnTypeName = method.returnType.name,
                    isStatic = Modifier.isStatic(method.modifiers)
                )
            }

    private class CommandServerRuntimeHandle(
        private val config: String,
        private val platform: Any,
        private val classLoader: ClassLoader?,
        private val logger: (String) -> Unit,
        private val commandServerClassName: String,
        private val commandServerHandlerClassName: String,
        private val overrideOptionsClassName: String
    ) : SingBoxRuntimeHandle {
        private var commandServer: Any? = null

        override fun start() {
            val handlerClass = Class.forName(commandServerHandlerClassName, true, classLoader)
            val commandServerClass = Class.forName(commandServerClassName, true, classLoader)
            val overrideOptionsClass = Class.forName(overrideOptionsClassName, true, classLoader)
            val handler = java.lang.reflect.Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { proxy, method, args ->
                when (method.name) {
                    "serviceStop", "serviceReload", "writeDebugMessage", "setSystemProxyEnabled" -> Unit
                    "getSystemProxyStatus" -> null
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "ZoootCommandServerHandler"
                    else -> defaultValue(method.returnType)
                }
            }
            val server = commandServerClass.getConstructor(handlerClass, platform.javaClass.interfaces.first { it.name == "io.nekohasekai.libbox.PlatformInterface" }).newInstance(handler, platform)
            commandServer = server
            server.javaClass.getMethod("start").invoke(server)
            val options = overrideOptionsClass.getDeclaredConstructor().newInstance()
            try {
                server.javaClass.getMethod("startOrReloadService", String::class.java, overrideOptionsClass).invoke(server, config, options)
            } catch (e: InvocationTargetException) {
                throw e.targetException ?: e
            }
            runCatching { server.javaClass.getMethod("needWIFIState").invoke(server) as? Boolean }
                .onSuccess { needed -> logger("core wifi_state_required=${needed == true}") }
        }

        override fun close() {
            val server = commandServer ?: return
            commandServer = null
            val closeServiceFailure = runCatching { server.javaClass.getMethod("closeService").invoke(server) }.exceptionOrNull()
            val closeFailure = runCatching { server.javaClass.getMethod("close").invoke(server) }.exceptionOrNull()
            val failure = closeServiceFailure ?: closeFailure
            if (failure != null) throw failure
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Void.TYPE -> Unit
            else -> null
        }
    }
}

internal interface SingBoxRuntimeHandle {
    fun start()
    fun close()
}

internal data class LibboxRuntimeInspection(
    val libboxPresent: Boolean,
    val version: String?,
    val publicMethods: List<LibboxMethodDiagnostic>,
    val boxServicePresent: Boolean,
    val runtimeSupported: Boolean,
    val backendName: String?,
    val unsupportedReason: String?
)

internal data class LibboxMethodDiagnostic(
    val name: String,
    val parameterTypeNames: List<String>,
    val returnTypeName: String,
    val isStatic: Boolean
) {
    override fun toString(): String = "${if (isStatic) "static " else ""}$name(${parameterTypeNames.joinToString(",")}) -> $returnTypeName"
}
