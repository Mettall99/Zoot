package com.zooot.vpn.vpn

import android.util.Log
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object LibboxRuntimeSupport {
    const val UNSUPPORTED_CORE_MESSAGE = "Current bundled libbox does not expose Android VPN/TUN runtime API"
    const val COMMAND_SERVER_ONLY_MESSAGE = "Bundled libbox exposes CommandServer only, but no Android VPN/TUN runtime API is available"
    const val COMMAND_SERVER_BACKEND = "sing-box libbox CommandServer diagnostics"
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
            .getOrElse { return LibboxRuntimeInspection(false, null, emptyList(), false, false, false, null, "${it::class.java.simpleName}: ${sanitize(it.message)}") }
        val version = runCatching { libboxClass.noArgMethod("version")?.invokeStaticOrThrow()?.toString() }.getOrNull()
        val diagnostics = publicMethodDiagnostics(classLoader, libboxClass, boxServiceClassName, commandServerClassName)
        val boxServiceClass = runCatching { Class.forName(boxServiceClassName, false, classLoader) }.getOrNull()
        val commandServerClass = runCatching { Class.forName(commandServerClassName, false, classLoader) }.getOrNull()
        val commandDiagnosticSupported = commandServerDiagnosticSupported(
            classLoader,
            libboxClass,
            commandServerClass,
            commandServerHandlerClassName,
            overrideOptionsClassName
        )
        val directServiceSupported = boxServiceRuntimeSupported(classLoader, libboxClass, boxServiceClass)
        val unsupportedReason = when {
            directServiceSupported -> null
            commandDiagnosticSupported -> COMMAND_SERVER_ONLY_MESSAGE
            else -> UNSUPPORTED_CORE_MESSAGE
        }
        return LibboxRuntimeInspection(
            libboxPresent = true,
            version = version,
            publicMethods = diagnostics,
            boxServicePresent = boxServiceClass != null,
            commandServerPresent = commandServerClass != null,
            runtimeSupported = directServiceSupported,
            backendName = if (directServiceSupported) BOX_SERVICE_BACKEND else null,
            unsupportedReason = unsupportedReason
        )
    }

    fun logInspection(inspection: LibboxRuntimeInspection, logger: (String) -> Unit = { Log.d(TAG, it) }) {
        logger(
            "LibboxRuntimeSupport inspection result version=${sanitize(inspection.version ?: "unavailable")} " +
                "backend=${inspection.backendName ?: "unsupported"} runtime_supported=${inspection.runtimeSupported} " +
                "box_service_present=${inspection.boxServicePresent} command_server_present=${inspection.commandServerPresent}"
        )
        if (!inspection.runtimeSupported) {
            logger("Libbox runtime API missing: ${inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE} box_service_present=${inspection.boxServicePresent} command_server_present=${inspection.commandServerPresent}")
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
        if (!inspection.runtimeSupported) throw IllegalStateException(inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE)
        logger("runtime backend selected=${inspection.backendName}")
        return when (inspection.backendName) {
            BOX_SERVICE_BACKEND -> BoxServiceRuntimeHandle(config, platform, classLoader, logger, libboxClassName, boxServiceClassName)
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

    private fun boxServiceRuntimeSupported(classLoader: ClassLoader?, libboxClass: Class<*>, boxServiceClass: Class<*>?): Boolean = runCatching {
        val boxService = boxServiceClass ?: return@runCatching false
        val platform = Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
        check(platform.methods.any { it.name == "openTun" && it.parameterCount == 1 && it.returnType.isTunFdReturnType() })
        val factory = findNewServiceFactory(libboxClass, boxService, platform)
        val constructor = boxService.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(String::class.java, platform) }
        check(factory != null || constructor != null)
        boxService.getMethod("start")
        boxService.getMethod("close")
        true
    }.getOrDefault(false)

    private fun commandServerDiagnosticSupported(
        classLoader: ClassLoader?,
        libboxClass: Class<*>,
        commandServerClass: Class<*>?,
        handlerClassName: String,
        overrideOptionsClassName: String
    ): Boolean = runCatching {
        val commandServer = commandServerClass ?: return@runCatching false
        val handler = Class.forName(handlerClassName, false, classLoader)
        val platform = Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
        val overrideOptions = Class.forName(overrideOptionsClassName, false, classLoader)
        val factory = findNewCommandServerFactory(libboxClass, commandServer, handler, platform)
        val constructor = commandServer.constructors.firstOrNull { ctor -> ctor.parameterTypes.toList() == listOf(handler, platform) }
        check(factory != null || constructor != null)
        commandServer.getMethod("start")
        commandServer.getMethod("startOrReloadService", String::class.java, overrideOptions)
        true
    }.getOrDefault(false)

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
        commandServerClassName: String
    ): List<LibboxMethodDiagnostic> {
        val classes = listOfNotNull(
            libboxClass,
            runCatching { Class.forName(boxServiceClassName, false, classLoader) }.getOrNull(),
            runCatching { Class.forName(commandServerClassName, false, classLoader) }.getOrNull(),
            runCatching { Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader) }.getOrNull()
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
        private val boxServiceClassName: String
    ) : SingBoxRuntimeHandle {
        private var service: Any? = null

        override fun start() {
            val libboxClass = Class.forName(libboxClassName, true, classLoader)
            val boxServiceClass = Class.forName(boxServiceClassName, true, classLoader)
            val platformInterface = platformInterface()
            check(platformInterface.methods.any { it.name == "openTun" && it.parameterCount == 1 && it.returnType.isTunFdReturnType() }) {
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

        private fun platformInterface(): Class<*> = platform.javaClass.interfaces.firstOrNull { it.name == PLATFORM_INTERFACE_CLASS_NAME }
            ?: Class.forName(PLATFORM_INTERFACE_CLASS_NAME, false, classLoader)
    }

    private fun Class<*>.noArgMethod(name: String): Method? = methods.firstOrNull { it.name.equals(name, ignoreCase = true) && it.parameterCount == 0 }
    private fun Class<*>.isTunFdReturnType(): Boolean = this == Integer.TYPE || this == java.lang.Long.TYPE
    private fun Method.invokeStaticOrThrow(vararg args: Any?): Any? = invokeOrThrow(null, *args)
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
