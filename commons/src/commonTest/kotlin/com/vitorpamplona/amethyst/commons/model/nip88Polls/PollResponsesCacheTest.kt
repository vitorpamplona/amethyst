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
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
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
        assertEquals(1, tally.totalVoters())
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

        assertEquals(1, cache.responses.value.totalVoters())
    }

    // ---------------------------------------------------------------------------------------
    // Poll-aware tally: everything below needs the kind-1068 rules, which ResponseTally only has
    // once updatePolicy has run.
    // ---------------------------------------------------------------------------------------

    /** Builds a response casting several codes at once, to exercise the per-type accept rules. */
    private fun multiResponseNote(
        id: HexKey,
        pubKey: HexKey,
        options: List<String>,
        createdAt: Long,
    ): Note {
        val event =
            PollResponseEvent(
                id = id,
                pubKey = pubKey,
                createdAt = createdAt,
                tags = arrayOf(arrayOf("e", pollId)) + options.map { arrayOf("response", it) },
                content = "",
                sig = "0".repeat(128),
            )
        val note = Note(id)
        note.loadEvent(event, user(pubKey), emptyList())
        return note
    }

    private fun policy(
        type: PollType,
        codes: Set<String> = setOf("yes", "no"),
        createdAt: Long = 0,
        endsAt: Long? = null,
    ) = PollTallyPolicy(validCodes = codes, type = type, createdAt = createdAt, endsAt = endsAt)

    @Test
    fun singleChoiceCountsOnlyTheFirstResponseTag() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        // A client that emits one response tag per option must not land in every bucket.
        cache.addResponse(multiResponseNote("1".repeat(64), "b".repeat(64), listOf("yes", "no"), createdAt = 10))

        val tally = cache.responses.value
        assertEquals(1, tally.totalVoters())
        assertEquals(1, tally.totalSelections())
        assertEquals(1, tally.tally["yes"]?.size)
        assertTrue(tally.tally["no"].isNullOrEmpty())
    }

    @Test
    fun singleChoiceRejectsWhenTheFirstTagIsNotAnOption() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        // NIP-88 says the first tag *is* the response — so an invalid first tag is an invalid vote,
        // not an invitation to look for a valid one further down.
        cache.addResponse(multiResponseNote("1".repeat(64), "b".repeat(64), listOf("bogus", "yes"), createdAt = 10))

        val tally = cache.responses.value
        assertEquals(0, tally.totalVoters())
        assertEquals(1, tally.ignoredVotes)
    }

    @Test
    fun multiChoiceCountsEveryValidCodeOncePerVoter() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.MULTI_CHOICE))

        cache.addResponse(multiResponseNote("1".repeat(64), "b".repeat(64), listOf("yes", "no", "yes"), createdAt = 10))

        val tally = cache.responses.value
        // One person, two selections: the duplicate "yes" collapses.
        assertEquals(1, tally.totalVoters())
        assertEquals(2, tally.totalSelections())
    }

    @Test
    fun multiChoicePercentagesAreShareOfVotersNotSelections() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.MULTI_CHOICE))

        // Two voters; one picks both options, the other picks only "yes".
        cache.addResponse(multiResponseNote("1".repeat(64), "b".repeat(64), listOf("yes", "no"), createdAt = 10))
        cache.addResponse(multiResponseNote("2".repeat(64), "c".repeat(64), listOf("yes"), createdAt = 10))

        val tally = cache.responses.value
        assertEquals(2, tally.totalVoters())
        assertEquals(3, tally.totalSelections())

        val forKey = "0".repeat(64)
        // 2 of 2 people want "yes", 1 of 2 want "no" — bars sum past 100% on purpose.
        assertEquals(1.0f, cache.currentTally("yes", forKey, emptySet()).percent)
        assertEquals(0.5f, cache.currentTally("no", forKey, emptySet()).percent)
    }

    @Test
    fun votesOutsideThePollWindowAreExcluded() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE, createdAt = 100, endsAt = 200))

        val voter = "b".repeat(64)
        cache.addResponse(responseNote("1".repeat(64), voter, option = "yes", createdAt = 150))
        // Same voter again after the deadline: a later timestamp must not overwrite a valid vote.
        cache.addResponse(responseNote("2".repeat(64), voter, option = "no", createdAt = 500))
        // And a vote from before the poll existed is not a vote either.
        cache.addResponse(responseNote("3".repeat(64), "c".repeat(64), option = "no", createdAt = 50))

        val tally = cache.responses.value
        assertEquals(1, tally.totalVoters())
        assertEquals("yes", tally.winning())
        assertEquals(2, tally.lateVotes)
    }

    @Test
    fun unknownOptionCodesDoNotDragDownPercentages() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        cache.addResponse(responseNote("1".repeat(64), "b".repeat(64), option = "yes", createdAt = 10))
        cache.addResponse(responseNote("2".repeat(64), "c".repeat(64), option = "spam", createdAt = 10))

        val tally = cache.responses.value
        assertEquals(1, tally.totalVoters())
        assertEquals(1, tally.ignoredVotes)
        assertTrue(tally.tally["spam"].isNullOrEmpty())
        // The one real vote is 100% of the poll, not 50%.
        assertEquals(1.0f, cache.currentTally("yes", "0".repeat(64), emptySet()).percent)
    }

    @Test
    fun policyArrivingAfterResponsesRecomputesTheTally() {
        val cache = PollResponsesCache()

        // Responses regularly beat their poll to the client. Until the poll lands the tally is
        // permissive, so the bogus code counts.
        cache.addResponse(responseNote("1".repeat(64), "b".repeat(64), option = "yes", createdAt = 10))
        cache.addResponse(responseNote("2".repeat(64), "c".repeat(64), option = "spam", createdAt = 10))
        assertEquals(2, cache.responses.value.totalVoters())

        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        assertEquals(1, cache.responses.value.totalVoters())
    }

    @Test
    fun updatePolicyIsIdempotent() {
        val cache = PollResponsesCache()
        cache.addResponse(responseNote("1".repeat(64), "b".repeat(64), option = "yes", createdAt = 10))

        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))
        val first = cache.responses.value
        // Same poll re-delivered by another relay must not churn the tally identity.
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        assertSame(first, cache.responses.value)
    }

    @Test
    fun voterWhoseVoteWasIgnoredCanStillVote() {
        val cache = PollResponsesCache()
        cache.updatePolicy(policy(PollType.SINGLE_CHOICE))

        val voter = "b".repeat(64)
        cache.addResponse(responseNote("1".repeat(64), voter, option = "spam", createdAt = 10))

        // They responded, but cast nothing countable — the card should still offer them the
        // controls rather than showing results for a vote that does not exist.
        assertFalse(cache.hasPubKeyVoted(user(voter)))
    }
}
