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
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * NIP-88 poll consumption: a kind-1068 poll becomes a renderable Note, and a kind-1018
 * response is linked into that poll Note's `pollState()` tally. Second identical response
 * (a relay echo) must not double-count.
 */
class DesktopLocalCachePollTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.test/")

    private fun signedPoll(
        signer: NostrSignerSync,
        createdAt: Long,
    ): PollEvent =
        signer.sign(
            createdAt = createdAt,
            kind = PollEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("option", "0", "Yes"),
                    arrayOf("option", "1", "No"),
                    arrayOf("polltype", "singlechoice"),
                ),
            content = "Pick one",
        )

    private fun signedResponse(
        signer: NostrSignerSync,
        pollId: String,
        option: String,
        createdAt: Long,
    ): PollResponseEvent =
        signer.sign(
            createdAt = createdAt,
            kind = PollResponseEvent.KIND,
            tags =
                arrayOf(
                    arrayOf("e", pollId),
                    arrayOf("response", option),
                ),
            content = "",
        )

    @Test
    fun `a poll response is linked into the poll's tally`() {
        val cache = DesktopLocalCache()
        val author = NostrSignerSync(KeyPair())
        val voter = NostrSignerSync(KeyPair())

        val poll = signedPoll(author, createdAt = 1_700_000_000)
        assertTrue(cache.consume(poll, relayUrl, wasVerified = true), "poll should be consumed")

        val response = signedResponse(voter, poll.id, option = "0", createdAt = 1_700_000_100)
        assertTrue(cache.consume(response, relayUrl, wasVerified = true), "response should be consumed")

        val pollNote = cache.getNoteIfExists(poll.id)
        assertTrue(pollNote != null, "poll note must exist")
        val tally = pollNote.pollState().responses.value
        assertEquals(1, tally.totalVotes())
        assertEquals("0", tally.winning())
    }

    @Test
    fun `a duplicate response is not counted twice`() {
        val cache = DesktopLocalCache()
        val author = NostrSignerSync(KeyPair())
        val voter = NostrSignerSync(KeyPair())

        val poll = signedPoll(author, createdAt = 1_700_000_000)
        cache.consume(poll, relayUrl, wasVerified = true)

        val response = signedResponse(voter, poll.id, option = "1", createdAt = 1_700_000_100)
        assertTrue(cache.consume(response, relayUrl, wasVerified = true))
        // Same signed event echoed back by another relay — id-dedup must reject it.
        assertTrue(!cache.consume(response, relayUrl, wasVerified = true))

        val tally =
            cache
                .getNoteIfExists(poll.id)!!
                .pollState()
                .responses.value
        assertEquals(1, tally.totalVotes())
    }
}
