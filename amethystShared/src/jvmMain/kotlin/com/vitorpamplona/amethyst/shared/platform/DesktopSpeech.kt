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
package com.vitorpamplona.amethyst.shared.platform

import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Speech synthesis on desktop, using whatever the OS already ships.
 *
 * Every desktop OS has a synthesiser, so text-to-speech is a real capability
 * here rather than a gap — it just lives behind a subprocess instead of a
 * system service:
 *
 * | OS | command |
 * |---|---|
 * | macOS | `say` |
 * | Windows | PowerShell `System.Speech.Synthesis.SpeechSynthesizer` |
 * | Linux | `spd-say`, falling back to `espeak` |
 *
 * Speaking is asynchronous and cancellable: an utterance runs in its own
 * process so [stop] can kill it, which is what a reader expects when they move
 * to another post mid-sentence.
 */
object DesktopSpeech : TextToSpeech.Synthesizer {
    private val osName = System.getProperty("os.name").orEmpty().lowercase()

    private val isMac = osName.contains("mac")
    private val isWindows = osName.contains("win")

    @Volatile
    private var speaking: Process? = null

    /** Installs this as the process-wide synthesizer. Safe to call more than once. */
    fun install() {
        TextToSpeech.setSynthesizer(this)
    }

    override fun speak(
        text: CharSequence,
        locale: Locale,
        utteranceId: String?,
    ): Boolean {
        stop()
        val command = commandFor(text.toString()) ?: return false
        return try {
            val process =
                ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
            speaking = process
            // Blocking here is intentional: the caller already treats speak() as
            // the duration of the utterance and reports onDone afterwards.
            val finished = process.waitFor()
            speaking = null
            finished == 0
        } catch (e: Exception) {
            speaking = null
            false
        }
    }

    override fun stop() {
        speaking?.destroy()
        speaking = null
    }

    /**
     * Enumerating installed voices costs a subprocess per query on every OS, so
     * this reports the default locale as supported and lets a real mismatch
     * surface as a failed utterance rather than paying that cost on startup.
     */
    override fun availableLocales(): Set<Locale> = setOf(Locale.getDefault())

    private fun commandFor(text: String): List<String>? =
        when {
            isMac -> listOf("say", text)
            isWindows ->
                listOf(
                    "powershell",
                    "-NoProfile",
                    "-Command",
                    "Add-Type -AssemblyName System.Speech; " +
                        "(New-Object System.Speech.Synthesis.SpeechSynthesizer).Speak([Console]::In.ReadToEnd())",
                )
            else -> firstAvailable(listOf(listOf("spd-say", "-w", text), listOf("espeak", text)))
        }

    private fun firstAvailable(candidates: List<List<String>>): List<String>? =
        candidates.firstOrNull { candidate ->
            runCatching {
                ProcessBuilder("which", candidate.first())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor() == 0
            }.getOrDefault(false)
        }
}
