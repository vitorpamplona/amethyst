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
package com.vitorpamplona.quartz.experimental.ballots

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.Kind38000Fixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class BallotEventTest {
    private fun parse(json: String) = assertIs<BallotEvent>(Event.fromJson(json))

    private fun ballotWith(
        content: String,
        vararg extraTags: Array<String>,
    ) = BallotEvent("0".repeat(64), "1".repeat(64), 1L, arrayOf(arrayOf("election", "e1"), *extraTags), content, "")

    @Test
    fun responsesShape() {
        val event = parse(Kind38000Fixtures.BALLOT_RESPONSES)

        assertEquals("sec06-feedback-1774311582", event.election())
        val answers = event.answers()
        assertEquals(10, answers.size)
        assertEquals(BallotAnswer("q1", "Yes"), answers[0])
        // Numbers read as text.
        assertEquals(BallotAnswer("q3", "5"), answers[2])
        assertEquals(BallotAnswer("q9", "NA"), answers[8])
        assertNull(event.proofHash())
    }

    @Test
    fun responsesWithProofHashTag() {
        val event = parse(Kind38000Fixtures.BALLOT_RESPONSES_PROOF_HASH)

        assertEquals("spring-2026-council", event.election())
        assertEquals(listOf(BallotAnswer("proposal_approval", "Yes")), event.answers())
        assertEquals("361689b5ccd44f35eebba995129d8f2b00dd369aca4c51b55f00065b2b477ddd", event.proofHash())
        assertEquals("spring-2026-council\nYes", event.indexableContent())
    }

    @Test
    fun ballotObjectShape() {
        val event = parse(Kind38000Fixtures.BALLOT_OBJECT)

        assertEquals("spring-2026-council", event.election())
        assertEquals(
            listOf(
                BallotAnswer("funding_priority", "community-grants"),
                BallotAnswer("audit_policy", "every-election"),
            ),
            event.answers(),
        )
        assertEquals("bb64227a0b57fa6af765881b0952fe9d01a3a18faf52a3e34eeb7a896ee36b95", event.proofHash())
    }

    @Test
    fun voteChoiceShape() {
        val event = parse(Kind38000Fixtures.BALLOT_VOTE_CHOICE)

        assertEquals("election1", event.election())
        assertEquals(listOf(BallotAnswer(BallotEvent.VOTE_QUESTION, "yes")), event.answers())
        assertNull(event.proofHash())
    }

    @Test
    fun proofHashFallsBackToContent() {
        assertEquals("abc", ballotWith("""{"proof_hash":"abc","vote_choice":"no"}""").proofHash())
        assertEquals("tag", ballotWith("""{"proof_hash":"abc"}""", arrayOf("proof_hash", "tag")).proofHash())
        assertEquals("dash", ballotWith("""{"proof_hash":"abc"}""", arrayOf("proof_hash", "underscore"), arrayOf("proof-hash", "dash")).proofHash())
    }

    @Test
    fun nonScalarValuesAreSkipped() {
        val responses =
            ballotWith(
                """{"responses":[1,{"question_id":"q","value":{"x":1}},{"question_id":"q2","value":null},{"value":"orphan"},""" +
                    """{"question_id":"q3","value":true},{"question_id":4,"value":2.5},{"question_id":"q5","value":" "}]}""",
            )
        assertEquals(listOf(BallotAnswer("q3", "true"), BallotAnswer("4", "2.5")), responses.answers())

        val ballotObject = ballotWith("""{"ballot":{"a":[1],"b":2,"c":null,"d":{"x":"y"},"e":"E"}}""")
        assertEquals(listOf(BallotAnswer("b", "2"), BallotAnswer("e", "E")), ballotObject.answers())

        assertEquals(emptyList(), ballotWith("""{"vote_choice":{"x":1}}""").answers())
    }

    @Test
    fun junkJsonNeverThrows() {
        listOf("", "not json", "{", "[]", "null", "{\"responses\":", "{\"responses\": \"x\", \"ballot\": 3}", "{\"a\":".repeat(5000)).forEach {
            val event = ballotWith(it)
            assertEquals(emptyList(), event.answers())
            assertNull(event.proofHash())
            assertEquals("e1", event.indexableContent())
        }
    }

    @Test
    fun visitorAgreesWithIndexedContentAndStops() {
        listOf(Kind38000Fixtures.BALLOT_RESPONSES, Kind38000Fixtures.BALLOT_OBJECT, Kind38000Fixtures.BALLOT_VOTE_CHOICE).forEach { json ->
            val event = parse(json)
            val visited = mutableListOf<String>()
            event.forEachIndexableField {
                if (it != null) visited.add(it)
                true
            }
            assertEquals(event.indexableContent(), visited.joinToString(event.indexableSeparator()))

            var seen = 0
            event.forEachIndexableField {
                seen++
                false
            }
            assertEquals(1, seen)
        }
    }
}
