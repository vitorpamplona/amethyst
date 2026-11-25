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
package com.vitorpamplona.emulator

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * How to start an AVD. The defaults start it with no window, gRPC on [grpcPort] behind a token,
 * and no boot animation.
 *
 * The flags are the ones documented for the emulator's command line (`emulator -help`); they
 * have not been run here, as this module is built where no emulator is installed. Check them
 * against `emulator -help` of the installed version before relying on one.
 */
data class EmulatorLaunchOptions(
    /** `-grpc <port>`. Must be free; see [EmulatorLauncher.freeLocalPort]. */
    val grpcPort: Int,
    /** `-no-window`: no emulator window; the screen is seen only through gRPC. */
    val headless: Boolean = true,
    /** `-grpc-use-token`: write a `grpc.token` to the discovery file and require it. */
    val useToken: Boolean = true,
    /** `-no-boot-anim`. */
    val noBootAnimation: Boolean = true,
    /** `-no-audio`. */
    val noAudio: Boolean = false,
    /** `-no-snapshot-save`: do not save state on exit. */
    val noSnapshotSave: Boolean = false,
    /** `-read-only`: lets a second instance of the same AVD run, discarding its changes. */
    val readOnly: Boolean = false,
    /** `-gpu <mode>`, e.g. `swiftshader_indirect` where there is no host GPU. */
    val gpu: String? = null,
    val extraArgs: List<String> = emptyList(),
    /** Where the emulator's stdout and stderr go; null discards them. */
    val logFile: File? = null,
)

/**
 * Runs the SDK's `emulator` program: lists AVDs and starts one. Starting returns at once with
 * the process; [awaitRunning] then waits for its discovery file to show a gRPC port.
 */
class EmulatorLauncher(
    val emulatorBinary: Path,
    /** The SDK root, exported to the emulator as `ANDROID_HOME`/`ANDROID_SDK_ROOT` if unset. */
    val sdkRoot: Path? = null,
) {
    constructor(sdk: AndroidSdk) : this(
        emulatorBinary = requireNotNull(sdk.emulatorBinary) { "no emulator in ${sdk.root}; install the SDK's Emulator package" },
        sdkRoot = sdk.root,
    )

    /**
     * AVD names from `emulator -list-avds`; when that fails or prints none, from the `*.ini`
     * files in the AVD home ([AndroidSdk.listAvdFiles]).
     */
    suspend fun listAvds(timeout: Duration = 30.seconds): List<String> {
        val fromEmulator =
            try {
                runListAvds(timeout)
            } catch (e: IOException) {
                null
            }
        if (!fromEmulator.isNullOrEmpty()) return fromEmulator
        return withContext(Dispatchers.IO) { AndroidSdk.listAvdFiles().map { it.name } }
    }

    private suspend fun runListAvds(timeout: Duration): List<String>? =
        withContext(Dispatchers.IO) {
            val process =
                newProcess(listOf(emulatorBinary.toString(), "-list-avds"))
                    .redirectErrorStream(true)
                    .start()
            try {
                withTimeoutOrNull(timeout) {
                    val output = runInterruptible { process.inputStream.bufferedReader().readText() }
                    val exited = runInterruptible { process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) }
                    if (exited && process.exitValue() == 0) parseListAvds(output) else null
                }
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }
        }

    /** Starts [avdName] as [options] say. The caller owns the process (and should end it). */
    fun start(
        avdName: String,
        options: EmulatorLaunchOptions,
    ): Process {
        val builder = newProcess(command(emulatorBinary.toString(), avdName, options)).redirectErrorStream(true)
        val log = options.logFile
        if (log == null) {
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        } else {
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log))
        }
        return builder.start()
    }

    private fun newProcess(command: List<String>): ProcessBuilder {
        val builder = ProcessBuilder(command)
        val root = sdkRoot
        if (root != null) {
            val env = builder.environment()
            if (env["ANDROID_HOME"].isNullOrBlank()) env["ANDROID_HOME"] = root.toString()
            if (env["ANDROID_SDK_ROOT"].isNullOrBlank()) env["ANDROID_SDK_ROOT"] = root.toString()
        }
        return builder
    }

    companion object {
        private val AVD_NAME = Regex("[A-Za-z0-9._-]+")

        /** The emulator command line for [options]. */
        fun command(
            emulator: String,
            avdName: String,
            options: EmulatorLaunchOptions,
        ): List<String> =
            buildList {
                add(emulator)
                add("-avd")
                add(avdName)
                if (options.headless) add("-no-window")
                add("-grpc")
                add(options.grpcPort.toString())
                if (options.useToken) add("-grpc-use-token")
                if (options.noBootAnimation) add("-no-boot-anim")
                if (options.noAudio) add("-no-audio")
                if (options.noSnapshotSave) add("-no-snapshot-save")
                if (options.readOnly) add("-read-only")
                options.gpu?.let {
                    add("-gpu")
                    add(it)
                }
                addAll(options.extraArgs)
            }

        /**
         * The names `emulator -list-avds` printed. Newer emulators may mix in log lines
         * (`INFO    | ...`); AVD names can only hold letters, digits, `.`, `_` and `-`, so
         * anything else is dropped.
         */
        fun parseListAvds(output: String): List<String> =
            output
                .lineSequence()
                .map { it.trim() }
                .filter { AVD_NAME.matches(it) }
                .toList()

        /** A loopback port nobody listens on right now (it can still be taken before use). */
        fun freeLocalPort(): Int = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }

        /**
         * Waits until an emulator from [process] shows up in [discovery] with a gRPC port.
         *
         * The discovery file names the pid of the emulator proper. Where `emulator` is a launcher
         * that runs it as a child rather than exec'ing it, that is a descendant of [process], so
         * an entry matches when its pid is [process] or one of its descendants; failing that,
         * when its AVD name is [avdName] (which can also pick an instance of that AVD started
         * elsewhere). Returns null on timeout, or when [process] has exited and nothing matched.
         */
        suspend fun awaitRunning(
            process: ProcessHandle,
            avdName: String?,
            discovery: EmulatorDiscovery = EmulatorDiscovery(),
            timeout: Duration = 5.minutes,
            pollInterval: Duration = 500.milliseconds,
        ): RunningEmulator? =
            withTimeoutOrNull(timeout) {
                var found: RunningEmulator? = null
                while (found == null) {
                    found = withContext(Dispatchers.IO) { match(process, avdName, discovery.running()) }
                    if (found == null) {
                        if (!process.isAlive) break
                        delay(pollInterval)
                    }
                }
                found
            }

        internal fun match(
            process: ProcessHandle,
            avdName: String?,
            running: List<RunningEmulator>,
        ): RunningEmulator? {
            val withGrpc = running.filter { it.grpcPort != null }
            val family = HashSet<Long>()
            family.add(process.pid())
            process.descendants().forEach { family.add(it.pid()) }
            return withGrpc.firstOrNull { it.pid in family }
                ?: avdName?.let { name -> withGrpc.firstOrNull { it.avdName == name || it.avdId == name } }
        }
    }
}
