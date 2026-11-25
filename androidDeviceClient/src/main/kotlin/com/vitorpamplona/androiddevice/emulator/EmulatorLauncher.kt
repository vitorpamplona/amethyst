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
package com.vitorpamplona.androiddevice.emulator

import com.vitorpamplona.androiddevice.CommandRunner
import com.vitorpamplona.androiddevice.ProcessCommandRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

/** How to start an AVD. */
data class LaunchOptions(
    /** No window of its own: the app shows the screen. Off, the emulator's own window opens too. */
    val embedded: Boolean = true,
    /** Boot from scratch instead of the quick-boot snapshot. */
    val coldBoot: Boolean = false,
    /** Reset the device's data first. */
    val wipeData: Boolean = false,
    /**
     * A throwaway copy: boots from the AVD's saved state and drops every change when it exits. The
     * emulator runs several instances of one AVD only when all of them are read-only.
     */
    val readOnly: Boolean = false,
)

/** An emulator this app started: its process and how to reach it. */
class LaunchedEmulator(
    val process: Process,
    val info: RunningEmulator,
)

/**
 * Starts the SDK's `emulator` the way Android Studio's embedded emulator does: its window hidden
 * (`-qt-hide-window`), a gRPC port of its own with the console token for auth (`-grpc-use-token`),
 * so the app can stream the screen and send input, and waits for its discovery file to learn its
 * adb serial and token.
 */
class EmulatorLauncher(
    private val emulator: File,
    private val runner: CommandRunner = ProcessCommandRunner,
    private val discoveryDirs: () -> List<File> = { EmulatorDiscovery.directories() },
) {
    fun arguments(
        avd: String,
        grpcPort: Int,
        options: LaunchOptions,
    ): List<String> =
        buildList {
            add(emulator.absolutePath)
            add("-avd")
            add(avd)
            add("-grpc")
            add(grpcPort.toString())
            add("-grpc-use-token")
            if (options.embedded) add("-qt-hide-window")
            add("-no-boot-anim")
            if (options.coldBoot) add("-no-snapshot-load")
            if (options.wipeData) add("-wipe-data")
            if (options.readOnly) add("-read-only")
        }

    /**
     * Starts [avd] and returns once it has said where it listens (not once Android has booted).
     * Throws [IOException] when it exits first or doesn't answer within [timeoutMs]; [log] then
     * holds what it printed.
     */
    suspend fun launch(
        avd: String,
        options: LaunchOptions = LaunchOptions(),
        log: File? = null,
        timeoutMs: Long = 90_000,
    ): LaunchedEmulator {
        val port = withContext(Dispatchers.IO) { freePort() }
        log?.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Could not create $it") }
        val process = withContext(Dispatchers.IO) { runner.start(arguments(avd, port, options), log = log) }
        val started = System.currentTimeMillis()
        while (System.currentTimeMillis() - started < timeoutMs) {
            if (!process.isAlive) {
                throw IOException("The emulator exited (code ${process.exitValue()}) before it started${log?.let { ": " + tail(it) } ?: ""}")
            }
            val info = withContext(Dispatchers.IO) { EmulatorDiscovery.find(process.pid(), discoveryDirs()) }
            if (info != null && info.grpcPort != null) return LaunchedEmulator(process, info)
            delay(POLL_MS)
        }
        process.destroy()
        throw IOException("The emulator didn't start within ${timeoutMs / 1000}s${log?.let { ": " + tail(it) } ?: ""}")
    }

    companion object {
        private const val POLL_MS = 500L
        const val FIRST_GRPC_PORT = 8554

        /** The first port from [FIRST_GRPC_PORT] nothing listens on; another emulator may hold the first ones. */
        fun freePort(): Int {
            for (port in FIRST_GRPC_PORT until FIRST_GRPC_PORT + 200) {
                try {
                    ServerSocket(port, 1, InetAddress.getLoopbackAddress()).use { return port }
                } catch (_: IOException) {
                    // Taken: try the next.
                }
            }
            throw IOException("No free port for the emulator's gRPC endpoint")
        }

        /**
         * Why a program stopped, in its words: the last lines of [log] that say ERROR, FATAL or
         * PANIC (the emulator's banner comes first and says nothing), else its last lines.
         */
        fun tail(
            log: File,
            lines: Int = 6,
        ): String =
            try {
                val all = log.readLines().filter { it.isNotBlank() }
                val errors = all.filter { line -> ERROR_WORDS.any { line.contains(it) } }
                (errors.ifEmpty { all }).takeLast(lines).joinToString(" / ") { it.trim() }
            } catch (_: IOException) {
                ""
            }

        private val ERROR_WORDS = listOf("ERROR", "FATAL", "PANIC", "FAILURE:", "What went wrong", "e: ")
    }
}
