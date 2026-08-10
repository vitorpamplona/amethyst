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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip17Dm.messages.ChatMessageEvent
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The predicate behind every mute suppression point: the row dot, the bottom-bar
 * badge, the push dispatcher and the Notifications feed all ask this one question,
 * so they cannot drift apart.
 */
class MutedPublicChatsTest {
    private val channelId: HexKey = "4".repeat(64)
    private val otherChannel: HexKey = "5".repeat(64)
    private val parentId: HexKey = "6".repeat(64)
    private val author: HexKey = "b".repeat(64)
    private val relay = "wss://relay.damus.io"
    private val sig = "0".repeat(128)

    private fun channelMessage(tags: Array<Array<String>>) = ChannelMessageEvent("3".repeat(64), author, 1778593701L, tags, "hi", sig)

    private fun topLevel() = channelMessage(arrayOf(arrayOf("e", channelId, relay, "root")))

    private fun reply() =
        channelMessage(
            arrayOf(
                arrayOf("e", channelId, relay, "root"),
                arrayOf("e", parentId, relay, "reply"),
            ),
        )

    @Test
    fun topLevelMessageResolvesToItsChannel() {
        assertEquals(channelId, mutedChannelIdOf(topLevel()))
    }

    @Test
    fun replyResolvesToTheChannelNotItsParent() {
        // Every message in a NIP-28 channel shares one root, so mute is per-channel.
        assertEquals(channelId, mutedChannelIdOf(reply()))
    }

    @Test
    fun nonPublicChatEventHasNoChannel() {
        val dm = ChatMessageEvent("3".repeat(64), author, 1L, arrayOf(arrayOf("p", author)), "hi", sig)
        assertNull(mutedChannelIdOf(dm))
        assertNull(mutedChannelIdOf(null))
    }

    @Test
    fun messageInMutedChannelIsMuted() {
        assertTrue(isMutedPublicChatMessage(topLevel(), setOf(channelId)))
        assertTrue(isMutedPublicChatMessage(reply(), setOf(channelId)))
    }

    @Test
    fun messageInAnotherChannelIsNotMuted() {
        assertFalse(isMutedPublicChatMessage(topLevel(), setOf(otherChannel)))
    }

    @Test
    fun emptyMuteSetMutesNothing() {
        assertFalse(isMutedPublicChatMessage(topLevel(), emptySet()))
    }

    @Test
    fun nonPublicChatEventIsNeverMuted() {
        val dm = ChatMessageEvent("3".repeat(64), author, 1L, arrayOf(arrayOf("p", author)), "hi", sig)
        assertFalse(isMutedPublicChatMessage(dm, setOf(channelId)))
        assertFalse(isMutedPublicChatMessage(null, setOf(channelId)))
    }
}
