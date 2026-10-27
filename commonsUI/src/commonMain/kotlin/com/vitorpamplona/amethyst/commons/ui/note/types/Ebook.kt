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
package com.vitorpamplona.amethyst.commons.ui.note.types

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.commons.relayClient.event.observeNoteEvent
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.ebook_open_file
import com.vitorpamplona.amethyst.commons.resources.ebook_read
import com.vitorpamplona.amethyst.commons.richtext.MediaContentKind
import com.vitorpamplona.amethyst.commons.richtext.MediaUrlPdf
import com.vitorpamplona.amethyst.commons.richtext.RichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DisplayUncitedHashtags
import com.vitorpamplona.amethyst.commons.ui.note.platform.PdfViewerDialog
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.commons.ui.theme.grayText
import com.vitorpamplona.amethyst.commons.util.countToHumanReadableBytes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.pressstr.ebook.EbookEvent

/**
 * Renders a kind-33953 PressStr ebook: its cover, title and authors, what the file is, the blurb,
 * and a button that opens the book.
 *
 * A PDF opens in the in-app reader. The feed deliberately does not draw the inline first-page
 * preview a PDF link gets: that downloads the whole book to show a page the `image` cover already
 * shows. Anything else (EPUB) goes to the system, since no reader in the app can show it.
 */
@Composable
fun RenderEbook(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    // Observed, not read once: 33953 is addressable, so a revised ebook replaces the event on the
    // same Note instance, and a bare read of `event` registers no snapshot dependency.
    val observedEvent by observeNoteEvent<EbookEvent>(note, accountViewModel)
    val noteEvent = observedEvent ?: return

    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }
    val authors = remember(noteEvent) { noteEvent.authors().joinToString(", ").ifBlank { null } }
    val summary = remember(noteEvent) { noteEvent.summary()?.ifBlank { null } }
    val cover = remember(noteEvent) { noteEvent.image()?.ifBlank { null } }
    val url = remember(noteEvent) { noteEvent.url()?.ifBlank { null } }
    // What a reader decides on before opening a book: its format, size, language and age.
    val detail =
        remember(noteEvent) {
            listOfNotNull(
                noteEvent.formatLabel(),
                noteEvent.size()?.let { countToHumanReadableBytes(it) },
                noteEvent.language()?.uppercase(),
                noteEvent.publishedAt()?.let { formatLongDate(it) },
            ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        }
    // The PDF the in-app reader opens, or null when the file is something it cannot show.
    val pdf =
        remember(noteEvent) {
            url?.takeIf { RichTextParser.classifyMedia(it, noteEvent.mimeType()) == MediaContentKind.PDF }?.let {
                MediaUrlPdf(url = it, description = noteEvent.title(), hash = noteEvent.hash(), uri = note.toNostrUri(), mimeType = noteEvent.mimeType())
            }
        }

    var reading by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val open: (() -> Unit)? =
        url?.let {
            { if (pdf != null) reading = true else uriHandler.openUri(it) }
        }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Column(if (open != null) Modifier.clickable(onClick = open) else Modifier) {
                PublicationCover(cover, accountViewModel)
            }

            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                authors?.let {
                    Spacer(Modifier.padding(top = 4.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.grayText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                detail?.let {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.grayText,
                    )
                }

                summary?.let {
                    Spacer(Modifier.padding(top = 8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth(),
                tags = tags,
                backgroundColor = backgroundColor,
                id = note.idHex,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        open?.let {
            FilledTonalButton(onClick = it, modifier = Modifier.padding(top = 6.dp)) {
                Icon(
                    symbol = MaterialSymbols.MenuBook,
                    contentDescription = null,
                    modifier = Size18Modifier,
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringRes(if (pdf != null) Res.string.ebook_read else Res.string.ebook_open_file))
            }
        }

        DisplayUncitedHashtags(noteEvent, note.toNostrUri(), accountViewModel, nav)
    }

    if (reading && pdf != null) {
        PdfViewerDialog(pdf, onDismiss = { reading = false }, accountViewModel = accountViewModel)
    }
}

/** "PDF", "EPUB", or the MIME subtype as published when it is something else. */
private fun EbookEvent.formatLabel(): String? =
    when {
        isPdf() -> "PDF"
        isEpub() -> "EPUB"
        else -> mimeType()?.substringAfter('/')?.ifBlank { null }?.uppercase()
    }
