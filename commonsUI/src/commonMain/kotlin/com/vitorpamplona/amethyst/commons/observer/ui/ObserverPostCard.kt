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
package com.vitorpamplona.amethyst.commons.observer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.observer.ObserverDesk
import com.vitorpamplona.amethyst.commons.observer.ObserverDetail
import com.vitorpamplona.amethyst.commons.observer.ObserverStory
import com.vitorpamplona.amethyst.commons.observer.ObserverStorySize
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.observer_detail_all_day
import com.vitorpamplona.amethyst.commons.resources.observer_detail_duration
import com.vitorpamplona.amethyst.commons.resources.observer_detail_location
import com.vitorpamplona.amethyst.commons.resources.observer_detail_price_per
import com.vitorpamplona.amethyst.commons.resources.observer_detail_quoting
import com.vitorpamplona.amethyst.commons.resources.observer_detail_source
import com.vitorpamplona.amethyst.commons.resources.observer_detail_starts
import com.vitorpamplona.amethyst.commons.resources.observer_detail_status
import com.vitorpamplona.amethyst.commons.resources.observer_detail_watching
import com.vitorpamplona.amethyst.commons.resources.observer_from_the_post
import com.vitorpamplona.amethyst.commons.resources.observer_on_air_since
import com.vitorpamplona.amethyst.commons.resources.observer_written_on_device
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.note.UserPicture
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event

private val Serif = FontFamily.Serif

/**
 * One news item as its own post — built on the device from the source event,
 * never signed and never published. The paper is a feed of these, sized by
 * what each story earned ([ObserverStory.size]): a LARGE post carries a big
 * headline, the full text and a full-width picture; a MEDIUM post a headline,
 * a few lines and a thumbnail; a SMALL post a line or two.
 *
 * Every post wears its author's real face and name (the press caches their
 * profiles), the avatar opens their profile, and the card opens the thread.
 */
@Composable
fun ObserverPostCard(
    story: ObserverStory,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpen: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpen(story.event) },
    ) {
        when (story.size) {
            ObserverStorySize.LARGE -> LargePost(story, accountViewModel, nav)
            ObserverStorySize.MEDIUM -> MediumPost(story, accountViewModel, nav)
            ObserverStorySize.SMALL -> SmallPost(story, accountViewModel, nav)
        }
    }
}

