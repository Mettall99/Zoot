package com.zooot.vpn.vpn

import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object LibboxRuntimeSupport {
    const val UNSUPPORTED_CORE_MESSAGE = "Current libbox dependency does not expose VPN runtime API"
    const val COMMAND_SERVER_BACKEND = "sing-box libbox CommandServer"
    const val BOX_SERVICE_BACKEND = "sing-box libbox BoxService"
    private const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
    private const val BOX_SERVICE_CLASS_NAME = "io.nekohasekai.libbox.BoxService"
    private const val COMMAND_SERVER_CLASS_NAME = "io.nekohasekai.libbox.CommandServer"
    private const val COMMAND_SERVER_HANDLER_CLASS_NAME = "io.nekohasekai.libbox.CommandServerHandler"
    private const val OVERRIDE_OPTIONS_CLASS_NAME = "io.nekohasekai.libbox.OverrideOptions"
    private const val PLATFORM_INTERFACE_CLASS_NAME = "io.nekohasekai.libbox.PlatformInterface"
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
        val version = runCatching { libboxClass.noArgMethod("version")?.invokeStaticOrThrow()?.toString() }.getOrNull()
        val methods = publicMethods(libboxClass)
        val hasBoxService = runCatching { Class.forName(boxServiceClassName, false, classLoader) }.isSuccess
        val directServiceSupported = boxServiceRuntimeSupported(classLoader, libboxClass, boxServiceClassName)
        val commandServerSupported = commandServerRuntimeSupported(
            classLoader,
            libboxClass,
            commandServerClassName,
            commandServerHandlerClassName,
            overrideOptionsClassName
        )
        val backend = when {
            directServiceSupported -> BOX_SERVICE_BACKEND
            commandServerSupported -> COMMAND_SERVER_BACKEND
            else -> null
        }
        val unsupportedReason = if (backend != null) null else UNSUPPORTED_CORE_MESSAGE
        return LibboxRuntimeInspection(true, version, methods, hasBoxService, backend != null, backend, unsupportedReason)
    }

    fun logInspection(inspection: LibboxRuntimeInspection, logger: (String) -> Unit = { Log.d(TAG, it) }) {
        logger("LibboxRuntimeSupport inspection result version=${sanitize(inspection.version ?: "unavailable")} backend=${inspection.backendName ?: "unsupported"} runtime_supported=${inspection.runtimeSupported} box_service_present=${inspection.boxServicePresent}")
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
        overrideOptionsClassName: String = OVERRIDE_OPTIONS_CLASS_NAME,
        boxServiceClassName: String = BOX_SERVICE_CLASS_NAME
    ): SingBoxRuntimeHandle {
        val inspection = inspect(
            classLoader = classLoader,
            libboxClassName = libboxClassName,
            boxServiceClassName = boxServiceClassName,
            commandServerClassName = commandServerClassName,
            commandServerHandlerClassName = commandServerHandlerClassName,
            overrideOptionsClassName = overrideOptionsClassName
        )
        logInspection(inspection, logger)
        if (!inspection.runtimeSupported) throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
        logger("runtime backend selected=${inspection.backendName}")
        return when (inspection.backendName) {
            BOX_SERVICE_BACKEND -> BoxServiceRuntimeHandle(config, platform, classLoader, logger, libboxClassName, boxServiceClassName)
            COMMAND_SERVER_BACKEND -> CommandServerRuntimeHandle(config, platform, classLoader, logger, libboxClassName, commandServerClassName, commandServerHandlerClassName, overrideOptionsClassName)
            else -> throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
        }
    }

    fun sanitize(message: String?): String = message
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
        ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|host|server_name|server)[=:/][^\\s,;)]*"), "\$1=<redacted>")
        ?.replace(Regex("vless://[^\\s,;)]*", RegexOption.IGNORE_CASE), "vless://<redacted>")
        ?.replace(Regex("[A-Za-z0-9_-]{20,}"), "<redacted>")
        ?.take(160)
        ?: ""

    private fun boxServiceRuntimeSupported(classLoader: ClassLoader?, libboxClass: Class<*>, boxServiceClassName: String): Boolean = runCatching {
        val boxService = Class.forName(boxServiceClassName, false, classLoader)
        val platform = Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
        val factory = libboxClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name.equals("newService", ignoreCase = true) &&
                method.parameterTypes.toList() == listOf(String::class.java, platform) &&
                boxService.isAssignableFrom(method.returnType)
        }
        val constructor = boxService.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(String::class.java, platform) }
        check(factory != null || constructor != null)
        boxService.getMethod("start")
        boxService.getMethod("close")
        true
    }.getOrDefault(false)

    private fun commandServerRuntimeSupported(classLoader: ClassLoader?, libboxClass: Class<*>, commandServerClassName: String, handlerClassName: String, overrideOptionsClassName: String): Boolean = runCatching {
        val commandServer = Class.forName(commandServerClassName, false, classLoader)
        val handler = Class.forName(handlerClassName, false, classLoader)
        val platform = Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
        val overrideOptions = Class.forName(overrideOptionsClassName, false, classLoader)
        val factory = findNewCommandServerFactory(libboxClass, commandServer, handler, platform)
        val constructor = commandServer.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(handler, platform) }
        check(factory != null || constructor != null)
        commandServer.getMethod("start")
        commandServer.getMethod("startOrReloadService", String::class.java, overrideOptions)
        commandServer.getMethod("closeService")
        commandServer.getMethod("close")
        true
    }.getOrDefault(false)

    private fun findNewCommandServerFactory(libboxClass: Class<*>, commandServer: Class<*>, handler: Class<*>, platform: Class<*>): Method? =
        libboxClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name.equals("newCommandServer", ignoreCase = true) &&
                method.parameterTypes.toList() == listOf(handler, platform) &&
                commandServer.isAssignableFrom(method.returnType)
        }

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

    private class BoxServiceRuntimeHandle(
        private val config: String,
        private val platform: Any,
        private val classLoader: ClassLoader?,
        private val logger: (String) -> Unit,
        private val libboxClassName: String,
        private val boxServiceClassName: String
    ) : SingBoxRuntimeHandle {
        private var service: Any? = null

        override fun start() {
            val libboxClass = Class.forName(libboxClassName, true, classLoader)
            val boxServiceClass = Class.forName(boxServiceClassName, true, classLoader)
            val platformInterface = platformInterface()
            val factory = libboxClass.methods.firstOrNull { method ->
                Modifier.isStatic(method.modifiers) && method.name.equals("newService", ignoreCase = true) && method.parameterTypes.toList() == listOf(String::class.java, platformInterface)
            }
            val created = if (factory != null) {
                logger("BoxService/runtime object created via Libbox.${factory.name}")
                factory.invokeStaticOrThrow(config, platform) ?: error("Libbox.newService returned null")
            } else {
                logger("BoxService/runtime object created via constructor")
                boxServiceClass.getConstructor(String::class.java, platformInterface).newInstance(config, platform)
            }
            service = created
            logger("runtime start called backend=$BOX_SERVICE_BACKEND")
            created.javaClass.getMethod("start").invokeOrThrow(created)
            runCatching { created.javaClass.getMethod("needWIFIState").invoke(created) as? Boolean }
                .onSuccess { needed -> logger("core wifi_state_required=${needed == true}") }
        }

        override fun close() {
            val current = service ?: return
            service = null
            current.javaClass.getMethod("close").invokeOrThrow(current)
        }

        private fun platformInterface(): Class<*> = platform.javaClass.interfaces.firstOrNull { it.name == PLATFORM_INTERFACE_CLASS_NAME }
            ?: Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
    }

    private class CommandServerRuntimeHandle(
        private val config: String,
        private val platform: Any,
        private val classLoader: ClassLoader?,
        private val logger: (String) -> Unit,
        private val libboxClassName: String,
        private val commandServerClassName: String,
        private val commandServerHandlerClassName: String,
        private val overrideOptionsClassName: String
    ) : SingBoxRuntimeHandle {
        private var commandServer: Any? = null

        override fun start() {
            val libboxClass = Class.forName(libboxClassName, true, classLoader)
            val handlerClass = Class.forName(commandServerHandlerClassName, true, classLoader)
            val commandServerClass = Class.forName(commandServerClassName, true, classLoader)
            val overrideOptionsClass = Class.forName(overrideOptionsClassName, true, classLoader)
            val platformInterface = platformInterface()
            val handler = java.lang.reflect.Proxy.newProxyInstance(handlerClass.classLoader, arrayOf(handlerClass)) { proxy, method, args ->
                when (method.name.replaceFirstChar { it.lowercase() }) {
                    "serviceStop", "serviceReload" -> null
                    "writeDebugMessage" -> args?.firstOrNull()?.toString()?.let { logger("core debug=${sanitize(it)}") }
                    "setSystemProxyEnabled" -> null
                    "getSystemProxyStatus" -> null
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "ZoootCommandServerHandler"
                    else -> defaultValue(method.returnType)
                }
            }
            val factory = findNewCommandServerFactory(libboxClass, commandServerClass, handlerClass, platformInterface)
            val server = if (factory != null) {
                logger("CommandServer/runtime object created via Libbox.${factory.name}")
                factory.invokeStaticOrThrow(handler, platform) ?: error("Libbox.newCommandServer returned null")
            } else {
                logger("CommandServer/runtime object created via constructor")
                commandServerClass.getConstructor(handlerClass, platformInterface).newInstance(handler, platform)
            }
            commandServer = server
            logger("runtime start called backend=$COMMAND_SERVER_BACKEND command_server_start")
            server.javaClass.getMethod("start").invokeOrThrow(server)
            val options = overrideOptionsClass.getDeclaredConstructor().newInstance()
            logger("runtime start called backend=$COMMAND_SERVER_BACKEND service_start_or_reload")
            server.javaClass.getMethod("startOrReloadService", String::class.java, overrideOptionsClass).invokeOrThrow(server, config, options)
            runCatching { server.javaClass.getMethod("needWIFIState").invoke(server) as? Boolean }
                .onSuccess { needed -> logger("core wifi_state_required=${needed == true}") }
        }

        override fun close() {
            val server = commandServer ?: return
            commandServer = null
            val closeServiceFailure = runCatching { server.javaClass.getMethod("closeService").invokeOrThrow(server) }.exceptionOrNull()
            val closeFailure = runCatching { server.javaClass.getMethod("close").invokeOrThrow(server) }.exceptionOrNull()
            val failure = closeServiceFailure ?: closeFailure
            if (failure != null) throw failure
        }

        private fun platformInterface(): Class<*> = platform.javaClass.interfaces.firstOrNull { it.name == PLATFORM_INTERFACE_CLASS_NAME }
            ?: Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
    }

    private fun Class<*>.noArgMethod(name: String): Method? = methods.firstOrNull { it.name.equals(name, ignoreCase = true) && it.parameterCount == 0 }
    private fun Method.invokeStaticOrThrow(vararg args: Any?): Any? = invokeOrThrow(null, *args)
    private fun Method.invokeOrThrow(target: Any?, vararg args: Any?): Any? = try {
        invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Void.TYPE -> Unit
        else -> null
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
