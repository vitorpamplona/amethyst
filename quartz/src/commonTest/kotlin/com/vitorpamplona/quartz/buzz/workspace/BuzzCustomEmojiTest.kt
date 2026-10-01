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
package com.vitorpamplona.quartz.buzz.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `normalize_custom_emoji_shortcode` in `buzz-sdk/src/builders.rs`, which the relay runs on 30030/10030 `emoji` tags. */
class BuzzCustomEmojiTest {
    @Test
    fun acceptsTheNip30AlphabetUpToSixtyFourBytes() {
        assertTrue(BuzzCustomEmoji.isValidShortcode("party_parrot"))
        assertTrue(BuzzCustomEmoji.isValidShortcode("Soap-Box_123"))
        assertTrue(BuzzCustomEmoji.isValidShortcode("a".repeat(64)))
        assertFalse(BuzzCustomEmoji.isValidShortcode("a".repeat(65)))
    }

    @Test
    fun rejectsCharactersOutsideTheAlphabet() {
        assertFalse(BuzzCustomEmoji.isValidShortcode(""))
        assertFalse(BuzzCustomEmoji.isValidShortcode("::"))
        assertFalse(BuzzCustomEmoji.isValidShortcode("soap box"))
        assertFalse(BuzzCustomEmoji.isValidShortcode("café"))
        assertFalse(BuzzCustomEmoji.isValidShortcode("a.b"))
    }

    @Test
    fun normalizesLikeTheRelay() {
        assertEquals("partyparrot", BuzzCustomEmoji.normalizeShortcode("  :PartyParrot:  "))
        assertNull(BuzzCustomEmoji.normalizeShortcode(": spaced :"))
    }
}
