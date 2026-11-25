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

import com.vitorpamplona.androiddevice.avd.Avds
import java.io.File

/**
 * An emulator running on this computer, as its discovery file (`pid_<pid>_info.ini`) describes it:
 * every emulator writes one, whoever started it (Android Studio, a terminal, this app).
 *
 * [grpcToken] is set when it was started with `-grpc-use-token`; one that uses JWTs instead
 * ([usesJwt], the default when no `-grpc` port is given, and what Android Studio starts) only
 * answers clients holding a key it trusts, so the app drives it through adb instead.
 */
data class RunningEmulator(
    val pid: Long,
    val consolePort: Int,
    val adbPort: Int?,
    val avdName: String?,
    val avdDir: String?,
    val grpcPort: Int?,
    val grpcToken: String?,
    val usesJwt: Boolean,
    val discoveryFile: File,
) {
    /** How adb names it. */
    val serial: String get() = "emulator-$consolePort"

    /** Whether this app can open its gRPC endpoint: a port, and either no auth or the token. */
    val grpcReachable: Boolean get() = grpcPort != null && !usesJwt
}

object EmulatorDiscovery {
    /**
     * The folders emulators write discovery files to, on [os]: Android Studio reads the same ones.
     * Linux tries each candidate, since the emulator falls back to a temporary folder when
     * `XDG_RUNTIME_DIR` is unset.
     */
    fun directories(
        os: String = System.getProperty("os.name").orEmpty(),
        env: Map<String, String> = System.getenv(),
        home: String = System.getProperty("user.home"),
        tmp: String = System.getProperty("java.io.tmpdir"),
        user: String = System.getProperty("user.name").orEmpty(),
    ): List<File> {
        val roots =
            when {
                os.startsWith("Mac", ignoreCase = true) -> listOf(File(home, "Library/Caches/TemporaryItems"))
                os.startsWith("Windows", ignoreCase = true) ->
                    listOfNotNull(env["LOCALAPPDATA"]?.let { File(it, "Temp") }, File(home, "AppData/Local/Temp"), File(tmp))
                else -> listOfNotNull(env["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() }?.let(::File), File(tmp, "android-$user"), File(tmp))
            }
        return roots.map { File(it, "avd/running") }.distinctBy { it.absolutePath }
    }

    /** The emulators running now: discovery files whose process is still alive. */
    fun running(
        dirs: List<File> = directories(),
        alive: (Long) -> Boolean = { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) },
    ): List<RunningEmulator> =
        dirs
            .flatMap { dir -> dir.listFiles { f -> f.isFile && FILE.matches(f.name) }?.toList().orEmpty() }
            .mapNotNull { parse(it) }
            .filter { alive(it.pid) }
            .distinctBy { it.pid }

    /** The discovery file of the emulator process [pid], once it has written one. */
    fun find(
        pid: Long,
        dirs: List<File> = directories(),
    ): RunningEmulator? =
        dirs
            .asSequence()
            .map { File(it, "pid_${pid}_info.ini") }
            .firstOrNull { it.isFile }
            ?.let(::parse)

    fun parse(file: File): RunningEmulator? {
        val pid =
            FILE
                .matchEntire(file.name)
                ?.groupValues
                ?.get(1)
                ?.toLongOrNull() ?: return null
        val ini = Avds.readIni(file)
        val console = ini["port.serial"]?.toIntOrNull() ?: return null
        return RunningEmulator(
            pid = pid,
            consolePort = console,
            adbPort = ini["port.adb"]?.toIntOrNull(),
            avdName = ini["avd.name"],
            avdDir = ini["avd.dir"],
            grpcPort = ini["grpc.port"]?.toIntOrNull(),
            grpcToken = ini["grpc.token"]?.takeIf { it.isNotBlank() },
            usesJwt = !ini["grpc.jwks"].isNullOrBlank() && ini["grpc.token"].isNullOrBlank(),
            discoveryFile = file,
        )
    }

    private val FILE = Regex("pid_(\\d+)_info\\.ini")
}
