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

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * One running emulator, read from its discovery file `pid_<pid>.ini`.
 *
 * @property entries every key of the file, for the ones not modelled here.
 */
data class RunningEmulator(
    val pid: Long,
    val avdName: String?,
    val avdId: String?,
    val avdDir: String?,
    /** The console port (`port.serial`): adb calls the device `emulator-<serialPort>`. */
    val serialPort: Int?,
    val adbPort: Int?,
    val grpcPort: Int?,
    /** Present when the emulator was started with `-grpc-use-token`. */
    val grpcToken: String?,
    val commandLine: String?,
    val discoveryFile: Path,
    val entries: Map<String, String>,
) {
    /** The adb serial, e.g. `emulator-5554`. */
    val adbSerial: String? get() = serialPort?.let { "emulator-$it" }

    /**
     * The emulator authenticates gRPC with JWTs (Android Studio's way), which [EmulatorController]
     * does not do. Detected by a `grpc.jwks` key; that name is from memory, not checked.
     */
    val usesJwt: Boolean get() = entries.keys.any { it.startsWith("grpc.jwk") }

    val name: String get() = avdName ?: avdId ?: "emulator ($pid)"
}

/**
 * Finds running emulators through the discovery files each one writes while it runs: one
 * `pid_<pid>.ini` per emulator in an `avd/running` directory, holding `key=value` lines such as
 * `port.serial`, `port.adb`, `avd.name`, `avd.id`, `avd.dir`, `cmdline`, `grpc.port` and (with
 * `-grpc-use-token`) `grpc.token`.
 *
 * Where that directory is (from the emulator's source, not checked on every OS here):
 * - Linux: `$XDG_RUNTIME_DIR/avd/running`, else `/tmp/android-$USER/avd/running` (both scanned);
 * - macOS: `~/Library/Caches/TemporaryItems/avd/running`;
 * - Windows: `%LOCALAPPDATA%\Temp\avd\running`.
 *
 * `ANDROID_EMULATOR_HOME` / `ANDROID_AVD_HOME` are not consulted: whether they move this
 * directory could not be verified. Pass extra [directories] if a setup needs them. The parser is
 * tolerant: unknown keys are kept in [RunningEmulator.entries], missing ones are null, and files
 * it cannot read are skipped.
 *
 * Files whose process is gone (an emulator that crashed leaves its file behind) are skipped.
 */
class EmulatorDiscovery(
    val directories: List<Path> = defaultDirectories(),
    private val isAlive: (Long) -> Boolean = ::processAlive,
) {
    /** The emulators running now, ordered by pid. */
    fun running(): List<RunningEmulator> =
        directories
            .asSequence()
            .filter { it.isDirectory() }
            .flatMap { dir -> listIni(dir) }
            .mapNotNull { file -> read(file) }
            .distinctBy { it.pid }
            .filter { isAlive(it.pid) }
            .sortedBy { it.pid }
            .toList()

    private fun listIni(dir: Path): List<Path> =
        try {
            Files.newDirectoryStream(dir, "pid_*.ini").use { it.toList() }
        } catch (e: IOException) {
            emptyList()
        }

    private fun read(file: Path): RunningEmulator? {
        val pid = pidOf(file) ?: return null
        val text =
            try {
                file.readText()
            } catch (e: IOException) {
                return null
            }
        return parse(pid, parseIni(text), file)
    }

    companion object {
        private val PID_FILE = Regex("""pid_(\d+)\.ini""")

        fun pidOf(file: Path): Long? =
            PID_FILE
                .matchEntire(file.name)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull()

        /** `key=value` lines; the value is everything after the first `=`, so `cmdline` survives. */
        fun parseIni(text: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) return@forEach
                val eq = line.indexOf('=')
                if (eq <= 0) return@forEach
                out[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
            return out
        }

        fun parse(
            pid: Long,
            entries: Map<String, String>,
            file: Path,
        ): RunningEmulator =
            RunningEmulator(
                pid = pid,
                avdName = entries["avd.name"]?.ifEmpty { null },
                avdId = entries["avd.id"]?.ifEmpty { null },
                avdDir = entries["avd.dir"]?.ifEmpty { null },
                serialPort = entries["port.serial"]?.toIntOrNull(),
                adbPort = entries["port.adb"]?.toIntOrNull(),
                grpcPort = entries["grpc.port"]?.toIntOrNull(),
                grpcToken = entries["grpc.token"]?.ifEmpty { null },
                commandLine = entries["cmdline"]?.ifEmpty { null },
                discoveryFile = file,
                entries = entries,
            )

        fun processAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

        fun defaultDirectories(
            osName: String = System.getProperty("os.name").orEmpty(),
            env: Map<String, String> = System.getenv(),
            userHome: String = System.getProperty("user.home").orEmpty(),
            userName: String = System.getProperty("user.name").orEmpty(),
        ): List<Path> {
            val os = osName.lowercase()
            return when {
                os.startsWith("windows") -> {
                    val local = env["LOCALAPPDATA"]?.ifEmpty { null } ?: Paths.get(userHome, "AppData", "Local").toString()
                    listOf(Paths.get(local, "Temp", "avd", "running"))
                }
                os.startsWith("mac") || os.contains("darwin") ->
                    listOf(Paths.get(userHome, "Library", "Caches", "TemporaryItems", "avd", "running"))
                else -> {
                    val user = env["USER"]?.ifEmpty { null } ?: userName
                    listOfNotNull(
                        env["XDG_RUNTIME_DIR"]?.ifEmpty { null }?.let { Paths.get(it, "avd", "running") },
                        Paths.get("/tmp", "android-$user", "avd", "running"),
                    )
                }
            }
        }
    }
}
