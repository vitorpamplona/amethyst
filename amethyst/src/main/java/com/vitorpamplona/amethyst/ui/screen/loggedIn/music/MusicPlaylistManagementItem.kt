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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.HalfHalfVertPadding
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size50Modifier
import com.vitorpamplona.amethyst.ui.theme.SpacedBy5dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn

/**
 * One row in the Add-to-Playlist sheet. Mirrors `BookmarkGroupManagementItem` so the visual
 * vocabulary matches the bookmark UI: leading icon + total count, headline = playlist title,
 * supporting = membership text, trailing = round add/remove action button. Row click navigates
 * to the playlist itself; the trailing button is the toggle.
 */
@Composable
fun MusicPlaylistManagementItem(
    modifier: Modifier = Modifier,
    playlistTitle: String,
    isTrackInPlaylist: Boolean,
    totalTracks: Int,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = playlistTitle.ifBlank { stringRes(R.string.music_playlist_untitled) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            PlaylistMembershipStatus(isTrackInPlaylist = isTrackInPlaylist)
        },
        leadingContent = {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    symbol = MaterialSymbols.MusicNote,
                    contentDescription = stringRes(R.string.music_playlist_icon_label),
                    modifier = Size50Modifier,
                )
                Spacer(StdVertSpacer)
                Text(
                    text = pluralStringResource(R.plurals.music_playlist_track_count_short, totalTracks, totalTracks),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            PlaylistToggleButton(
                isTrackInPlaylist = isTrackInPlaylist,
                onToggle = onToggle,
            )
        },
    )
}

@Composable
private fun PlaylistMembershipStatus(isTrackInPlaylist: Boolean) {
    Row(
        modifier = HalfHalfVertPadding,
        horizontalArrangement = SpacedBy5dp,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text =
            if (isTrackInPlaylist) {
                stringRes(R.string.music_playlist_presence_indicator)
            } else {
                stringRes(R.string.music_playlist_absence_indicator)
            }

        val icon =
            if (isTrackInPlaylist) {
                MaterialSymbols.PlayCircle
            } else {
                MaterialSymbols.RemoveCircleOutline
            }

        Icon(
            symbol = icon,
            contentDescription = text,
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = text,
            overflow = TextOverflow.MiddleEllipsis,
            maxLines = 1,
        )
    }
}

@Composable
private fun PlaylistToggleButton(
    isTrackInPlaylist: Boolean,
    onToggle: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onToggle,
            modifier =
                Modifier
                    .background(
                        color =
                            if (isTrackInPlaylist) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        shape = RoundedCornerShape(percent = 80),
                    ),
        ) {
            if (isTrackInPlaylist) {
                Icon(
                    symbol = MaterialSymbols.RemoveCircleOutline,
                    contentDescription = stringRes(R.string.music_playlist_remove_action_desc),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else {
                MaterialSymbols.AutoMirrored.PlaylistAdd.let { addSymbol ->
                    Icon(
                        symbol = addSymbol,
                        contentDescription = stringRes(R.string.music_playlist_add_action_desc),
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun MusicPlaylistManagementItemNotInPreview() {
    ThemeComparisonColumn {
        MusicPlaylistManagementItem(
            playlistTitle = "Summer Vibes 2024",
            isTrackInPlaylist = false,
            totalTracks = 12,
            onClick = {},
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun MusicPlaylistManagementItemInPreview() {
    ThemeComparisonColumn {
        MusicPlaylistManagementItem(
            playlistTitle = "Late-night Coding Beats",
            isTrackInPlaylist = true,
            totalTracks = 47,
            onClick = {},
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun MusicPlaylistManagementItemEmptyPreview() {
    ThemeComparisonColumn {
        MusicPlaylistManagementItem(
            playlistTitle = "Fresh Picks",
            isTrackInPlaylist = false,
            totalTracks = 0,
            onClick = {},
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun MusicPlaylistManagementItemUntitledPreview() {
    ThemeComparisonColumn {
        MusicPlaylistManagementItem(
            playlistTitle = "",
            isTrackInPlaylist = false,
            totalTracks = 3,
            onClick = {},
            onToggle = {},
        )
    }
}
