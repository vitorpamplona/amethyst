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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.toImmutableListOfLists
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNote
import com.vitorpamplona.amethyst.ui.components.MyAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.LoadAddressableNote
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size10dp
import com.vitorpamplona.amethyst.ui.theme.Size18dp
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.quartz.experimental.publications.PublicationIndexEvent
import com.vitorpamplona.quartz.experimental.ratings.EntityRatingEvent
import com.vitorpamplona.quartz.experimental.ratings.RatingMark
import kotlin.math.floor
import kotlin.math.roundToInt

private val StarSize = Modifier.size(Size18dp)

// A 2:3 portrait thumbnail — the aspect ratio of a book cover and a film poster alike, which is
// what the two richly-rendered marks need. Hoisted so the shape isn't rebuilt per recomposition.
private val CoverShape = RoundedCornerShape(6.dp)
private val CoverModifier =
    Modifier
        .width(44.dp)
        .height(66.dp)
        .clip(CoverShape)

/**
 * Renders a kind-34259 entity rating: the stars, the thing being rated, and the review body.
 *
 * The kind is generic (see [EntityRatingEvent]), so this degrades in layers rather than blanking.
 * A publication we have cached shows its cover, title, author and summary; one we do not shows a
 * name derived from the coordinate's identifier; a mark that names a plain string (a hashtag, a
 * relay) shows that string; and a rating we cannot score at all still shows its review.
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
    val mark = remember(noteEvent) { noteEvent.mark() }
    val targetAddress = remember(noteEvent) { noteEvent.targetAddress() }

    Column(Modifier.fillMaxWidth()) {
        stars?.let { RatingStars(it, mark) }

        if (targetAddress != null) {
            LoadAddressableNote(targetAddress, accountViewModel) { targetNote ->
                targetNote?.let {
                    RatedPublicationCard(
                        targetNote = it,
                        mark = mark,
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
                RatedTargetCard(mark = mark, title = identifier, subtitle = null, onClick = null)
            }
        }

        if (noteEvent.content.isNotBlank()) {
            val tags = remember(noteEvent) { noteEvent.tags.toImmutableListOfLists() }

            TranslatableRichTextViewer(
                content = noteEvent.content,
                canPreview = canPreview && !makeItShort,
                quotesLeft = quotesLeft,
                modifier = Modifier.fillMaxWidth().padding(top = Size10dp),
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
 * The score, as a row of five stars.
 *
 * Filled and empty stars are the **same glyph** at two tints: Material Symbols expresses fill
 * through the FILL variable axis rather than through separate codepoints, so `star`, `star_border`
 * and `star_outline` all resolve to U+F09A. Only `star_half` is a glyph of its own.
 *
 * The numeral is shown only when the score is fractional — next to five filled stars, a "5" is
 * noise. The whole row carries one merged content description, because five undescribed icons
 * announce nothing to a screen reader.
 */
