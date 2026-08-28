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
package com.vitorpamplona.quartz.nip51Lists.originlessServers

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.EventFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginlessServersEventTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val umbrel = "http://originless.umbrel.local"
    private val publicHint = "https://originless.example"

    @Test
    fun kindIs10062Replaceable() {
        assertEquals(10062, OriginlessServersEvent.KIND)
        assertTrue(OriginlessServersEvent.KIND in 10000..19999)
    }

    @Test
    fun createPutsServersOnlyInEncryptedContent() =
        runTest {
            val event = OriginlessServersEvent.create(listOf(umbrel, publicHint), signer)

            assertEquals(OriginlessServersEvent.KIND, event.kind)
            assertTrue(event.tags.none { it.getOrNull(0) == "server" })
            assertEquals(emptyList(), event.publicServers())
            assertFalse(event.content.contains("umbrel", ignoreCase = true))
            assertFalse(event.content.contains(publicHint))
            assertEquals(listOf(umbrel, publicHint), event.servers(signer))
        }

    @Test
    fun updateReplacesPrivateServerList() =
        runTest {
            val first = OriginlessServersEvent.create(listOf(umbrel), signer)
            val updated = OriginlessServersEvent.updateServerList(first, listOf(publicHint), signer)

            assertEquals(listOf(umbrel), first.servers(signer))
            assertEquals(listOf(publicHint), updated.servers(signer))
            assertTrue(updated.tags.none { it.getOrNull(0) == "server" })
            assertTrue(updated.createdAt >= first.createdAt)
        }

    @Test
    fun emptyListStillEncryptsWithoutPublicServers() =
        runTest {
            val event = OriginlessServersEvent.create(emptyList(), signer)
            assertEquals(emptyList(), event.servers(signer))
            assertTrue(event.tags.none { it.getOrNull(0) == "server" })
        }

    @Test
    fun eventFactoryDispatchesKind10062() =
        runTest {
            val created = OriginlessServersEvent.create(listOf(umbrel), signer)
            val parsed =
                EventFactory.create<Event>(
                    created.id,
                    created.pubKey,
                    created.createdAt,
                    created.kind,
                    created.tags,
                    created.content,
                    created.sig,
                )

            assertTrue(parsed is OriginlessServersEvent)
            assertEquals(listOf(umbrel), parsed.servers(signer))
        }

    @Test
    fun otherSignerCannotReadPrivateServers() =
        runTest {
            val event = OriginlessServersEvent.create(listOf(umbrel), signer)
            val stranger = NostrSignerInternal(KeyPair())
            assertEquals(null, event.privateServers(stranger))
            assertEquals(emptyList(), event.servers(stranger))
        }
}
