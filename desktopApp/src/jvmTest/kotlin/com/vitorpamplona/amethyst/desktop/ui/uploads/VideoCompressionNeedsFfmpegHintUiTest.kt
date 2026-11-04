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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.vitorpamplona.amethyst.commons.service.uploads.CompressorQuality
import com.vitorpamplona.amethyst.commons.service.uploads.MultiOrchestrator
import com.vitorpamplona.amethyst.commons.service.uploads.SelectedMedia
import com.vitorpamplona.amethyst.commons.service.uploads.StringMediaUri
import com.vitorpamplona.amethyst.commons.service.uploads.VideoCompressionSupport
import com.vitorpamplona.amethyst.commons.service.uploads.VideoUploadControls
import com.vitorpamplona.amethyst.commons.ui.note.creators.uploads.VideoCompressionNeedsFfmpegHint
import com.vitorpamplona.amethyst.commons.ui.theme.AmethystPreviewTheme
import org.junit.Rule
import kotlin.test.Test

class VideoCompressionNeedsFfmpegHintUiTest {
    @get:Rule
    val compose = createComposeRule()

    private val video = MultiOrchestrator(listOf(SelectedMedia(StringMediaUri("/tmp/a.mov"), "video/quicktime")))
    private val high = CompressorQuality.sliderSteps.indexOf(CompressorQuality.HIGH)
    private val hint = "Install ffmpeg to compress videos. Until then videos are uploaded as they are."

    @Test
    fun hintIsShownForAVideoWhenFfmpegIsMissing() {
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                VideoCompressionNeedsFfmpegHint(VideoUploadControls(video, VideoCompressionSupport.NeedsFfmpeg, high))
            }
        }
        compose.onNodeWithText(hint).assertIsDisplayed()
    }

    @Test
    fun hintIsAbsentWhenFfmpegCompressesTheVideo() {
        compose.setContent {
            AmethystPreviewTheme(dark = false) {
                VideoCompressionNeedsFfmpegHint(VideoUploadControls(video, VideoCompressionSupport.Supported, high))
            }
        }
        compose.onNodeWithText(hint).assertDoesNotExist()
    }
}
