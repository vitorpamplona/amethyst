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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.amethyst.commons.model.marmotGroupLastReadRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Notifications badge skips Marmot messages their chat has read, so reading a Marmot chat must
 * turn the badge off then, not when the feed next emits.
 */
class MarmotReadBadgeTest {
    private val chat = "aa".repeat(32)

    @Test
    fun readingAMarmotChatClearsTheBadgeWithoutAFeedChange() =
        runTest {
            val chatRead = MutableStateFlow(0L)
            val routes = MutableStateFlow(mapOf(marmotGroupLastReadRoute(chat) to chatRead, "Notification" to MutableStateFlow(0L)))
            // One card: a message of that chat, sent at 100. News while the chat has not read it.
            val cards = MutableStateFlow<List<Long>?>(listOf(100L))
            val seen = ArrayList<Boolean>()
            notificationHasNews(flowOf(50L), cards, marmotChatReadPositions(routes)) { list -> list.firstOrNull { it > chatRead.value } }
                .onEach { seen += it }
                .launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
            assertEquals(listOf(true), seen)

            chatRead.value = 100L
            assertEquals("the chat was read: no news, with no feed change", false, seen.last())
        }

    @Test
    fun aChatThatGetsItsFirstReadTimeLaterIsFollowedToo() =
        runTest {
            val routes = MutableStateFlow(mapOf<String, MutableStateFlow<Long>>())
            val seen = ArrayList<List<Long>>()
            marmotChatReadPositions(routes)
                .onEach { seen += it }
                .launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
            val chatRead = MutableStateFlow(0L)
            routes.value = mapOf(marmotGroupLastReadRoute(chat) to chatRead, "Other/route" to MutableStateFlow(5L))
            chatRead.value = 42L
            assertEquals(listOf(emptyList(), listOf(0L), listOf(42L)), seen)
        }
}
