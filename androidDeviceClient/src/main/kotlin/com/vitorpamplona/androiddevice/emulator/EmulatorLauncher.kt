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
    /** Whether a display is there to hide a window on: Linux without X or Wayland has none. */
    private val hasDisplay: () -> Boolean = ::displayAvailable,
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
            // A hidden window still starts Qt, which aborts the emulator (code 134) where there is
            // no display to open: there it runs with no window at all.
            if (options.embedded) add(if (hasDisplay()) "-qt-hide-window" else "-no-window")
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
        // Held until the emulator has bound it: two starting at once would otherwise both find it free.
        val port = withContext(Dispatchers.IO) { reservePort() }
        try {
            log?.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Could not create $it") }
            val process = withContext(Dispatchers.IO) { runner.start(arguments(avd, port, options), log = log) }
            try {
                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < timeoutMs) {
                    if (!process.isAlive) {
                        throw IOException("The emulator exited (code ${process.exitValue()}) before it started${log?.let { ": " + tail(it) } ?: ""}")
                    }
                    val info = withContext(Dispatchers.IO) { EmulatorDiscovery.find(process.pid(), discoveryDirs()) }
                    if (info != null && info.grpcPort != null) return LaunchedEmulator(process, info)
                    delay(POLL_MS)
                }
                throw IOException("The emulator didn't start within ${timeoutMs / 1000}s${log?.let { ": " + tail(it) } ?: ""}")
            } catch (e: Throwable) {
                // Timed out, or the caller gave up (cancelled): nobody would track it, so it goes.
                process.destroy()
                throw e
            }
        } finally {
            releasePort(port)
        }
    }

    companion object {
        private const val POLL_MS = 500L
        const val FIRST_GRPC_PORT = 8554

        private val reserved = HashSet<Int>()

        /** [freePort], held for this launcher until [releasePort]: another start skips it meanwhile. */
        fun reservePort(): Int = synchronized(reserved) { freePort(reserved).also { reserved += it } }

        fun releasePort(port: Int) = synchronized(reserved) { reserved -= port }

        /** The first port from [FIRST_GRPC_PORT] nothing listens on and [skip] doesn't hold; another emulator may hold the first ones. */
        fun freePort(skip: Set<Int> = emptySet()): Int {
            for (port in FIRST_GRPC_PORT until FIRST_GRPC_PORT + 200) {
                if (port in skip) continue
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

/** Linux needs an X or Wayland display for the emulator's (hidden) window; macOS and Windows always have one. */
internal fun displayAvailable(
    os: String = System.getProperty("os.name").orEmpty(),
    env: Map<String, String> = System.getenv(),
): Boolean {
    if (os.startsWith("Mac", ignoreCase = true) || os.startsWith("Windows", ignoreCase = true)) return true
    return !env["DISPLAY"].isNullOrBlank() || !env["WAYLAND_DISPLAY"].isNullOrBlank()
}
