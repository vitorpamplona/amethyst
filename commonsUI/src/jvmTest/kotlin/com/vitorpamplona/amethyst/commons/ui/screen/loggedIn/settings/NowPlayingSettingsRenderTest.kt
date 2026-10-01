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
package com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingAccess
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettings
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.jetbrains.skia.EncodedImageFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders the Now Playing settings headlessly in both themes. Like the other render tests it
 * checks structure, not exact pixels: every string resolves (a missing one throws here) and the
 * screen follows the theme. Set NOW_PLAYING_RENDER_DIR to also write the PNGs for a human to look at.
 */
class NowPlayingSettingsRenderTest {
    private val width = 820
    private val height = 1900

    private class FakeAccess(
        val granted: Boolean,
    ) : NowPlayingAccess {
        override fun hasAccess() = granted

        override fun requestAccess() {}
    }

    private fun state(settings: NowPlayingSettings) = NowPlayingSettingsState().apply { restore(settings) }

    private val apps =
        mapOf(
            "com.spotify.music" to "Spotify",
            "com.google.android.apps.youtube.music" to "YouTube Music",
            "de.danoeh.antennapod" to "AntennaPod",
        )

    private val scenarios: Map<String, @Composable () -> Unit> =
        mapOf(
            "off" to { NowPlayingSettingsContent(state(NowPlayingSettings()), FakeAccess(false)) },
            "needs-access" to {
                NowPlayingSettingsContent(state(NowPlayingSettings(shareInApp = true, shareOtherApps = true)), FakeAccess(false))
            },
            "on" to {
                NowPlayingSettingsContent(
                    state(
                        NowPlayingSettings(
                            shareInApp = true,
                            shareOtherApps = true,
                            knownApps = apps,
                            blockedApps = setOf("com.google.android.apps.youtube.music"),
                        ),
                    ),
                    FakeAccess(true),
                    preview = { isOn ->
                        NowPlayingPreviewCard(
                            avatar = { MonogramBadge("Vitor", size = 44, shape = CircleShape) },
                            name = "Vitor",
                            liveStatus = "One More Time - Daft Punk",
                            isOn = isOn,
                        )
                    },
                )
            },
            "desktop" to {
                NowPlayingSettingsContent(
                    state(NowPlayingSettings(shareOtherApps = true, knownApps = mapOf("spotify" to "Spotify", "vlc" to "VLC media player"))),
                    access = null,
                    showInApp = false,
                )
            },
        )

    private fun render(
        dark: Boolean,
        content: @Composable () -> Unit,
    ): BufferedImage {
        val scene =
            ImageComposeScene(width = width, height = height, density = Density(2f)) {
                AmethystPreviewTheme(dark = dark) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        content()
                    }
                }
            }
        return try {
            val png = scene.render().encodeToData(EncodedImageFormat.PNG)!!.bytes
            System.getenv("NOW_PLAYING_RENDER_DIR")?.let { dir ->
                val name = scenarios.entries.first { it.value === content }.key
                File(dir, "now-playing-$name-${if (dark) "dark" else "light"}.png").writeBytes(png)
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
    fun everyStateRendersInBothThemes() {
        scenarios.values.forEach { content ->
            val light = render(false, content)
            val dark = render(true, content)

            assertTrue(light.distinctColours() > 20, "the light screen drew ${light.distinctColours()} colours")
            assertTrue(dark.distinctColours() > 20, "the dark screen drew ${dark.distinctColours()} colours")
            assertTrue(light.getRGB(2, 2) != dark.getRGB(2, 2), "the screen ignored the theme")
        }
    }
}
