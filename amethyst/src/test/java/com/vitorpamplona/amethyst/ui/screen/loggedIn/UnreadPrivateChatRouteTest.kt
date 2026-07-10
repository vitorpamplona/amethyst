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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.base.ChatroomKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The unread predicate behind the Messages tab dot: a room whose newest message was
 * authored by the logged-in user counts as read (#1286, #1287).
 */
class UnreadPrivateChatRouteTest {
    private val me: HexKey = "a".repeat(64)
    private val peer: HexKey = "b".repeat(64)

    private val roomWithPeer = "Room/${ChatroomKey(persistentSetOf(peer)).hashCode()}"

    private fun message(
        from: HexKey,
        to: HexKey,
        createdAt: Long,
    ) = ChatMessageEvent(
        id = "0".repeat(64),
        pubKey = from,
        createdAt = createdAt,
        tags = arrayOf(arrayOf("p", to)),
        content = "hello",
        sig = "",
    )

    @Test
    fun newestMessageFromPeerReturnsTheRoomRoute() {
        val route = unreadPrivateChatRoute(message(from = peer, to = me, createdAt = 100), me, isAllHidden = { false })

        assertEquals(roomWithPeer to 100L, route)
    }

    @Test
    fun newestMessageAuthoredByMeCountsAsRead() {
        assertNull(unreadPrivateChatRoute(message(from = me, to = peer, createdAt = 100), me, isAllHidden = { false }))
    }

    @Test
    fun hiddenRoomsAreNeverUnread() {
        assertNull(unreadPrivateChatRoute(message(from = peer, to = me, createdAt = 100), me, isAllHidden = { true }))
    }

    @Test
    fun missingEventIsNotUnread() {
        assertNull(unreadPrivateChatRoute(null, me, isAllHidden = { false }))
    }

    @Test
    fun nonChatEventsAreNotUnread() {
        val reaction =
            ReactionEvent(
                id = "0".repeat(64),
                pubKey = peer,
                createdAt = 100,
                tags = arrayOf(arrayOf("p", me)),
                content = "+",
                sig = "",
            )

        assertNull(unreadPrivateChatRoute(reaction, me, isAllHidden = { false }))
    }
}
