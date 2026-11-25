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
package com.vitorpamplona.androiddevice.avd

import java.io.File
import java.io.IOException

/**
 * One virtual device the SDK's AVD manager (or Android Studio's Device Manager) created: [name] is
 * what `emulator -avd` takes; the rest is read from its `config.ini` for the picker.
 */
data class AvdInfo(
    val name: String,
    val displayName: String,
    val apiLevel: Int?,
    val abi: String?,
    val dir: File,
    val playStore: Boolean,
)

/** The AVDs in this user's AVD folder, read from their `.ini` files: no process to start. */
object Avds {
    /**
     * Where the emulator looks for AVDs: `ANDROID_AVD_HOME`, else `ANDROID_USER_HOME/avd`, else the
     * legacy `ANDROID_SDK_HOME/.android/avd`, else `~/.android/avd`.
     */
    fun home(
        env: Map<String, String> = System.getenv(),
        userHome: String = System.getProperty("user.home"),
    ): File =
        env["ANDROID_AVD_HOME"]?.takeIf { it.isNotBlank() }?.let(::File)
            ?: env["ANDROID_USER_HOME"]?.takeIf { it.isNotBlank() }?.let { File(it, "avd") }
            ?: env["ANDROID_SDK_HOME"]?.takeIf { it.isNotBlank() }?.let { File(it, ".android/avd") }
            ?: File(userHome, ".android/avd")

    fun list(home: File = home()): List<AvdInfo> {
        val inis = home.listFiles { f -> f.isFile && f.name.endsWith(".ini") } ?: return emptyList()
        return inis
            .mapNotNull { ini ->
                val name = ini.name.removeSuffix(".ini")
                val pointer = readIni(ini)
                // `path` is absolute; `path.rel` is relative to the AVD folder's parent (~/.android).
                val dir =
                    pointer["path"]?.let(::File)?.takeIf { it.isDirectory }
                        ?: pointer["path.rel"]?.let { File(home.parentFile, it) }?.takeIf { it.isDirectory }
                        ?: File(home, "$name.avd").takeIf { it.isDirectory }
                        ?: return@mapNotNull null
                val config = readIni(File(dir, "config.ini"))
                AvdInfo(
                    name = name,
                    displayName = config["avd.ini.displayname"]?.takeIf { it.isNotBlank() } ?: name.replace('_', ' '),
                    apiLevel = apiLevel(config, pointer),
                    abi = config["abi.type"],
                    dir = dir,
                    playStore = config["PlayStore.enabled"].equals("true", ignoreCase = true) || config["tag.id"] == "google_apis_playstore",
                )
            }.sortedBy { it.displayName.lowercase() }
    }

    /** `image.sysdir.1=system-images/android-35/google_apis/x86_64/`, or the pointer's `target=android-35`. */
    private fun apiLevel(
        config: Map<String, String>,
        pointer: Map<String, String>,
    ): Int? {
        val fromImage =
            config["image.sysdir.1"]?.let {
                Regex("android-(\\d+)")
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
        return fromImage ?: pointer["target"]?.let {
            Regex("android-(\\d+)")
                .find(it)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }
    }

    /** `key=value` lines, as the SDK writes them. A file that can't be read reads as empty. */
    fun readIni(file: File): Map<String, String> =
        try {
            if (!file.isFile) {
                emptyMap()
            } else {
                file
                    .readLines()
                    .mapNotNull { line ->
                        val eq = line.indexOf('=')
                        if (eq <= 0 || line.trimStart().startsWith("#")) null else line.substring(0, eq).trim() to line.substring(eq + 1).trim()
                    }.toMap()
            }
        } catch (_: IOException) {
            emptyMap()
        }
}
