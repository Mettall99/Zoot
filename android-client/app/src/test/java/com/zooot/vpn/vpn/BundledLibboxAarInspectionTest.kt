package com.zooot.vpn.vpn

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile

class BundledLibboxAarInspectionTest {
    @Test
    fun bundledSingBoxLibboxAar_exposesRealRuntimeOrExplicitCommandServerOnlyError() {
        val aar = bundledAar()
        assumeTrue("android-client/app/libs/sing-box-libbox.aar is not present in this checkout", aar.isFile)

        val classesJar = extractClassesJar(aar)
        URLClassLoader(arrayOf(classesJar.toURI().toURL()), null).use { loader ->
            val inspection = LibboxRuntimeSupport.inspect(loader)
            val diagnostics = inspection.publicMethods.joinToString("\n")

            println("Libbox inspection result: runtimeSupported=${inspection.runtimeSupported} backend=${inspection.backendName ?: "unsupported"}")
            println(diagnostics)

            assertTrue("AAR classes.jar must contain io.nekohasekai.libbox.Libbox", inspection.libboxPresent)
            assertTrue(
                "AAR must expose either a real BoxService/newService runtime or CommandServer diagnostics",
                inspection.runtimeSupported || inspection.commandServerPresent
            )

            if (inspection.runtimeSupported) {
                assertEquals(LibboxRuntimeSupport.BOX_SERVICE_BACKEND, inspection.backendName)
                assertTrue(diagnostics.contains("io.nekohasekai.libbox.Libbox static newService"))
                assertTrue(diagnostics.contains("io.nekohasekai.libbox.PlatformInterface openTun"))
                assertTrue(diagnostics.contains("io.nekohasekai.libbox.BoxService start()"))
                assertTrue(diagnostics.contains("io.nekohasekai.libbox.BoxService close()"))
            } else {
                assertFalse("CommandServer must not be treated as Android VPN/TUN runtime", inspection.runtimeSupported)
                assertEquals(LibboxRuntimeSupport.COMMAND_SERVER_ONLY_MESSAGE, inspection.unsupportedReason)
                assertTrue(diagnostics.contains("io.nekohasekai.libbox.CommandServer"))
            }
        }
    }

    private fun bundledAar(): File {
        val roots = sequenceOf(
            File(System.getProperty("user.dir")),
            File(System.getProperty("user.dir")).parentFile,
            File(System.getProperty("user.dir")).parentFile?.parentFile
        ).filterNotNull().distinctBy { it.absolutePath }
        return roots.map { File(it, "android-client/app/libs/sing-box-libbox.aar") }
            .firstOrNull { it.exists() }
            ?: File("android-client/app/libs/sing-box-libbox.aar")
    }

    private fun extractClassesJar(aar: File): File {
        val output = createTempFile(prefix = "sing-box-libbox-classes", suffix = ".jar")
        ZipFile(aar).use { zip ->
            val entry = zip.getEntry("classes.jar") ?: error("${aar.path} does not contain classes.jar")
            zip.getInputStream(entry).use { input -> output.outputStream().use { input.copyTo(it) } }
        }
        output.deleteOnExit()
        return output
    }
}
