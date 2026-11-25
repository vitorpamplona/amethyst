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
package com.vitorpamplona.amethyst.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.richtext.BaseMediaContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlContent
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.commons.ui.components.ClickableUrl
import com.vitorpamplona.amethyst.commons.ui.components.ImageViewerDialog
import com.vitorpamplona.amethyst.commons.ui.components.ZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.rememberZoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.components.zoomSourceAnchor
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.platform.NotePlatform
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import kotlinx.collections.immutable.ImmutableList

/**
 * iOS's note media, until playback and link cards arrive (B4, B5): images draw inline through Coil
 * and open the shared [ImageViewerDialog], and every other piece (videos, PDFs, link previews) is a link the user can
 * open, never an empty slot. Everything else keeps [NotePlatform]'s empty defaults.
 */
object IosNotePlatform : NotePlatform {
    override val maxInlineMediaHeight: Dp get() = MAX_INLINE_MEDIA_HEIGHT

    @Composable
    override fun ZoomableContentView(
        content: BaseMediaContent,
        images: ImmutableList<BaseMediaContent>,
        roundedCorner: Boolean,
        contentScale: ContentScale,
        accountViewModel: AccountViewModel,
    ) {
        val url = (content as? MediaUrlContent)?.url ?: return
        if (content is MediaUrlImage) {
            var showViewer by remember { mutableStateOf(false) }
            // Keyed by the image, so a viewer paged to a sibling can close into that one's thumbnail.
            val source = rememberZoomSourceAnchor(content)
            val cornerRadius = if (roundedCorner) 12.dp else 0.dp
            AsyncImage(
                model = url,
                contentDescription = content.description,
                contentScale = contentScale,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = MAX_INLINE_MEDIA_HEIGHT)
                        .zoomSourceAnchor(source)
                        .clip(RoundedCornerShape(cornerRadius))
                        .clickable { showViewer = true },
            )
            if (showViewer) {
                ImageViewerDialog(content, images, source, cornerRadius) { showViewer = false }
            }
        } else {
            ClickableUrl(url)
        }
    }

    @Composable
    override fun ZoomableImageDialog(
        imageUrl: BaseMediaContent,
        allImages: ImmutableList<BaseMediaContent>,
        source: ZoomSourceAnchor?,
        onDismiss: () -> Unit,
        accountViewModel: AccountViewModel,
    ) = ImageViewerDialog(imageUrl, allImages, source, 0.dp, onDismiss)

    @Composable
    override fun UrlPreview(
        url: String,
        urlText: String,
        callbackUri: String?,
        accountViewModel: AccountViewModel,
        nav: INav?,
    ) = ClickableUrl(url, urlText)

    private val MAX_INLINE_MEDIA_HEIGHT = 600.dp
}
