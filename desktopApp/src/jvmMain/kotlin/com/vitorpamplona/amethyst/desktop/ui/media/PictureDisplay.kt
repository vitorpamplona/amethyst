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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vitorpamplona.quartz.nip68Picture.PictureEvent

/**
 * Displays a kind 20 picture event (NIP-68) with image-first layout.
 */
@Composable
fun PictureDisplay(
    event: PictureEvent,
    modifier: Modifier = Modifier,
    onImageClick: ((List<String>, Int) -> Unit)? = null,
) {
    val imetas = remember(event.id) { event.imetaTags() }
    val imageUrls = remember(imetas) { imetas.mapNotNull { it.url } }
    val title = remember(event.id) { event.title() }
    val description = event.content

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column {
            // Images
            for ((index, url) in imageUrls.withIndex()) {
                val imageModifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .clip(
                            if (index == 0 && title == null && description.isBlank()) {
                                RoundedCornerShape(8.dp)
                            } else if (index == 0) {
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            } else {
                                RoundedCornerShape(0.dp)
                            },
                        ).then(
                            if (onImageClick != null) {
                                Modifier.clickable { onImageClick(imageUrls, index) }
                            } else {
                                Modifier
                            },
                        )
                if (isAnimatedGifUrl(url)) {
                    AnimatedGifImage(
                        url = url,
                        contentDescription = title,
                        modifier = imageModifier,
                        contentScale = ContentScale.FillWidth,
                    )
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = title,
                        modifier = imageModifier,
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }

            // Title + description below images
            if (title != null || description.isNotBlank()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    title?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
