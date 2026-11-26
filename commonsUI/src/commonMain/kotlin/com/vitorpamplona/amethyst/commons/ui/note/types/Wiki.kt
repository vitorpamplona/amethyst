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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.content.CachedDocuments
import com.vitorpamplona.amethyst.commons.content.Document
import com.vitorpamplona.amethyst.commons.content.NostrInlinePass
import com.vitorpamplona.amethyst.commons.content.PlainText
import com.vitorpamplona.amethyst.commons.content.djot.DjotToDocument
import com.vitorpamplona.amethyst.commons.content.withoutLeadingTitle
import com.vitorpamplona.amethyst.commons.model.ImmutableListOfLists
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.preview_card_image_for
import com.vitorpamplona.amethyst.commons.richtext.CachedRichTextParser
import com.vitorpamplona.amethyst.commons.ui.components.ExpandableTextViewer
import com.vitorpamplona.amethyst.commons.ui.components.LocalMediaCapture
import com.vitorpamplona.amethyst.commons.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.commons.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.commons.ui.content.RenderDocument
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
import com.vitorpamplona.amethyst.commons.wiki.WikiTopics
import com.vitorpamplona.quartz.nip01Core.core.HexKey
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
 * Older articles are AsciiDoc, NIP-54's format before Djot: their first non-blank line is an
 * AsciiDoc document title (`= Title`). That line means nothing in Djot, which has no `=` headings,
 * so no Djot article starts with one.
 */
fun isLegacyAsciiDocWiki(content: String): Boolean {
    val first = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return false
    return first.startsWith("= ") && first.length > 2 && !first[2].isWhitespace()
}

/**
 * A wiki article's [body] as a document: Djot (NIP-54), or the AsciiDoc of an older article. A first
 * heading that repeats the title tag is left out, as the article's header already shows it.
 */
fun wikiArticleDocument(
    event: WikiArticleEvent,
    body: String,
    tags: ImmutableListOfLists<String>,
    callbackUri: String?,
): Document =
    if (isLegacyAsciiDocWiki(body)) {
        CachedDocuments.asciidoc(body, tags, callbackUri, event.pubKey, wikilinkResolver(event.pubKey))
    } else {
        CachedDocuments.djot(body, tags, callbackUri, event.pubKey, wikilinkResolver(event.pubKey))
    }.withoutLeadingTitle(event.title())

/**
 * A wiki article's body, drawn from its own format by the shared document renderer, translated and
 * cropped behind "Show more" like a note.
 */
@Composable
fun RenderWikiArticleBody(
    event: WikiArticleEvent,
    body: String,
    id: String,
    tags: ImmutableListOfLists<String>,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    callbackUri: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    TranslatableRichTextViewer(content = body, id = id, accountViewModel = accountViewModel) { text ->
        ExpandableTextViewer(content = text, modifier = Modifier.fillMaxWidth(), backgroundColor = backgroundColor, id = id) { shown, modifier ->
            val document = remember(event, shown, tags, callbackUri) { wikiArticleDocument(event, shown, tags, callbackUri) }
            val state = remember(shown, tags, callbackUri) { CachedRichTextParser.parseText(shown, tags, callbackUri) }
            RenderDocument(document, state, canPreview, quotesLeft, backgroundColor, callbackUri, accountViewModel, nav, modifier)
        }
    }
}

/**
 * NIP-54 wikilinks: a reference-style link the article doesn't define points to the topic its text
 * names, normalized. Which version to read is the reader's choice, so the link opens the topic's
 * screen, every author's version of it, with this author's first.
 */
fun wikilinkResolver(author: HexKey): (String) -> String? =
    { label ->
        WikiRedirectEvent
            .normalizeSlug(label)
            .ifEmpty { null }
            ?.let { slug -> WikiTopics.uri(slug, author) }
    }

// The body's first words as plain text, for a card without a summary tag. Only the start is parsed:
// a card shows 200 characters, and a long article would otherwise cost a full parse per card.
private fun wikiArticlePreview(event: WikiArticleEvent): String? =
    (if (isLegacyAsciiDocWiki(event.content)) event.content else PlainText.of(DjotToDocument(NostrInlinePass.NONE).parse(event.content.take(1000))))
        .replace('\n', ' ')
        .trim()
        .take(200)
        .ifBlank { null }
