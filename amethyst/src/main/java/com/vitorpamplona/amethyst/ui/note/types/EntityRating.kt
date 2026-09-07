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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.subtleBorder
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RatingMark
import kotlin.math.floor
import kotlin.math.roundToInt

private val StarSize = Modifier.size(18.dp)

/**
 * Renders a kind-34259 entity rating: the star row, the thing being rated, and the review body.
 *
 * The kind is generic (see [EntityRatingEvent]), so this degrades rather than blanks: a rating
 * whose mark we have no dedicated card for still shows its stars and its review, and a target we
 * cannot resolve to a cached event still shows a name derived from its identifier.
 */
@Composable
fun RenderEntityRating(
    note: Note,
    makeItShort: Boolean,
    canPreview: Boolean,
    quotesLeft: Int,
    backgroundColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? EntityRatingEvent ?: return

    val stars = remember(noteEvent) { noteEvent.stars() }
    val targetAddress = remember(noteEvent) { noteEvent.targetAddress() }

    Column(Modifier.fillMaxWidth()) {
        stars?.let { RatingStars(it, noteEvent.mark()) }

        if (targetAddress != null) {
            LoadAddressableNote(targetAddress, accountViewModel) { targetNote ->
                targetNote?.let {
                    RatedPublicationCard(
                        targetNote = it,
                        fallbackIdentifier = noteEvent.targetIdentifier(),
                        accountViewModel = accountViewModel,
                        nav = nav,
                    )
                }
            }
        } else {
            // Marks like `hashtag` or `relay` name their target with a plain string rather than a
            // coordinate, so there is no note to load. Showing the identifier is the difference
            // between a review of something and a row of stars floating on its own.
            val identifier = remember(noteEvent) { noteEvent.targetIdentifier() }

            if (identifier.isNotEmpty()) {
                RatedIdentifierRow(identifier)
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
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
 * The score as a row of five stars plus the numeric value, followed by the mark when it is one we
 * do not otherwise show ("books" is already implied by the publication card underneath).
 *
 * Filled and empty stars are the same glyph at different tints: Material Symbols expresses fill
 * through the FILL variable axis rather than through separate codepoints, so `star`,
 * `star_border` and `star_outline` all resolve to U+F09A. Only `star_half` is its own glyph.
 */
@Composable
private fun RatingStars(
    stars: Double,
    mark: String,
) {
    val filled = floor(stars).roundToInt()
    val hasHalf = stars - filled >= 0.25 && filled < EntityRatingEvent.MAX_STARS

    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(EntityRatingEvent.MAX_STARS) { index ->
            val symbol =
                if (index < filled) {
                    MaterialSymbols.Star
                } else if (index == filled && hasHalf) {
                    MaterialSymbols.StarHalf
                } else {
                    MaterialSymbols.StarBorder
                }

            Icon(
                symbol = symbol,
                contentDescription = null,
                modifier = StarSize,
                tint =
                    if (index < filled || (index == filled && hasHalf)) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }

        Spacer(StdHorzSpacer)

        Text(
            text = formatStars(stars),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (mark.isNotEmpty() && mark != RATED_MARK_IMPLIED_BY_CARD) {
            Spacer(StdHorzSpacer)

            Text(
                text = mark,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The rated publication, as a tappable one-line card.
 *
 * [observeNote] both watches the cache and asks the relays for the event, so a rating that arrives
 * before its kind-30040 index fills in once the index does. Until then the card shows the name
 * derived from the coordinate's identifier, which for the slugs publishers use ("wuthering-heights")
 * is already the title.
 */
@Composable
private fun RatedPublicationCard(
    targetNote: AddressableNote,
    fallbackIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(targetNote, accountViewModel)

    val title =
        remember(noteState) {
            (targetNote.event as? PublicationIndexEvent)?.titleOrIdentifier()
                ?: PublicationIndexEvent.humanizeIdentifier(targetNote.address.dTag.ifEmpty { fallbackIdentifier })
        }

    val author = remember(noteState) { (targetNote.event as? PublicationIndexEvent)?.author() }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .clickable {
                    routeFor(targetNote, accountViewModel.account)?.let { nav.nav(it) }
                }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            symbol = MaterialSymbols.MenuBook,
            contentDescription = null,
            modifier = StarSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(StdHorzSpacer)

        Column {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            author?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview
@Composable
fun RatingStarsPreview() {
    ThemeComparisonColumn {
        Column {
            // The four cases the stars() ladder can produce, plus a mark we render no card for.
            RatingStars(5.0, RatingMark.BOOKS)
            RatingStars(3.5, RatingMark.BOOKS)
            RatingStars(1.0, RatingMark.BOOKS)
            RatingStars(4.0, RatingMark.MOVIES)
        }
    }
}

/** The target of a rating whose mark names a plain string (a hashtag, a relay URL) rather than an event. */
@Composable
private fun RatedIdentifierRow(identifier: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 5.dp)
                .clip(shape = QuoteBorder)
                .border(1.dp, MaterialTheme.colorScheme.subtleBorder, QuoteBorder)
                .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = identifier,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "books" is already spelled out by the publication card, so the chip would just repeat it. */
private const val RATED_MARK_IMPLIED_BY_CARD = "books"

/** "4" rather than "4.0", but "4.5" when the score really is fractional. */
private fun formatStars(stars: Double): String {
    val rounded = (stars * 10).roundToInt()
    return if (rounded % 10 == 0) "${rounded / 10}" else "${rounded / 10}.${rounded % 10}"
}
