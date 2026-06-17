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
package com.vitorpamplona.quartz.nip10Notes

import com.vitorpamplona.quartz.nip31Alts.AltTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AltTagSurrogateTest {
    // The summarizer truncates the note to 50 UTF-16 units for the NIP-31 "alt"
    // tag. In this note the 50th unit falls between the two halves of the
    // saluting-face emoji (🫡, U+1FAE1 = surrogate pair D83E DEE1), so a naive
    // take(50) used to leave a lone high surrogate at the end of the alt tag.
    //
    // A lone surrogate is unencodable as UTF-8: it is kept in memory while the
    // event id is hashed (so the signature is over that id), but it is replaced
    // by '?' the moment the event is serialized to the relay. Relays therefore
    // recompute a different id and reject the event as having an invalid id.
    private val note =
        "I had to toggle a setting in fit 👍 That's slick 🫡\n" +
            "https://haven.downisontheup.ca/b598bd967080491578db15cb861ebacdfd056a6ba9e0701190444b183380933b.jpg"

    private fun altOf(note: String): String = TextNoteEvent.build(note).tags.firstNotNullOfOrNull(AltTag::parse)!!

    @Test
    fun altTagHasNoLoneSurrogate() {
        val alt = altOf(note)
        val loneSurrogate =
            alt.indices.any { i ->
                val c = alt[i]
                when {
                    c.isHighSurrogate() -> i + 1 >= alt.length || !alt[i + 1].isLowSurrogate()
                    c.isLowSurrogate() -> i == 0 || !alt[i - 1].isHighSurrogate()
                    else -> false
                }
            }
        assertTrue(!loneSurrogate, "alt tag must not contain a lone surrogate: <$alt>")
    }

    @Test
    fun altTagSurvivesUtf8WireRoundTrip() {
        // Models the UTF-8 encode the relay transport performs. A lone surrogate
        // would be replaced (and the round-trip would differ), which is exactly
        // what corrupted the event id.
        val alt = altOf(note)
        assertEquals(alt, alt.encodeToByteArray().decodeToString())
    }
}
