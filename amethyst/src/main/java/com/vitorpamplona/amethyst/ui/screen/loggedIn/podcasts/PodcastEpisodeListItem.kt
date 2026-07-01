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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.observeNoteReplyCount
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.note.CommentIcon
import com.vitorpamplona.amethyst.ui.note.timeAgo
import com.vitorpamplona.amethyst.ui.note.types.PodcastEpisodeAudioPlayer
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.Size18Modifier
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.podcasts.PodcastEpisode

private val PLAYER_SHAPE = Modifier.clip(RoundedCornerShape(12.dp))

/**
 * One episode as a compact "show list" row inside a podcast's screen: publish date, title,
 * short description, and an inline audio player so the episode plays without leaving the list.
 * The show artwork is the same for every episode, so it's omitted here — the header carries it.
 */
@Composable
fun PodcastEpisodeListItem(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event ?: return
    // Both kind 54 (NIP-F4) and kind 30054 (Podcasting 2.0) episodes implement PodcastEpisode.
    val episode = noteEvent as? PodcastEpisode ?: return

    val title = remember(noteEvent) { episode.episodeTitle() }
    val description = remember(noteEvent) { episode.episodeDescription() }
    // Prefer audio; fall back to a video source so video-only episodes still play inline.
    val media = remember(noteEvent) { episode.episodeAudio().firstOrNull() ?: episode.episodeVideo() }
    val image = remember(noteEvent) { episode.episodeImage() }
    val season = remember(noteEvent) { episode.episodeSeason() }
    val episodeNumber = remember(noteEvent) { episode.episodeNumber() }

    val context = LocalContext.current
    val dateStr = remember(noteEvent) { timeAgo(noteEvent.createdAt, context, prefix = "") }
    val seasonEpisodeLabel =
        when {
            season != null && episodeNumber != null -> stringRes(R.string.podcast_season_episode, season, episodeNumber)
            episodeNumber != null -> stringRes(R.string.podcast_episode_number, episodeNumber)
            season != null -> stringRes(R.string.podcast_season, season)
            else -> null
        }
    val subtitle = listOfNotNull(seasonEpisodeLabel, dateStr.takeIf { it.isNotBlank() }).joinToString(" · ")

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Size5dp),
    ) {
        subtitle.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.grayText,
            )
        }

        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        media?.let { audio ->
            PodcastEpisodeAudioPlayer(
                audio = audio,
                note = note,
                title = title,
                image = image,
                borderModifier = PLAYER_SHAPE,
                accountViewModel = accountViewModel,
            )
        }

        EpisodeCommentsChip(note, accountViewModel, nav)
    }
}

/**
 * A "comments" affordance for an episode list row: the NIP-22 (kind 1111) reply count plus a comment
 * glyph, opening the episode's thread where the discussion lives and new comments are composed.
 * Everything downstream — fetching `#a`/`#A` comments, the reply composer, the count — already works
 * through the standard thread; this just surfaces it on the podcast-specific list.
 */
@Composable
private fun EpisodeCommentsChip(
    note: Note,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val commentCount by observeNoteReplyCount(note, accountViewModel)

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { routeFor(note, accountViewModel.account)?.let { nav.nav(it) } }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CommentIcon(Size18Modifier, MaterialTheme.colorScheme.grayText)
        Text(
            text =
                if (commentCount == 0) {
                    stringRes(R.string.podcast_comment_action)
                } else {
                    pluralStringResource(R.plurals.podcast_comment_count, commentCount, commentCount)
                },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.grayText,
        )
    }
}
