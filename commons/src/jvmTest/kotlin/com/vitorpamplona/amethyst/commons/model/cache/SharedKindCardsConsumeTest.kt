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
package com.vitorpamplona.amethyst.commons.model.cache

import com.vitorpamplona.quartz.experimental.kanban.board.KanbanBoardEvent
import com.vitorpamplona.quartz.experimental.kanban.board.UnrecognizedKind30301Event
import com.vitorpamplona.quartz.experimental.kanban.card.KanbanCardEvent
import com.vitorpamplona.quartz.experimental.walletScrutiny.verification.BuildVerificationEvent
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.MostroInfoEvent
import com.vitorpamplona.quartz.nip69P2pOrderEvents.mostroInfo.UnrecognizedKind38385Event
import com.vitorpamplona.quartz.utils.EventFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The carded shapes of the shared kinds 30301, 30302 and 38385 reach the addressable cache, where
 * their cards read them; the other apps' shapes on the same kinds do not, so nothing draws them.
 * A Kanban card is also filed under its board's replies, which is where the board card counts it.
 */
class SharedKindCardsConsumeTest {
    private val owner = "aa".repeat(32)
    private val sig = "22".repeat(64)

    private fun event(
        idByte: String,
        kind: Int,
        createdAt: Long,
        vararg tags: Array<String>,
        content: String = "",
    ): Event = EventFactory.create(idByte.repeat(32), owner, createdAt, kind, arrayOf(*tags), content, sig)

    private fun EventCache.stored(event: Event) = getAddressableNoteIfExists((event as AddressableEvent).address())?.event

    @Test
    fun consumesBoardsVerificationsAndMostroTerms() {
        val cache = EventCache()

        val board = event("01", 30301, 1_700_000_000L, arrayOf("d", "roadmap"), arrayOf("title", "Roadmap"))
        val verdict = event("02", 30301, 1_700_000_000L, arrayOf("d", "w:1:android:ff"), arrayOf("i", "com.example.wallet"), arrayOf("status", "reproducible"))
        val mostro = event("03", 38385, 1_700_000_000L, arrayOf("d", owner), arrayOf("z", "info"), arrayOf("mostro_version", "0.12.8"))

        assertIs<KanbanBoardEvent>(board)
        assertIs<BuildVerificationEvent>(verdict)
        assertIs<MostroInfoEvent>(mostro)

        listOf(board, verdict, mostro).forEach {
            assertTrue(cache.justConsume(it, null, true), it.toJson())
            assertSame(it, cache.stored(it))
        }
    }

    @Test
    fun leavesOtherAppsShapesOutOfTheCache() {
        val cache = EventCache()

        val planner = event("04", 30301, 1_700_000_000L, arrayOf("d", "task-1"), arrayOf("b", "ff".repeat(32)), arrayOf("col", "day"), content = "ciphertext")
        val paygress = event("05", 38385, 1_700_000_000L, arrayOf("d", "paygress:revocation:v1:1"))

        assertIs<UnrecognizedKind30301Event>(planner)
        assertIs<UnrecognizedKind38385Event>(paygress)

        assertFalse(cache.justConsume(planner, null, true))
        assertFalse(cache.justConsume(paygress, null, true))
        assertNull(cache.stored(planner))
        assertNull(cache.stored(paygress))
    }

    @Test
    fun aKanbanCardIsFiledUnderItsBoardAndMovesWhenEdited() {
        val cache = EventCache()

        val board = event("01", 30301, 1_700_000_000L, arrayOf("d", "roadmap"), arrayOf("title", "Roadmap"))
        val other = event("02", 30301, 1_700_000_000L, arrayOf("d", "other"), arrayOf("title", "Other"))
        val card = event("06", 30302, 1_700_000_100L, arrayOf("d", "fix"), arrayOf("title", "Fix it"), arrayOf("a", "30301:$owner:roadmap"), arrayOf("s", "todo"))
        assertIs<KanbanCardEvent>(card)

        cache.justConsume(board, null, true)
        cache.justConsume(other, null, true)
        assertTrue(cache.justConsume(card, null, true))

        val boardNote = cache.getAddressableNoteIfExists((board as AddressableEvent).address())!!
        val otherNote = cache.getAddressableNoteIfExists((other as AddressableEvent).address())!!
        val cardNote = cache.getAddressableNoteIfExists(card.address())!!

        assertEquals(listOf(cardNote), boardNote.replies)

        // The card is moved to another board by republishing it: it leaves the first board's replies.
        val moved = event("07", 30302, 1_700_000_200L, arrayOf("d", "fix"), arrayOf("title", "Fix it"), arrayOf("a", "30301:$owner:other"))
        assertTrue(cache.justConsume(moved, null, true))

        assertEquals(emptyList(), boardNote.replies)
        assertEquals(listOf(cardNote), otherNote.replies)
    }
}
