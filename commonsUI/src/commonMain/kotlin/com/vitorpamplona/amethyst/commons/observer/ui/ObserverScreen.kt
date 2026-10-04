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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.observer.ObserverDetail
import com.vitorpamplona.amethyst.commons.observer.ObserverEdition
import com.vitorpamplona.amethyst.commons.observer.ObserverPress
import com.vitorpamplona.amethyst.commons.observer.ObserverSection
import com.vitorpamplona.amethyst.commons.observer.ObserverSectionKind
import com.vitorpamplona.amethyst.commons.observer.ObserverStory
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.observer
import com.vitorpamplona.amethyst.commons.resources.observer_day_notes
import com.vitorpamplona.amethyst.commons.resources.observer_detail_all_day
import com.vitorpamplona.amethyst.commons.resources.observer_detail_duration
import com.vitorpamplona.amethyst.commons.resources.observer_detail_location
import com.vitorpamplona.amethyst.commons.resources.observer_detail_price_per
import com.vitorpamplona.amethyst.commons.resources.observer_detail_quoting
import com.vitorpamplona.amethyst.commons.resources.observer_detail_source
import com.vitorpamplona.amethyst.commons.resources.observer_detail_starts
import com.vitorpamplona.amethyst.commons.resources.observer_detail_status
import com.vitorpamplona.amethyst.commons.resources.observer_detail_watching
import com.vitorpamplona.amethyst.commons.resources.observer_edition_code
import com.vitorpamplona.amethyst.commons.resources.observer_empty
import com.vitorpamplona.amethyst.commons.resources.observer_failed
import com.vitorpamplona.amethyst.commons.resources.observer_intro_body
import com.vitorpamplona.amethyst.commons.resources.observer_intro_title
import com.vitorpamplona.amethyst.commons.resources.observer_masthead
import com.vitorpamplona.amethyst.commons.resources.observer_motto
import com.vitorpamplona.amethyst.commons.resources.observer_no_lens_title
import com.vitorpamplona.amethyst.commons.resources.observer_on_air_since
import com.vitorpamplona.amethyst.commons.resources.observer_print
import com.vitorpamplona.amethyst.commons.resources.observer_printing
import com.vitorpamplona.amethyst.commons.resources.observer_ranked_as
import com.vitorpamplona.amethyst.commons.resources.observer_reprint
import com.vitorpamplona.amethyst.commons.resources.observer_section_apps
import com.vitorpamplona.amethyst.commons.resources.observer_section_calendar
import com.vitorpamplona.amethyst.commons.resources.observer_section_classifieds
import com.vitorpamplona.amethyst.commons.resources.observer_section_code
import com.vitorpamplona.amethyst.commons.resources.observer_section_highlights
import com.vitorpamplona.amethyst.commons.resources.observer_section_live
import com.vitorpamplona.amethyst.commons.resources.observer_section_long_reads
import com.vitorpamplona.amethyst.commons.resources.observer_section_photos
import com.vitorpamplona.amethyst.commons.resources.observer_section_polls
import com.vitorpamplona.amethyst.commons.resources.observer_section_video
import com.vitorpamplona.amethyst.commons.resources.observer_section_wiki
import com.vitorpamplona.amethyst.commons.resources.observer_section_wire
import com.vitorpamplona.amethyst.commons.resources.observer_stats_signals
import com.vitorpamplona.amethyst.commons.resources.observer_stats_stories
import com.vitorpamplona.amethyst.commons.resources.observer_top_stories
import com.vitorpamplona.amethyst.commons.resources.observer_trending
import com.vitorpamplona.amethyst.commons.resources.observer_try_again
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.commons.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.commons.ui.note.rememberTimeOfDayFormatter
import com.vitorpamplona.amethyst.commons.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.commons.ui.pluralStringRes
import com.vitorpamplona.amethyst.commons.ui.screen.loggedIn.calendars.formatLongDate
import com.vitorpamplona.amethyst.commons.ui.stringRes
import com.vitorpamplona.amethyst.commons.viewmodels.AccountViewModel
import org.jetbrains.compose.resources.StringResource

