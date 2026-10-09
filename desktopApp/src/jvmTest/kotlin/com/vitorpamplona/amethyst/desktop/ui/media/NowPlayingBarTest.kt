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
package com.vitorpamplona.amethyst.desktop.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NowPlayingBarTest {
    @get:Rule
    val compose = createComposeRule()

    private val hash = "3f9a1c8d2e7b4a6f0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b"

    @Test
    fun blossomHashNamesAreHidden() {
        assertNull(readableFileName("https://blossom.primal.net/$hash"))
        assertNull(readableFileName("https://blossom.primal.net/$hash.mp4"))
        assertNull(readableFileName("https://blossom.primal.net/$hash.thumb.jpg"))
        assertNull(readableFileName("https://blossom.primal.net/${hash.uppercase()}.mp4?x=a/b#t=10"))
        assertNull(readableFileName("https://example.com/"))
    }

    @Test
    fun readableNamesAreDecoded() {
        assertEquals("my-podcast-ep12.mp3", readableFileName("https://example.com/feed/my-podcast-ep12.mp3?token=1"))
        assertEquals("My Podcast – Ep 12.mp3", readableFileName("https://example.com/My%20Podcast%20%E2%80%93%20Ep%2012.mp3"))
        // A path keeps '+' literal.
        assertEquals("c++.mp4", readableFileName("https://example.com/c++.mp4"))
        // A broken escape falls back to the raw segment.
        assertEquals("100%.mp3", readableFileName("https://example.com/100%.mp3"))
    }

    @Test
    fun timesPastTheHourShowHours() {
        assertEquals("0:00", formatTime(0))
        assertEquals("3:44", formatTime(224_000))
        assertEquals("59:59", formatTime(3_599_000))
        assertEquals("1:03:32", formatTime(3_812_000))
    }

    @Test
    fun stopKeepsItsRoomInNarrowWindows() {
        val widths = listOf(320.dp, 380.dp, 560.dp, 700.dp)
        var width by mutableStateOf(widths.first())
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                Box(Modifier.width(width)) {
                    NowPlayingBarContent(
                        state = MediaPlaybackState(url = "https://example.com/a-very-long-file-name-for-a-video.mp4", isPlaying = true, position = 0.5f, currentTime = 1_800_000, duration = 3_812_000),
                        type = MediaType.VIDEO,
                        thumbnail = null,
                        onPlayPause = {},
                        onSeek = {},
                        onToggleMute = {},
                        onVolumeChange = {},
                        onSave = {},
                        onFullscreen = {},
                        onStop = {},
                    )
                }
            }
        }
        for (w in widths) {
            width = w
            compose.onNodeWithContentDescription("Close").assertWidthIsAtLeast(36.dp)
        }
    }
}
