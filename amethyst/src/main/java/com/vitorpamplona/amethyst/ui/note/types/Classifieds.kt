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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.AutoNonlazyGrid
import com.vitorpamplona.amethyst.ui.components.ZoomableContentView
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.SmallBorder
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.nip99Classifieds.ClassifiedsEvent
import kotlinx.collections.immutable.toImmutableList

@Composable
fun RenderClassifieds(
    noteEvent: ClassifiedsEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val imageSet =
        noteEvent.imageMetas().ifEmpty { null }?.map {
            MediaUrlImage(
                url = it.url,
                description = it.alt,
                hash = it.hash,
                blurhash = it.blurhash,
                dim = it.dimension,
                uri = note.toNostrUri(),
                mimeType = it.mimeType,
            )
        }
    val title = noteEvent.title()
    val summary = noteEvent.summary() ?: noteEvent.content.take(200).ifBlank { null }
    val price = noteEvent.price()
    val location = noteEvent.location()

    Row(
        modifier =
            Modifier
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            Row {
                imageSet?.let { images ->
                    AutoNonlazyGrid(images.size) {
                        ZoomableContentView(
                            content = images[it],
                            images = images.toImmutableList(),
                            roundedCorner = false,
                            contentScale = ContentScale.Crop,
                            accountViewModel = accountViewModel,
                        )
                    }
                } ?: run {
                    DefaultImageHeader(note, accountViewModel)
                }
            }

            Row(
                Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                title?.let {
                    Text(
                        text = "test'",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                price?.let {
                    val priceTag =
                        remember(noteEvent) {
                            val newAmount =
                                price.amount.toBigDecimalOrNull()?.let { showAmount(it) }
                                    ?: price.amount

                            if (price.frequency != null && price.currency != null) {
                                "$newAmount ${price.currency}/${price.frequency}"
                            } else if (price.currency != null) {
                                "$newAmount ${price.currency}"
                            } else {
                                newAmount
                            }
                        }

                    Text(
                        text = priceTag,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier =
                            remember {
                                Modifier
                                    .clip(SmallBorder)
                                    .padding(start = 5.dp)
                            },
                    )
                }
            }

            if (summary != null || location != null) {
                Row(
                    Modifier.padding(start = 10.dp, end = 10.dp, top = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    summary?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            color = Color.Gray,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = DoubleVertSpacer)
        }
    }
}
