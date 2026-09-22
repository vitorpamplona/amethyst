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
package com.vitorpamplona.quartz.nip88Polls

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent
import com.vitorpamplona.quartz.nip88Polls.poll.endsAt
import com.vitorpamplona.quartz.nip88Polls.poll.options
import com.vitorpamplona.quartz.nip88Polls.poll.pollType
import com.vitorpamplona.quartz.nip88Polls.poll.relays
import com.vitorpamplona.quartz.nip88Polls.poll.tags.EndsAtTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.OptionTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollType
import com.vitorpamplona.quartz.nip88Polls.poll.tags.PollTypeTag
import com.vitorpamplona.quartz.nip88Polls.poll.tags.RelayTag
import com.vitorpamplona.quartz.nip88Polls.response.PollResponseEvent
import com.vitorpamplona.quartz.nip88Polls.response.poll
import com.vitorpamplona.quartz.nip88Polls.response.responses
import com.vitorpamplona.quartz.nip88Polls.response.tags.PollTag
import com.vitorpamplona.quartz.nip88Polls.response.tags.ResponseTag
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The NIP-88 tag layer against the spec's own example events.
 *
 * Both halves are checked, because a parser and a serializer can be wrong in opposite directions
 * and still agree with each other: the examples are parsed field by field, and what the builders
 * emit is compared to the literal tag arrays the spec prints.
 */
class Nip88PollTagsTest {
    // Verbatim from NIP-88, "Poll Event".
    private val specPoll =
        """{
          "content": "Pineapple on pizza",
          "created_at": 1719888496,
          "id": "9d1b6b9562e66f2ecf35eb0a3c2decc736c47fddb13d6fb8f87185a153ea3634",
          "kind": 1068,
          "pubkey": "dee45a23c4f1d93f3a2043650c5081e4ac14a778e0acbef03de3768e4f81ac7b",
          "sig": "7fa93bf3c430eaef784b0dacc217d3cd5eff1c520e7ef5d961381bc0f014dde6286618048d924808e54d1be03f2f2c2f0f8b5c9c2082a4480caf45a565ca9797",
          "tags": [
            ["option", "qj518h583", "Yay"],
            ["option", "gga6cdnqj", "Nay"],
            ["relay", "wss://relay.damus.io"],
            ["relay", "wss://nos.lol"],
            ["polltype", "singlechoice"],
            ["endsAt", "1719988496"]
          ]
        }"""

    // Verbatim from NIP-88, "Responses".
    private val specResponse =
        """{
          "content": "",
          "created_at": 1720097117,
          "id": "60a005e32e9596c3f544a841a9bc4e46d3020ca3650d6a739c95c1568e33f6d8",
          "kind": 1018,
          "pubkey": "1bc70a0148b3f316da33fe7e89f23e3e71ac4ff998027ec712b905cd24f6a411",
          "sig": "30071a633c65db8f3a075c7a8de757fbd8ce65e3607f4ba287fe6d7fbf839a380f94ff4e826fbba593f6faaa13683b7ea9114ade140720ecf4927010ebf3e44f",
          "tags": [
            ["e", "1fc80cf813f1af33d5a435862b7ef7fb96b47e68a48f1abcadf8081f5a545550"],
            ["response", "gga6cdnqj"],
            ["response", "m3agjsdq1"]
          ]
        }"""

    @Test
    fun theSpecsPollExampleParsesFieldForField() {
        val event = Event.fromJson(specPoll)
        assertTrue(event is PollEvent)
        val poll = event as PollEvent

        assertEquals("Pineapple on pizza", poll.content)
        assertEquals(listOf("qj518h583" to "Yay", "gga6cdnqj" to "Nay"), poll.options().map { it.code to it.label })
        assertEquals(listOf("wss://relay.damus.io/", "wss://nos.lol/"), poll.relays().map { it.url })
        assertEquals(PollType.SINGLE_CHOICE, poll.pollType())
        assertEquals(1719988496L, poll.endsAt())
    }

    @Test
    fun theSpecsResponseExampleParsesFieldForField() {
        val event = Event.fromJson(specResponse)
        assertTrue(event is PollResponseEvent)
        val response = event as PollResponseEvent

        assertEquals("1fc80cf813f1af33d5a435862b7ef7fb96b47e68a48f1abcadf8081f5a545550", response.poll()?.eventId)
        // Both tags are read. Which of them counts is the polltype's business, not the parser's.
        assertEquals(listOf("gga6cdnqj", "m3agjsdq1"), response.responses())
    }

    @Test
    fun aPollWithNoPollTypeIsSingleChoice() {
        // "Polls that do not have a polltype should be considered a singlechoice poll."
        val untyped = Event.fromJson(specPoll.replace("""["polltype", "singlechoice"],""", ""))
        assertEquals(PollType.SINGLE_CHOICE, (untyped as PollEvent).pollType())

        // An unrecognised one falls back the same way rather than being treated as multi-choice,
        // which would let one response cast several votes.
        val unknown = Event.fromJson(specPoll.replace("singlechoice", "rankedchoice"))
        assertEquals(PollType.SINGLE_CHOICE, (unknown as PollEvent).pollType())
    }