private val Serif = FontFamily.Serif

/**
 * The Nostr Observer, as a native screen: today's front page laid out from the
 * reader's own web-of-trust lens by [ObserverPress], read like a newspaper and
 * tapped through to the real notes.
 */
@Composable
fun ObserverScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val press = accountViewModel.account.observerPress
    val state by press.state.collectAsStateWithLifecycle()
    val edition by press.edition.collectAsStateWithLifecycle()

    // The paper is in front of the reader: the floating banner steps aside and a
    // finished edition counts as read.
    DisposableEffect(press) {
        press.readerIsLooking.value = true
        onDispose { press.readerIsLooking.value = false }
    }
    LaunchedEffect(state) { if (state is ObserverPress.State.Ready) press.markSeen() }

    val openStory: (ObserverStory) -> Unit = { story ->
        nav.nav { routeFor(story.event, accountViewModel.account) }
    }

    Scaffold(
        topBar = {
            TopBarWithBackButton(stringRes(Res.string.observer), nav) {
                if (edition != null && state !is ObserverPress.State.Printing) {
                    IconButton(onClick = press::print) {
                        Icon(MaterialSymbols.Refresh, contentDescription = stringRes(Res.string.observer_reprint))
                    }
                }
            }
        },
    ) { padding ->
        val current = edition
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (current == null) {
                Cover(state, onPrint = press::print, modifier = Modifier.widthIn(max = 720.dp))
            } else {
                Paper(current, state, onPrint = press::print, onOpen = openStory, modifier = Modifier.widthIn(max = 720.dp))
            }
        }
    }
}

/** Before the first edition: what this is, a button, and the press's state. */
@Composable
private fun Cover(
    state: ObserverPress.State,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Masthead()
        Text(
            stringRes(Res.string.observer_intro_title),
            fontFamily = Serif,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            stringRes(Res.string.observer_intro_body),
            fontFamily = Serif,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PressStatus(state, onPrint, showPrintButton = true)
    }
}

