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
package com.vitorpamplona.amethyst.commons.model.nip88Polls

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PollResponsesCacheTest {
    private val pollId = "a".repeat(64)

    // The tally keys votes by `User` identity, and the real cache returns one `User`
    // instance per pubkey (getOrCreateUser). Mirror that here so same-pubkey re-votes
    // and hasPubKeyVoted() lookups resolve to the same object.
    private val userCache = mutableMapOf<HexKey, User>()

    private fun user(pubKey: HexKey): User = userCache.getOrPut(pubKey) { User(pubKey) { addr -> Note(addr.toValue()) } }

    /** Builds a kind-1018 response Note authored by [pubKey] choosing [option] at [createdAt]. */
    private fun responseNote(
        id: HexKey,
        pubKey: HexKey,
        option: String,
        createdAt: Long,
    ): Note {
        val event =
            PollResponseEvent(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                tags =
                    arrayOf(
                        arrayOf("e", pollId),
                        arrayOf("response", option),
                    ),
                content = "",
                sig = "0".repeat(128),
            )
        val note = Note(id)
        note.loadEvent(event, user(pubKey), emptyList())
        return note
    }

    @Test
    fun latestVoteWinsDedup() {
        val cache = PollResponsesCache()
        val voter = "b".repeat(64)

        // Same voter votes twice; the later-timestamp response must win.
        cache.addResponse(responseNote("1".repeat(64), voter, option = "yes", createdAt = 100))
        cache.addResponse(responseNote("2".repeat(64), voter, option = "no", createdAt = 200))

        val tally = cache.responses.value

        // Exactly one vote counted for this user.
        assertEquals(1, tally.totalVotes())
        // The winning option is the newer one.
        assertEquals("no", tally.winning())
        // Old option carries no voters.
        assertTrue(tally.tally["yes"].isNullOrEmpty())
    }

    @Test
    fun tallyPercentReflectsVoteShare() {
        val cache = PollResponsesCache()
        val forKey = "0".repeat(64)

        cache.addResponse(responseNote("1".repeat(64), "b".repeat(64), option = "yes", createdAt = 10))
        cache.addResponse(responseNote("2".repeat(64), "c".repeat(64), option = "yes", createdAt = 10))
        cache.addResponse(responseNote("3".repeat(64), "d".repeat(64), option = "no", createdAt = 10))

        val yes = cache.currentTally("yes", forKey, emptySet())
        val no = cache.currentTally("no", forKey, emptySet())

        assertEquals(2f / 3f, yes.percent)
        assertEquals(1f / 3f, no.percent)
        assertTrue(yes.isWinning)
        assertFalse(no.isWinning)
    }

    @Test
    fun wotPrioritySortOrdersUsers() {
        val cache = PollResponsesCache()
        val forKey = "f".repeat(64) // the logged-in user
        val followed = "e".repeat(64)
        val stranger = "d".repeat(64)

        // Three voters all pick "yes": self, a followed user, and a stranger.
        cache.addResponse(responseNote("1".repeat(64), forKey, option = "yes", createdAt = 10))
        cache.addResponse(responseNote("2".repeat(64), stranger, option = "yes", createdAt = 10))
        cache.addResponse(responseNote("3".repeat(64), followed, option = "yes", createdAt = 10))

        val tally = cache.currentTally("yes", forKey, priorityAccounts = setOf(followed))
        val order = tally.users.map { it.pubkeyHex }

        // Self first, then followed (WoT priority), then the stranger.
        assertEquals(listOf(forKey, followed, stranger), order)
    }

    @Test
    fun hasPubKeyVotedTracksVoter() {
        val cache = PollResponsesCache()
        val voter = "b".repeat(64)
        val other = "c".repeat(64)

        cache.addResponse(responseNote("1".repeat(64), voter, option = "yes", createdAt = 10))

        assertTrue(cache.hasPubKeyVoted(user(voter)))
        assertFalse(cache.hasPubKeyVoted(user(other)))
    }

    @Test
    fun addResponseIsIdempotentForSameNote() {
        val cache = PollResponsesCache()
        val note = responseNote("1".repeat(64), "b".repeat(64), option = "yes", createdAt = 10)

        cache.addResponse(note)
        cache.addResponse(note) // relay echo of the same note must not double-count

        assertEquals(1, cache.responses.value.totalVotes())
    }
}