    @Test
    fun aWellFormedPollTypeTagIsRecognisedAsOne() {
        // isTag and parse have to agree: the spec's tag has exactly two elements, and a stricter
        // isTag makes `tags.any(PollTypeTag::isTag)` answer "no poll declares a type".
        assertTrue(PollTypeTag.isTag(arrayOf("polltype", "singlechoice")))
        assertTrue(PollTypeTag.isTag(arrayOf("polltype", "multiplechoice")))
        assertFalse(PollTypeTag.isTag(arrayOf("polltype")))
        assertFalse(PollTypeTag.isTag(arrayOf("endsAt", "1719988496")))
    }

    @Test
    fun anOptionNeedsBothAnIdAndALabel() {
        assertEquals("qj518h583" to "Yay", OptionTag.parse(arrayOf("option", "qj518h583", "Yay"))?.let { it.code to it.label })

        // NIP-88 defines an option as an id "followed by an option label field", and an option
        // nobody can read is not a choice anyone can make — a blank tappable row would collect
        // votes for a question the voter never saw. Both fields are required, and isTag agrees
        // with parse about that.
        assertNull(OptionTag.parse(arrayOf("option", "qj518h583", "")))
        assertNull(OptionTag.parse(arrayOf("option", "qj518h583")))
        assertNull(OptionTag.parse(arrayOf("option", "", "Yay")))
        assertNull(OptionTag.parse(arrayOf("option")))
        assertNull(OptionTag.parse(arrayOf("response", "qj518h583", "Yay")))

        assertTrue(OptionTag.isTag(arrayOf("option", "qj518h583", "Yay")))
        assertFalse(OptionTag.isTag(arrayOf("option", "qj518h583", "")))
        assertFalse(OptionTag.isTag(arrayOf("option", "qj518h583")))
    }

    @Test
    fun theBuildersEmitTheTagsTheSpecPrints() {
        val template =
            PollEvent.build(
                description = "Pineapple on pizza",
                options = listOf(OptionTag("qj518h583", "Yay"), OptionTag("gga6cdnqj", "Nay")),
                endsAt = 1719988496L,
                relays = listOfNotNull(RelayUrlNormalizer.normalizeOrNull("wss://relay.damus.io")),
                pollType = PollType.SINGLE_CHOICE,
            )

        assertEquals("Pineapple on pizza", template.content)
        assertEquals(PollEvent.KIND, template.kind)
        val tags = template.tags.associateBy({ it[0] }, { it.toList() })
        assertContentEquals(listOf("polltype", "singlechoice"), tags["polltype"])
        assertContentEquals(listOf("endsAt", "1719988496"), tags["endsAt"])
        assertContentEquals(listOf("relay", "wss://relay.damus.io/"), tags["relay"])
        assertContentEquals(
            listOf(listOf("option", "qj518h583", "Yay"), listOf("option", "gga6cdnqj", "Nay")),
            template.tags.filter { it[0] == "option" }.map { it.toList() },
        )
    }

    @Test
    fun aSingleChoiceAnswerCanOnlyBeOneTag() {
        val poll = EventHintBundle(Event.fromJson(specPoll) as PollEvent, null)

        val template = PollResponseEvent.buildSingleChoice(poll, "gga6cdnqj")
        val responses = template.tags.filter { it[0] == "response" }

        // The spec reads the FIRST response tag as the answer, so a single-choice event that
        // carries more than one is ambiguous by construction. The typed builder cannot emit one.
        assertEquals(listOf(listOf("response", "gga6cdnqj")), responses.map { it.toList() })
        assertEquals(PollResponseEvent.KIND, template.kind)
    }

    @Test
    fun aMultipleChoiceAnswerWritesEveryCodeItWasGiven() {
        val poll = EventHintBundle(Event.fromJson(specPoll) as PollEvent, null)

        val template = PollResponseEvent.buildMultipleChoice(poll, setOf("qj518h583", "gga6cdnqj"))
        val codes = template.tags.filter { it[0] == "response" }.map { it[1] }

        assertEquals(setOf("qj518h583", "gga6cdnqj"), codes.toSet())
    }

    @Test
    fun theTagNamesAreSpelledTheWayTheSpecSpellsThem() {
        // `endsAt` is camelCase in NIP-88 while almost every other nostr tag is lowercase, so it
        // is the one most likely to be "corrected" into something no other client reads.
        assertEquals("endsAt", EndsAtTag.TAG_NAME)
        assertEquals("polltype", PollTypeTag.TAG_NAME)
        assertEquals("option", OptionTag.TAG_NAME)
        assertEquals("relay", RelayTag.TAG_NAME)
        assertEquals("response", ResponseTag.TAG_NAME)
        assertEquals("e", PollTag.TAG_NAME)
        assertEquals("singlechoice", PollType.SINGLE_CHOICE.code)
        assertEquals("multiplechoice", PollType.MULTI_CHOICE.code)
    }
}
