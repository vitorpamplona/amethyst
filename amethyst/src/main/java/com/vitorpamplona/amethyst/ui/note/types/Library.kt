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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.amethyst.ui.theme.replyModifier
import com.vitorpamplona.quartz.experimental.library.BlossomPieceIndexEvent
import com.vitorpamplona.quartz.experimental.library.BookshelfDirectoryEvent
import com.vitorpamplona.quartz.experimental.library.LearningResourceEvent
import kotlinx.collections.immutable.toImmutableList

// Landscape rather than the publications' 2:3: a course banner and a file preview are wide, and
// letterboxing them into a book jacket would crop the part that identifies them.
private const val BANNER_ASPECT = 16f / 9f
private val BannerWidth = 96.dp

// A shelf can hold hundreds; each row carries its own relay subscription, so the cap is a fetch
// budget as much as a layout one.
private const val MAX_PREVIEW_ITEMS = 12

/**
 * Renders a kind-30142 learning resource — a course, tutorial or lesson.
 *
 * The reference Android client marks this kind `reader = true`, i.e. it is meant to be read
 * rather than skimmed, so the body renders in full underneath the header rather than as a blurb.
 */
@Composable
fun RenderLearningResource(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? LearningResourceEvent ?: return

    Column(Modifier.fillMaxWidth()) {
        LibraryHeader(
            symbol = MaterialSymbols.MenuBook,
            label = stringRes(R.string.library_learning_resource),
            title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
            detail = null,
            banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
            accountViewModel = accountViewModel,
        )

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/**
 * Renders a kind-30045 directory — a curated shelf of publications and notes.
 *
 * The shelf's contents are shaped exactly like a publication's table of contents, so they render
 * through the same rows: titles resolve from the list's own tags first and upgrade when the
 * items arrive.
 */
@Composable
fun RenderBookshelfDirectory(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? BookshelfDirectoryEvent ?: return

    val itemCount = remember(noteEvent) { noteEvent.itemCount() }

    Column(Modifier.fillMaxWidth()) {
        LibraryHeader(
            symbol = MaterialSymbols.Collections,
            label = stringRes(R.string.library_directory),
            title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
            subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
            detail = if (itemCount > 0) pluralStringResource(R.plurals.library_directory_items, itemCount, itemCount) else null,
            banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
            accountViewModel = accountViewModel,
        )

        DirectoryContents(noteEvent, accountViewModel, nav)
    }
}

/** Renders a kind-32176 Blossom piece index — a titled record of a file, with its size and type. */
@Composable
fun RenderBlossomPieceIndex(
    note: Note,
    accountViewModel: AccountViewModel,
) {
    val noteEvent = note.event as? BlossomPieceIndexEvent ?: return

    LibraryHeader(
        symbol = MaterialSymbols.Storage,
        label = stringRes(R.string.library_blossom_piece),
        title = remember(noteEvent) { noteEvent.titleOrIdentifier() },
        subtitle = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } },
        // Size and type are the two facts that distinguish one file record from another, so they
        // go on the detail line rather than being dropped for lack of a label.
        detail =
            remember(noteEvent) {
                listOfNotNull(noteEvent.type(), noteEvent.size()).takeIf { it.isNotEmpty() }?.joinToString(" · ")
            },
        banner = remember(noteEvent) { noteEvent.image()?.ifBlank { null } },
        accountViewModel = accountViewModel,
    )
}

/**
 * The shelf's contents.
 *
 * A directory lists its items with the same `a`/`e` grammar a publication index uses for its
 * table of contents, so the rows are the same rows — resolving lazily, showing the list's own
 * title until the item arrives.
 */
@Composable
private fun DirectoryContents(
    noteEvent: BookshelfDirectoryEvent,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val items = remember(noteEvent) { noteEvent.items().toImmutableList() }

    if (items.isEmpty()) return

    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        items.take(MAX_PREVIEW_ITEMS).forEachIndexed { index, ref ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            }

            PublicationSectionRow(index + 1, ref, accountViewModel, nav)
        }

        if (items.size > MAX_PREVIEW_ITEMS) {
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            val remaining = items.size - MAX_PREVIEW_ITEMS
            Text(
                text = pluralStringResource(R.plurals.library_directory_items, remaining, remaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
        }
    }
}

/** One header for the three: a banner when there is one, the mark's icon when there is not. */
@Composable
private fun LibraryHeader(
    symbol: MaterialSymbol,
    label: String,
    title: String,
    subtitle: String?,
    detail: String?,
    banner: String?,
    accountViewModel: AccountViewModel,
) {
    Column(MaterialTheme.colorScheme.replyModifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Size5dp)) {
            Icon(
                symbol = symbol,
                contentDescription = null,
                modifier = Modifier.width(16.dp),
                tint = MaterialTheme.colorScheme.grayText,
            )

            Spacer(Modifier.width(Size5dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        Row(Modifier.fillMaxWidth()) {
            LibraryBanner(banner, symbol, accountViewModel)

            Column(Modifier.padding(start = 10.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                subtitle?.let {
                    Spacer(Modifier.padding(top = 2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                detail?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryBanner(
    banner: String?,
    symbol: MaterialSymbol,
    accountViewModel: AccountViewModel,
) {
    val shape = RoundedCornerShape(6.dp)
    val modifier = Modifier.width(BannerWidth).aspectRatio(BANNER_ASPECT).clip(shape)

    Box(modifier) {
        if (banner != null) {
            MyAsyncImage(
                imageUrl = banner,
                contentDescription = stringRes(R.string.preview_card_image_for, banner),
                contentScale = ContentScale.Crop,
                mainImageModifier = Modifier.fillMaxSize(),
                loadedImageModifier = modifier,
                accountViewModel = accountViewModel,
                onLoadingBackground = { LibraryBannerPlaceholder(symbol) },
                onError = { LibraryBannerPlaceholder(symbol) },
            )
        } else {
            LibraryBannerPlaceholder(symbol)
        }
    }
}

@Composable
private fun LibraryBannerPlaceholder(symbol: MaterialSymbol) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = symbol,
            contentDescription = null,
            modifier = Modifier.width(24.dp),
            tint = MaterialTheme.colorScheme.grayText,
        )
    }
}
