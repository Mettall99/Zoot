package com.zooot.vpn.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibboxServiceReflectionTest {
    @Test
    fun selectNewService_usesCorrectAssignableDirection() {
        val method = LibboxServiceReflection.selectNewService(MockLibbox::class.java, MockPlatformInterface::class.java)

        assertEquals("newService", method.name)
        assertEquals(BasePlatform::class.java, method.parameterTypes[1])
    }

    @Test
    fun selectNewService_reportsCandidateCount_whenSignatureWrong() {
        val error = try {
            LibboxServiceReflection.selectNewService(WrongSignatureLibbox::class.java, MockPlatformInterface::class.java)
            ""
        } catch (e: NoSuchMethodException) {
            e.message.orEmpty()
        }

        assertEquals("Libbox.newService signature not found. candidates=1", error)
    }

    @Test
    fun diagnostics_includeSafeMethodShapeOnly() {
        val diagnostics = LibboxServiceReflection.diagnostics(WrongSignatureLibbox::class.java).single().toString()

        assertEquals("newService(java.lang.Integer,java.lang.String) -> java.lang.String", diagnostics)
        assertFalse(diagnostics.contains("11111111-1111-1111-1111-111111111111"))
        assertFalse(diagnostics.contains("vless://"))
    }


    @Test
    fun realityErrorMessage_reportsMissingLibboxClass() {
        val error = ZootVpnService.realityErrorMessage(ClassNotFoundException("io.nekohasekai.libbox.Libbox"))

        assertEquals("ClassNotFoundException: io.nekohasekai.libbox.Libbox", error)
    }

    @Test
    fun realityErrorMessage_reportsOpenTunFailure() {
        val error = ZootVpnService.realityErrorMessage(IllegalStateException("openTun failed: VpnService.Builder.establish returned null"))

        assertEquals("openTun failed: VpnService.Builder.establish returned null", error)
    }

    @Test
    fun invokeNewService_returnsMockedService() {
        val method = LibboxServiceReflection.selectNewService(MockLibbox::class.java, MockPlatformInterface::class.java)
        val service = LibboxServiceReflection.invokeNewService(method, "{}", object : MockPlatformInterface {})

        assertTrue(service is MockBoxService)
    }

    @Test
    fun invokeNewService_reportsInvocationTargetCause_sanitized() {
        val method = LibboxServiceReflection.selectNewService(ThrowingLibbox::class.java, MockPlatformInterface::class.java)
        val error = try {
            LibboxServiceReflection.invokeNewService(method, "{}", object : MockPlatformInterface {})
            ""
        } catch (e: IllegalStateException) {
            e.message.orEmpty()
        }

        assertTrue(error.startsWith("InvocationTargetException cause: IllegalArgumentException: uuid=<redacted>"))
        assertFalse(error.contains("11111111-1111-1111-1111-111111111111"))
    }

    interface BasePlatform
    interface MockPlatformInterface : BasePlatform

    class MockBoxService

    class MockLibbox {
        companion object {
            @JvmStatic
            fun newService(config: String, platform: BasePlatform): MockBoxService = MockBoxService()
        }
    }

    class WrongSignatureLibbox {
        companion object {
            @JvmStatic
            fun newService(count: Int, config: String): String = "unused"
        }
    }

    class ThrowingLibbox {
        companion object {
            @JvmStatic
            fun newService(config: String, platform: MockPlatformInterface): MockBoxService {
                throw IllegalArgumentException("uuid=11111111-1111-1111-1111-111111111111 failed")
            }
        }
    }
}
