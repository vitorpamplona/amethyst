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
package com.vitorpamplona.quartz.nipCCGeocaching.listing

import kotlin.math.ln

/**
 * Which of a hint's two forms is the one that spoils.
 *
 * NIP-CC is self-contradictory here and the network followed it both ways. The tag table calls
 * `hint` "plaintext" and the spec's own example carries `["hint", "In the branches"]`, but the
 * Clients section asks for "hint encoding, such as ROT13, to prevent spoilers" — and the
 * reference client (treasures.to, mirrored in Lightning Piggy's `nostrPlacesService.ts`) rot13s
 * on write and rot13s back on read.
 *
 * Measured, not assumed: of 34 hints sampled from relay.damus.io / nos.lol / relay.primal.net /
 * nostr.wine, exactly 17 were ROT13 ciphertext and 17 were plaintext, split cleanly *by author*
 * — one publisher's caches are all encoded, another's are all plain. So there is no convention
 * to follow, only a coin flip, and either fixed choice hands the answer to the reader unasked on
 * half the caches in the world.
 *
 * ROT13 is its own inverse, so the two candidate forms of any hint are just `text` and
 * `rot13(text)` — one of them is the hint and the other is noise. Rather than guess the
 * publisher's convention, [hidden] picks whichever form reads *less* like prose and shows that;
 * [revealed] is the other one. Under either convention the reader sees noise until they ask.
 *
 * The test is average log letter-frequency against English. A vowel count is the obvious idea
 * and it is *wrong*: ROT13 maps the common consonants `n→a`, `r→e`, `h→u` and `b→o`, so English
 * ciphertext usually has **more** vowels than the plaintext it came from — "In the branches" has
 * 4 vowels in 13 letters, its rotation "Va gur oenapurf" has 6. Scoring the whole distribution
 * instead gets every hint in the sample right, including short ones like "Small" and
 * "Look up".
 *
 * It is still a guess, and a narrow one: it assumes English and ASCII. A plaintext Spanish hint
 * ("Bajo el banco") scores worse than its own rotation and would be "revealed" as noise. That is
 * why the UI cycles prompt → best guess → other rotation rather than revealing once — a misfire
 * costs a tap, not the hint — and why nothing here should ever be the last word on which form a
 * reader gets to see.
 *
 * None of this is secrecy. The hint is public on the relay in whatever form its author chose;
 * the only goal is that a reader has to opt in.
 */
object HintObfuscation {
    /** Relative frequency of `a`..`z` in English text, as percentages. */
    private val ENGLISH_FREQUENCY =
        doubleArrayOf(
            8.17,
            1.49,
            2.78,
            4.25,
            12.70,
            2.23,
            2.02,
            6.09,
            6.97,
            0.15,
            0.77,
            4.03,
            2.41,
            6.75,
            7.51,
            1.93,
            0.10,
            5.99,
            6.33,
            9.06,
            2.76,
            0.98,
            2.36,
            0.15,
            1.97,
            0.07,
        )

    /**
     * How much [text] looks like English: the mean log frequency of its ASCII letters, 0 when it
     * has none. Higher is more English-like. Only ever compared against the same measure of the
     * same text rotated, so the scale itself carries no meaning.
     */
    fun englishScore(text: String): Double {
        var letters = 0
        var total = 0.0
        text.forEach { char ->
            val lower = char.lowercaseChar()
            if (lower in 'a'..'z') {
                letters++
                total += ln(ENGLISH_FREQUENCY[lower - 'a'])
            }
        }
        return if (letters == 0) 0.0 else total / letters
    }

    /**
     * Whether [onWire] already looks like readable prose — i.e. the publisher wrote it plain and
     * it is the *rotated* form that is safe to show.
     */
    fun isProse(onWire: String) = englishScore(onWire) >= englishScore(rot13(onWire))

    /** The form to show before the reader asks: whichever of the two reads less like prose. */
    fun hidden(onWire: String) = if (isProse(onWire)) rot13(onWire) else onWire

    /** The other form — what the reader gets when they ask. */
    fun revealed(onWire: String) = if (isProse(onWire)) onWire else rot13(onWire)
}
