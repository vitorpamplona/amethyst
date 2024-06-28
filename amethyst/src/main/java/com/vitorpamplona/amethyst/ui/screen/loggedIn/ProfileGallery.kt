/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment.Companion.BottomStart
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import coil.compose.AsyncImage
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.components.SensitivityWarning
import com.vitorpamplona.amethyst.ui.note.CheckHiddenFeedWatchBlockAndReport
import com.vitorpamplona.amethyst.ui.note.ClickableNote
import com.vitorpamplona.amethyst.ui.note.LongPressToQuickAction
import com.vitorpamplona.amethyst.ui.note.WatchAuthor
import com.vitorpamplona.amethyst.ui.note.WatchNoteEvent
import com.vitorpamplona.amethyst.ui.note.calculateBackgroundColor
import com.vitorpamplona.amethyst.ui.note.elements.BannerImage
import com.vitorpamplona.amethyst.ui.theme.HalfPadding
import com.vitorpamplona.amethyst.ui.theme.QuoteBorder
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.quartz.events.GalleryListEvent

// TODO This is to large parts from the ChannelCardCompose
// Why does it not be in a grid, like the marketplace
@Composable
fun ProfileGallery(
    baseNotes: List<GalleryThumb>,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    isHiddenFeed: Boolean = false,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    for (thumb in baseNotes) {
        thumb.baseNote?.let {
            WatchNoteEvent(baseNote = it, accountViewModel = accountViewModel) {
                if (thumb.baseNote.event?.kind() == GalleryListEvent.KIND) {
                    CheckHiddenFeedWatchBlockAndReport(
                        note = thumb.baseNote,
                        modifier = modifier,
                        ignoreAllBlocksAndReports = isHiddenFeed,
                        showHiddenWarning = false,
                        accountViewModel = accountViewModel,
                        nav = nav,
                    ) { canPreview ->

                        thumb.image?.let { it1 ->
                            GalleryCard(
                                baseNote = thumb.baseNote,
                                url = it1,
                                modifier = modifier,
                                parentBackgroundColor = parentBackgroundColor,
                                accountViewModel = accountViewModel,
                                nav = nav,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryCard(
    baseNote: Note,
    url: String,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    // baseNote.event?.let { Text(text = it.pubKey()) }
    LongPressToQuickAction(baseNote = baseNote, accountViewModel = accountViewModel) { showPopup ->

        CheckNewAndRenderChannelCard(
            baseNote,
            url,
            modifier,
            parentBackgroundColor,
            accountViewModel,
            showPopup,
            nav,
        )
    }
}

@Composable
private fun CheckNewAndRenderChannelCard(
    baseNote: Note,
    url: String,
    modifier: Modifier = Modifier,
    parentBackgroundColor: MutableState<Color>? = null,
    accountViewModel: AccountViewModel,
    showPopup: () -> Unit,
    nav: (String) -> Unit,
) {
    val backgroundColor =
        calculateBackgroundColor(
            createdAt = baseNote.createdAt(),
            parentBackgroundColor = parentBackgroundColor,
            accountViewModel = accountViewModel,
        )

    ClickableNote(
        baseNote = baseNote,
        backgroundColor = backgroundColor,
        modifier = modifier,
        accountViewModel = accountViewModel,
        showPopup = showPopup,
        nav = nav,
    ) {
        // baseNote.event?.let { Text(text = it.pubKey()) }
        InnerGalleryCardWithReactions(
            baseNote = baseNote,
            url = url,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@Composable
fun InnerGalleryCardWithReactions(
    baseNote: Note,
    url: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    InnerGalleryCardBox(baseNote, url, accountViewModel, nav)
}

@Composable
fun InnerGalleryCardBox(
    baseNote: Note,
    url: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    Column(HalfPadding) {
        SensitivityWarning(
            note = baseNote,
            accountViewModel = accountViewModel,
        ) {
            RenderGalleryThumb(baseNote, url, accountViewModel, nav)
        }
    }
}

@Immutable
data class GalleryThumb(
    val baseNote: Note?,
    val id: String?,
    val image: String?,
    val title: String?,
    // val price: Price?,
)

@Composable
fun RenderGalleryThumb(
    baseNote: Note,
    url: String,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteEvent = baseNote.event as? GalleryListEvent ?: return

    val card by
        baseNote
            .live()
            .metadata
            .map {
                val noteEvent = baseNote.event as GalleryListEvent

                GalleryThumb(
                    baseNote = baseNote,
                    id = "",
                    image = url,
                    title = "Hello",
                    // noteEvent?.title(),
                    // price = noteEvent?.price(),
                )
            }.distinctUntilChanged()
            .observeAsState(
                GalleryThumb(
                    baseNote = baseNote,
                    id = "",
                    image = "https://gokaygokay-aurasr.hf.space/file=/tmp/gradio/68292f324a38d7071453cf6912dfb1da9d1305c8/image3.png",
                    title = "Hello",
                    // image = noteEvent.image(),
                    // title = noteEvent.title(),
                    // price = noteEvent.price(),
                ),
            )

    InnerRenderGalleryThumb(card as GalleryThumb, baseNote)
}

@Preview
@Composable
fun RenderGalleryThumbPreview() {
    Surface(Modifier.size(200.dp)) {
        InnerRenderGalleryThumb(
            card =
                GalleryThumb(
                    baseNote = null,
                    id = "",
                    image = null,
                    title = "Like New",
                    // price = Price("800000", "SATS", null),
                ),
            note = Note("hex"),
        )
    }
}

@Composable
fun InnerRenderGalleryThumb(
    card: GalleryThumb,
    note: Note,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        contentAlignment = BottomStart,
    ) {
        card.image?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } ?: run { DisplayGalleryAuthorBanner(note) }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.6f))
                .padding(Size5dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            card.title?.let {
                Text(
                    text = it,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
            /*
            card.price?.let {
                val priceTag =
                    remember(card) {
                        val newAmount = it.amount.toBigDecimalOrNull()?.let { showAmountAxis(it) } ?: it.amount

                        if (it.frequency != null && it.currency != null) {
                            "$newAmount ${it.currency}/${it.frequency}"
                        } else if (it.currency != null) {
                            "$newAmount ${it.currency}"
                        } else {
                            newAmount
                        }
                    }

                Text(
                    text = priceTag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }*/
        }
    }
}

@Composable
fun DisplayGalleryAuthorBanner(note: Note) {
    WatchAuthor(note) {
        BannerImage(
            it,
            Modifier
                .fillMaxSize()
                .clip(QuoteBorder),
        )
    }
}
