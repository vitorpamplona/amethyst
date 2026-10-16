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
package com.vitorpamplona.amethyst.desktop.spellcheck

import com.vitorpamplona.amethyst.commons.model.SpellCheckType
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellCheckService
import com.vitorpamplona.amethyst.commons.service.spellcheck.SpellChecker
import com.vitorpamplona.amethyst.commons.service.spellcheck.UserDictionary
import com.vitorpamplona.amethyst.desktop.platform.Platform
import com.vitorpamplona.amethyst.desktop.platform.PlatformInfo
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Picks the spell checker for the user's [setting] and hands it to [service]:
 *
 * - [SpellCheckType.AUTOMATIC]: the operating system's checker (macOS, Windows) when it loads
 *   and passes its self-test, the bundled Hunspell dictionary otherwise (always on Linux).
 * - [SpellCheckType.BUILT_IN]: the bundled dictionary, the same on every OS.
 * - [SpellCheckType.OFF]: nothing.
 *
 * The native checkers are reached through JNA, where a wrong call can crash the JVM instead of
 * throwing. So the first attempt writes a marker naming the app version and deletes it once the
 * attempt is over; a marker still there at the next launch means the attempt took the app down,
 * and that version never tries again.
 *
 * Nothing loads until [start], which runs when a composer first asks ([SpellCheckService.activate]):
 * loading the dictionary takes ~0.4 s and churns ~30 MB of short-lived garbage, though it keeps
 * only ~2 MB. Once loaded it stays, so switching back to it is instant.
 */
class DesktopSpellCheck(
    private val filesDir: File,
    private val appVersion: String,
    private val setting: StateFlow<SpellCheckType>,
    private val scope: CoroutineScope,
) {
    // One thread for every engine call: engines are not thread-safe, and COM ties the Windows
    // checker to the thread that made it.
    private val dispatcher =
        Executors
            .newSingleThreadExecutor { runnable -> Thread(runnable, "SpellCheck").apply { isDaemon = true } }
            .asCoroutineDispatcher()

    val service = SpellCheckService(dispatcher, FileUserDictionary(File(filesDir, USER_DICTIONARY_FILE)), onActivate = ::start)

    private val started = AtomicBoolean(false)

    private var builtIn: SpellChecker? = null
    private var builtInFailed = false
    private var native: SpellChecker? = null
    private var nativeTried = false

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            setting.collectLatest { type ->
                val engine =
                    try {
                        withContext(dispatcher) { engineFor(type) }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        // Keep listening: the next setting change gets another try.
                        Log.w(TAG, "Could not start spell checking ($type)", e)
                        null
                    }
                Log.i(TAG) { "Spell checking with ${engine?.name ?: "nothing"} ($type)" }
                service.setEngine(engine)
            }
        }
    }

    private fun engineFor(type: SpellCheckType): SpellChecker? {
        val engine =
            when (type) {
                SpellCheckType.OFF -> null
                SpellCheckType.BUILT_IN -> builtIn()
                SpellCheckType.AUTOMATIC -> native() ?: builtIn()
            }
        return engine
    }

    private fun builtIn(): SpellChecker? {
        if (builtIn == null && !builtInFailed) {
            try {
                builtIn = HunspellSpellChecker.loadBundled()
            } catch (e: Exception) {
                Log.w(TAG, "Could not load the bundled dictionary", e)
                builtInFailed = true
            }
        }
        return builtIn
    }

    private fun native(): SpellChecker? {
        if (nativeTried) return native
        nativeTried = true

        val host = PlatformInfo.host
        if (host != Platform.MACOS && host != Platform.WINDOWS) return null

        val marker = File(filesDir, NATIVE_PROBE_MARKER)
        try {
            if (marker.exists() && marker.readText().trim() == appVersion) {
                Log.w(TAG) { "The system spell checker crashed this version of the app before; using the built-in dictionary" }
                return null
            }
            marker.writeText(appVersion)
        } catch (e: IOException) {
            // Without the marker a crash would repeat every launch: don't risk the native call.
            Log.w(TAG, "Cannot keep the spell checker's crash marker; using the built-in dictionary", e)
            return null
        }
        native =
            try {
                when (host) {
                    Platform.MACOS -> MacSpellChecker.create()
                    else -> WindowsSpellChecker.create(Locale.getDefault().toLanguageTag())
                }
            } catch (e: Throwable) {
                // LinkageError from JNA included: a missing API is a reason to fall back, not to fail.
                Log.w(TAG, "The system spell checker is not usable", e)
                null
            }
        // The attempt ended without taking the process down, whatever its result.
        if (!marker.delete()) Log.w(TAG) { "Could not delete $marker" }
        return native
    }

    companion object {
        private const val TAG = "DesktopSpellCheck"
        const val USER_DICTIONARY_FILE = "spelling-dictionary.txt"
        const val NATIVE_PROBE_MARKER = "spellcheck-native-probe"
    }
}

/** The user's own words, one per line, in [file]. */
class FileUserDictionary(
    private val file: File,
) : UserDictionary {
    override fun load(): Set<String> =
        if (file.exists()) {
            file
                .readLines(Charsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        } else {
            emptySet()
        }

    override fun save(words: Set<String>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(words.sorted().joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
