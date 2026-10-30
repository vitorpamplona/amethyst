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
 * 64 music patterns. Each note has a pitch (0-63, 33 is A at 440 Hz), one of 8 waveforms or a
 * custom instrument (another effect, played as the voice at the note's relative pitch), a volume
 * (0-7) and an effect (slide, vibrato, drop, fade in/out, fast/slow arpeggio). One note lasts
 * `speed * 183` samples at 22050 Hz.
 *
 * The waveforms are approximations by ear, not a match for the original synth, and the per-effect
 * filters (noiz, buzz, detune, reverb, dampen) are not applied.
 */
class Audio(
    private val ram: Ram,
) {
    /** A position inside one effect: which note, how far into it, and the oscillator state. */
    private class Voice {
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
    }

    private class Channel {
        val voice = Voice()

        /** The custom instrument a note is playing through, restarted at each new note. */
        val instrument = Voice()
        var instrumentNote = -1
        var fromMusic = false
    }

    private val channels = Array(4) { Channel() }
    private val random = Random(0x5eed)

    private var pattern = -1
    private var patternSamplesLeft = 0
    private var patternSamplesPlayed = 0
    private var patternsPlayed = 0
    private var musicMask = 0x0f

    /** Music volume (0..1) and how much it changes per sample while fading; stops at 0 when fading out. */
    private var musicVolume = 1.0
    private var musicFadeStep = 0.0

    // ---------------------------------------------------------------- control

    /**
     * `sfx(n, channel, offset, length)`. A negative [n] is a command for [channel] (or every
     * channel when it is negative): -1 stops, -2 releases a loop. Channel -1 picks a free one,
     * channel -2 stops effect [n] wherever it plays.
     */
    fun sfx(
        n: Int,
        channel: Int,
        offset: Int,
        length: Int,
    ) {
        if (n < 0) {
            val targets = if (channel in 0..3) listOf(channels[channel]) else channels.toList()
            when (n) {
                -1 -> targets.forEach { if (channel in 0..3 || !it.fromMusic) stop(it) }
                -2 -> targets.forEach { it.voice.released = true }
            }
            return
        }
        if (n > 63) return
        if (channel == -2) {
            channels.forEach { if (it.voice.sfx == n) stop(it) }
            return
        }
        val ch =
            if (channel in 0..3) {
                channel
            } else {
                // Prefer the channel already playing this effect, then a silent one, then one music doesn't hold.
                channels.indexOfFirst { it.voice.sfx == n && !it.fromMusic }.takeIf { it >= 0 }
                    ?: channels.indexOfFirst { it.voice.sfx < 0 }.takeIf { it >= 0 }
                    ?: (0..3).firstOrNull { pattern < 0 || (musicMask shr it) and 1 == 0 }
                    ?: (0..3).firstOrNull { !channels[it].fromMusic }
                    ?: 0
            }
        val start = offset.coerceIn(0, 31)
        start(channels[ch], n, start, if (length > 0) start + length else 32, fromMusic = false)
    }

    /** `music(n, fade, mask)`: plays from pattern [n], fading in over [fadeMs]; a negative [n] stops (fading out). */
    fun music(
        n: Int,
        fadeMs: Int,
        mask: Int,
    ) {
        if (n !in 0..63) {
            if (fadeMs > 0 && pattern >= 0) {
                musicFadeStep = -1.0 / (fadeMs * SAMPLE_RATE / 1000.0)
            } else {
                stopMusic()
            }
            return
        }
        channels.forEach { if (it.fromMusic) stop(it) }
        musicMask = if (mask == 0) 0x0f else mask and 0x0f
        patternsPlayed = 0
        if (fadeMs > 0) {
            musicVolume = 0.0
            musicFadeStep = 1.0 / (fadeMs * SAMPLE_RATE / 1000.0)
        } else {
            musicVolume = 1.0
            musicFadeStep = 0.0
        }
        startPattern(n)
    }

    private fun stopMusic() {
        channels.forEach { if (it.fromMusic) stop(it) }
        pattern = -1
        musicFadeStep = 0.0
        musicVolume = 1.0
    }

    /**
     * A `\a` sound in `print`: nothing beeps, digits play that effect, anything else is notes
     * (`a`-`g`, `#`/`-`, octave digit, `.` rests; `s` speed, `l` loop, `i` instrument, `v` volume,
     * `x` effect, `<`/`>` volume steps) written into a free effect 60-63 and played.
     */
    fun playInline(spec: String) {
        if (spec.isNotEmpty() && spec.all { it in '0'..'9' }) {
            sfx(spec.toInt(), -1, 0, 0)
            return
        }
        val slot = (60..63).firstOrNull { n -> channels.none { it.voice.sfx == n } } ?: 63
        val base = Addr.SFX + slot * 68
        ram.fill(base, 0, 68)
        var speed = 16
        var loopStart = 0
        var loopEnd = 0
        var instrument = 5
        var volume = 5
        var effect = 0
        var octave = 2
        var notes = 0
        var i = 0

        fun param(): Int = if (i < spec.length) Printer.paramDigit(spec[i++]) else 0

        fun write(
            pitch: Int,
            vol: Int,
        ) {
            val word =
                (pitch.coerceIn(0, 63)) or ((instrument and 7) shl 6) or ((vol and 7) shl 9) or
                    ((effect and 7) shl 12) or (if (instrument >= 8) 0x8000 else 0)
            ram.poke2(base + notes * 2, word)
            notes++
        }
        while (i < spec.length && notes < 32) {
            when (val c = spec[i++]) {
                's' -> speed = maxOf(1, param())
                'l' -> {
                    loopStart = param()
                    loopEnd = param()
                }
                'i' -> instrument = param() and 15
                'v' -> volume = param() and 7
                'x' -> effect = param() and 7
                '<' -> volume = maxOf(0, volume - 1)
                '>' -> volume = minOf(7, volume + 1)
                '.' -> write(0, 0)
                in 'a'..'g', in 'A'..'G' -> {
                    var semitone = SEMITONES[c.lowercaseChar() - 'a']
                    if (i < spec.length && spec[i] == '#') {
                        semitone++
                        i++
                    } else if (i < spec.length && spec[i] == '-') {
                        semitone--
                        i++
                    }
                    if (i < spec.length && spec[i] in '0'..'9') octave = spec[i++] - '0'
                    write(octave * 12 + semitone, volume)
                }
            }
        }
        if (notes == 0) write(24 + 9, volume)
        ram[base + 65] = speed
        ram[base + 66] = loopStart
        ram[base + 67] = loopEnd
        sfx(slot, -1, 0, notes)
    }

    private fun start(
        c: Channel,
        n: Int,
        offset: Int,
        end: Int,
        fromMusic: Boolean,
    ) {
        c.voice.sfx = n
        c.voice.note = offset
        c.voice.endNote = minOf(end, 32)
        c.voice.sampleInNote = 0
        c.voice.prevPitch = -1.0
        c.voice.released = false
        c.instrumentNote = -1
        c.fromMusic = fromMusic
    }

    private fun stop(c: Channel) {
        c.voice.sfx = -1
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
            if (ch.voice.sfx < 0 || ch.fromMusic) start(ch, sfx, 0, 32, fromMusic = true)
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
        patternsPlayed++
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

    fun channelSfx(ch: Int): Int = channels.getOrNull(ch)?.voice?.sfx ?: -1

    fun channelNote(ch: Int): Int = channels.getOrNull(ch)?.voice?.let { if (it.sfx < 0) -1 else it.note } ?: -1

    fun currentPattern(): Int = pattern

    fun patternsPlayed(): Int = patternsPlayed

    fun patternTicks(): Int = patternSamplesPlayed / TICK

    fun musicPlaying(): Boolean = pattern >= 0

    // ---------------------------------------------------------------- synthesis

    /** Fills [out] from [offset] with [count] mono samples in [-1, 1]. */
    fun render(
        out: FloatArray,
        offset: Int,
        count: Int,
    ) {
        for (s in 0 until count) {
            var mix = 0.0
            for (c in channels) {
                if (c.voice.sfx < 0) continue
                val v = channelSample(c)
                mix += if (c.fromMusic) v * musicVolume else v
            }
            if (musicFadeStep != 0.0) {
                musicVolume += musicFadeStep
                if (musicVolume >= 1.0) {
                    musicVolume = 1.0
                    musicFadeStep = 0.0
                } else if (musicVolume <= 0.0) {
                    stopMusic()
                }
            }
            if (pattern >= 0) {
                patternSamplesPlayed++
                if (--patternSamplesLeft <= 0) nextPattern()
            }
            out[offset + s] = (mix * 0.35).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    private class Note(
        var pitch: Double,
        var wave: Int,
        var volume: Double,
        var effect: Int,
        var custom: Boolean,
    )

    private val note = Note(0.0, 0, 0.0, 0, false)
    private val instNote = Note(0.0, 0, 0.0, 0, false)

    /** Reads the note under [v] into [into], with its effect applied to pitch and volume. */
    private fun readNote(
        v: Voice,
        into: Note,
    ) {
        val base = Addr.SFX + v.sfx * 68
        val speed = maxOf(1, ram[base + 65])
        val word = ram[base + v.note * 2] or (ram[base + v.note * 2 + 1] shl 8)
        into.pitch = (word and 0x3f).toDouble()
        into.wave = (word shr 6) and 7
        into.volume = ((word shr 9) and 7) / 7.0
        into.effect = (word shr 12) and 7
        into.custom = word and 0x8000 != 0
        val progress = v.sampleInNote.toDouble() / (speed * TICK)
        when (into.effect) {
            1 -> if (v.prevPitch >= 0) into.pitch = v.prevPitch + (into.pitch - v.prevPitch) * progress
            2 -> into.pitch += 0.25 * sin(2 * PI * 7.5 * v.elapsed / SAMPLE_RATE)
            3 -> {} // the drop is applied to the frequency, see dropFactor
            4 -> into.volume *= progress
            5 -> into.volume *= 1 - progress
            6, 7 -> {
                val step = if (into.effect == 6) (if (speed <= 8) 2 else 4) else (if (speed <= 8) 4 else 8)
                val group = v.note and 3.inv()
                val index = ((v.elapsed / (step * TICK)) % 4).toInt()
                val w = ram[base + (group + index) * 2] or (ram[base + (group + index) * 2 + 1] shl 8)
                into.pitch = (w and 0x3f).toDouble()
            }
        }
    }

    private fun dropFactor(
        v: Voice,
        effect: Int,
    ): Double {
        if (effect != 3) return 1.0
        val speed = maxOf(1, ram[Addr.SFX + v.sfx * 68 + 65])
        return 1 - v.sampleInNote.toDouble() / (speed * TICK)
    }

    private fun channelSample(c: Channel): Double {
        val v = c.voice
        readNote(v, note)
        var freq: Double
        var amp: Double
        var wave: Int
        val oscillator: Voice
        if (note.custom) {
            // The note plays effect [wave] as its instrument, shifted so that C-2 (24) is unchanged.
            val inst = c.instrument
            if (c.instrumentNote != v.note || inst.sfx != note.wave) {
                c.instrumentNote = v.note
                inst.sfx = note.wave
                inst.note = 0
                inst.endNote = 32
                inst.sampleInNote = 0
                inst.prevPitch = -1.0
                inst.released = false
            }
            if (inst.sfx < 0) {
                advance(v, c)
                return 0.0
            }
            readNote(inst, instNote)
            freq = pitchToFreq(instNote.pitch + (note.pitch - 24)) * dropFactor(inst, instNote.effect) * dropFactor(v, note.effect)
            amp = note.volume * instNote.volume
            wave = instNote.wave
            oscillator = inst
            advanceInstrument(inst)
        } else {
            freq = pitchToFreq(note.pitch) * dropFactor(v, note.effect)
            amp = note.volume
            wave = note.wave
            oscillator = v
        }
        val value = if (amp > 0) waveform(oscillator, wave, freq) * amp else 0.0
        oscillator.phase = (oscillator.phase + freq / SAMPLE_RATE) % 1.0
        oscillator.phase2 = (oscillator.phase2 + freq * 127.0 / 128.0 / SAMPLE_RATE) % 1.0
        advance(v, c)
        return value
    }

    private fun pitchToFreq(pitch: Double) = 440.0 * 2.0.pow((pitch - 33) / 12)

    /** Moves a channel's effect one sample on, to its next note, loop or end. */
    private fun advance(
        v: Voice,
        c: Channel,
    ) {
        v.elapsed++
        val base = Addr.SFX + v.sfx * 68
        val speed = maxOf(1, ram[base + 65])
        if (++v.sampleInNote < speed * TICK) return
        if (!nextNote(v, base)) stop(c)
    }

    /** An instrument restarts its own effect from the top and loops it like any other; when it ends it stays silent. */
    private fun advanceInstrument(v: Voice) {
        v.elapsed++
        val base = Addr.SFX + v.sfx * 68
        val speed = maxOf(1, ram[base + 65])
        if (++v.sampleInNote < speed * TICK) return
        if (!nextNote(v, base)) v.sfx = -1
    }

    /** Steps [v] to its next note; false when the effect is over. */
    private fun nextNote(
        v: Voice,
        base: Int,
    ): Boolean {
        val word = ram[base + v.note * 2]
        v.sampleInNote = 0
        v.prevPitch = (word and 0x3f).toDouble()
        v.note++
        val loopStart = ram[base + 66]
        val loopEnd = ram[base + 67]
        return when {
            loopEnd > loopStart && !v.released && v.note >= loopEnd -> {
                v.note = loopStart
                true
            }
            loopEnd == 0 && loopStart > 0 && v.note >= loopStart -> false
            v.note >= v.endNote -> false
            else -> true
        }
    }

    private fun tri(t: Double): Double = abs(4 * t - 2) - 1

    private fun waveform(
        v: Voice,
        wave: Int,
        freq: Double,
    ): Double {
        val t = v.phase
        return when (wave) {
            0 -> tri(t)
            1 -> if (t < 0.875) t / 0.875 * 2 - 1 else 1 - (t - 0.875) / 0.125 * 2
            2 -> (2 * t - 1) * 0.7
            3 -> if (t < 0.5) 0.5 else -0.5
            4 -> if (t < 0.3125) 0.5 else -0.5
            5 -> (if (t < 0.5) 3 - abs(24 * t - 6) else 1 - abs(16 * t - 12)) / 3
            6 -> {
                // Noise whose brightness follows the pitch.
                if ((t + freq / SAMPLE_RATE) >= 1.0 || random.nextInt(8) == 0) v.noiseTarget = random.nextDouble() * 2 - 1
                v.noise += (v.noiseTarget - v.noise) * minOf(1.0, freq * 16 / SAMPLE_RATE)
                v.noise
            }
            else -> (tri(t) + tri(v.phase2)) * 0.5
        }
    }

    companion object {
        const val SAMPLE_RATE = 22050
        const val TICK = 183

        private val SEMITONES = intArrayOf(9, 11, 0, 2, 4, 5, 7) // a b c d e f g
    }
}
