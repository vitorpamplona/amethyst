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
import kotlin.io.path.isRegularFile
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/** An AVD as its `<name>.ini` pointer file in the AVD home describes it. */
data class AvdInfo(
    val name: String,
    val iniFile: Path,
    /** The `<name>.avd` directory (`path=` in the ini), when given. */
    val avdDir: Path?,
)

/**
 * An Android SDK on disk, and where its tools and AVDs are.
 *
 * Found from `ANDROID_HOME`, then the deprecated `ANDROID_SDK_ROOT`, then the default install
 * place of Android Studio: `~/Android/Sdk` (Linux), `~/Library/Android/sdk` (macOS),
 * `%LOCALAPPDATA%\Android\Sdk` (Windows).
 */
class AndroidSdk(
    val root: Path,
    private val windows: Boolean = isWindows(System.getProperty("os.name").orEmpty()),
) {
    /** `<sdk>/emulator/emulator`, if installed. */
    val emulatorBinary: Path?
        get() = root.resolve("emulator").resolve(exe("emulator")).takeIf { it.isRegularFile() }

    /** `<sdk>/platform-tools/adb`, if installed. */
    val adbBinary: Path?
        get() = root.resolve("platform-tools").resolve(exe("adb")).takeIf { it.isRegularFile() }

    private fun exe(name: String) = if (windows) "$name.exe" else name

    override fun toString(): String = "AndroidSdk($root)"

    companion object {
        fun isWindows(osName: String) = osName.lowercase().startsWith("windows")

        private fun isMac(osName: String) = osName.lowercase().let { it.startsWith("mac") || it.contains("darwin") }

        /** Where to look, in order; the first that is a directory wins in [locate]. */
        fun candidates(
            osName: String = System.getProperty("os.name").orEmpty(),
            env: Map<String, String> = System.getenv(),
            userHome: String = System.getProperty("user.home").orEmpty(),
        ): List<Path> =
            buildList {
                env["ANDROID_HOME"]?.ifBlank { null }?.let { add(Paths.get(it)) }
                env["ANDROID_SDK_ROOT"]?.ifBlank { null }?.let { add(Paths.get(it)) }
                when {
                    isWindows(osName) -> {
                        val local = env["LOCALAPPDATA"]?.ifBlank { null } ?: Paths.get(userHome, "AppData", "Local").toString()
                        add(Paths.get(local, "Android", "Sdk"))
                    }
                    isMac(osName) -> add(Paths.get(userHome, "Library", "Android", "sdk"))
                    else -> add(Paths.get(userHome, "Android", "Sdk"))
                }
            }

        fun locate(
            osName: String = System.getProperty("os.name").orEmpty(),
            env: Map<String, String> = System.getenv(),
            userHome: String = System.getProperty("user.home").orEmpty(),
        ): AndroidSdk? =
            candidates(osName, env, userHome)
                .firstOrNull { it.isDirectory() }
                ?.let { AndroidSdk(it, isWindows(osName)) }

        /**
         * Where AVDs live: `ANDROID_AVD_HOME`, else `$ANDROID_USER_HOME/avd`, else the legacy
         * `$ANDROID_EMULATOR_HOME/avd`, else `~/.android/avd` (the SDK's documented order).
         */
        fun avdHome(
            env: Map<String, String> = System.getenv(),
            userHome: String = System.getProperty("user.home").orEmpty(),
        ): Path {
            env["ANDROID_AVD_HOME"]?.ifBlank { null }?.let { return Paths.get(it) }
            env["ANDROID_USER_HOME"]?.ifBlank { null }?.let { return Paths.get(it, "avd") }
            env["ANDROID_EMULATOR_HOME"]?.ifBlank { null }?.let { return Paths.get(it, "avd") }
            return Paths.get(userHome, ".android", "avd")
        }

        /** The AVDs whose `<name>.ini` sits in [avdHome], by name. Does not run the emulator. */
        fun listAvdFiles(avdHome: Path = avdHome()): List<AvdInfo> {
            if (!avdHome.isDirectory()) return emptyList()
            val files =
                try {
                    Files.newDirectoryStream(avdHome, "*.ini").use { it.toList() }
                } catch (e: IOException) {
                    return emptyList()
                }
            return files
                .filter { it.isRegularFile() }
                .map { ini ->
                    val path =
                        try {
                            EmulatorDiscovery.parseIni(ini.readText())["path"]
                        } catch (e: IOException) {
                            null
                        }
                    AvdInfo(ini.nameWithoutExtension, ini, path?.ifBlank { null }?.let { Paths.get(it) })
                }.sortedBy { it.name.lowercase() }
        }
    }
}