@Composable
private fun LargePost(
    story: ObserverStory,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.padding(14.dp)) {
        PostHeader(story, 40.dp, accountViewModel, nav)
        Spacer(Modifier.height(10.dp))
        val written = story.written
        if (written != null) {
            // The model's headline and summary lead; the author's own words follow as the evidence.
            Text(written.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp)
            Spacer(Modifier.height(6.dp))
            Text(written.summary, fontFamily = Serif, style = MaterialTheme.typography.bodyLarge)
            WrittenLabel()
            FromThePost(story, maxLines = 8)
        } else if (story.desk == ObserverDesk.HIGHLIGHTS) {
            Quote(story.headline, large = true)
        } else {
            Text(story.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 29.sp)
        }
        if (written == null && story.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(story.body, fontFamily = Serif, style = MaterialTheme.typography.bodyLarge, maxLines = 14, overflow = TextOverflow.Ellipsis)
        }
        story.imageUrl?.let {
            Spacer(Modifier.height(10.dp))
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)),
            )
        }
        Details(story)
        Signals(story, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MediumPost(
    story: ObserverStory,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column(Modifier.padding(12.dp)) {
        PostHeader(story, 32.dp, accountViewModel, nav)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                val written = story.written
                if (written != null) {
                    Text(written.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(written.summary, fontFamily = Serif, style = MaterialTheme.typography.bodyMedium)
                    WrittenLabel()
                    FromThePost(story, maxLines = 3)
                } else if (story.desk == ObserverDesk.HIGHLIGHTS) {
                    Quote(story.headline, large = false)
                } else {
                    Text(story.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                if (written == null && story.body.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        story.body,
                        fontFamily = Serif,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            story.imageUrl?.let { Thumbnail(it, 88.dp) }
        }
        Details(story)
        Signals(story, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun SmallPost(
    story: ObserverStory,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        UserPicture(userHex = story.author, size = 24.dp, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            Text(
                story.written?.headline ?: story.headline,
                fontFamily = Serif,
                fontStyle = if (story.desk == ObserverDesk.HIGHLIGHTS) FontStyle.Italic else FontStyle.Normal,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                story.byline + " · " + timeAgoNoDot(story.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Signals(story)
    }
}

/** Photographs are pictures first: a square image with the caption under it. */
@Composable
fun ObserverPhotoPost(
    story: ObserverStory,
    accountViewModel: AccountViewModel,
    nav: INav,
    onOpen: (Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onOpen(story.event) },
    ) {
        Column {
            story.imageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = story.headline.takeIf { h -> h.isNotBlank() },
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
            Column(Modifier.padding(10.dp)) {
                PostHeader(story, 24.dp, accountViewModel, nav)
                if (story.headline.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(story.headline, fontFamily = Serif, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun PostHeader(
    story: ObserverStory,
    avatar: Dp,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UserPicture(userHex = story.author, size = avatar, accountViewModel = accountViewModel, nav = nav)
        Column(Modifier.weight(1f)) {
            Text(story.byline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                timeAgoNoDot(story.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Says plainly that the headline and summary above were written by a model on this phone,
 * not by the author — a reader should never have to guess which words are whose.
 */
@Composable
fun WrittenLabel(modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(MaterialSymbols.AutoAwesome, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringRes(Res.string.observer_written_on_device), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The author's own words under a written summary, so every summary carries its source. */
@Composable
private fun FromThePost(
    story: ObserverStory,
    maxLines: Int,
) {
    val original = listOf(story.headline, story.body).filter { it.isNotBlank() }.joinToString(" ")
    if (original.isBlank()) return
    Spacer(Modifier.height(8.dp))
    Text(stringRes(Res.string.observer_from_the_post).uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.height(IntrinsicSize.Min).padding(top = 2.dp)) {
        Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.width(8.dp))
        Text(
            original,
            fontFamily = Serif,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A highlight is somebody else's sentence, so it runs as a pull quote. */
@Composable
private fun Quote(
    text: String,
    large: Boolean,
) {
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(10.dp))
        Text(
            "“$text”",
            fontFamily = Serif,
            fontStyle = FontStyle.Italic,
            style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Thumbnail(
    url: String,
    size: Dp,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)),
    )
}

/** The signals that ranked the story; nothing is drawn for zero, so a quiet post stays quiet. */
@Composable
private fun Signals(
    story: ObserverStory,
    modifier: Modifier = Modifier,
) {
    if (story.score <= 0) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Signal(MaterialSymbols.Reply, story.replies)
        Signal(MaterialSymbols.Bolt, story.zaps)
        Signal(MaterialSymbols.Sync, story.reposts)
        Signal(MaterialSymbols.Favorite, story.reactions)
    }
}

@Composable
private fun Signal(
    symbol: MaterialSymbol,
    count: Int,
) {
    if (count <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Details(story: ObserverStory) {
    if (story.details.isEmpty()) return
    Column(Modifier.padding(top = 4.dp)) {
        story.details.forEach { detail ->
            Text(
                observerDetailText(detail, story.event.kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One fact in the reader's words: a price, a date, an audience. Shared by posts and roundups. */
@Composable
fun observerDetailText(
    detail: ObserverDetail,
    eventKind: Int,
): String {
    val timeOfDay = rememberTimeOfDayFormatter()
    return when (detail) {
        is ObserverDetail.Price -> {
            val amount = listOfNotNull(detail.amount, detail.currency).joinToString(" ")
            detail.frequency?.let { stringRes(Res.string.observer_detail_price_per, amount, it) } ?: amount
        }

        is ObserverDetail.Starts -> {
            val epoch = detail.epochSeconds
            when {
                epoch == null -> {
                    stringRes(Res.string.observer_detail_all_day, detail.date ?: "")
                }

                // A 24/7 stream can have started days ago: the date is part of the fact.
                eventKind == 30311 -> {
                    stringRes(Res.string.observer_on_air_since, formatLongDate(epoch) + " · " + timeOfDay(epoch * 1000))
                }

                else -> {
                    stringRes(Res.string.observer_detail_starts, formatLongDate(epoch) + " · " + timeOfDay(epoch * 1000))
                }
            }
        }

        is ObserverDetail.Location -> {
            stringRes(Res.string.observer_detail_location, detail.text)
        }

        is ObserverDetail.Duration -> {
            val minutes = detail.seconds / 60
            val seconds = detail.seconds % 60
            stringRes(Res.string.observer_detail_duration, if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s")
        }

        is ObserverDetail.PollOptions -> {
            detail.options.joinToString("  ·  ")
        }

        is ObserverDetail.QuotedAuthor -> {
            stringRes(Res.string.observer_detail_quoting, detail.name)
        }

        is ObserverDetail.Source -> {
            stringRes(Res.string.observer_detail_source, detail.text)
        }

        is ObserverDetail.Watching -> {
            pluralStringRes(Res.plurals.observer_detail_watching, detail.count, detail.count)
        }

        is ObserverDetail.ItemStatus -> {
            stringRes(Res.string.observer_detail_status, detail.text)
        }
    }
}
