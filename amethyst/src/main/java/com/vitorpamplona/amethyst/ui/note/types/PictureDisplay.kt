/**
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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip68Picture.PictureEvent
import kotlinx.collections.immutable.toImmutableList

@Composable
fun PictureDisplay(
    note: Note,
    roundedCorner: Boolean,
    contentScale: ContentScale,
    padding: PaddingValues,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val event = (note.event as? PictureEvent) ?: return
    val uri = note.toNostrUri()

    val images by
        remember(note) {
            mutableStateOf(
                event
                    .imetaTags()
                    .map {
                        MediaUrlImage(
                            url = it.url,
                            description = it.alt,
                            hash = it.hash,
                            blurhash = it.blurhash,
                            dim = it.dimension,
                            uri = uri,
                            mimeType = it.mimeType,
                        )
                    }.toImmutableList(),
            )
        }

    val first = images.firstOrNull()

    if (first != null) {
        val title = event.title()

        SensitivityWarning(note = note, accountViewModel = accountViewModel) {
            Column {
                if (title != null) {
                    Text(
                        modifier = Modifier.padding(padding),
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(StdVertSpacer)
                }

                if (images.size == 1) {
                    ZoomableContentView(
                        content = images.first(),
                        images = images,
                        roundedCorner = roundedCorner,
                        contentScale = ContentScale.FillWidth,
                        accountViewModel = accountViewModel,
                    )
                } else {
                    AutoNonlazyGrid(images.size) {
                        ZoomableContentView(
                            content = images[it],
                            images = images,
                            roundedCorner = roundedCorner,
                            contentScale = ContentScale.Crop,
                            accountViewModel = accountViewModel,
                        )
                    }
                }

                TranslatableRichTextViewer(
                    content = event.content,
                    canPreview = false,
                    quotesLeft = 0,
                    modifier = Modifier.padding(padding),
                    tags = EmptyTagList,
                    backgroundColor = backgroundColor,
                    id = note.idHex,
                    callbackUri = uri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }
    }
}
