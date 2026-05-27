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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.grayText

/**
 * Modal-style picker that lets a user toggle the visible track in/out of any of their own
 * playlists. Replaces the earlier bookmark-style management UI (ListItem + leading icon stack +
 * trailing button); music doesn't carry the public/private dimension that bookmarks do, so
 * those affordances were just visual noise.
 *
 * Layout:
 *   - inline "create new playlist" row at the top (name field + Create button)
 *   - LazyColumn of compact rows: check icon + title + track count
 *   - tap-anywhere-on-row toggles membership
 *   - bottom link: "Manage all playlists" → opens the dedicated playlists feed
 */
@Composable
fun AddToMusicPlaylistSheet(
    trackAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: AddToMusicPlaylistViewModel = viewModel()
    vm.init(accountViewModel, trackAddress)

    Scaffold(
        modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(R.string.add_to_music_playlist_title),
                nav = nav,
            )
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding(),
                    ).consumeWindowInsets(contentPadding)
                    .imePadding(),
        ) {
            CreatePlaylistRow(vm = vm, accountViewModel = accountViewModel)
            HorizontalDivider()
            PlaylistPickerBody(vm = vm, accountViewModel = accountViewModel)
            HorizontalDivider()
            ManageAllPlaylistsLink(nav = nav)
        }
    }
}

@Composable
private fun CreatePlaylistRow(
    vm: AddToMusicPlaylistViewModel,
    accountViewModel: AccountViewModel,
) {
    var name by rememberSaveable { mutableStateOf("") }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = { Text(stringRes(R.string.music_playlist_new_title_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isNotBlank()) {
                    accountViewModel.launchSigner {
                        if (vm.createWithTrack(trimmed) != null) name = ""
                    }
                }
            },
            enabled = name.trim().isNotBlank() && !vm.isWorking.value,
        ) {
            Text(stringRes(R.string.music_playlist_create_action))
        }
    }
}

@Composable
private fun ColumnScope.PlaylistPickerBody(
    vm: AddToMusicPlaylistViewModel,
    accountViewModel: AccountViewModel,
) {
    val playlists by vm.ownedPlaylists

    if (playlists.isEmpty()) {
        Text(
            text = stringRes(R.string.add_to_music_playlist_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, item -> item.address.toValue() },
        ) { _, summary ->
            PlaylistPickerRow(
                summary = summary,
                onToggle = {
                    accountViewModel.launchSigner {
                        vm.toggle(summary.address)
                    }
                },
            )
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    summary: OwnedPlaylistSummary,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Check / plus icon swaps based on membership — the only state cue we need here, since
        // playlists don't have the public/private split that justified the bookmark UI.
        Icon(
            symbol =
                if (summary.containsTrack) {
                    MaterialSymbols.CheckCircle
                } else {
                    MaterialSymbols.AutoMirrored.PlaylistAdd
                },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.title.ifBlank { stringRes(R.string.music_playlist_untitled) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (summary.containsTrack) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val count = summary.trackCount
            Text(
                text = pluralStringResource(R.plurals.music_playlist_track_count_short, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.grayText,
            )
        }
    }
}

@Composable
private fun ManageAllPlaylistsLink(nav: INav) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable { nav.nav(Route.MusicPlaylists) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = MaterialSymbols.AutoMirrored.FormatListBulleted,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringRes(R.string.add_to_music_playlist_manage_all),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview
@Composable
private fun AddToMusicPlaylistSheetEmptyPreview() {
    ThemeComparisonColumn {
        AddToMusicPlaylistSheet(
            // No matching addressable in LocalCache → the VM resolves zero playlists,
            // which is the empty-state path we want to capture.
            trackAddress = "36787:0000000000000000000000000000000000000000000000000000000000000000:preview-empty",
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
