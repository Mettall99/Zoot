package com.zooot.vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertFailsWith

class LibboxRuntimeSupportTest {
    @Test
    fun inspect_reportsUnsupported_whenOnlyCommandApiExists() {
        val inspection = LibboxRuntimeSupport.inspect(CommandOnlyLibbox::class.java.classLoader, CommandOnlyLibbox::class.java.name, commandServerClassName = "missing.CommandServer")

        assertTrue(inspection.libboxPresent)
        assertEquals("2.1.0-test", inspection.version)
        assertFalse(inspection.runtimeSupported)
        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, inspection.unsupportedReason)
        assertTrue(inspection.publicMethods.any { it.className == CommandOnlyLibbox::class.java.name && it.name == "checkConfig" })
    }

    @Test
    fun inspect_reportsCommandServerOnlyAsDiagnosticNotRuntime() {
        val inspection = LibboxRuntimeSupport.inspect(
            io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.classLoader,
            io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.name
        )

        assertTrue(inspection.libboxPresent)
        assertEquals("1.13.12-command-only-test", inspection.version)
        assertTrue(inspection.commandServerPresent)
        assertFalse(inspection.runtimeSupported)
        assertEquals(null, inspection.backendName)
        assertEquals(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE, inspection.unsupportedReason)
    }

    @Test
    fun start_throwsCommandServerOnlyUnsupportedCoreError_andDoesNotReportSuccess() {
        val platform = platformProxy()
        val error = assertFailsWith<IllegalStateException> {
            LibboxRuntimeSupport.start(
                "{}",
                platform,
                io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.classLoader,
                logger = {},
                libboxClassName = io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.name
            )
        }

        assertEquals(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE, error.message)
    }

    @Test
    fun inspect_prefersBoxServiceFactory_whenBundledRuntimeExposesDirectService() {
        val inspection = LibboxRuntimeSupport.inspect(io.nekohasekai.libbox.Libbox::class.java.classLoader)

        assertTrue(inspection.libboxPresent)
        assertTrue(inspection.runtimeSupported)
        assertEquals(LibboxRuntimeSupport.BOX_SERVICE_BACKEND, inspection.backendName)
        assertEquals(null, inspection.unsupportedReason)
    }

    @Test
    fun start_usesRealRuntimeAdapter_andReportsStartedOnlyAfterCoreStart() {
        val runtime = LibboxRuntimeSupport.start(
            "{\"inbounds\":[{\"tag\":\"tun-in\"}]}",
            platformProxy(),
            io.nekohasekai.libbox.Libbox::class.java.classLoader,
            logger = {}
        )

        runtime.start()
        runtime.close()
    }

    @Test
    fun diagnostics_includeSafePublicMethodShapeOnly() {
        val diagnostics = LibboxRuntimeSupport.inspect(CommandOnlyLibbox::class.java.classLoader, CommandOnlyLibbox::class.java.name).publicMethods.joinToString("\n")

        assertTrue(diagnostics.contains("com.zooot.vpn.vpn.CommandOnlyLibbox static checkConfig(java.lang.String) -> void"))
        assertFalse(diagnostics.contains("11111111-1111-1111-1111-111111111111"))
        assertFalse(diagnostics.contains("vless://"))
    }

    @Test
    fun diagnostics_includeCommandServerAndPlatformMethodShapes() {
        val diagnostics = LibboxRuntimeSupport.inspect(io.nekohasekai.libbox.Libbox::class.java.classLoader).publicMethods.joinToString("\n")

        assertTrue(diagnostics.contains("io.nekohasekai.libbox.Libbox static newService(java.lang.String,io.nekohasekai.libbox.PlatformInterface) -> io.nekohasekai.libbox.BoxService"))
        assertTrue(diagnostics.contains("io.nekohasekai.libbox.CommandServer startOrReloadService(java.lang.String,io.nekohasekai.libbox.OverrideOptions) -> void"))
        assertTrue(diagnostics.contains("io.nekohasekai.libbox.PlatformInterface openTun(io.nekohasekai.libbox.TunOptions) -> int"))
    }

    @Test
    fun successfulRuntimeStart_setsRunningTrueOnlyAfterStartSucceeds() {
        val runtime = RecordingRuntime()

        ZootVpnService.startRuntimeForTest { runtime }

        assertTrue(runtime.started)
        assertTrue(ZootVpnService.isRealityRunning())
        assertEquals(null, ZootVpnService.lastRealityError())
    }

    @Test
    fun runtimeStartFailure_setsLastRealityError_andDoesNotReportRunning() {
        ZootVpnService.startRuntimeForTest { RecordingRuntime(IllegalStateException("boom from runtime")) }

        assertFalse(ZootVpnService.isRealityRunning())
        assertEquals("IllegalStateException: boom from runtime", ZootVpnService.lastRealityError())
    }

    @Test
    fun start_throwsExplicitUnsupportedCoreError_whenRuntimeApiMissing() {
        val error = assertFailsWith<IllegalStateException> {
            LibboxRuntimeSupport.start("{}", Any(), CommandOnlyLibbox::class.java.classLoader, logger = {}, libboxClassName = CommandOnlyLibbox::class.java.name, commandServerClassName = "missing.CommandServer")
        }

        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, error.message)
    }

    @Test
    fun unsupportedRuntimeIsKnownImmediatelyWithoutCreatingTun() {
        var openTunCalls = 0
        val platform = platformProxy { openTunCalls++ }

        assertFailsWith<IllegalStateException> {
            LibboxRuntimeSupport.start(
                "{}",
                platform,
                io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.classLoader,
                logger = {},
                libboxClassName = io.nekohasekai.libbox.CommandServerOnlyLibbox::class.java.name
            )
        }

        assertEquals(0, openTunCalls)
    }

    @Test
    fun realityErrorMessage_reportsUnsupportedRuntime() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE))

        assertEquals(LibboxRuntimeSupport.UNSUPPORTED_CORE_MESSAGE, error)
    }

    @Test
    fun realityErrorMessage_reportsCommandServerOnlyUnsupportedRuntime() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE))

        assertEquals(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE, error)
    }

    @Test
    fun realityErrorMessage_reportsOpenTunFailure() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException("openTun failed: VpnService.Builder.establish() returned null"))

        assertEquals("openTun failed: VpnService.Builder.establish() returned null", error)
    }

    private fun platformProxy(onOpenTun: () -> Unit = {}): Any = java.lang.reflect.Proxy.newProxyInstance(
        io.nekohasekai.libbox.PlatformInterface::class.java.classLoader,
        arrayOf(io.nekohasekai.libbox.PlatformInterface::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "openTun" -> { onOpenTun(); 42 }
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "TestPlatform"
            else -> null
        }
    }
}

private class RecordingRuntime(private val failure: Throwable? = null) : SingBoxRuntimeHandle {
    var started = false
    override fun start() {
        failure?.let { throw it }
        started = true
    }
    override fun close() = Unit
}