/** Progress, a refusal or a failure, and the button that starts (or restarts) the press. */
@Composable
private fun PressStatus(
    state: ObserverPress.State,
    onPrint: () -> Unit,
    showPrintButton: Boolean,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (state) {
            is ObserverPress.State.Printing -> {
                Text(stringRes(Res.string.observer_printing), style = MaterialTheme.typography.titleSmall)
                ObserverPrintingProgress(state, Modifier.fillMaxWidth())
            }

            is ObserverPress.State.NoLens -> {
                Text(stringRes(Res.string.observer_no_lens_title), fontFamily = Serif, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringRes(noLensMessage(state.reason)), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                OutlinedButton(onClick = onPrint) { Text(stringRes(Res.string.observer_try_again)) }
            }

            is ObserverPress.State.Failed -> {
                Text(stringRes(Res.string.observer_failed), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                OutlinedButton(onClick = onPrint) { Text(stringRes(Res.string.observer_try_again)) }
            }

            else -> {
                if (showPrintButton) Button(onClick = onPrint) { Text(stringRes(Res.string.observer_print)) }
            }
        }
    }
}

@Composable
private fun Masthead() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringRes(Res.string.observer_masthead),
            fontFamily = Serif,
            fontWeight = FontWeight.Black,
            fontSize = 34.sp,
            lineHeight = 38.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringRes(Res.string.observer_motto),
            fontFamily = Serif,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Paper(
    edition: ObserverEdition,
    state: ObserverPress.State,
    onPrint: () -> Unit,
    onOpen: (ObserverStory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        if (state !is ObserverPress.State.Idle && state !is ObserverPress.State.Ready) {
            item(key = "status") { Box(Modifier.padding(16.dp)) { PressStatus(state, onPrint, showPrintButton = false) } }
        }

        item(key = "masthead") { FrontMatter(edition) }

        if (edition.isEmpty) {
            item(key = "empty") {
                Text(
                    stringRes(Res.string.observer_empty),
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (edition.trending.isNotEmpty()) {
            item(key = "trending") { Trending(edition) }
        }

        edition.lead?.let { lead ->
            item(key = "lead") { LeadStory(lead, onOpen) }
        }

        if (edition.topStories.isNotEmpty()) {
            item(key = "top-header") { SectionHeader(stringRes(Res.string.observer_top_stories)) }
            items(edition.topStories, key = { "top-" + it.event.id }) { TopStory(it, onOpen) }
        }

        edition.sections.forEach { section(it, onOpen) }

        item(key = "colophon") {
            Text(
                stringRes(Res.string.observer_edition_code, edition.code),
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                textAlign = TextAlign.Center,
                fontFamily = Serif,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun LazyListScope.section(
    section: ObserverSection,
    onOpen: (ObserverStory) -> Unit,
) {
    item(key = "header-" + section.kind.name) { SectionHeader(stringRes(sectionTitle(section.kind))) }
    when (section.kind) {
        ObserverSectionKind.PHOTOS -> {
            item(key = "photos") { Photos(section.stories, onOpen) }
        }

        ObserverSectionKind.HIGHLIGHTS -> {
            items(section.stories, key = { section.kind.name + it.event.id }) { PullQuote(it, onOpen) }
        }

        else -> {
            items(section.stories, key = { section.kind.name + it.event.id }) { Brief(it, onOpen) }
        }
    }
}

private fun sectionTitle(kind: ObserverSectionKind): StringResource =
    when (kind) {
        ObserverSectionKind.PHOTOS -> Res.string.observer_section_photos
        ObserverSectionKind.LONG_READS -> Res.string.observer_section_long_reads
        ObserverSectionKind.LIVE -> Res.string.observer_section_live
        ObserverSectionKind.VIDEO -> Res.string.observer_section_video
        ObserverSectionKind.HIGHLIGHTS -> Res.string.observer_section_highlights
        ObserverSectionKind.POLLS -> Res.string.observer_section_polls
        ObserverSectionKind.CALENDAR -> Res.string.observer_section_calendar
        ObserverSectionKind.CLASSIFIEDS -> Res.string.observer_section_classifieds
        ObserverSectionKind.APPS -> Res.string.observer_section_apps
        ObserverSectionKind.CODE -> Res.string.observer_section_code
        ObserverSectionKind.WIKI -> Res.string.observer_section_wiki
        ObserverSectionKind.WIRE -> Res.string.observer_section_wire
    }

@Composable
private fun FrontMatter(edition: ObserverEdition) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Masthead()
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatLongDate(edition.until), fontFamily = Serif, style = MaterialTheme.typography.labelMedium)
            Text(stringRes(Res.string.observer_edition_code, edition.code), fontFamily = Serif, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))

        val stats =
            buildList {
                edition.readerName?.let { add(stringRes(Res.string.observer_ranked_as, it)) }
                add(pluralStringRes(Res.plurals.observer_stats_stories, edition.stats.printed, edition.stats.printed, edition.stats.authors))
                edition.stats.dayNotes?.let { add(stringRes(Res.string.observer_day_notes, it.toString())) }
                if (edition.stats.signals > 0) add(pluralStringRes(Res.plurals.observer_stats_signals, edition.stats.signals, edition.stats.signals))
            }
        Text(
            stats.joinToString(" · "),
            fontFamily = Serif,
            fontStyle = FontStyle.Italic,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Trending(edition: ObserverEdition) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            stringRes(Res.string.observer_trending).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.5.sp,
            fontWeight = FontWeight.Bold,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            edition.trending.forEach {
                Text("#" + it.hashtag, fontFamily = Serif, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.onSurface)
        Text(
            title.uppercase(),
            modifier = Modifier.padding(top = 4.dp),
            fontFamily = Serif,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun LeadStory(
    story: ObserverStory,
    onOpen: (ObserverStory) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(story) }.padding(16.dp)) {
        story.imageUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(story.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 33.sp)
        Spacer(Modifier.height(6.dp))
        Byline(story)
        if (story.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(story.body, fontFamily = Serif, style = MaterialTheme.typography.bodyLarge)
        }
        Details(story)
    }
}

@Composable
private fun TopStory(
    story: ObserverStory,
    onOpen: (ObserverStory) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(story) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(story.headline, fontFamily = Serif, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Byline(story)
            }
            story.imageUrl?.let { Thumbnail(it, 96) }
        }
        if (story.body.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(story.body, fontFamily = Serif, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
        }
        Details(story)
        HorizontalDivider(Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun Brief(
    story: ObserverStory,
    onOpen: (ObserverStory) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable { onOpen(story) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(story.headline, fontFamily = Serif, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                if (story.body.isNotBlank()) {
                    Text(
                        story.body,
                        fontFamily = Serif,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Byline(story)
            }
            story.imageUrl?.let { Thumbnail(it, 64) }
        }
        Details(story)
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PullQuote(
    story: ObserverStory,
    onOpen: (ObserverStory) -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable { onOpen(story) }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Box(Modifier.width(3.dp).height(48.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("“" + story.headline + "”", fontFamily = Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            Details(story)
            Byline(story)
        }
    }
}

@Composable
private fun Photos(
    stories: List<ObserverStory>,
    onOpen: (ObserverStory) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        items(stories, key = { it.event.id }) { story ->
            Column(Modifier.width(220.dp).clickable { onOpen(story) }) {
                story.imageUrl?.let {
                    AsyncImage(
                        model = it,
                        contentDescription = story.headline.takeIf { h -> h.isNotBlank() },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
                if (story.headline.isNotBlank()) {
                    Text(story.headline, fontFamily = Serif, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Byline(story)
            }
        }
    }
}

@Composable
private fun Thumbnail(
    url: String,
    sizeDp: Int,
) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun Byline(story: ObserverStory) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            story.byline + " · " + timeAgoNoDot(story.createdAt),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Signal(MaterialSymbols.Favorite, story.reactions)
        Signal(MaterialSymbols.Sync, story.reposts)
        Signal(MaterialSymbols.Reply, story.replies)
        Signal(MaterialSymbols.Bolt, story.zaps)
    }
}

/** One kind of trusted engagement; nothing is drawn for zero, so a quiet story stays quiet. */
@Composable
private fun Signal(
    symbol: MaterialSymbol,
    count: Int,
) {
    if (count <= 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(symbol, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Details(story: ObserverStory) {
    if (story.details.isEmpty()) return
    val timeOfDay = rememberTimeOfDayFormatter()
    Column(Modifier.padding(top = 4.dp)) {
        story.details.forEach { detail ->
            val text =
                when (detail) {
                    is ObserverDetail.Price -> {
                        val amount = listOfNotNull(detail.amount, detail.currency).joinToString(" ")
                        detail.frequency?.let { stringRes(Res.string.observer_detail_price_per, amount, it) } ?: amount
                    }

                    is ObserverDetail.Starts -> {
                        when {
                            detail.epochSeconds != null && story.event.kind == 30311 -> {
                                // A 24/7 stream can have started days ago: the date is part of the fact.
                                stringRes(Res.string.observer_on_air_since, formatLongDate(detail.epochSeconds) + " · " + timeOfDay(detail.epochSeconds * 1000))
                            }

                            detail.epochSeconds != null -> {
                                stringRes(Res.string.observer_detail_starts, formatLongDate(detail.epochSeconds) + " · " + timeOfDay(detail.epochSeconds * 1000))
                            }

                            else -> {
                                stringRes(Res.string.observer_detail_all_day, detail.date ?: "")
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
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
