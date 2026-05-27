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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.bottombars.FabBottomBarPadded
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.navigation.topbars.TopBarWithBackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.lists.list.NewListButton
import com.vitorpamplona.amethyst.ui.stringRes

@Composable
fun AddToMusicPlaylistSheet(
    trackAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: AddToMusicPlaylistViewModel = viewModel()
    vm.init(accountViewModel, trackAddress)

    var creating by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize().recalculateWindowInsets(),
        topBar = {
            TopBarWithBackButton(
                caption = stringRes(R.string.add_to_music_playlist_title),
                nav = nav,
            )
        },
        floatingActionButton = {
            FabBottomBarPadded(nav) {
                NewListButton(onClick = { creating = true })
            }
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
            AddToMusicPlaylistBody(vm = vm, accountViewModel = accountViewModel, nav = nav)
        }
    }

    if (creating) {
        NewMusicPlaylistDialog(
            onDismiss = { creating = false },
            onCreate = { name ->
                accountViewModel.launchSigner {
                    if (vm.createWithTrack(name) != null) {
                        creating = false
                    }
                }
            },
        )
    }
}

@Composable
private fun AddToMusicPlaylistBody(
    vm: AddToMusicPlaylistViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(
            items = playlists,
            key = { _, item -> item.address.toValue() },
        ) { _, summary ->
            MusicPlaylistManagementItem(
                modifier = Modifier.fillMaxWidth().animateItem(),
                playlistTitle = summary.title,
                isTrackInPlaylist = summary.containsTrack,
                totalTracks = summary.trackCount,
                onClick = { nav.nav(Route.Note(summary.address.toValue())) },
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
private fun NewMusicPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.music_playlist_new_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringRes(R.string.music_playlist_new_title_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.trim().isNotBlank(),
            ) {
                Text(stringRes(R.string.music_playlist_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.cancel))
            }
        },
    )
}
