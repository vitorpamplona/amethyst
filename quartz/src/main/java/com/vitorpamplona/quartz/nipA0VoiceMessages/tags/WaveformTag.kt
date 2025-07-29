/**
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
package com.vitorpamplona.quartz.nipA0VoiceMessages.tags

import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.utils.ensure

class WaveformTag(
    val wave: List<Int>,
) {
    fun toTagArray() = assemble(wave)

    companion object {
        const val TAG_NAME = "waveform"

        @JvmStatic
        fun parse(tag: Array<String>): WaveformTag? = parseWave(tag)?.let { WaveformTag(it) }

        @JvmStatic
        fun parseWave(tag: Array<String>): List<Int>? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0] == TAG_NAME) { return null }
            ensure(tag[1].isNotEmpty()) { return null }

            val wave = tag[1].split(" ").mapNotNull { it.toIntOrNull() }
            if (wave.isEmpty()) return null
            return wave
        }

        fun parseWave(wave: String): List<Int>? {
            val wave = wave.split(" ").mapNotNull { it.toIntOrNull() }
            if (wave.isEmpty()) return null
            return wave
        }

        @JvmStatic
        fun assembleWave(wave: List<Int>) = wave.joinToString(" ")

        @JvmStatic
        fun assemble(wave: List<Int>) = arrayOf(TAG_NAME, assembleWave(wave))
    }
}
