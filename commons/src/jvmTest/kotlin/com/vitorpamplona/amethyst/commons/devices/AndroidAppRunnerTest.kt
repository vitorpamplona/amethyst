/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.commons.devices

import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppRunner
import com.vitorpamplona.amethyst.commons.devices.android.AndroidAppTarget
import com.vitorpamplona.amethyst.commons.devices.android.AndroidBackend
import com.vitorpamplona.devicecore.CommandResult
import com.vitorpamplona.devicecore.CommandRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Run on device: Gradle builds, the APK for the device's ABI is installed, the app opens. */
class AndroidAppRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterTest
    fun stop() = scope.cancel()

    private val root: File = Files.createTempDirectory("app").toFile().also { it.deleteOnExit() }
    private val sdk: File =
        Files.createTempDirectory("sdk").toFile().also { dir -> FakeSdk.tool(dir, "platform-tools/adb") }

    private val target = AndroidAppTarget(root, ":app", File(root, "app"), listOf("playDebug"))

    private inner class Fake(
        val gradleFails: Boolean = false,
        val gradleHangs: Boolean = false,
    ) : CommandRunner {
        val commands = mutableListOf<String>()

        override suspend fun run(
            command: List<String>,
            timeoutMs: Long,
            environment: Map<String, String>,
            directory: File?,
            log: File?,
        ): CommandResult {
            val line = command.joinToString(" ")
            commands += line

            fun ok(text: String = "") = CommandResult(0, text.toByteArray(), "")
            return when {
                // gradlew.bat on Windows.
                command.first().substringAfterLast(File.separatorChar).startsWith("gradlew") -> {
                    assertEquals(root, directory)
                    assertEquals("emulator-5554", environment["ANDROID_SERIAL"])
                    if (gradleHangs) delay(60_000)
                    if (gradleFails) {
                        log?.writeText("> Task :app:compileKotlin\ne: Unresolved reference 'foo'\nFAILURE: Build failed with an exception.\n")
                        return CommandResult(1, ByteArray(0), "")
                    }
                    val out = File(root, "app/build/outputs/apk/play/debug").apply { mkdirs() }
                    listOf("arm64-v8a", "x86_64").forEach { File(out, "app-play-$it-debug.apk").writeText("apk") }
                    File(out, "app-play-universal-debug.apk").writeText("apk")
                    File(out, "output-metadata.json").writeText(
                        """
                        {"version":3,"applicationId":"com.example.app.debug","variantName":"playDebug","elements":[
                          {"type":"ONE_OF_MANY","filters":[{"filterType":"ABI","value":"arm64-v8a"}],"outputFile":"app-play-arm64-v8a-debug.apk"},
                          {"type":"ONE_OF_MANY","filters":[{"filterType":"ABI","value":"x86_64"}],"outputFile":"app-play-x86_64-debug.apk"},
                          {"type":"UNIVERSAL","filters":[],"outputFile":"app-play-universal-debug.apk"}
                        ]}
                        """.trimIndent(),
                    )
                    ok()
                }
                line.endsWith("devices -l") -> ok("List of devices attached\nemulator-5554 device model:sdk\n")
                line.endsWith("getprop sys.boot_completed") -> ok("1")
                line.endsWith("getprop ro.product.cpu.abilist") -> ok("x86_64,arm64-v8a\n")
                line.contains(" install ") -> ok("Success\n")
                else -> ok("Events injected: 1\n")
            }
        }

        override fun start(
            command: List<String>,
            environment: Map<String, String>,
            log: File?,
        ): Process = throw UnsupportedOperationException()
    }

    private fun runner(fake: Fake): AndroidAppRunner {
        File(root, "gradlew").apply {
            writeText("#!/bin/sh\n")
            setExecutable(true)
        }
        val android = AndroidBackend(sdkHome = { sdk }, scope = scope, runner = fake, discoveryDirs = { emptyList() }, avdHome = { File(sdk, "none") })
        return AndroidAppRunner(android, runner = fake)
    }

    @Test
    fun buildsInstallsTheDevicesAbiAndOpensTheApp() =
        runBlocking {
            val fake = Fake()
            val runner = runner(fake)

            val pkg = runner.run(target, "playDebug", "emulator-5554")

            assertEquals("com.example.app.debug", pkg)
            assertTrue(fake.commands.any { GRADLE_ASSEMBLE.containsMatchIn(it) }, fake.commands.toString())
            // The device's first ABI, x86_64, not the universal APK or arm64.
            assertTrue(fake.commands.any { it.contains("install -r -t") && it.endsWith("app-play-x86_64-debug.apk") }, fake.commands.toString())
            assertTrue(fake.commands.any { it.contains("monkey -p com.example.app.debug") }, fake.commands.toString())
            assertIs<AppRunState.Done>(runner.states.value[root.path])
            Unit
        }

    @Test
    fun aFailedBuildSaysWhyFromItsLog() =
        runBlocking {
            val runner = runner(Fake(gradleFails = true))
            val e = assertFailsWith<IOException> { runner.run(target, "playDebug", "emulator-5554") }
            assertTrue(e.message!!.contains("Unresolved reference 'foo'"), e.message)
            val state = assertIs<AppRunState.Failed>(runner.states.value[root.path])
            assertTrue(state.log!!.isFile)
        }

    @Test
    fun aCancelledRunCanBeRunAgain() =
        runBlocking {
            val runner = runner(Fake(gradleHangs = true))
            assertFailsWith<TimeoutCancellationException> { withTimeout(300) { runner.run(target, "playDebug", "emulator-5554") } }
            // Not stuck on "already being built".
            assertIs<AppRunState.Idle>(runner.states.value[root.path])
            Unit
        }

    companion object {
        private val GRADLE_ASSEMBLE = Regex("gradlew(\\.bat)? :app:assemblePlayDebug$")
    }
}
