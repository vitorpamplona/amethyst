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

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.richtext.CachedAsciiDocToMarkdown
import com.vitorpamplona.amethyst.commons.richtext.CachedDjotToMarkdown
import com.vitorpamplona.amethyst.commons.richtext.DjotToMarkdown
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeader
import com.vitorpamplona.amethyst.commons.ui.note.elements.DefaultImageHeaderBackground
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.commons.ui.theme.Size5dp
import com.vitorpamplona.amethyst.commons.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.commons.ui.theme.imageModifier
import com.vitorpamplona.amethyst.commons.ui.theme.subtleBorder
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.experimental.publications.AsciiDocToMarkdown
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip54Wiki.WikiArticleEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiRedirectEvent

@Composable
fun RenderWikiContent(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? WikiArticleEvent ?: return

    WikiArticleHeader(noteEvent, note, accountViewModel, nav)
}

@Composable
private fun WikiArticleHeader(
    noteEvent: WikiArticleEvent,
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val title = remember(noteEvent) { noteEvent.title() }
    val summary =
        remember(noteEvent) {
            noteEvent.summary()?.ifBlank { null } ?: wikiArticlePreview(noteEvent)
        }
    val image = remember(noteEvent) { noteEvent.image() }

    Row(
        modifier =
            Modifier
                .padding(top = Size5dp)
                .clip(shape = QuoteBorder)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.subtleBorder,
                    QuoteBorder,
                ),
    ) {
        Column {
            if (LocalMediaCapture.current != null || accountViewModel.settings.showUrlPreview()) {
                image?.let {
                    MyAsyncImage(
                        imageUrl = it,
                        contentDescription =
                            stringRes(
                                Res.string.preview_card_image_for,
                                it,
                            ),
                        contentScale = ContentScale.FillWidth,
                        mainImageModifier = Modifier,
                        loadedImageModifier = MaterialTheme.colorScheme.imageModifier,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = { DefaultImageHeaderBackground(note, accountViewModel) },
                        onError = { DefaultImageHeader(note, accountViewModel) },
                    )
                } ?: run {
                    DefaultImageHeader(note, accountViewModel)
                }
            }

            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, top = 10.dp),
                )
            }

            summary?.let {
                Spacer(modifier = StdVertSpacer)
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    color = Color.Gray,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The markdown a wiki article's Djot body (NIP-54) renders as. It is also the exact string handed
 * to the translator, and so the key the translation cache stores the result under: the copy-text
 * menus look the translation up through [displayedNoteText], which calls this for an article.
 */
fun wikiArticleMarkdown(
    event: WikiArticleEvent,
    body: String = event.content,
): String =
    when {
        // Older articles are AsciiDoc, NIP-54's format before Djot: the publications' converter reads them.
        DjotToMarkdown.isLegacyAsciiDoc(body) ->
            if (body == event.content) {
                CachedAsciiDocToMarkdown.convert(event.id, body, wikilinkResolver(event))
            } else {
                AsciiDocToMarkdown.convert(body, wikilinkResolver(event))
            }
        body == event.content -> CachedDjotToMarkdown.convert(event.id, body, wikilinkResolver(event))
        // A body other than the event's own (an edit shown in its place) is not cached under its id.
        else -> DjotToMarkdown.convert(body, wikilinkResolver(event))
    }

/**
 * NIP-54 wikilinks: a reference-style link the article doesn't define points to the wiki article
 * whose `d` tag is the link's text, normalized. Which version to read is the reader's choice; with
 * no screen yet that lists every author's version of a topic, the link opens this author's own.
 */
private fun wikilinkResolver(event: WikiArticleEvent): (String) -> String? =
    { label ->
        WikiRedirectEvent
            .normalizeSlug(label)
            .ifEmpty { null }
            ?.let { slug -> NAddress.createNostrUriOrNull(WikiArticleEvent.KIND, event.pubKey, slug, null) }
    }

// The body's first words as plain text, for a card without a summary tag. Only the start is parsed:
// a card shows 200 characters, and a long article would otherwise cost a full parse per card.
private fun wikiArticlePreview(event: WikiArticleEvent): String? =
    (if (DjotToMarkdown.isLegacyAsciiDoc(event.content)) event.content else DjotToMarkdown.plainText(event.content.take(1000)))
        .replace('\n', ' ')
        .trim()
        .take(200)
        .ifBlank { null }
