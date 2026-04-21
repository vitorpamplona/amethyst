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
package com.vitorpamplona.quartz.nip53LiveActivities.clip

import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LiveActivitiesClipEventTest {
    private val host = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    private val viewerAuthor = "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd"
    private val dummySig = "0".repeat(128)

    @Test
    fun parsesZapStreamShapedClip() {
        val event =
            LiveActivitiesClipEvent(
                id = "2".repeat(64),
                pubKey = viewerAuthor,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("a", "${LiveActivitiesEvent.KIND}:$host:stream-d", "wss://relay.example"),
                        arrayOf("p", host),
                        arrayOf("r", "https://cdn.example/clip.mp4"),
                        arrayOf("title", "Nice moment"),
                        arrayOf("alt", "Live stream clip"),
                    ),
                content = "Check this out",
                sig = dummySig,
            )

        val activity = assertNotNull(event.activity())
        assertEquals(LiveActivitiesEvent.KIND, activity.kind)
        assertEquals(host, activity.pubKeyHex)
        assertEquals("stream-d", activity.dTag)

        assertEquals(host, event.host())
        assertEquals("https://cdn.example/clip.mp4", event.videoUrl())
        assertEquals("Nice moment", event.title())
        assertEquals("Check this out", event.content)
    }

    @Test
    fun ignoresClipLackingStreamReference() {
        val event =
            LiveActivitiesClipEvent(
                id = "2".repeat(64),
                pubKey = viewerAuthor,
                createdAt = 1_700_000_000L,
                tags =
                    arrayOf(
                        arrayOf("p", host),
                        arrayOf("r", "https://cdn.example/clip.mp4"),
                        arrayOf("title", "Nice moment"),
                    ),
                content = "",
                sig = dummySig,
            )

        assertNull(event.activity())
        assertNull(event.activityAddress())
        assertEquals("https://cdn.example/clip.mp4", event.videoUrl())
    }
}
