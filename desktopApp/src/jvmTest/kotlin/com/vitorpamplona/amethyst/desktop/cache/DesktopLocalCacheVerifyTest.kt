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

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies that [DesktopLocalCache] rejects unverified events at its public
 * ingress points (`consume` and `justConsumeMyOwnEvent`). This is the
 * desktop equivalent of Amethyst Android's `LocalCache.justVerify` guard
 * and closes the receive-time verification gap flagged in the Quartz
 * security review (item 2.1, finding #1).
 */
class DesktopLocalCacheVerifyTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    private fun signedTextNote(
        content: String = "hello",
        createdAt: Long = 1_700_000_000,
    ): TextNoteEvent {
        val signer = NostrSignerSync(KeyPair())
        return signer.sign<TextNoteEvent>(
            createdAt = createdAt,
            kind = TextNoteEvent.KIND,
            tags = emptyArray(),
            content = content,
        )
    }

    @Test
    fun `consume rejects an event with a forged signature`() {
        val cache = DesktopLocalCache()
        val authentic = signedTextNote()
        val tampered =
            TextNoteEvent(
                id = authentic.id,
                pubKey = authentic.pubKey,
                createdAt = authentic.createdAt,
                tags = authentic.tags,
                content = "tampered content",
                sig = authentic.sig,
            )

        val consumed = cache.consume(tampered, relayUrl)

        assertFalse(consumed, "Tampered event should be rejected")
        assertNull(
            cache.getNoteIfExists(tampered.id),
            "Tampered event must not enter the cache",
        )
    }

    @Test
    fun `consume rejects an event whose id-hash does not match its content`() {
        // Synthetic event with arbitrary id and signature — fails the id check
        // before the Schnorr verify is even attempted.
        val cache = DesktopLocalCache()
        val event =
            TextNoteEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1_700_000_000,
                tags = emptyArray(),
                content = "synthetic",
                sig = "0".repeat(128),
            )

        val consumed = cache.consume(event, relayUrl)

        assertFalse(consumed)
        assertNull(cache.getNoteIfExists(event.id))
    }

    @Test
    fun `consume accepts a properly signed event`() {
        val cache = DesktopLocalCache()
        val signed = signedTextNote(content = "authentic")

        val consumed = cache.consume(signed, relayUrl)

        assertTrue(consumed, "Signed event should be accepted")
        val note = cache.getNoteIfExists(signed.id)
        assertNotNull(note, "Signed event must reach the cache")
        assertEquals(signed.id, note.event?.id)
    }

    @Test
    fun `justConsumeMyOwnEvent rejects unverified events`() {
        val cache = DesktopLocalCache()
        val unsigned =
            TextNoteEvent(
                id = "a".repeat(64),
                pubKey = "b".repeat(64),
                createdAt = 1_700_000_000,
                tags = emptyArray(),
                content = "not signed",
                sig = "0".repeat(128),
            )

        val accepted = cache.justConsumeMyOwnEvent(unsigned)

        assertFalse(accepted)
    }

    @Test
    fun `wasVerified bypass routes synthetic test events`() {
        val cache = DesktopLocalCache()
        val event =
            TextNoteEvent(
                id = "n1".padEnd(64, '0'),
                pubKey = "a".repeat(64),
                createdAt = 1_700_000_000,
                tags = emptyArray(),
                content = "synthetic",
                sig = "0".repeat(128),
            )

        val consumed = cache.consume(event, relayUrl, wasVerified = true)

        assertTrue(consumed, "wasVerified=true must skip the signature check")
        assertNotNull(cache.getNoteIfExists(event.id))
    }
}
