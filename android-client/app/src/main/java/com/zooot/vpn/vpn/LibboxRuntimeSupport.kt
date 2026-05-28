package com.zooot.vpn.vpn

import android.util.Log
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object LibboxRuntimeSupport {
    const val UNSUPPORTED_CORE_MESSAGE = "Current libbox dependency does not expose VPN runtime API"
    private const val LIBBOX_CLASS_NAME = "io.nekohasekai.libbox.Libbox"
    private const val BOX_SERVICE_CLASS_NAME = "io.nekohasekai.libbox.BoxService"
    private const val TAG = "LibboxRuntimeSupport"

    fun inspect(classLoader: ClassLoader? = Thread.currentThread().contextClassLoader, libboxClassName: String = LIBBOX_CLASS_NAME, boxServiceClassName: String = BOX_SERVICE_CLASS_NAME): LibboxRuntimeInspection {
        val libboxClass = runCatching { Class.forName(libboxClassName, false, classLoader) }
            .getOrElse { return LibboxRuntimeInspection(false, null, emptyList(), false, false, "${it::class.java.simpleName}: ${sanitize(it.message)}") }
        val version = runCatching { libboxClass.getMethod("version").invoke(null)?.toString() }.getOrNull()
        val methods = publicMethods(libboxClass)
        val hasBoxService = runCatching { Class.forName(boxServiceClassName, false, classLoader) }.isSuccess
        return LibboxRuntimeInspection(true, version, methods, hasBoxService, false, UNSUPPORTED_CORE_MESSAGE)
    }

    fun logInspection(inspection: LibboxRuntimeInspection, logger: (String) -> Unit = { Log.d(TAG, it) }) {
        logger("Libbox.version=${sanitize(inspection.version ?: "unavailable")}")
        if (!inspection.runtimeSupported) {
            logger("Libbox runtime API missing: ${inspection.unsupportedReason ?: UNSUPPORTED_CORE_MESSAGE} box_service_present=${inspection.boxServicePresent}")
            inspection.publicMethods.forEach { logger("Libbox public method: $it") }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun start(config: String, platform: Any, classLoader: ClassLoader? = Thread.currentThread().contextClassLoader, logger: (String) -> Unit = { Log.d(TAG, it) }): Any {
        val inspection = inspect(classLoader)
        logInspection(inspection, logger)
        if (!inspection.runtimeSupported) throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
        throw IllegalStateException(UNSUPPORTED_CORE_MESSAGE)
    }

    fun sanitize(message: String?): String = message
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
        ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|vless://)[^\\s,;)]*"), "\$1=<redacted>")
        ?.replace(Regex("[A-Za-z0-9_-]{20,}"), "<redacted>")
        ?.take(160)
        ?: ""

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
}

internal data class LibboxRuntimeInspection(
    val libboxPresent: Boolean,
    val version: String?,
    val publicMethods: List<LibboxMethodDiagnostic>,
    val boxServicePresent: Boolean,
    val runtimeSupported: Boolean,
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
