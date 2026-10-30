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
package com.vitorpamplona.fantasyconsole.runtime

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Four-channel synth for the sound effects and music stored in [Ram]: 64 effects of 32 notes,
 * 64 music patterns. Each note has a pitch (0-63, 33 is A at 440 Hz), one of 8 waveforms, a
 * volume (0-7) and an effect (slide, vibrato, drop, fade in/out, fast/slow arpeggio). One note
 * lasts `speed * 183` samples at 22050 Hz.
 *
 * The waveforms are approximations by ear, not a match for the original synth.
 */
class Audio(
    private val ram: Ram,
) {
    private class Channel {
        var sfx = -1
        var note = 0
        var endNote = 32
        var sampleInNote = 0
        var elapsed = 0L
        var phase = 0.0
        var phase2 = 0.0
        var prevPitch = -1.0
        var noise = 0.0
        var noiseTarget = 0.0
        var released = false
        var fromMusic = false
    }

    private val channels = Array(4) { Channel() }
    private val random = Random(0x5eed)

    private var pattern = -1
    private var patternSamplesLeft = 0
    private var patternSamplesPlayed = 0
    private var musicMask = 0x0f

    // ---------------------------------------------------------------- control

    /** `sfx(n, channel, offset, length)`. -1 stops, -2 releases a loop; channel -1 picks a free one. */
    fun sfx(
        n: Int,
        channel: Int,
        offset: Int,
        length: Int,
    ) {
        if (n == -1) {
            if (channel in 0..3) stop(channels[channel]) else channels.forEach { if (!it.fromMusic) stop(it) }
            return
        }
        if (n == -2) {
            if (channel in 0..3) channels[channel].released = true else channels.forEach { it.released = true }
            return
        }
        if (n !in 0..63) return
        val ch =
            if (channel in 0..3) {
                channel
            } else {
                channels.indexOfFirst { it.sfx == n && !it.fromMusic }.takeIf { it >= 0 }
                    ?: channels.indexOfFirst { it.sfx < 0 }.takeIf { it >= 0 }
                    ?: (0..3).firstOrNull { (musicMask shr it) and 1 == 0 || pattern < 0 }
                    ?: 0
            }
        start(channels[ch], n, offset.coerceIn(0, 31), if (length > 0) offset + length else 32, fromMusic = false)
    }

    /** `music(n, fade, mask)`: plays from pattern [n]; a negative [n] stops the music. */
    fun music(
        n: Int,
        mask: Int,
    ) {
        channels.forEach { if (it.fromMusic) stop(it) }
        if (n !in 0..63) {
            pattern = -1
            return
        }
        musicMask = if (mask == 0) 0x0f else mask and 0x0f
        startPattern(n)
    }

    private fun start(
        c: Channel,
        n: Int,
        offset: Int,
        end: Int,
        fromMusic: Boolean,
    ) {
        c.sfx = n
        c.note = offset
        c.endNote = minOf(end, 32)
        c.sampleInNote = 0
        c.prevPitch = -1.0
        c.released = false
        c.fromMusic = fromMusic
    }

    private fun stop(c: Channel) {
        c.sfx = -1
        c.fromMusic = false
    }

    private fun startPattern(n: Int) {
        val base = Addr.MUSIC + n * 4
        var anyEnabled = false
        var lengthNotes = -1
        var lengthSpeed = 1
        var fallbackSpeed = -1
        for (i in 0 until 4) {
            val b = ram[base + i]
            if (b and 0x40 != 0 || (musicMask shr i) and 1 == 0) continue
            val sfx = b and 0x3f
            anyEnabled = true
            val sfxBase = Addr.SFX + sfx * 68
            val speed = maxOf(1, ram[sfxBase + 65])
            val loopStart = ram[sfxBase + 66]
            val loopEnd = ram[sfxBase + 67]
            if (fallbackSpeed < 0) fallbackSpeed = speed
            // The leftmost channel that doesn't loop decides how long the pattern lasts.
            if (lengthNotes < 0 && !(loopEnd > loopStart)) {
                lengthNotes = if (loopEnd == 0 && loopStart > 0) loopStart else 32
                lengthSpeed = speed
            }
            val ch = channels[i]
            if (ch.sfx < 0 || ch.fromMusic) start(ch, sfx, 0, 32, fromMusic = true)
        }
        if (!anyEnabled) {
            pattern = -1
            return
        }
        if (lengthNotes < 0) {
            lengthNotes = 32
            lengthSpeed = fallbackSpeed
        }
        pattern = n
        patternSamplesLeft = lengthNotes * lengthSpeed * TICK
        patternSamplesPlayed = 0
    }

    private fun nextPattern() {
        val flags =
            ram[Addr.MUSIC + pattern * 4] shr 7 or
                ((ram[Addr.MUSIC + pattern * 4 + 1] shr 7) shl 1) or
                ((ram[Addr.MUSIC + pattern * 4 + 2] shr 7) shl 2)
        channels.forEach { if (it.fromMusic) stop(it) }
        when {
            flags and 4 != 0 -> pattern = -1
            flags and 2 != 0 -> {
                var back = pattern
                while (back > 0 && ram[Addr.MUSIC + back * 4] shr 7 == 0) back--
                startPattern(back)
            }
            pattern + 1 > 63 -> pattern = -1
            else -> startPattern(pattern + 1)
        }
    }

    // ---------------------------------------------------------------- state for stat()

    fun channelSfx(ch: Int): Int = channels.getOrNull(ch)?.sfx ?: -1

    fun channelNote(ch: Int): Int = channels.getOrNull(ch)?.let { if (it.sfx < 0) -1 else it.note } ?: -1

    fun currentPattern(): Int = pattern

    fun patternTicks(): Int = patternSamplesPlayed / TICK

    // ---------------------------------------------------------------- synthesis

    /** Fills [out] from [offset] with [count] mono samples in [-1, 1]. */
    fun render(
        out: FloatArray,
        offset: Int,
        count: Int,
    ) {
        for (s in 0 until count) {
            var mix = 0.0
            for (c in channels) if (c.sfx >= 0) mix += sample(c)
            if (pattern >= 0) {
                patternSamplesPlayed++
                if (--patternSamplesLeft <= 0) nextPattern()
            }
            out[offset + s] = (mix * 0.35).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private fun sample(c: Channel): Double {
        val base = Addr.SFX + c.sfx * 68
        val speed = maxOf(1, ram[base + 65])
        val noteLength = speed * TICK
        val word = ram[base + c.note * 2] or (ram[base + c.note * 2 + 1] shl 8)
        var pitch = (word and 0x3f).toDouble()
        var wave = (word shr 6) and 7
        val volume = (word shr 9) and 7
        val effect = (word shr 12) and 7
        if (word and 0x8000 != 0) {
            // A custom instrument: borrow the waveform of that effect's first note.
            val inst = Addr.SFX + wave * 68
            wave = ((ram[inst] or (ram[inst + 1] shl 8)) shr 6) and 7
        }
        val progress = c.sampleInNote.toDouble() / noteLength
        var amp = volume / 7.0
        when (effect) {
            1 -> if (c.prevPitch >= 0) pitch = c.prevPitch + (pitch - c.prevPitch) * progress
            2 -> pitch += 0.25 * sin(2 * PI * 7.5 * c.elapsed / SAMPLE_RATE)
            3 -> {}
            4 -> amp *= progress
            5 -> amp *= 1 - progress
            6, 7 -> {
                val step = if (effect == 6) (if (speed <= 8) 2 else 4) else (if (speed <= 8) 4 else 8)
                val group = c.note and 3.inv()
                val index = ((c.elapsed / (step * TICK)) % 4).toInt()
                val w = ram[base + (group + index) * 2] or (ram[base + (group + index) * 2 + 1] shl 8)
                pitch = (w and 0x3f).toDouble()
            }
        }
        var freq = 440.0 * 2.0.pow((pitch - 33) / 12)
        if (effect == 3) freq *= 1 - progress

        val value = if (amp > 0) waveform(c, wave, freq) * amp else 0.0
        c.phase = (c.phase + freq / SAMPLE_RATE) % 1.0
        c.phase2 = (c.phase2 + freq * 127.0 / 128.0 / SAMPLE_RATE) % 1.0
        c.elapsed++
        if (++c.sampleInNote >= noteLength) advance(c, base, word)
        return value
    }

    private fun advance(
        c: Channel,
        base: Int,
        word: Int,
    ) {
        c.sampleInNote = 0
        c.prevPitch = (word and 0x3f).toDouble()
        c.note++
        val loopStart = ram[base + 66]
        val loopEnd = ram[base + 67]
        if (loopEnd > loopStart && !c.released && c.note >= loopEnd) {
            c.note = loopStart
        } else if (loopEnd == 0 && loopStart > 0 && c.note >= loopStart) {
            stop(c)
        } else if (c.note >= c.endNote) {
            stop(c)
        }
    }

    private fun tri(t: Double): Double = abs(4 * t - 2) - 1

    private fun waveform(
        c: Channel,
        wave: Int,
        freq: Double,
    ): Double {
        val t = c.phase
        return when (wave) {
            0 -> tri(t)
            1 -> if (t < 0.875) t / 0.875 * 2 - 1 else 1 - (t - 0.875) / 0.125 * 2
            2 -> (2 * t - 1) * 0.7
            3 -> if (t < 0.5) 0.5 else -0.5
            4 -> if (t < 0.3125) 0.5 else -0.5
            5 -> (if (t < 0.5) 3 - abs(24 * t - 6) else 1 - abs(16 * t - 12)) / 3
            6 -> {
                // Noise whose brightness follows the pitch.
                if ((t + freq / SAMPLE_RATE) >= 1.0 || random.nextInt(8) == 0) c.noiseTarget = random.nextDouble() * 2 - 1
                c.noise += (c.noiseTarget - c.noise) * minOf(1.0, freq * 16 / SAMPLE_RATE)
                c.noise
            }
            else -> (tri(t) + tri(c.phase2)) * 0.5
        }
    }

    companion object {
        const val SAMPLE_RATE = 22050
        const val TICK = 183
    }
}
