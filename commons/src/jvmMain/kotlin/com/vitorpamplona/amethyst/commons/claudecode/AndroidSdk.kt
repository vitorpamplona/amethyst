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
package com.vitorpamplona.amethyst.commons.claudecode

import java.io.File

/**
 * Where the Android SDK is, for the builds the app starts: the build checks and agent sessions.
 * Started from the Dock or Finder, the app has no shell profile, so no `ANDROID_HOME`; and a
 * workstream's clones are fresh, without the gitignored `local.properties` Android Studio writes.
 * Gradle then stops at the first Android task ("SDK location not found").
 */
object AndroidSdk {
    /**
     * `ANDROID_HOME` for a child process when [parent] names no SDK and one is installed where
     * Android Studio puts it; empty otherwise. A `local.properties` `sdk.dir` still wins in Gradle.
     * A stale `ANDROID_SDK_ROOT` (naming no folder) is pointed at the same SDK: AGP 9.5 skips such
     * a variable, but an older AGP, in another repository a workstream clones, may stop on two
     * variables naming different paths.
     */
    fun environment(
        parent: Map<String, String> = System.getenv(),
        home: String = System.getProperty("user.home"),
        os: String = System.getProperty("os.name").orEmpty(),
    ): Map<String, String> {
        if (listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { parent[it]?.let { dir -> File(dir).isDirectory } == true }) return emptyMap()
        // As a File's path: the separators this system writes, whichever system's layout matched.
        val found = candidates(parent, home, os).map(::File).firstOrNull { it.isDirectory }?.path ?: return emptyMap()
        return if (parent["ANDROID_SDK_ROOT"] != null) mapOf("ANDROID_HOME" to found, "ANDROID_SDK_ROOT" to found) else mapOf("ANDROID_HOME" to found)
    }

    /**
     * The SDK folder: `ANDROID_HOME` or `ANDROID_SDK_ROOT` when either names one, else where
     * Android Studio installs it; null when there is none. Where `adb` and the emulator are.
     */
    fun home(
        parent: Map<String, String> = System.getenv(),
        home: String = System.getProperty("user.home"),
        os: String = System.getProperty("os.name").orEmpty(),
    ): File? =
        listOf("ANDROID_HOME", "ANDROID_SDK_ROOT").firstNotNullOfOrNull { name -> parent[name]?.let(::File)?.takeIf { it.isDirectory } }
            ?: candidates(parent, home, os).map(::File).firstOrNull { it.isDirectory }

    /** Android Studio's default SDK folder on each system. */
    internal fun candidates(
        parent: Map<String, String>,
        home: String,
        os: String,
    ): List<String> =
        when {
            os.startsWith("Mac", ignoreCase = true) -> listOf("$home/Library/Android/sdk")
            os.startsWith("Windows", ignoreCase = true) ->
                listOfNotNull(parent["LOCALAPPDATA"]?.let { "$it\\Android\\Sdk" }, "$home\\AppData\\Local\\Android\\Sdk")
            else -> listOf("$home/Android/Sdk", "$home/Android/sdk")
        }
}
