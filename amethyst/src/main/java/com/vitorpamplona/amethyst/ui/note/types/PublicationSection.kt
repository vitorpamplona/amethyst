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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.ui.components.markdown.RenderContentAsMarkdown
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.experimental.publications.AsciiDocToMarkdown
import com.vitorpamplona.quartz.experimental.publications.PublicationContentEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip54Wiki.WikiNoteEvent

/**
 * Renders a kind-30041 NKBIP-01 publication section — a chapter, zettel or episode.
 *
 * Unlike the kind-30040 index this one *is* prose, so it renders as a titled body.
 *
 * NKBIP-01 says the body may be **AsciiDoc**, and for this kind the reference implementation
 * treats it as AsciiDoc unconditionally rather than sniffing — so we do the same. The body goes
 * through [AsciiDocToMarkdown] and then the same CommonMark renderer that draws kind-30023
 * long-form, which brings media, imeta and `nostr:` link handling with it for free. See that
 * converter for what it does and does not cover; anything it does not recognize is passed
 * through as the plain text it already was.
 */
@Composable
fun RenderPublicationSection(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? PublicationContentEvent ?: return

    val title = remember(noteEvent) { noteEvent.titleOrIdentifier() }

    // Converting is a pure string pass over the whole body, so it is remembered per event
    // rather than redone on every recomposition of a scrolling feed.
    val markdown = remember(noteEvent) { AsciiDocToMarkdown.convert(noteEvent.content, wikilinkResolver(noteEvent)) }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (markdown.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            RenderContentAsMarkdown(
                content = markdown,
                tags = tags,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                backgroundColor = backgroundColor,
                callbackUri = note.toNostrUri(),
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }
    }
}

/**
 * Turns a `[[target]]` into a `nostr:` URI using the section's `wikilink` tags, so a reference
 * opens the page inside Amethyst instead of leaving for someone's web wiki.
 *
 * Preference order matters: an event id addresses one exact revision, whereas a coordinate
 * addresses "whatever that author's page says now". The tag tells us which it meant.
 *
 * A target with no usable tag returns null, and the converter falls back to the bare label —
 * better than a dead link to a page we cannot name.
 */
private fun wikilinkResolver(event: PublicationContentEvent): (String) -> String? =
    { target ->
        val link = event.wikilinkFor(target)

        when {
            link == null -> null

            link.eventId != null ->
                "nostr:" + NEvent.create(link.eventId!!, link.pubKey, WikiNoteEvent.KIND, link.relay)

            link.pubKey != null ->
                "nostr:" +
                    NAddress.create(
                        WikiNoteEvent.KIND,
                        link.pubKey!!,
                        PublicationContentEvent.normalizeWikilink(link.target),
                        link.relay,
                    )

            else -> null
        }
    }
