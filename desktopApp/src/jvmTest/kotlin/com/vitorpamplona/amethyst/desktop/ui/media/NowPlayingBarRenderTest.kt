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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import com.vitorpamplona.amethyst.desktop.service.media.MediaPlaybackState
import org.jetbrains.skia.EncodedImageFormat
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the now-playing bar headlessly in both themes and at three window widths. Like the other
 * render tests it checks structure, not exact pixels. Set NOW_PLAYING_BAR_RENDER_DIR to also write
 * the PNGs for a human to look at.
 */
class NowPlayingBarRenderTest {
    private val hash = "3f9a1c8d2e7b4a6f0c1d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f2a3b"

    private fun poster(): ImageBitmap {
        val image = BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.paint = GradientPaint(0f, 0f, Color(0x6A3DE8), 320f, 180f, Color(0xE8743D))
        g.fillRect(0, 0, 320, 180)
        g.dispose()
        return image.toComposeImageBitmap()
    }

    private class Scenario(
        val name: String,
        val state: MediaPlaybackState,
        val type: MediaType,
        val thumbnail: Boolean,
    )

    private val scenarios =
        listOf(
            Scenario(
                "video-blossom",
                MediaPlaybackState(url = "https://blossom.primal.net/$hash.mp4", isPlaying = true, position = 0.37f, currentTime = 83_000, duration = 224_000, volume = 70),
                MediaType.VIDEO,
                thumbnail = true,
            ),
            Scenario(
                "audio-named-muted",
                MediaPlaybackState(url = "https://example.com/podcasts/my-podcast-episode-12-the-long-title.mp3", isPlaying = false, position = 0.08f, currentTime = 301_000, duration = 3_812_000, volume = 0, isMuted = true),
                MediaType.AUDIO,
                thumbnail = false,
            ),
        )

    private fun render(
        scenario: Scenario,
        dark: Boolean,
        widthDp: Int,
    ): BufferedImage {
        val density = 2f
        val poster = if (scenario.thumbnail) poster() else null
        val scene =
            ImageComposeScene(width = (widthDp * density).toInt(), height = (72 * density).toInt(), density = Density(density)) {
                AmethystPreviewTheme(dark = dark) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.BottomCenter) {
                        NowPlayingBarContent(
                            state = scenario.state,
                            type = scenario.type,
                            thumbnail = poster,
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
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            System.getenv("NOW_PLAYING_BAR_RENDER_DIR")?.let { dir ->
                File(dir, "now-playing-bar-${scenario.name}-$widthDp-${if (dark) "dark" else "light"}.png").writeBytes(png)
            }
            ImageIO.read(ByteArrayInputStream(png))
        } finally {
            scene.close()
        }
    }

    private fun BufferedImage.distinctColours(): Int {
        val seen = mutableSetOf<Int>()
        for (x in 0 until width step 3) {
            for (y in 0 until height step 3) seen += getRGB(x, y)
        }
        return seen.size
    }

    @Test
    fun everyStateRendersInBothThemesAndWidths() {
        for (scenario in scenarios) {
            for (width in listOf(1280, 720, 420)) {
                val light = render(scenario, dark = false, widthDp = width)
                val dark = render(scenario, dark = true, widthDp = width)

                assertTrue(light.distinctColours() > 20, "${scenario.name} light drew ${light.distinctColours()} colours")
                assertTrue(dark.distinctColours() > 20, "${scenario.name} dark drew ${dark.distinctColours()} colours")
                assertTrue(light.getRGB(light.width / 2, light.height - 2) != dark.getRGB(dark.width / 2, dark.height - 2), "the bar ignored the theme")
            }
        }
    }
}
