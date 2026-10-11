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
package com.vitorpamplona.amethyst.desktop.ui.uploads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.UnavailableMediaUploader
import com.vitorpamplona.amethyst.commons.service.uploads.VideoAwareSliderSteps
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.MediaQualitySlider
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The slider's ticks are reached by pointer, the way a person uses it, because the value a tick
 * hands back is a float the track geometry computes, not the exact integer the tick stands for:
 * with four stops the thirds land a hair under their integer at many widths.
 */
class MediaQualitySliderUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val video = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/a.mp4"), "video/mp4")))
    private val uploader =
        object : MediaUploader by UnavailableMediaUploader {
            override val videoCompression: VideoCompressionSupport get() = VideoCompressionSupport.Supported
        }
    private val slider = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test
    fun everyVideoStopIsReachableByDraggingToItsTickAtAnyWidth() {
        val allSteps = CompressorQuality.sliderSteps
        val steps = VideoAwareSliderSteps.stepsFor(allSteps, hasVideo = true)
        var position by mutableStateOf(allSteps.indexOf(CompressorQuality.LOW))
        var boxWidth: Dp by mutableStateOf(240.dp)
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                Box(Modifier.width(boxWidth)) {
                    MediaQualitySlider(position = position, onPositionChange = { position = it }, media = video, uploader = uploader)
                }
            }
        }

        val misses = mutableListOf<String>()
        for (dp in 240..720 step 8) {
            boxWidth = dp.dp
            compose.waitForIdle()
            for ((tick, expected) in steps.withIndex()) {
                compose.onNode(slider).performTouchInput {
                    // Tick i sits at i / lastIndex of the track; a drag from the middle lands on it,
                    // and the thumb's inset leaves ample snap tolerance.
                    val x = (width * tick / steps.lastIndex.toFloat()).coerceIn(4f, width - 4f)
                    swipe(Offset(width / 2f, centerY), Offset(x, centerY), durationMillis = 50)
                }
                compose.waitForIdle()
                if (allSteps[position] != expected) misses += "$dp dp, tick $tick: ${allSteps[position]} instead of $expected"
            }
        }
        assertEquals(emptyList(), misses)
    }
}