@Composable
private fun RatingStars(
    stars: Double,
    mark: String,
) {
    val filled = floor(stars).roundToInt()
    val hasHalf = stars - filled >= 0.25 && filled < EntityRatingEvent.MAX_STARS
    val label = formatStars(stars)
    // The glyphs can only show whole and half stars, so anything else would drop precision
    // silently; the numeral carries it. Next to five filled stars a "5" is just noise.
    val showLabel = label.contains('.')
    val a11y = stringRes(R.string.rating_stars_out_of_five, label)

    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(EntityRatingEvent.MAX_STARS) { index ->
            val isHalf = index == filled && hasHalf
            val isOn = index < filled || isHalf

            Icon(
                symbol =
                    when {
                        index < filled -> MaterialSymbols.Star
                        isHalf -> MaterialSymbols.StarHalf
                        else -> MaterialSymbols.StarBorder
                    },
                contentDescription = null,
                modifier = StarSize,
                tint =
                    if (isOn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    },
            )
        }

        if (showLabel) {
            Spacer(StdHorzSpacer)

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // The mark is the only clue to what was rated when the target card cannot name it, but it
        // would just repeat the icon and title of a card that can.
        if (mark !in MARKS_WITH_A_CARD) {
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
 * The rated publication as a cover-led card.
 *
 * [observeNote] both watches the cache and asks the relays for the event, so a rating that arrives
 * before its kind-30040 index fills in once the index does. Until then the title falls back to the
 * coordinate's identifier, which for the slugs publishers use ("wuthering-heights") already reads
 * as a title.
 */
@Composable
private fun RatedPublicationCard(
    targetNote: AddressableNote,
    mark: String,
    fallbackIdentifier: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteState by observeNote(targetNote, accountViewModel)
    val publication = noteState.note.event as? PublicationIndexEvent

    val title =
        remember(noteState) {
            publication?.titleOrIdentifier()
                ?: PublicationIndexEvent.humanizeIdentifier(targetNote.address.dTag.ifEmpty { fallbackIdentifier })
        }

    val subtitle = remember(noteState) { publication?.author() ?: publication?.summary() }
    val cover = remember(noteState) { publication?.image() }

    RatedTargetCard(
        mark = mark,
        title = title,
        subtitle = subtitle,
        onClick = { routeFor(targetNote, accountViewModel.account)?.let { nav.nav(it) } },
        cover = {
            if (cover != null) {
                Box(CoverModifier) {
                    MyAsyncImage(
                        imageUrl = cover,
                        contentDescription = stringRes(R.string.preview_card_image_for, cover),
                        contentScale = ContentScale.Crop,
                        mainImageModifier = Modifier.fillMaxSize(),
                        loadedImageModifier = CoverModifier,
                        accountViewModel = accountViewModel,
                        onLoadingBackground = { CoverPlaceholder(mark) },
                        onError = { CoverPlaceholder(mark) },
                    )
                }
            } else {
                CoverPlaceholder(mark)
            }
        },
    )
}

/**
 * One card shape for every kind of rated thing: a cover when there is one, the mark's icon when
 * there is not, and a title plus optional second line. Shared so the resolvable and unresolvable
 * cases cannot drift apart visually.
 */
@Composable
private fun RatedTargetCard(
    mark: String,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    cover: @Composable () -> Unit = { CoverPlaceholder(mark) },
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Size10dp)
                .clip(QuoteBorder)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(Size10dp)
                .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cover()

        Spacer(Modifier.width(Size10dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Size5dp))

                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The cover slot when there is no artwork: the mark's icon on a tinted plate, same size as a cover. */
@Composable
private fun CoverPlaceholder(mark: String) {
    Box(
        modifier = CoverModifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            symbol = iconForMark(mark),
            contentDescription = null,
            modifier = StarSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The marks whose target card already names and pictures the thing, so the chip would repeat it. */
private val MARKS_WITH_A_CARD = setOf(RatingMark.BOOKS, RatingMark.MOVIES)

private fun iconForMark(mark: String): MaterialSymbol =
    when (mark) {
        RatingMark.BOOKS -> MaterialSymbols.MenuBook
        RatingMark.MOVIES -> MaterialSymbols.Movie
        RatingMark.PROFILE -> MaterialSymbols.Person
        RatingMark.RELAY -> MaterialSymbols.Dns
        RatingMark.HASHTAG -> MaterialSymbols.Tag
        else -> MaterialSymbols.AutoMirrored.Article
    }

/** "4.5" for a fractional score, "4" for a whole one. */
private fun formatStars(stars: Double): String {
    val rounded = (stars * 10).roundToInt()
    return if (rounded % 10 == 0) "${rounded / 10}" else "${rounded / 10}.${rounded % 10}"
}

@Preview
@Composable
fun RatingStarsPreview() {
    ThemeComparisonColumn {
        Column {
            // Every shape the stars() ladder can produce, plus a mark that gets no card.
            RatingStars(5.0, RatingMark.BOOKS)
            RatingStars(3.5, RatingMark.BOOKS)
            RatingStars(1.0, RatingMark.BOOKS)
            RatingStars(4.0, RatingMark.HASHTAG)
        }
    }
}

@Preview
@Composable
fun RatedTargetCardPreview() {
    ThemeComparisonColumn {
        Column {
            RatedTargetCard(
                mark = RatingMark.BOOKS,
                title = "Wuthering Heights",
                subtitle = "Emily Bronte",
                onClick = {},
            )
            RatedTargetCard(
                mark = RatingMark.BOOKS,
                title = "A publication whose title is long enough to need a second line and then some",
                subtitle = "Collection of selected fables from the ancient Greek philosopher, known as Aesop.",
                onClick = {},
            )
            RatedTargetCard(
                mark = RatingMark.HASHTAG,
                title = "bookstr",
                subtitle = null,
                onClick = null,
            )
        }
    }
}
