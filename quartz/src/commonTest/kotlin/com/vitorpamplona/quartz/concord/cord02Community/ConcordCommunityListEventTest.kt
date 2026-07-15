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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConcordCommunityListEventTest {
    private val signer = NostrSignerInternal(KeyPair())

    private val entry =
        ConcordCommunityListEntry(
            id = "11".repeat(32),
            owner = "0f".repeat(32),
            ownerSalt = "aa".repeat(32),
            root = "bb".repeat(32),
            rootEpoch = 0,
            relays = listOf("wss://relay.example"),
            name = "Nostrichs",
        )

    @Test
    fun eventFactoryReturnsTypedClassAndDecrypts() =
        runTest {
            val event = ConcordCommunityListEvent.create(signer, listOf(entry), createdAt = 1L)
            assertEquals(ConcordCommunityListEvent.KIND, event.kind)
            assertTrue(!event.content.contains("Nostrichs")) // encrypted on the wire

            // A round-trip through JSON parsing resolves to the typed class via EventFactory.
            val reparsed = Event.fromJson(event.toJson())
            assertIs<ConcordCommunityListEvent>(reparsed)

            val entries = reparsed.decrypt(signer)
            assertEquals(1, entries.size)
            assertEquals("Nostrichs", entries[0].name)
            assertEquals(listOf("wss://relay.example"), entries[0].relays)
        }

    @Test
    fun replaceableAddressIsKindPubkeyEmpty() {
        val addr = ConcordCommunityListEvent.createAddress(signer.pubKey)
        assertEquals(ConcordCommunityListEvent.KIND, addr.kind)
        assertEquals(signer.pubKey, addr.pubKeyHex)
        assertEquals("", addr.dTag)
    }
}
