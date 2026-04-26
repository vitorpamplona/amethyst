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
package com.vitorpamplona.amethyst.service.lang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TranslationDictionaryTest {
    // ----- isWorthTranslating -----

    @Test
    fun `short text is not worth translating`() {
        assertFalse(TranslationDictionary.isWorthTranslating(""))
        assertFalse(TranslationDictionary.isWorthTranslating("a"))
        assertFalse(TranslationDictionary.isWorthTranslating("ab"))
        assertFalse(TranslationDictionary.isWorthTranslating("abc"))
    }

    @Test
    fun `letterless text is not worth translating`() {
        assertFalse(TranslationDictionary.isWorthTranslating("123456"))
        assertFalse(TranslationDictionary.isWorthTranslating("!!!!!!"))
        assertFalse(TranslationDictionary.isWorthTranslating("       "))
        // Emoji-only.
        assertFalse(TranslationDictionary.isWorthTranslating("😊😊😊"))
    }

    @Test
    fun `text with at least one letter is worth translating`() {
        assertTrue(TranslationDictionary.isWorthTranslating("Hello"))
        assertTrue(TranslationDictionary.isWorthTranslating("a123"))
        assertTrue(TranslationDictionary.isWorthTranslating("你好世界"))
        // Mixed emoji + letters.
        assertTrue(TranslationDictionary.isWorthTranslating("😊 hi"))
    }

    // ----- placeholder -----

    @Test
    fun `placeholder is a single Unicode Private Use Area codepoint`() {
        val p0 = TranslationDictionary.placeholder(0)
        val p1 = TranslationDictionary.placeholder(1)
        assertEquals(1, p0.codePointCount(0, p0.length))
        assertEquals(1, p1.codePointCount(0, p1.length))
        assertEquals(0xE000, p0.codePointAt(0))
        assertEquals(0xE001, p1.codePointAt(0))
        assertNotEquals(p0, p1)
    }

    @Test
    fun `placeholder rejects out of range index`() {
        try {
            TranslationDictionary.placeholder(-1)
            fail("expected IllegalArgumentException for negative index")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            TranslationDictionary.placeholder(TranslationDictionary.PLACEHOLDER_LIMIT + 1)
            fail("expected IllegalArgumentException for index past limit")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    // ----- build -----

    @Test
    fun `build empty dictionary for plain text`() {
        val dict = TranslationDictionary.build("Just plain text with no special tokens")
        assertTrue(dict.isEmpty())
    }

    @Test
    fun `build picks up a single URL`() {
        val text = "Have you seen this https://t.me/mygroup yet?"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue("dict should contain the URL value", dict.containsValue("https://t.me/mygroup"))
    }

    @Test
    fun `build picks up nostr NIP-19 references`() {
        val text = "see nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy here"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue("nostr:nevent1qqs0tsw8hjacs4fppgdg7f5yhgwwfkyua4xcs3re9wwkpkk2qeu6mhql22rcy"))
    }

    @Test
    fun `build picks up Lightning invoices`() {
        val invoice =
            "lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3k5tdzrg4cr8rj2f364sdq5g9kxy7fqd9h8vmmfvdjs"
        val dict = TranslationDictionary.build("Pay me: $invoice please")
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue(invoice))
    }

    @Test
    fun `build picks up legacy NIP-08 positional references`() {
        val text = "Have you seen this, #[0]"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
        assertTrue(dict.containsValue("#[0]"))
    }

    @Test
    fun `build deduplicates repeated occurrences of the same value`() {
        val text = "https://a.com and again https://a.com"
        val dict = TranslationDictionary.build(text)
        assertEquals(1, dict.size)
    }

    @Test
    fun `build collects multiple distinct tokens`() {
        val text =
            "ln: lnbc12u1p3lvjeupp5a5ecgp45k6pa8tu7rnkgzfuwdy3l5ylv3 url: https://a.com " +
                "ref: nostr:nevent1qqsabcdefghjklmnpqrstuvwxyz023456789 nip08: #[0]"
        val dict = TranslationDictionary.build(text)
        // We expect at least one entry per category. Exact count depends on the regexes' bech32-charset
        // truncation behaviour; the contract we care about is that each distinct kind is captured.
        assertTrue(dict.values.any { it.startsWith("lnbc") })
        assertTrue("https://a.com" in dict.values)
        assertTrue(dict.values.any { it.startsWith("nostr:nevent1") })
        assertTrue("#[0]" in dict.values)
    }

    @Test
    fun `build rejects URLs with Chinese full-width punctuation false-positives`() {
        // The URL detector greedily includes ， and 。 — those substrings are not real URLs.
        val text = "看 http://x.com，再见。"
        val dict = TranslationDictionary.build(text)
        for (value in dict.values) {
            assertFalse("URL with ， or 。 should be skipped: $value", value.contains('，') || value.contains('。'))
        }
    }

    // ----- encode / decode round-trip -----

    @Test
    fun `encode replaces dictionary values with placeholders and decode restores them`() {
        val text = "Have you seen this https://t.me/mygroup ?"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)

        assertFalse("URL must be removed from encoded text", encoded.contains("https://t.me/mygroup"))
        assertTrue("encoded text must contain the placeholder", encoded.codePoints().anyMatch { it in 0xE000..0xF8FF })

        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `round-trip preserves nostr references through a simulated translation`() {
        val text = "Have you seen this, #[0] and nostr:nevent1qqsabcdefgh023456?"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)

        // Simulate a translator: rewrite the surrounding English to Portuguese, but pass placeholders through unchanged.
        val translated = encoded.replace("Have you seen this", "Você já viu isso").replace("and", "e")

        val decoded = TranslationDictionary.decode(translated, dict)!!
        assertTrue("decoded must contain #[0]", decoded.contains("#[0]"))
        assertTrue("decoded must contain the nostr ref", decoded.contains("nostr:nevent1qqsabcdefgh023456"))
        assertFalse("decoded must not leak placeholder codepoints", decoded.codePoints().anyMatch { it in 0xE000..0xF8FF })
    }

    @Test
    fun `round-trip preserves multiple URLs of differing lengths`() {
        val text =
            "short https://a.co and " +
                "long https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg end"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `encode replaces longer values first to avoid prefix collisions`() {
        // If "https://a.co" was replaced before "https://a.co/long", the longer URL would be partially clobbered.
        val text = "long https://a.co/long short https://a.co end"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        // Both URLs must be fully replaced — no leftover http:// fragments.
        assertFalse("no leftover URL fragment in encoded text", encoded.contains("https://"))
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `decode does not corrupt user text containing the old B0 C0 A0 placeholders`() {
        // Regression for the pre-rewrite bug: old placeholders "B0", "C0", "A0" collided with arbitrary
        // user content. The new PUA placeholders are invisible codepoints that cannot occur in normal text,
        // so a sentence mentioning "B0" or "C0" should round-trip unchanged when there's nothing to replace.
        val text = "Pricing tier B0 vs C0 vs A0 — see https://docs.example.com/tiers"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)!!
        assertTrue(decoded.contains("B0"))
        assertTrue(decoded.contains("C0"))
        assertTrue(decoded.contains("A0"))
        assertEquals(text, decoded)
    }

    @Test
    fun `case sensitive replacement preserves user text that differs only in case`() {
        // The pre-rewrite implementation used ignoreCase=true, which could mangle user text that looked
        // like a URL placeholder in a different case. With case-sensitive replacement this can't happen.
        val text = "Visit HTTPS://A.COM/Path then revisit https://a.com/Path"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `encode is no-op when dictionary is empty`() {
        val text = "Plain text without anything special"
        assertEquals(text, TranslationDictionary.encode(text, emptyMap()))
    }

    @Test
    fun `decode handles null input`() {
        assertNull(TranslationDictionary.decode(null, mapOf("a" to "b")))
    }

    @Test
    fun `decode is no-op when dictionary is empty`() {
        val text = "anything goes"
        assertEquals(text, TranslationDictionary.decode(text, emptyMap()))
    }

    @Test
    fun `mixed content from real-world test cases round-trips`() {
        val text =
            "Hi there! \n How are you doing? \n https://i.imgur.com/asdEZ3QPswadfj2389rioasdjf9834riofaj9834aKLL.jpg"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
    }

    @Test
    fun `complex real-world post round-trips`() {
        // Mirrors TranslationsTest#testHttp: URL + emoji + multiple NIP-19 references.
        val text =
            "https://m.primal.net/MdDd.png \nRunning... 😁   " +
                "nostr:npub126ntw5mnermmj0znhjhgdk8lh2af72sm8qfzq48umdlnhaj9kuns3le9ll  " +
                "nostr:npub1getal6ykt05fsz5nqu4uld09nfj3y3qxmv8crys4aeut53unfvlqr80nfm"
        val dict = TranslationDictionary.build(text)
        val encoded = TranslationDictionary.encode(text, dict)
        val decoded = TranslationDictionary.decode(encoded, dict)
        assertEquals(text, decoded)
        // And every special token must have been replaced in the encoded form.
        assertFalse(encoded.contains("https://m.primal.net/MdDd.png"))
        assertFalse(encoded.contains("nostr:npub126ntw5mnermmj0znhjhgdk8lh2af72sm8qfzq48umdlnhaj9kuns3le9ll"))
        assertFalse(encoded.contains("nostr:npub1getal6ykt05fsz5nqu4uld09nfj3y3qxmv8crys4aeut53unfvlqr80nfm"))
    }
}
