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
package com.vitorpamplona.amethyst.commons.observer

import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ObserverRoundupsTest {
    private val reader = key(0)
    private val alice = key(1)
    private val bob = key(2)
    private val carol = key(3)
    private val dave = key(4)

    private var nextId = 100

    private fun key(n: Int) = n.toString(16).padStart(64, '0')

    private fun event(
        author: String,
        content: String,
        kind: Int = 1,
        tags: Array<Array<String>> = emptyArray(),
        createdAt: Long = 1_000,
    ) = Event(key(nextId++), author, createdAt, kind, tags, content, "")

    private fun story(
        event: Event,
        desk: ObserverDesk = ObserverDesk.NOTES,
        replies: Int = 0,
    ) = ObserverEditor.story(event, desk, ObserverEngagement(replies = replies), mapOf(alice to "Alice", bob to "Bob", carol to "Carol", dave to "Dave"), 400)

    private fun reply(
        author: String,
        to: Event,
        content: String,
        createdAt: Long = 2_000,
    ) = event(author, content, tags = arrayOf(arrayOf("e", to.id, "", "reply")), createdAt = createdAt)

    @Test
    fun aConversationIsTheStoryAndItsTrustedReplies() {
        val root = event(alice, "Should relays charge for writes? Here is my case for it.")
        val quiet = event(bob, "A post nobody answered, which is not a conversation.")
        val replies =
            listOf(
                reply(bob, root, "Paid writes kill the open relay model entirely.", createdAt = 2_000),
                reply(bob, root, "And another thing about paid writes.", createdAt = 2_100),
                reply(carol, root, "Agreed, spam is the bigger cost here.", createdAt = 2_200),
                reply(dave, root, "+1", createdAt = 2_300),
                // The author answering themselves is their thread, not the conversation.
                reply(alice, root, "To be clear, I mean only for large blobs.", createdAt = 2_400),
            )
        val roundups =
            ObserverRoundups.conversations(
                listOf(story(root, replies = 4), story(quiet)),
                mapOf(root.id to replies),
                mapOf(bob to "Bob", carol to "Carol"),
            )

        val talk = roundups.single()
        assertEquals(ObserverRoundupKind.CONVERSATION, talk.kind)
        assertEquals(root.id, talk.anchor?.event?.id)
        // Three different people replied; Dave's "+1" counts as a person but is not quoted.
        assertEquals(3, talk.people)
        assertEquals(listOf("Bob" to "Paid writes kill the open relay model entirely.", "Carol" to "Agreed, spam is the bigger cost here."), talk.lines.map { it.byline to it.text })
    }

    @Test
    fun oneReplyIsNotAConversation() {
        val root = event(alice, "A story with a single reply under it, from one person.")
        val replies = listOf(reply(bob, root, "Only one person had something to say."))
        assertTrue(ObserverRoundups.conversations(listOf(story(root)), mapOf(root.id to replies), emptyMap()).isEmpty())
    }

    @Test
    fun aTopicIsTheBestLineFromEachPersonWhoUsedTheTag() {
        val tag = arrayOf(arrayOf("t", "bitcoin"))
        val a1 = story(event(alice, "Alice's lesser bitcoin post.", tags = tag), replies = 1)
        val a2 = story(event(alice, "Alice's best bitcoin post.", tags = tag), replies = 5)
        val b = story(event(bob, "Bob on bitcoin.", tags = tag), replies = 2)
        val c = story(event(carol, "Carol on bitcoin.", tags = tag))
        val off = story(event(dave, "Dave on something else.", tags = arrayOf(arrayOf("t", "gardening"))))

        val roundups = ObserverRoundups.topics(listOf(ObserverTrend("bitcoin", 3), ObserverTrend("gardening", 1)), listOf(a1, a2, b, c, off))

        val topic = roundups.single()
        assertEquals("bitcoin", topic.topic)
        assertEquals(3, topic.people)
        assertEquals(listOf("Alice's best bitcoin post.", "Bob on bitcoin.", "Carol on bitcoin."), topic.lines.map { it.text })
    }

    @Test
    fun liveRunsByAudience() {
        fun stream(
            author: String,
            title: String,
            watching: Int,
        ) = story(event(author, "", kind = 30311, tags = arrayOf(arrayOf("title", title), arrayOf("status", "live"), arrayOf("current_participants", "$watching"))), ObserverDesk.LIVE)

        val roundup = ObserverRoundups.live(listOf(stream(alice, "Small", 2), stream(bob, "Big", 40), stream(carol, "Medium", 9)))
        assertEquals(listOf("Big", "Medium", "Small"), roundup?.lines?.map { it.text })
        assertEquals(ObserverDetail.Watching(40), roundup?.lines?.first()?.detail)
        assertNull(ObserverRoundups.live(listOf(stream(alice, "Alone", 3))))
    }

    @Test
    fun upcomingIsTheNextSevenDaysSoonestFirst() {
        val now = 1_791_072_000L // 2026-10-04

        fun listing(
            author: String,
            title: String,
            start: String,
            kind: Int = 31923,
        ) = story(event(author, "", kind = kind, tags = arrayOf(arrayOf("title", title), arrayOf("start", start))), ObserverDesk.CALENDAR)

        val roundup =
            ObserverRoundups.upcoming(
                listOf(
                    listing(alice, "Past", (now - 3 * 86_400).toString()),
                    listing(bob, "Thursday meetup", (now + 4 * 86_400).toString()),
                    listing(carol, "Tomorrow, all day", "2026-10-05", kind = 31922),
                    listing(dave, "Next month", (now + 30 * 86_400).toString()),
                ),
                now,
            )
        assertEquals(listOf("Tomorrow, all day", "Thursday meetup"), roundup?.lines?.map { it.text })
        assertEquals(2, roundup?.people)
    }

    @Test
    fun releasesGatherAppsAndCode() {
        val app = story(event(alice, "", kind = 32267, tags = arrayOf(arrayOf("name", "Amethyst"))), ObserverDesk.APPS)
        val repo = story(event(bob, "", kind = 30617, tags = arrayOf(arrayOf("name", "quartz"))), ObserverDesk.GIT)
        assertEquals(listOf("Amethyst", "quartz"), ObserverRoundups.releases(listOf(app, repo))?.lines?.map { it.text })
        assertNull(ObserverRoundups.releases(listOf(app)))

        // One store publishing for many developers gets two lines, not the whole card.
        val store = (1..5).map { story(event(carol, "", kind = 32267, tags = arrayOf(arrayOf("name", "App $it"))), ObserverDesk.APPS) }
        assertEquals(listOf("App 1", "App 2", "Amethyst"), ObserverRoundups.releases(store + app)?.lines?.map { it.text })
    }

    @Test
    fun theEditorPlacesRoundups() {
        val root = event(alice, "Should relays charge for writes? Here is my long case for it.")
        val replies = listOf(reply(bob, root, "Paid writes kill the open relay model."), reply(carol, root, "Spam is the bigger cost by far."))
        val streams =
            listOf(alice, bob).map {
                event(it, "", kind = 30311, tags = arrayOf(arrayOf("title", "Stream by $it"), arrayOf("status", "live"), arrayOf("current_participants", "3")))
            }
        val edition =
            ObserverEditor.edit(
                ObserverCorpus(
                    reader,
                    0,
                    86_400,
                    mapOf(ObserverDesk.NOTES to listOf(root), ObserverDesk.LIVE to streams),
                    mapOf(root.id to ObserverEngagement(replies = 2)),
                    emptyMap(),
                    null,
                    replies = mapOf(root.id to replies),
                ),
            )

        assertEquals(listOf(ObserverRoundupKind.CONVERSATION), edition.roundups.map { it.kind })
        assertEquals(
            ObserverRoundupKind.LIVE,
            edition.sections
                .single { it.kind == ObserverSectionKind.LIVE }
                .roundup
                ?.kind,
        )
    }
}
