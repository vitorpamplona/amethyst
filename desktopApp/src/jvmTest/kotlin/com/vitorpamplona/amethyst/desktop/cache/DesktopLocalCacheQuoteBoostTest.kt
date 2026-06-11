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
package com.vitorpamplona.amethyst.desktop.cache

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A NIP-18 quote-repost is a kind:1 note that carries a `q` tag pointing at the
 * quoted note. Amethyst's repost counter reads `Note.boosts`, which historically
 * only collected kind:6/kind:16 reposts — so quote-reposts never showed in the
 * quoted note's reaction row. These tests pin the fix: consuming a `q`-tagged note
 * adds it as a boost of the quoted note.
 */
class DesktopLocalCacheQuoteBoostTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    // The exact event reported in the task: a kind:1 quote-repost of 9430…ab10.
    private val quoteJson =
        """
        {
          "id": "e45b2897ea5550f68421e0dc3387c2ab2fdcb5f303f91f88199cf45abb18a6b5",
          "pubkey": "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c",
          "created_at": 1781031744,
          "kind": 1,
          "tags": [
            ["alt", "A short note: Few"],
            ["p", "deab79dafa1c2be4b4a6d3aca1357b6caa0b744bf46ad529a5ae464288579e68", "wss://nostr.wine/"],
            ["q", "943082a78d462df27a9cf2267366a2b9533817a6d32234e15a7e4f0f7df0ab10", "wss://spatia-arcana.com/", "deab79dafa1c2be4b4a6d3aca1357b6caa0b744bf46ad529a5ae464288579e68"],
            ["client", "Amethyst"]
          ],
          "content": "Few",
          "sig": "3d35780c14d295167b62a760f2409a50e444eb13381ddc0b851becb476c67564f486882a4ea1a2f0247acbfd7c58e37085101b5db6712d972d42e5baafc63520"
        }
        """.trimIndent()

    private val quotedId = "943082a78d462df27a9cf2267366a2b9533817a6d32234e15a7e4f0f7df0ab10"

    @Test
    fun `a quote-repost counts as a boost of the quoted note`() {
        val cache = DesktopLocalCache()
        val quote = Event.fromJson(quoteJson)

        // wasVerified = true: this test pins the boost wiring, not signature checks.
        val consumed = cache.consume(quote, relayUrl, wasVerified = true)
        assertTrue(consumed, "The quote-repost should be consumed")

        val quotedNote = cache.getNoteIfExists(quotedId)
        assertTrue(quotedNote != null, "The quoted note placeholder should exist")
        assertEquals(1, quotedNote.boosts.size, "The quote should count as one boost")
        assertEquals(quote.id, quotedNote.boosts.first().idHex)
    }

    @Test
    fun `a quote-repost authored locally is counted via a fresh signed event`() {
        val cache = DesktopLocalCache()
        val signer = NostrSignerSync(KeyPair())

        val original =
            signer.sign<TextNoteEvent>(
                createdAt = 1_700_000_000,
                kind = TextNoteEvent.KIND,
                tags = emptyArray(),
                content = "the original post",
            )
        cache.consume(original, relayUrl, wasVerified = true)

        val quote =
            signer.sign<TextNoteEvent>(
                createdAt = 1_700_000_100,
                kind = TextNoteEvent.KIND,
                tags = arrayOf(QEventTag(original.id).toTagArray()),
                content = "quoting the original",
            )
        cache.consume(quote, relayUrl, wasVerified = true)

        val originalNote = cache.getNoteIfExists(original.id)
        assertTrue(originalNote != null)
        assertEquals(1, originalNote.boosts.size, "The quote should boost the original")
        assertEquals(quote.id, originalNote.boosts.first().idHex)
    }

    @Test
    fun `a plain note with no quote tag does not boost anything`() {
        val cache = DesktopLocalCache()
        val signer = NostrSignerSync(KeyPair())

        val target =
            signer.sign<TextNoteEvent>(
                createdAt = 1_700_000_000,
                kind = TextNoteEvent.KIND,
                tags = emptyArray(),
                content = "target",
            )
        cache.consume(target, relayUrl, wasVerified = true)

        val plain =
            signer.sign<TextNoteEvent>(
                createdAt = 1_700_000_100,
                kind = TextNoteEvent.KIND,
                tags = emptyArray(),
                content = "no quotes here",
            )
        cache.consume(plain, relayUrl, wasVerified = true)

        assertEquals(0, cache.getNoteIfExists(target.id)?.boosts?.size)
    }
}
