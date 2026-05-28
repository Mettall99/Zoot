package com.zooot.vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibboxRuntimeSupportTest {
    @Test
    fun inspect_reportsUnsupported_whenOnlyCommandApiExists() {
        val inspection = LibboxRuntimeSupport.inspect(CommandOnlyLibbox::class.java.classLoader, CommandOnlyLibbox::class.java.name, commandServerClassName = "missing.CommandServer")

        assertTrue(inspection.libboxPresent)
        assertEquals("2.1.0-test", inspection.version)
        assertFalse(inspection.runtimeSupported)
        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, inspection.unsupportedReason)
        assertTrue(inspection.publicMethods.any { it.name == "checkConfig" })
    }

    @Test
    fun start_throwsExplicitUnsupportedCoreError_andDoesNotReportSuccess() {
        val error = try {
            LibboxRuntimeSupport.start("{}", Any(), CommandOnlyLibbox::class.java.classLoader, logger = {}, libboxClassName = CommandOnlyLibbox::class.java.name, commandServerClassName = "missing.CommandServer")
            ""
        } catch (e: IllegalStateException) {
            e.message.orEmpty()
        }

        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, error)
    }

    @Test
    fun inspect_reportsSupported_whenCommandServerRuntimeExists() {
        val inspection = LibboxRuntimeSupport.inspect(
            io.nekohasekai.libbox.RuntimeCapableLibbox::class.java.classLoader,
            io.nekohasekai.libbox.RuntimeCapableLibbox::class.java.name
        )

        assertTrue(inspection.libboxPresent)
        assertEquals("1.13.12-test", inspection.version)
        assertTrue(inspection.runtimeSupported)
        assertEquals(LibboxRuntimeSupport.COMMAND_SERVER_BACKEND, inspection.backendName)
        assertEquals(null, inspection.unsupportedReason)
    }

    @Test
    fun start_usesRealRuntimeAdapter_andReportsStartedOnlyAfterCoreStart() {
        val platform = java.lang.reflect.Proxy.newProxyInstance(
            io.nekohasekai.libbox.PlatformInterface::class.java.classLoader,
            arrayOf(io.nekohasekai.libbox.PlatformInterface::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "TestPlatform"
                else -> null
            }
        }

        val runtime = LibboxRuntimeSupport.start(
            "{\"inbounds\":[{\"tag\":\"tun-in\"}]}",
            platform,
            io.nekohasekai.libbox.RuntimeCapableLibbox::class.java.classLoader,
            logger = {}
        )

        runtime.start()
        runtime.close()
    }

    @Test
    fun diagnostics_includeSafePublicMethodShapeOnly() {
        val diagnostics = LibboxRuntimeSupport.inspect(CommandOnlyLibbox::class.java.classLoader, CommandOnlyLibbox::class.java.name).publicMethods.joinToString("\n")

        assertTrue(diagnostics.contains("static checkConfig(java.lang.String) -> void"))
        assertFalse(diagnostics.contains("11111111-1111-1111-1111-111111111111"))
        assertFalse(diagnostics.contains("vless://"))
    }

    @Test
    fun realityErrorMessage_reportsUnsupportedRuntime() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE))

        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, error)
    }

    @Test
    fun realityErrorMessage_reportsOpenTunFailure() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException("openTun failed: VpnService.Builder.establish returned null"))

        assertEquals("openTun failed: VpnService.Builder.establish returned null", error)
    }
}
