package com.zooot.vpn.vpn

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object LibboxServiceReflection {
    private const val NEW_SERVICE = "newService"

    fun diagnostics(libboxClass: Class<*>): List<LibboxMethodDiagnostic> =
        libboxClass.methods
            .filter { it.name == NEW_SERVICE }
            .sortedWith(compareBy<Method> { it.name }.thenBy { it.parameterTypes.joinToString(",") { type -> type.name } })
            .map { method ->
                LibboxMethodDiagnostic(
                    name = method.name,
                    parameterTypeNames = method.parameterTypes.map { it.name },
                    returnTypeName = method.returnType.name
                )
            }

    fun selectNewService(libboxClass: Class<*>, platformInterface: Class<*>): Method {
        val candidates = libboxClass.methods.filter { it.name == NEW_SERVICE }
        return candidates.firstOrNull { method ->
            Modifier.isStatic(method.modifiers) &&
                method.parameterTypes.size == 2 &&
                method.parameterTypes[0] == String::class.java &&
                method.parameterTypes[1].isAssignableFrom(platformInterface)
        } ?: throw NoSuchMethodException("Libbox.newService signature not found. candidates=${candidates.size}")
    }

    fun invokeNewService(method: Method, config: String, platformProxy: Any): Any = try {
        method.invoke(null, config, platformProxy) ?: throw IllegalStateException("Libbox.newService returned null")
    } catch (e: InvocationTargetException) {
        val cause = e.cause
        if (cause != null) throw IllegalStateException("InvocationTargetException cause: ${sanitize(cause::class.java.simpleName)}: ${sanitize(cause.message)}", cause)
        throw IllegalStateException("InvocationTargetException cause: unknown", e)
    } catch (e: NoSuchMethodException) {
        throw IllegalStateException("NoSuchMethodException: ${sanitize(e.message)}", e)
    } catch (e: Throwable) {
        throw IllegalStateException("${e::class.java.simpleName}: ${sanitize(e.message)}", e)
    }

    fun sanitize(message: String?): String = message
        ?.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "<redacted>")
        ?.replace(Regex("(?i)(uuid|public_?key|short_?id|sid|token|private_?key|vless://)[^\\s,;)]*"), "\$1=<redacted>")
        ?.replace(Regex("[A-Za-z0-9_-]{20,}"), "<redacted>")
        ?.take(160)
        ?: ""
}

internal data class LibboxMethodDiagnostic(
    val name: String,
    val parameterTypeNames: List<String>,
    val returnTypeName: String
) {
    override fun toString(): String = "$name(${parameterTypeNames.joinToString(",")}) -> $returnTypeName"
}
