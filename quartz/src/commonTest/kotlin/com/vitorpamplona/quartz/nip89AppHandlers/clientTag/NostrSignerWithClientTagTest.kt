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
package com.vitorpamplona.quartz.nip89AppHandlers.clientTag

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NostrSignerWithClientTagTest {
    private val keyPair = KeyPair()
    private val innerSigner = NostrSignerInternal(keyPair)

    @Test
    fun addsClientTagToSignedEvent() =
        runTest {
            val signer = NostrSignerWithClientTag(innerSigner, "Amethyst")

            val template =
                eventTemplate<TextNoteEvent>(
                    kind = TextNoteEvent.KIND,
                    description = "Hello Nostr",
                )

            val event = signer.sign<TextNoteEvent>(template)

            assertTrue(event.verify())

            val clientTags = event.tags.filter { it.size >= 2 && it[0] == "client" }
            assertEquals(1, clientTags.size)
            assertEquals("Amethyst", clientTags[0][1])
        }

    @Test
    fun doesNotDuplicateExistingClientTag() =
        runTest {
            val signer = NostrSignerWithClientTag(innerSigner, "Amethyst")

            val template =
                eventTemplate<TextNoteEvent>(
                    kind = TextNoteEvent.KIND,
                    description = "Hello Nostr",
                ) {
                    client("OtherClient")
                }

            val event = signer.sign<TextNoteEvent>(template)

            assertTrue(event.verify())

            val clientTags = event.tags.filter { it.size >= 2 && it[0] == "client" }
            assertEquals(1, clientTags.size)
            assertEquals("OtherClient", clientTags[0][1])
        }

    @Test
    fun addsClientTagWithAddressAndRelay() =
        runTest {
            val signer =
                NostrSignerWithClientTag(
                    innerSigner,
                    "Amethyst",
                    "31990:abc123:amethyst",
                    null,
                )

            val template =
                eventTemplate<TextNoteEvent>(
                    kind = TextNoteEvent.KIND,
                    description = "Hello Nostr",
                )

            val event = signer.sign<TextNoteEvent>(template)

            assertTrue(event.verify())

            val clientTags = event.tags.filter { it.size >= 2 && it[0] == "client" }
            assertEquals(1, clientTags.size)
            assertEquals("Amethyst", clientTags[0][1])
            assertEquals("31990:abc123:amethyst", clientTags[0][2])
        }

    @Test
    fun preservesSamePubKey() {
        val signer = NostrSignerWithClientTag(innerSigner, "Amethyst")
        assertEquals(innerSigner.pubKey, signer.pubKey)
    }

    @Test
    fun delegatesIsWriteable() {
        val signer = NostrSignerWithClientTag(innerSigner, "Amethyst")
        assertEquals(innerSigner.isWriteable(), signer.isWriteable())
    }
}
