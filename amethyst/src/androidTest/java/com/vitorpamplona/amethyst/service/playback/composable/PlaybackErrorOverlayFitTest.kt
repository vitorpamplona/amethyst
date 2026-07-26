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
package com.vitorpamplona.amethyst.service.playback.composable

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vitorpamplona.amethyst.R
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "open in browser" fallback is the only thing this overlay offers the user, so it has to
 * survive whatever box the media layout hands it.
 *
 * [Column] measures children in declaration order against the remaining height, so the button —
 * being last — is what starves when the content is taller than the box. A note in the feed is
 * inset under the 55dp author column (screenWidth - 89dp), and with no imeta `dim` the media box
 * is 16:9, which on a 411dp phone is 322dp wide and only 181dp tall. That was enough to squeeze
 * the button down to 0.38dp — present in the tree, invisible and untappable on screen — while the
 * same note opened in the thread (full bleed, screenWidth - 26dp, so a 217dp box) rendered it at
 * 35.8dp.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackErrorOverlayFitTest {
    @get:Rule val rule = createComposeRule()

    private val browserButtonLabel =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.error_video_open_in_browser)

    /** A FilledTonalButton's natural height; anything much under this is a squeezed button. */
    private val naturalButtonHeight = 40.dp

    private fun renderInBox(
        width: Dp,
        height: Dp,
    ) {
        rule.setContent {
            Box(Modifier.width(width).height(height)) {
                RenderPlaybackError(
                    controllerState =
                        MediaControllerState(
                            controller = mockk<Player>(relaxed = true),
                            playbackError =
                                mutableStateOf(
                                    PlaybackException(
                                        "Malformed HLS manifest",
                                        null,
                                        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                                    ),
                                ),
                        ),
                    videoUri = "https://streamstr.net/x/hls/live.m3u8",
                )
            }
        }
        rule.waitForIdle()
    }

    private fun assertButtonUsable(context: String) {
        val button = rule.onNodeWithText(browserButtonLabel)
        button.assertIsDisplayed()

        val bounds = button.getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue(
            "$context: browser button collapsed to $height (natural is $naturalButtonHeight)",
            height >= naturalButtonHeight - 2.dp,
        )
    }

    @Test
    fun buttonSurvivesTheFeedsSixteenByNineBox() {
        renderInBox(width = 322.dp, height = 181.dp)
        assertButtonUsable("feed 16:9")
    }

    @Test
    fun buttonSurvivesTheThreadsSixteenByNineBox() {
        renderInBox(width = 385.dp, height = 217.dp)
        assertButtonUsable("thread 16:9")
    }

    @Test
    fun buttonSurvivesAnUnusuallyShortBox() {
        // A 3:1 banner-ish stream, or a narrow quote card: far less height than 16:9 gives.
        renderInBox(width = 322.dp, height = 110.dp)
        assertButtonUsable("short box")
    }

    @Test
    fun descriptionIsDroppedRatherThanSlicedInHalf() {
        // A weighted Text given less than one line's height draws it clipped through the middle,
        // which looks broken. Under that much pressure it should not be emitted at all.
        renderInBox(width = 322.dp, height = 110.dp)

        rule
            .onNodeWithText(
                InstrumentationRegistry.getInstrumentation().targetContext.getString(
                    R.string.error_video_playback_failed_description,
                    "ERROR_CODE_PARSING_MANIFEST_MALFORMED",
                ),
            ).assertDoesNotExist()
    }

    @Test
    fun titleStillShowsWhenRoomIsTight() {
        renderInBox(width = 322.dp, height = 181.dp)
        rule.onNodeWithText(InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.error_video_playback_failed)).assertIsDisplayed()
    }
}
