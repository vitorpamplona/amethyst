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
package com.vitorpamplona.amethyst.ui.note.types

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.publication_section_count
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.pluralStringRes
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import kotlinx.collections.immutable.toImmutableList

// A 2:3 portrait cover — a book jacket, which is what NKBIP-01's default `book` type is. The
// wide 16:9 hero LongForm uses would letterbox every one of them.
private const val COVER_ASPECT = 2f / 3f
private val CoverWidth = 96.dp

/**
 * Renders a kind-30040 NKBIP-01 publication index: cover, title, author, and what the index knows
 * about the work.
 *
 * The index event carries no `content` of its own — the prose lives in the kind-30041 sections it
 * points at, which we do not parse yet. So this deliberately renders the *card*, not a reader: it
 * exists so a publication linked from an [EntityRating] resolves to something, rather than to a
 * note with nothing in it but a hashtag.
 */
@Composable
fun RenderPublicationIndex(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PublicationIndexEvent ?: return

    PublicationHeader(noteEvent, note, accountViewModel)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicationHeader(
    noteEvent: PublicationIndexEvent,
    note: Note,
    accountViewModel: AccountViewModel,
) {
    // `title` is mandatory in NKBIP-01 and missing from events in the wild, hence the slug
    // fallback rather than a blank card.
    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }
    val author = remember(noteEvent) { noteEvent.author()?.ifBlank { null } }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } }
    val cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } }
    val type = remember(noteEvent) { noteEvent.type().replaceFirstChar { it.uppercase() } }
    val version = remember(noteEvent) { noteEvent.version()?.ifBlank { null } }
    val sectionCount = remember(noteEvent) { noteEvent.sectionCount() }
    val topics =
        remember(noteEvent) {
            noteEvent
                .topics()
                .distinct()
                .take(3)
                .toImmutableList()
        }

    Column(MaterialTheme.colorScheme.replyModifier) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            PublicationCover(cover, accountViewModel)

            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                author?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    // `type` is open-ended spec vocabulary, not a translatable label, so it is
                    // shown as published rather than mapped through a string table that would
                    // silently drop every value the spec has not enumerated.
                    text =
                        buildList {
                            add(type)
                            version?.let { add(it) }
                            if (sectionCount > 0) {
                                add(pluralStringRes(Res.plurals.publication_section_count, sectionCount, sectionCount))
                            }
                        }.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.grayText,
                )
            }
        }

        summary?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            )
        }

        if (topics.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Size5dp),
                verticalArrangement = Arrangement.spacedBy(Size5dp),
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            ) {
                topics.forEach { PublicationTopicChip(it) }
            }
        }
    }
}

@Composable
private fun PublicationCover(
    cover: String?,
    accountViewModel: AccountViewModel,
) {
    val shape = RoundedCornerShape(6.dp)
    val modifier = Modifier.width(CoverWidth).aspectRatio(COVER_ASPECT).clip(shape)

    Box(modifier) {
        if (cover != null) {
            MyAsyncImage(
                imageUrl = cover,
                contentDescription = stringRes(R.string.preview_card_image_for, cover),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { PublicationCoverPlaceholder() },
                onError = { PublicationCoverPlaceholder() },
            )
        } else {
            PublicationCoverPlaceholder()
        }
    }
}

@Composable
private fun PublicationCoverPlaceholder() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = Modifier.width(28.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}

@Composable
private fun PublicationTopicChip(topic: String) {
    Text(
        text = "#$topic",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}
