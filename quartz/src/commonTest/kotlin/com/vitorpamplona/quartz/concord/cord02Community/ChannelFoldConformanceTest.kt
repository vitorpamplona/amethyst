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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CORD-03 §2 channel fold rules: deletion is terminal across the whole accepted chain, and a
 * Channel name is 1..64 UTF-8 bytes — an edition breaking the name rule is unauthorized and the
 * fold falls back to the previous candidate (the reference client's channel gate).
 */
class ChannelFoldConformanceTest {
    private val owner = "0f".repeat(32)
    private val chan = "c1".repeat(32)

    private fun chained(
        version: Long,
        prev: ControlEdition?,
        content: String,
        author: String = owner,
    ) = ControlEdition(ControlEntityKind.CHANNEL, chan.hexToByteArray(), version, prev?.hash, null, content, author, "r-$version", version)

    @Test
    fun aDeletedChannelCannotBeResurrectedByALaterEdition() {
        val c0 = chained(0, null, """{"name":"general"}""")
        val c1 = chained(1, c0, """{"name":"general","deleted":true}""")
        val c2 = chained(2, c1, """{"name":"general","deleted":false}""")

        val state = ConcordCommunityState.fold(listOf(c0, c1, c2), owner)
        assertNull(state.channels[chan], "a deletion anywhere in the accepted chain is terminal")
    }

    @Test
    fun anUnauthorizedDeleteDoesNotRetireTheChannel() {
        val troll = "77".repeat(32)
        val c0 = chained(0, null, """{"name":"general"}""")
        val forged = chained(1, c0, """{"name":"general","deleted":true}""", author = troll)

        val state = ConcordCommunityState.fold(listOf(c0, forged), owner)
        assertEquals("general", state.channels[chan]?.definition?.name)
    }

    @Test
    fun anOverCapOrEmptyNameFallsBackToThePreviousEdition() {
        val c0 = chained(0, null, """{"name":"general"}""")
        val tooLong = chained(1, c0, """{"name":"${"x".repeat(65)}"}""")
        assertEquals(
            "general",
            ConcordCommunityState
                .fold(listOf(c0, tooLong), owner)
                .channels[chan]
                ?.definition
                ?.name,
        )

        val empty = chained(1, c0, """{"name":""}""")
        assertEquals(
            "general",
            ConcordCommunityState
                .fold(listOf(c0, empty), owner)
                .channels[chan]
                ?.definition
                ?.name,
        )

        // Exactly 64 bytes is fine — counted in UTF-8, so 16 four-byte emoji hit the cap too.
        val atCap = chained(1, c0, """{"name":"${"x".repeat(64)}"}""")
        assertEquals(
            "x".repeat(64),
            ConcordCommunityState
                .fold(listOf(c0, atCap), owner)
                .channels[chan]
                ?.definition
                ?.name,
        )
    }

    @Test
    fun theNameRuleCountsUtf8Bytes() {
        val emoji = "😀" // 4 bytes in UTF-8
        assertTrue(ChannelEntity.isValidName(emoji.repeat(16)))
        assertFalse(ChannelEntity.isValidName(emoji.repeat(16) + "a"))
        assertFalse(ChannelEntity.isValidName(""))
        assertTrue(ChannelEntity(name = "general").hasValidName())
    }
}
