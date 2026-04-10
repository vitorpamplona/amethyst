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
package com.vitorpamplona.amethyst.ios.ui.media

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.ios.ui.markdown.MarkdownText

/**
 * Renders note content with inline images, video thumbnails, and URL preview
 * cards instead of raw text. Plain-text segments are rendered as before.
 *
 * @param content      Raw note content string
 * @param modifier     Modifier for the wrapping Column
 * @param onImageClick Optional callback when an image is tapped; defaults to
 *                     opening the full-screen viewer.
 * @param onUrlClick   Optional callback when a URL card / video is tapped;
 *                     defaults to opening the URL externally.
 */
@Composable
fun RichNoteContent(
    content: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
    onUrlClick: ((String) -> Unit)? = null,
) {
    val segments = remember(content) { MediaUtils.parseContent(content) }
    val uriHandler = LocalUriHandler.current

    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

    val handleImageClick: (String) -> Unit =
        onImageClick ?: { url ->
            fullScreenImageUrl = url
        }

    val handleUrlClick: (String) -> Unit =
        onUrlClick ?: { url ->
            try {
                uriHandler.openUri(url)
            } catch (_: Exception) {
                // Ignore if platform cannot open
            }
        }

    Column(modifier = modifier) {
        segments.forEachIndexed { index, segment ->
            if (index > 0) Spacer(Modifier.height(4.dp))

            when (segment) {
                is MediaUtils.ContentSegment.Text -> {
                    MarkdownText(
                        content = segment.text,
                    )
                }

                is MediaUtils.ContentSegment.ImageUrl -> {
                    InlineImage(
                        url = segment.url,
                        onClick = handleImageClick,
                    )
                }

                is MediaUtils.ContentSegment.VideoUrl -> {
                    IosVideoPlayer(
                        url = segment.url,
                        autoPlay = false,
                    )
                }

                is MediaUtils.ContentSegment.AudioUrl -> {
                    IosAudioPlayer(
                        url = segment.url,
                    )
                }

                is MediaUtils.ContentSegment.LinkUrl -> {
                    UrlPreviewCard(
                        url = segment.url,
                        onClick = handleUrlClick,
                    )
                }
            }
        }
    }

    // Full-screen image overlay
    fullScreenImageUrl?.let { url ->
        FullScreenImageViewer(
            imageUrl = url,
            onDismiss = { fullScreenImageUrl = null },
        )
    }
}
