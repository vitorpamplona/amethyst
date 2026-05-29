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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.ui.note.types.PodcastCoverCard
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.Size5dp
import com.vitorpamplona.amethyst.ui.theme.grayText
import com.vitorpamplona.quartz.nipF4Podcasts.metadata.PodcastMetadataEvent

/**
 * Hero header for a single podcast screen: large cover art, title, websites and a tappable
 * (collapse/expand) description, followed by the "Episodes (N)" section divider that the
 * episode rows hang under.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PodcastHeader(
    metadataNote: Note,
    metadataEvent: PodcastMetadataEvent?,
    episodeCount: Int,
    accountViewModel: AccountViewModel,
) {
    val title = remember(metadataEvent) { metadataEvent?.title() }
    val image = remember(metadataEvent) { metadataEvent?.image() }
    val description = remember(metadataEvent) { metadataEvent?.description() }
    val websites = remember(metadataEvent) { metadataEvent?.websites() ?: emptyList() }

    Column(Modifier.fillMaxWidth()) {
        PodcastCoverCard(image, metadataNote, accountViewModel)

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (websites.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Size5dp),
                    verticalArrangement = Arrangement.spacedBy(Size5dp),
                ) {
                    websites.forEach { website ->
                        Text(
                            text = website,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.grayText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            description?.let { ExpandableDescription(it) }
        }

        Text(
            text = pluralStringResource(R.plurals.podcast_episode_count, episodeCount, episodeCount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
        )

        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
private fun ExpandableDescription(description: String) {
    var expanded by remember(description) { mutableStateOf(false) }

    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (expanded) Int.MAX_VALUE else 4,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
    )
}
