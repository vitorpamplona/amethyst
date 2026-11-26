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
 * An emulator running on this computer, as its discovery file (`pid_<pid>.ini`) describes it:
 * every emulator writes one, whoever started it (Android Studio, a terminal, this app).
 *
 * [grpcToken] is set when it was started with `-grpc-use-token`; one that uses JWTs instead
 * ([usesJwt], what Android Studio starts) answers clients whose key it finds in [jwksDir]
 * ([EmulatorJwtAuth]).
 */
data class RunningEmulator(
    val pid: Long,
    val consolePort: Int,
    val adbPort: Int?,
    /** The AVD's id, its `<id>.ini` file's name: what `-avd` takes. */
    val avdName: String?,
    /** The AVD's display name, when the file gives one apart from the id. */
    val avdDisplayName: String?,
    val avdDir: String?,
    val grpcPort: Int?,
    val grpcToken: String?,
    val usesJwt: Boolean,
    /** Where a JWT client writes its public key (`grpc.jwks`). */
    val jwksDir: String? = null,
    /** The file listing the keys the emulator accepted (`grpc.jwk_active`). */
    val activeJwks: String? = null,
    val discoveryFile: File,
) {
    /** How adb names it. */
    val serial: String get() = "emulator-$consolePort"

    /** Whether this app can open its gRPC endpoint: a port, with no auth, the token, or a key folder. */
    val grpcReachable: Boolean get() = grpcPort != null && (!usesJwt || jwksDir != null)
}

object EmulatorDiscovery {
    /**
     * The folders emulators write discovery files to, on [os]: Android Studio reads the same ones.
     * Linux tries each candidate, since the emulator falls back to another folder when
     * `XDG_RUNTIME_DIR` is unset (`~/.android/avd/running`, seen with emulator 37; older ones used
     * a temporary folder).
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
                else ->
                    listOfNotNull(
                        env["XDG_RUNTIME_DIR"]?.takeIf { it.isNotBlank() }?.let(::File),
                        File(home, ".android"),
                        File(tmp, "android-$user"),
                        File(tmp),
                    )
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
            .flatMap { sequenceOf(File(it, "pid_$pid.ini"), File(it, "pid_${pid}_info.ini")) }
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
            // `avd.name` is the display name ("Pixel 9 API 36") when the AVD has one; `avd.id` is
            // the id, which newer emulators write and older ones left out.
            avdName = ini["avd.id"]?.takeIf { it.isNotBlank() } ?: ini["avd.name"],
            avdDisplayName = ini["avd.name"]?.takeIf { it.isNotBlank() },
            avdDir = ini["avd.dir"],
            grpcPort = ini["grpc.port"]?.toIntOrNull(),
            grpcToken = ini["grpc.token"]?.takeIf { it.isNotBlank() },
            usesJwt = !ini["grpc.jwks"].isNullOrBlank() && ini["grpc.token"].isNullOrBlank(),
            jwksDir = ini["grpc.jwks"]?.takeIf { it.isNotBlank() },
            activeJwks = ini["grpc.jwk_active"]?.takeIf { it.isNotBlank() },
            discoveryFile = file,
        )
    }

    // `pid_<pid>.ini`, as emulator 37 names it; `_info` kept for anything that wrote the older name.
    private val FILE = Regex("pid_(\\d+)(?:_info)?\\.ini")
}
