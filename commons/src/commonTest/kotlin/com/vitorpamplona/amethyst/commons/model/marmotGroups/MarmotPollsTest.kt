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
package com.vitorpamplona.amethyst.commons.model.marmotGroups

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The MDK 0.14 poll profile (`crates/traits/src/polls.rs`): the wire shape of a
 * poll and a vote, and the tally rules two clients have to agree on to show a
 * group the same result.
 */
class MarmotPollsTest {
    private val alice = "a".repeat(64)
    private val bob = "b".repeat(64)
    private val carol = "c".repeat(64)

    private fun event(
        id: String,
        pubKey: String,
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ) = Event(id, pubKey, createdAt, kind, tags, content, "")

    private fun poll(
        type: MarmotPolls.PollType = MarmotPolls.PollType.SINGLE_CHOICE,
        endsAt: Long? = 200,
    ): Event {
        val tags = MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee", "Water"), type, endsAt)!!
        return event("1".repeat(64), alice, 100, MarmotPolls.KIND_POLL, tags, "Drink?")
    }

    private fun vote(
        id: Char,
        voter: String,
        createdAt: Long,
        pollId: String,
        vararg options: String,
    ) = event(id.toString().repeat(64), voter, createdAt, MarmotPolls.KIND_POLL_RESPONSE, MarmotPolls.responseTags(pollId, options.toList())!!, "")

    @Test
    fun pollTagsMatchMdkShape() {
        val tags = MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee"), MarmotPolls.PollType.MULTIPLE_CHOICE, 200)!!
        assertContentEquals(arrayOf("option", "0", "Tea"), tags[0])
        assertContentEquals(arrayOf("option", "1", "Coffee"), tags[1])
        assertContentEquals(arrayOf("polltype", "multiplechoice"), tags[2])
        assertContentEquals(arrayOf("endsAt", "200"), tags[3])
        assertEquals(4, tags.size)
    }

    @Test
    fun pollTagsRejectWhatMdkRejects() {
        assertNull(MarmotPolls.pollTags(100, " Drink?", listOf("Tea", "Coffee"), MarmotPolls.PollType.SINGLE_CHOICE, null))
        assertNull(MarmotPolls.pollTags(100, "Drink?", listOf("Tea"), MarmotPolls.PollType.SINGLE_CHOICE, null))
        assertNull(MarmotPolls.pollTags(100, "Drink?", List(11) { "o$it" }, MarmotPolls.PollType.SINGLE_CHOICE, null))
        assertNull(MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Co‮ffee"), MarmotPolls.PollType.SINGLE_CHOICE, null))
        assertNull(MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee"), MarmotPolls.PollType.SINGLE_CHOICE, 100))
        assertNull(MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee"), MarmotPolls.PollType.SINGLE_CHOICE, 100 + MarmotPolls.MAX_LIFETIME_SECONDS + 1))
        assertNotNull(MarmotPolls.pollTags(100, "Drink?", listOf("Tea", "Coffee"), MarmotPolls.PollType.SINGLE_CHOICE, 100 + MarmotPolls.MAX_LIFETIME_SECONDS))
    }

    @Test
    fun voteTagsAreOneBareETagThenResponses() {
        val tags = MarmotPolls.responseTags("1".repeat(64), listOf("0", "2"))!!
        assertContentEquals(arrayOf("e", "1".repeat(64)), tags[0])
        assertContentEquals(arrayOf("response", "0"), tags[1])
        assertContentEquals(arrayOf("response", "2"), tags[2])
        assertNull(MarmotPolls.responseTags("1".repeat(64), emptyList()), "an empty selection is not an unvote")
        assertNull(MarmotPolls.responseTags("1".repeat(64), listOf("0", "0")))
        assertNull(MarmotPolls.responseTags("A".repeat(64), listOf("0")), "MDK requires a lowercase event id")
    }

    @Test
    fun parsePollIgnoresRelayHintsButKeepsKnownTagsStrict() {
        val base = poll()
        val withRelay = event(base.id, alice, 100, MarmotPolls.KIND_POLL, base.tags + arrayOf(arrayOf("relay", "wss://x"), arrayOf("future", "v")), "Drink?")
        assertNotNull(MarmotPolls.parsePoll(withRelay))

        val shortOption = event(base.id, alice, 100, MarmotPolls.KIND_POLL, arrayOf(arrayOf("option", "0"), arrayOf("option", "1", "B")), "Q")
        assertNull(MarmotPolls.parsePoll(shortOption))

        val twoTypes = event(base.id, alice, 100, MarmotPolls.KIND_POLL, base.tags + arrayOf(arrayOf("polltype", "singlechoice")), "Drink?")
        assertNull(MarmotPolls.parsePoll(twoTypes))

        val defaulted = event(base.id, alice, 100, MarmotPolls.KIND_POLL, arrayOf(arrayOf("option", "a", "A"), arrayOf("option", "b", "B")), "Q")
        assertEquals(MarmotPolls.PollType.SINGLE_CHOICE, MarmotPolls.parsePoll(defaulted)?.type)
    }

    @Test
    fun newestValidVotePerVoterCounts() {
        val p = poll()
        val definition = MarmotPolls.parsePoll(p)!!
        val votes =
            listOf(
                vote('2', bob, 110, p.id, "0"),
                vote('3', bob, 120, p.id, "1"),
                // Malformed for single choice: skipped, so bob's 120 vote stands.
                vote('4', bob, 130, p.id, "0", "1"),
                vote('5', carol, 150, p.id, "1"),
                // After the deadline: not a vote.
                vote('6', alice, 250, p.id, "2"),
            )
        val projection = MarmotPolls.project(p, definition, votes, bob)
        assertEquals(listOf(0, 2, 0), projection.options.map { it.votes })
        assertEquals(2, projection.participants)
        assertEquals(listOf("1"), projection.localSelection)
    }

    @Test
    fun unknownOptionVoidsTheWholeResponse() {
        val p = poll(MarmotPolls.PollType.MULTIPLE_CHOICE)
        val definition = MarmotPolls.parsePoll(p)!!
        val projection = MarmotPolls.project(p, definition, listOf(vote('2', bob, 110, p.id, "0", "9")), null)
        assertEquals(0, projection.participants)
    }

    @Test
    fun tiesBreakOnEventId() {
        val p = poll()
        val definition = MarmotPolls.parsePoll(p)!!
        val projection = MarmotPolls.project(p, definition, listOf(vote('2', bob, 110, p.id, "0"), vote('3', bob, 110, p.id, "2")), null)
        assertEquals(listOf(0, 0, 1), projection.options.map { it.votes })
    }

    @Test
    fun voteContentMustBeEmpty() {
        val p = poll()
        val bad = event("2".repeat(64), bob, 110, MarmotPolls.KIND_POLL_RESPONSE, MarmotPolls.responseTags(p.id, listOf("0"))!!, "x")
        assertNull(MarmotPolls.parseResponse(bad))
        val hinted = event("2".repeat(64), bob, 110, MarmotPolls.KIND_POLL_RESPONSE, arrayOf(arrayOf("e", p.id, "wss://x"), arrayOf("response", "0")), "")
        assertNull(MarmotPolls.parseResponse(hinted), "MDK rejects a relay hint on the vote's e tag")
    }

    @Test
    fun openUntilTheDeadlineInclusive() {
        val definition = MarmotPolls.parsePoll(poll())!!
        assertTrue(definition.isOpen(200))
        assertTrue(!definition.isOpen(201))
    }
}
