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
package com.vitorpamplona.quartz.nip19Bech32

import com.vitorpamplona.quartz.nip19Bech32.entities.Entity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the ICU-free scanner in [Nip19Parser] to the regexes it replaced.
 *
 * The scan used to run `Regex.matchAt` at every candidate position. On Android that goes through
 * ICU, and `Matcher.region()` copies the whole input into native memory on each call — one full
 * native copy of a note's content *per candidate* — which drove the native heap to ~1.9GB on a
 * cold start and got the process lmkd-killed. The scanner matches the grammar directly instead.
 *
 * [Nip19Parser.nip19regex] and [Nip19Parser.nip19regexEvents] are still the specification, so this
 * runs both over the same corpus and requires identical entity lists. The corpus deliberately
 * targets the places a hand-rolled matcher is most likely to drift from the regex: the exact-58
 * payload boundary, the bech32 alphabet's excluded characters, ASCII-only case folding, and which
 * characters `[\S]*` is willing to swallow.
 */
class Nip19ScannerRegexEquivalenceTest {
    companion object {
        const val NPUB = "npub1hv7k2s755n697sptva8vkh9jz40lzfzklnwj6ekewfmxp5crwdjs27007y"
        const val NOTE = "note1stqea6wmwezg9x6yyr6qkukw95ewtdukyaztycws65l8wppjmtpscawevv"
        const val NEVENT = "nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy"

        /** 58 valid bech32 chars, so `npub1` + this is exactly the fixed-length branch. */
        const val PAYLOAD58 = "qpzry9x8gf2tvdw0s3jn54khce6mua7lqpzry9x8gf2tvdw0s3jn54khce"
    }

    /** What `parseAll` did before: drive the regex from every position with `findAll`. */
    private fun referenceParseAll(content: String): List<Entity> {
        val out = mutableListOf<Entity>()
        Nip19Parser.nip19regex.findAll(content).forEach { m ->
            val type = m.groups[3]?.value ?: m.groups[5]?.value
            val key = m.groups[4]?.value ?: m.groups[6]?.value
            val additionalChars = m.groups[7]?.value
            if (type != null) {
                Nip19Parser.parseComponents(type, key, additionalChars)?.entity?.let { out.add(it) }
            }
        }
        return out
    }

    private fun referenceParseAllEvents(content: String): List<Entity> {
        val out = mutableListOf<Entity>()
        Nip19Parser.nip19regexEvents.findAll(content).forEach { m ->
            val type = m.groups[2]?.value
            val key = m.groups[3]?.value
            val additionalChars = m.groups[4]?.value
            if (type != null) {
                Nip19Parser.parseComponents(type, key, additionalChars)?.entity?.let { out.add(it) }
            }
        }
        return out
    }

    private fun assertSameAsRegex(content: String) {
        assertEquals(
            referenceParseAll(content),
            Nip19Parser.parseAll(content),
            "parseAll diverged from nip19regex on: ${content.take(90)}",
        )
        assertEquals(
            referenceParseAllEvents(content),
            Nip19Parser.parseAllEvents(content),
            "parseAllEvents diverged from nip19regexEvents on: ${content.take(90)}",
        )
    }

    private fun corpus(): List<String> =
        buildList {
            // plain placement
            add("")
            add(NPUB)
            add("hello $NPUB world")
            add("nostr:$NPUB")
            add("@$NPUB")
            add("nostr:@$NPUB")
            add("prefix-nostr:$NPUB-suffix")
            add(NOTE)
            add(NEVENT)

            // adjacency and repetition — where scan-resume position matters
            add(NPUB + NEVENT)
            add("$NPUB $NEVENT")
            add("$NPUB\n$NEVENT")
            add("$NPUB,$NEVENT")
            add(listOf(NPUB, NOTE, NEVENT).joinToString(" "))
            add(NPUB.repeat(3))

            // the exact-58 boundary for npub/nsec/note
            add("npub1" + PAYLOAD58)
            add("npub1" + PAYLOAD58.dropLast(1)) // 57 -> must not match
            add("npub1" + PAYLOAD58 + "q") // 59 -> 58 key, trailing takes the rest
            add("npub1" + PAYLOAD58 + " tail")
            add("note1" + PAYLOAD58)
            add("nsec1" + PAYLOAD58)

            // bech32 alphabet: 1, b, i, o are excluded and must terminate the payload
            add("nevent1qqs1qqs")
            add("nevent1qqsbqqs")
            add("nevent1qqsiqqs")
            add("nevent1qqsoqqs")

            // A *valid* variable-length entity butted straight against an excluded char. The
            // payload has to stop there and still decode. These are the cases with teeth: a
            // charset that wrongly accepted b/i/o/1 would swallow the extra char, fail the
            // bech32 decode and silently drop the entity — whereas cases whose payload is
            // invalid either way agree trivially and prove nothing.
            for (excluded in listOf("b", "i", "o", "1")) {
                add(NEVENT + excluded)
                add(NEVENT + excluded + "xyz")
                add("$NEVENT$excluded more text")
            }
            add("nevent1") // variable branch needs >= 1 payload char
            add("nprofile1")
            add("naddr1q")

            // ASCII-only case folding
            add(NPUB.uppercase())
            add("NOSTR:" + NPUB.uppercase())
            add(NEVENT.uppercase())
            add("nPuB1" + PAYLOAD58)
            // U+212A KELVIN SIGN folds to 'k' under Unicode rules but NOT under the regex's
            // ASCII-only CASE_INSENSITIVE; both sides must reject it.
            add("npub1" + PAYLOAD58.replaceFirst("k", "K"))

            // what [\S]* may swallow: Java's \s is the six ASCII whitespace chars only,
            // so U+00A0 and U+2003 are NON-space and belong to the trailing group.
            add("$NPUB\u00A0more")
            add("$NPUB\u2003more")
            add("${NPUB}more")
            add("${NPUB}1more")
            add("$NPUB\tmore")
            add("$NPUB\rmore")

            // near-misses that must not be mistaken for entities
            add("n")
            add("nn")
            add("np")
            add("no")
            add("nostr:")
            add("nothing to see here")
            add("a note about nothing")
            add("nopqrstuvwxyz")
            add("x$NPUB")
            add("1$NPUB")

            // long content with the entity at the far end (the 767KB-tail shape, scaled down)
            add("lorem ipsum ".repeat(2000) + NPUB)
            add(NPUB + " " + "dolor sit amet ".repeat(2000))
            // many 'n' candidates but no entities — the scanner's rejection path
            add("neither nor none never nothing ".repeat(500))
        }

    @Test
    fun matchesRegexAcrossCorpus() {
        corpus().forEach { assertSameAsRegex(it) }
    }

    @Test
    fun matchesRegexWithEntityAtEveryOffset() {
        // Slides the entity through a filler string so every start offset, including
        // immediately after another candidate 'n', is exercised.
        val filler = "n no non nost nostr "
        for (i in 0..filler.length) {
            assertSameAsRegex(filler.substring(0, i) + NPUB + filler.substring(i))
        }
    }

    @Test
    fun matchesRegexOnTruncatedPayloads() {
        // Every truncation of a real entity: catches off-by-one at the 58 boundary and in `+`.
        for (entity in listOf(NPUB, NOTE, NEVENT)) {
            for (len in 1..entity.length) {
                assertSameAsRegex(entity.substring(0, len))
                assertSameAsRegex("text " + entity.substring(0, len) + " text")
            }
        }
    }
}
