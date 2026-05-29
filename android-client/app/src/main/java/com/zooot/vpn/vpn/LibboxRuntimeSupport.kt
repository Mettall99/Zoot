package com.zooot.vpn.vpn

import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal object LibboxRuntimeSupport {
    const val UNSUPPORTED_CORE_MESSAGE = "Current bundled libbox does not expose Android VPN/TUN runtime API"
    const val COMMAND_SERVER_ONLY_MESSAGE = "Bundled libbox exposes CommandServer only, but no Android VPN/TUN runtime API is available"
    const val COMMAND_SERVER_BACKEND = "sing-box libbox CommandServer service runtime"
    const val BOX_SERVICE_BACKEND = "sing-box libbox BoxService"
    private const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
    private const val BOX_SERVICE_CLASS_NAME = "io.nekohasekai.libbox.BoxService"
    private const val COMMAND_SERVER_CLASS_NAME = "io.nekohasekai.libbox.CommandServer"
    private const val COMMAND_SERVER_HANDLER_CLASS_NAME = "io.nekohasekai.libbox.CommandServerHandler"
    private const val OVERRIDE_OPTIONS_CLASS_NAME = "io.nekohasekai.libbox.OverrideOptions"
    private const val PLATFORM_INTERFACE_CLASS_NAME = "io.nekohasekai.libbox.PlatformInterface"
    private const val TUN_OPTIONS_CLASS_NAME = "io.nekohasekai.libbox.TunOptions"
    private const val TAG = "LibboxRuntimeSupport"

    fun inspect(
        classLoader: ClassLoader? = Thread.currentThread().contextClassLoader,
        libboxClassName: String = LIBBOX_CLASS_NAME,
        boxServiceClassName: String = BOX_SERVICE_CLASS_NAME,
        commandServerClassName: String = COMMAND_SERVER_CLASS_NAME,
        commandServerHandlerClassName: String = COMMAND_SERVER_HANDLER_CLASS_NAME,
        overrideOptionsClassName: String = OVERRIDE_OPTIONS_CLASS_NAME,
        platformInterfaceClassName: String = PLATFORM_INTERFACE_CLASS_NAME
    ): LibboxRuntimeInspection {
        val libboxClass = runCatching { Class.forName(libboxClassName, false, classLoader) }
            .getOrElse { return LibboxRuntimeInspection(false, null, emptyList(), false, false, false, false, false, false, null, "${it::class.java.simpleName}: ${sanitize(it.message)}") }
        val version = runCatching { libboxClass.noArgMethod("version")?.invokeStaticOrThrow()?.toString() }.getOrNull()
        val diagnostics = publicMethodDiagnostics(classLoader, libboxClass, boxServiceClassName, commandServerClassName, platformInterfaceClassName)
        val boxServiceClass = runCatching { Class.forName(boxServiceClassName, false, classLoader) }.getOrNull()
        val commandServerClass = runCatching { Class.forName(commandServerClassName, false, classLoader) }.getOrNull()
        val commandCapabilities = commandServerRuntimeCapabilities(
            classLoader,
            libboxClass,
            commandServerClass,
            commandServerHandlerClassName,
            overrideOptionsClassName,
            platformInterfaceClassName
        )
        val directServiceSupported = boxServiceRuntimeSupported(classLoader, libboxClass, boxServiceClass, platformInterfaceClassName)
        val commandRuntimeSupported = commandCapabilities.runtimeSupported
        val unsupportedReason = when {
            directServiceSupported || commandRuntimeSupported -> null
            commandServerClass != null -> COMMAND_SERVER_ONLY_MESSAGE
            else -> UNSUPPORTED_CORE_MESSAGE
        }
        return LibboxRuntimeInspection(
            libboxPresent = true,
            version = version,
            publicMethods = diagnostics,
            boxServicePresent = boxServiceClass != null,
            commandServerPresent = commandServerClass != null,
            runtimeSupported = directServiceSupported || commandRuntimeSupported,
            commandServerStartOrReloadPresent = commandCapabilities.startOrReloadPresent,
            platformOpenTunPresent = commandCapabilities.platformOpenTunPresent,
            commandServerClosePresent = commandCapabilities.closePresent,
            backendName = when {
                directServiceSupported -> BOX_SERVICE_BACKEND
                commandRuntimeSupported -> COMMAND_SERVER_BACKEND
                else -> null
            },
            unsupportedReason = unsupportedReason
        )
    }

    fun logInspection(inspection: LibboxRuntimeInspection, logger: (String) -> Unit = { Log.d(TAG, it) }) {
        logger(
            "LibboxRuntimeSupport inspection result version=${sanitize(inspection.version ?: "unavailable")} " +
                "backend=${inspection.backendName ?: "unsupported"} runtime_supported=${inspection.runtimeSupported} " +
                "box_service_present=${inspection.boxServicePresent} command_server_present=${inspection.commandServerPresent} " +
                "command_server_start_or_reload_present=${inspection.commandServerStartOrReloadPresent} " +
                "platform_open_tun_present=${inspection.platformOpenTunPresent}"
        )
        if (!inspection.runtimeSupported) {
            logger("Libbox runtime API missing: ${inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE} box_service_present=${inspection.boxServicePresent} " +
                "command_server_present=${inspection.commandServerPresent} command_server_start_or_reload_present=${inspection.commandServerStartOrReloadPresent} " +
                "platform_open_tun_present=${inspection.platformOpenTunPresent}")
        }
        inspection.publicMethods.forEach { logger("Libbox public method diagnostic: $it") }
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
        boxServiceClassName: String = BOX_SERVICE_CLASS_NAME,
        platformInterfaceClassName: String = PLATFORM_INTERFACE_CLASS_NAME
    ): SingBoxRuntimeHandle {
        val inspection = inspect(
            classLoader = classLoader,
            libboxClassName = libboxClassName,
            boxServiceClassName = boxServiceClassName,
            commandServerClassName = commandServerClassName,
            commandServerHandlerClassName = commandServerHandlerClassName,
            overrideOptionsClassName = overrideOptionsClassName,
            platformInterfaceClassName = platformInterfaceClassName
        )
        logInspection(inspection, logger)
        if (!inspection.runtimeSupported) throw IllegalStateException(inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE)
        logger("runtime backend selected=${inspection.backendName}")
        return when (inspection.backendName) {
            BOX_SERVICE_BACKEND -> BoxServiceRuntimeHandle(config, platform, classLoader, logger, libboxClassName, boxServiceClassName, platformInterfaceClassName)
            COMMAND_SERVER_BACKEND -> CommandServerRuntimeHandle(config, platform, classLoader, logger, libboxClassName, commandServerClassName, commandServerHandlerClassName, overrideOptionsClassName, platformInterfaceClassName)
            else -> throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
        }
    }

    fun sanitize(message: String?): String = message
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
        ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|host|server_name|server)[=:/][^\\s,;)]*"), "\$1=<redacted>")
        ?.replace(Regex("vless://[^\\s,;)]*", RegexOption.IGNORE_CASE), "vless://<redacted>")
        ?.replace(Regex("ss://[^\\s,;)]*", RegexOption.IGNORE_CASE), "ss://<redacted>")
        ?.replace(Regex("""(?i)"password"\s*:\s*"[^"]*"""), "\"password\":\"<redacted>\"")
        ?.replace(Regex("[A-Za-z0-9_-]{32,}"), "<redacted>")
        ?.take(160)
        ?: ""

    private fun boxServiceRuntimeSupported(classLoader: ClassLoader?, libboxClass: Class<*>, boxServiceClass: Class<*>?, platformInterfaceClassName: String): Boolean = runCatching {
        val boxService = boxServiceClass ?: return@runCatching false
        val platform = Class.forName(platformInterfaceClassName, false, classLoader)
        check(platform.methods.any { it.isOpenTunMethod() })
        val factory = findNewServiceFactory(libboxClass, boxService, platform)
        val constructor = boxService.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(String::class.java, platform) }
        check(factory != null || constructor != null)
        boxService.getMethod("start")
        boxService.getMethod("close")
        true
    }.getOrDefault(false)

    private fun commandServerRuntimeCapabilities(
        classLoader: ClassLoader?,
        libboxClass: Class<*>,
        commandServerClass: Class<*>?,
        handlerClassName: String,
        overrideOptionsClassName: String,
        platformInterfaceClassName: String
    ): CommandServerRuntimeCapabilities = runCatching {
        val commandServer = commandServerClass ?: return@runCatching CommandServerRuntimeCapabilities()
        val handler = Class.forName(handlerClassName, false, classLoader)
        val platform = Class.forName(platformInterfaceClassName, false, classLoader)
        val overrideOptions = Class.forName(overrideOptionsClassName, false, classLoader)
        val factory = findNewCommandServerFactory(libboxClass, commandServer, handler, platform)
        val constructor = commandServer.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(handler, platform) }
        val factoryPresent = factory != null || constructor != null
        val startPresent = runCatching { commandServer.getMethod("start") }.isSuccess
        val startOrReloadPresent = runCatching { commandServer.getMethod("startOrReloadService", String::class.java, overrideOptions) }.isSuccess
        val closeServicePresent = runCatching { commandServer.getMethod("closeService") }.isSuccess
        val closePresent = runCatching { commandServer.getMethod("close") }.isSuccess
        val openTunPresent = platform.methods.any { it.isOpenTunMethod() }
        CommandServerRuntimeCapabilities(
            runtimeSupported = factoryPresent && startPresent && startOrReloadPresent && (closeServicePresent || closePresent) && openTunPresent,
            startOrReloadPresent = startOrReloadPresent,
            platformOpenTunPresent = openTunPresent,
            closePresent = closeServicePresent || closePresent
        )
    }.getOrDefault(CommandServerRuntimeCapabilities())

    private fun findNewServiceFactory(libboxClass: Class<*>, boxService: Class<*>, platform: Class<*>): Method? =
        libboxClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name.equals("newService", ignoreCase = true) &&
                method.parameterTypes.toList() == listOf(String::class.java, platform) &&
                boxService.isAssignableFrom(method.returnType)
        }

    private fun findNewCommandServerFactory(libboxClass: Class<*>, commandServer: Class<*>, handler: Class<*>, platform: Class<*>): Method? =
        libboxClass.methods.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.name.equals("newCommandServer", ignoreCase = true) &&
                method.parameterTypes.toList() == listOf(handler, platform) &&
                commandServer.isAssignableFrom(method.returnType)
        }

    private fun publicMethodDiagnostics(
        classLoader: ClassLoader?,
        libboxClass: Class<*>,
        boxServiceClassName: String,
        commandServerClassName: String,
        platformInterfaceClassName: String
    ): List<LibboxMethodDiagnostic> {
        val classes = listOfNotNull(
            libboxClass,
            runCatching { Class.forName(boxServiceClassName, false, classLoader) }.getOrNull(),
            runCatching { Class.forName(commandServerClassName, false, classLoader) }.getOrNull(),
            runCatching { Class.forName(platformInterfaceClassName, false, classLoader) }.getOrNull()
        ).distinctBy { it.name }
        return classes.flatMap { clazz -> publicMethods(clazz) }
            .sortedWith(compareBy<LibboxMethodDiagnostic> { it.className }.thenBy { it.name }.thenBy { it.parameterTypeNames.joinToString(",") })
    }

    private fun publicMethods(clazz: Class<*>): List<LibboxMethodDiagnostic> =
        clazz.methods
            .filter { Modifier.isPublic(it.modifiers) && it.declaringClass != Any::class.java }
            .map { method ->
                LibboxMethodDiagnostic(
                    className = method.declaringClass.name,
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
        private val boxServiceClassName: String,
        private val platformInterfaceClassName: String
    ) : SingBoxRuntimeHandle {
        private var service: Any? = null

        override fun start() {
            val libboxClass = Class.forName(libboxClassName, true, classLoader)
            val boxServiceClass = Class.forName(boxServiceClassName, true, classLoader)
            val platformInterface = platformInterface()
            check(platformInterface.methods.any { it.isOpenTunMethod() }) {
                UNSUPPORTED_CORE_MESSAGE
            }
            val factory = findNewServiceFactory(libboxClass, boxServiceClass, platformInterface)
            val created = if (factory != null) {
                logger("BoxService/runtime object created via Libbox.${factory.name}")
                factory.invokeStaticOrThrow(config, platform) ?: error("Libbox.newService returned null")
            } else {
                logger("BoxService/runtime object created via constructor")
                boxServiceClass.getConstructor(String::class.java, platformInterface).newInstance(config, platform)
            }
            service = created
            logger("runtime start called backend=$BOX_SERVICE_BACKEND service_start")
            created.javaClass.getMethod("start").invokeOrThrow(created)
            runCatching { created.javaClass.getMethod("needWIFIState").invoke(created) as? Boolean }
                .onSuccess { needed -> logger("core wifi_state_required=${needed == true}") }
        }

        override fun close() {
            val created = service ?: return
            service = null
            created.javaClass.getMethod("close").invokeOrThrow(created)
        }

        private fun platformInterface(): Class<*> = platform.javaClass.interfaces.firstOrNull { it.name == platformInterfaceClassName }
            ?: Class.forName(platformInterfaceClassName, false, classLoader)
    }


    private class CommandServerRuntimeHandle(
        private val config: String,
        private val platform: Any,
        private val classLoader: ClassLoader?,
        private val logger: (String) -> Unit,
        private val libboxClassName: String,
        private val commandServerClassName: String,
        private val commandServerHandlerClassName: String,
        private val overrideOptionsClassName: String,
        private val platformInterfaceClassName: String
    ) : SingBoxRuntimeHandle {
        private var commandServer: Any? = null
        private val startThreadStopRequested = AtomicBoolean(false)
        private val startThreadFailure = AtomicReference<Throwable?>(null)
        private var startThread: Thread? = null

        override fun start() {
            val libboxClass = Class.forName(libboxClassName, true, classLoader)
            val commandServerClass = Class.forName(commandServerClassName, true, classLoader)
            val handlerInterface = Class.forName(commandServerHandlerClassName, true, classLoader)
            val overrideOptionsClass = Class.forName(overrideOptionsClassName, true, classLoader)
            val platformInterface = platformInterface()
            check(platformInterface.methods.any { it.isOpenTunMethod() }) {
                UNSUPPORTED_CORE_MESSAGE
            }
            val handler = commandServerHandler(handlerInterface)
            val factory = findNewCommandServerFactory(libboxClass, commandServerClass, handlerInterface, platformInterface)
            val created = if (factory != null) {
                logger("CommandServer/runtime object created via Libbox.${factory.name}")
                factory.invokeStaticOrThrow(handler, platform) ?: error("Libbox.newCommandServer returned null")
            } else {
                logger("CommandServer/runtime object created via constructor")
                commandServerClass.getConstructor(handlerInterface, platformInterface).newInstance(handler, platform)
            }
            commandServer = created
            val startMethod = created.javaClass.getMethod("start")
            logger("commandServer start thread launching")
            startThreadStopRequested.set(false)
            startThreadFailure.set(null)
            startThread = Thread({
                try {
                    logger("commandServer start entered")
                    startMethod.invokeOrThrow(created)
                    logger("commandServer start returned")
                } catch (t: Throwable) {
                    if (!startThreadStopRequested.get()) {
                        startThreadFailure.set(t)
                        logger("commandServer start exception class=${t::class.java.simpleName} message=${sanitize(t.message)}")
                    }
                }
            }, "zooot-libbox-command-server")
                .also { thread ->
                    thread.isDaemon = true
                    logger("commandServer start thread launched")
                    thread.start()
                }
            logger("commandServer readiness wait begin")
            Thread.sleep(COMMAND_SERVER_START_READY_WAIT_MS)
            logger("commandServer readiness wait end")
            startThreadFailure.get()?.let { throw it }

            logger("startOrReloadService preparing")
            val overrideOptions = overrideOptionsClass.getDeclaredConstructor().newInstance()
            logger("OverrideOptions object created")
            logger("startOrReloadService called")
            try {
                created.javaClass.getMethod("startOrReloadService", String::class.java, overrideOptionsClass)
                    .invokeOrThrow(created, config, overrideOptions)
                logger("startOrReloadService success")
            } catch (t: Throwable) {
                logger("startOrReloadService exception class=${t::class.java.simpleName} message=${sanitize(t.message)}")
                throw t
            }
        }

        override fun close() {
            startThreadStopRequested.set(true)
            val created = commandServer ?: return
            commandServer = null
            created.javaClass.methods.firstOrNull { it.name == "closeService" && it.parameterCount == 0 }?.let { method ->
                runCatching { method.invokeOrThrow(created) }
                    .onFailure { logger("closeService failure ${sanitize(it.message)}") }
            }
            created.javaClass.methods.firstOrNull { it.name == "close" && it.parameterCount == 0 }?.let { method ->
                runCatching { method.invokeOrThrow(created) }
                    .onFailure { logger("commandServer close failure ${sanitize(it.message)}") }
            }
        }

        private fun commandServerHandler(handlerInterface: Class<*>): Any = java.lang.reflect.Proxy.newProxyInstance(
            handlerInterface.classLoader,
            arrayOf(handlerInterface)
        ) { proxy, method, args ->
            when (method.name) {
                "serviceStop", "serviceReload" -> Unit
                "getSystemProxyStatus" -> null
                "setSystemProxyEnabled" -> Unit
                "writeDebugMessage" -> Unit
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "ZoootCommandServerHandlerProxy"
                else -> defaultValue(method.returnType)
            }
        }

        private fun platformInterface(): Class<*> = platform.javaClass.interfaces.firstOrNull { it.name == platformInterfaceClassName }
            ?: Class.forName(platformInterfaceClassName, false, classLoader)
    }

    private const val COMMAND_SERVER_START_READY_WAIT_MS = 200L

    private data class CommandServerRuntimeCapabilities(
        val runtimeSupported: Boolean = false,
        val startOrReloadPresent: Boolean = false,
        val platformOpenTunPresent: Boolean = false,
        val closePresent: Boolean = false
    )

    private fun Class<*>.noArgMethod(name: String): Method? = methods.firstOrNull { it.name.equals(name, ignoreCase = true) && it.parameterCount == 0 }
    private fun Class<*>.isTunFdReturnType(): Boolean = this == Integer.TYPE || this == java.lang.Long.TYPE
    private fun Method.isOpenTunMethod(): Boolean = name == "openTun" && parameterTypes.singleOrNull()?.name == TUN_OPTIONS_CLASS_NAME && returnType.isTunFdReturnType()
    private fun Method.invokeStaticOrThrow(vararg args: Any?): Any? = invokeOrThrow(null, *args)
    private fun defaultValue(type: Class<*>): Any? = when (type) { java.lang.Boolean.TYPE -> false; java.lang.Integer.TYPE -> 0; java.lang.Long.TYPE -> 0L; java.lang.Void.TYPE -> Unit; else -> null }
    private fun Method.invokeOrThrow(target: Any?, vararg args: Any?): Any? = try {
        invoke(target, *args)
    } catch (e: InvocationTargetException) {
        throw e.targetException ?: e
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
    val commandServerPresent: Boolean,
    val runtimeSupported: Boolean,
    val commandServerStartOrReloadPresent: Boolean,
    val platformOpenTunPresent: Boolean,
    val commandServerClosePresent: Boolean,
    val backendName: String?,
    val unsupportedReason: String?
)

internal data class LibboxMethodDiagnostic(
    val className: String,
    val name: String,
    val parameterTypeNames: List<String>,
    val returnTypeName: String,
    val isStatic: Boolean
) {
    override fun toString(): String = "$className ${if (isStatic) "static " else ""}$name(${parameterTypeNames.joinToString(",")}) -> $returnTypeName"
}
