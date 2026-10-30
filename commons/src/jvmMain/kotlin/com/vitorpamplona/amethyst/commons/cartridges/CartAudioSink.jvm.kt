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
package com.vitorpamplona.amethyst.commons.cartridges

import com.vitorpamplona.quartz.utils.Log
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

actual fun openCartAudioSink(sampleRate: Int): CartAudioSink? =
    try {
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        // About 100 ms: enough to ride out a slow frame, short enough to stay in sync.
        line.open(format, sampleRate / 10 * 2)
        line.start()
        var bytes = ByteArray(0)
        object : CartAudioSink {
            override fun write(
                samples: FloatArray,
                count: Int,
            ) {
                if (bytes.size < count * 2) bytes = ByteArray(count * 2)
                for (i in 0 until count) {
                    val v = (samples[i] * 32767f).toInt().coerceIn(-32768, 32767)
                    bytes[i * 2] = v.toByte()
                    bytes[i * 2 + 1] = (v shr 8).toByte()
                }
                line.write(bytes, 0, count * 2)
            }

            override fun close() {
                line.stop()
                line.close()
            }
        }
    } catch (e: Exception) {
        // No sound card, or a headless machine: the cart plays silently.
        Log.w("CartAudioSink", "No audio output for the cart", e)
        null
    }
