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

import com.vitorpamplona.quartz.concord.events.ConcordKinds
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConcordCommunityListTest {
    private val signer = NostrSignerInternal(KeyPair())
    private val other = NostrSignerInternal(KeyPair())

    private fun entry(
        id: String,
        name: String,
        epoch: Long = 0,
    ) = ConcordCommunityListEntry(
        id = id,
        owner = "0f".repeat(32),
        ownerSalt = "aa".repeat(32),
        root = "bb".repeat(32),
        rootEpoch = epoch,
        relays = listOf("wss://relay.example"),
        name = name,
    )

    @Test
    fun selfEncryptedListRoundTrips() =
        runTest {
            val entries = listOf(entry("11".repeat(32), "Gamers"), entry("22".repeat(32), "Nostrichs"))
            val event = ConcordCommunityList.build(signer, entries, createdAt = 1_700_000_000L)

            assertEquals(ConcordKinds.COMMUNITY_LIST, event.kind)
            assertFalse(event.content.contains("Gamers")) // encrypted on the wire

            val parsed = ConcordCommunityList.parse(event, signer)
            assertEquals(2, parsed.size)
            assertEquals("Gamers", parsed[0].name)
            assertEquals(listOf("wss://relay.example"), parsed[0].relays)
        }

    @Test
    fun onlyTheOwnerCanDecrypt() =
        runTest {
            val event = ConcordCommunityList.build(signer, listOf(entry("11".repeat(32), "Secret")), createdAt = 1L)
            assertTrue(ConcordCommunityList.parse(event, other).isEmpty()) // wrong key ⇒ nothing
        }

    @Test
    fun mergeKeepsFreshestEpochPerCommunity() {
        val a = listOf(entry("11".repeat(32), "Old", epoch = 1))
        val b = listOf(entry("11".repeat(32), "New", epoch = 3), entry("22".repeat(32), "Other", epoch = 0))
        val merged = ConcordCommunityList.merge(a, b)
        assertEquals(2, merged.size)
        assertEquals("New", merged.first { it.id == "11".repeat(32) }.name) // higher epoch wins
    }
}
