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
package com.vitorpamplona.quartz.concord.cord05Invites

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ConcordDirectInviteTest {
    private val sender = NostrSignerInternal(KeyPair())
    private val recipient = NostrSignerInternal(KeyPair())
    private val stranger = NostrSignerInternal(KeyPair())

    private val invite =
        CommunityInvite(
            communityId = "11".repeat(32),
            owner = "0f".repeat(32),
            ownerSalt = "aa".repeat(32),
            communityRoot = "bb".repeat(32),
            name = "Nostrichs",
        )

    @Test
    fun directInviteRoundTripsToTheRecipient() =
        runTest {
            val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, invite, createdAt = 1_700_000_000L)

            // Wrap is a giftwrap tagged for the recipient and indexable by k=3313.
            assertEquals(recipient.pubKey, wrap.tags.first { it[0] == "p" }[1])
            assertEquals("3313", wrap.tags.first { it[0] == "k" }[1])

            val parsed = ConcordDirectInvite.parse(wrap, recipient)
            assertNotNull(parsed)
            assertEquals("Nostrichs", parsed.name)
            assertEquals("11".repeat(32), parsed.communityId)
        }

    @Test
    fun strangersCannotOpenIt() =
        runTest {
            val wrap = ConcordDirectInvite.build(sender, recipient.pubKey, invite, createdAt = 1L)
            assertNull(ConcordDirectInvite.parse(wrap, stranger))
        }
}
