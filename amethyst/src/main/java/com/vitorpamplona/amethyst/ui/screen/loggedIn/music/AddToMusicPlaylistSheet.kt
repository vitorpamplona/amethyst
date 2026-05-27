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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToMusicPlaylistSheet(
    trackAddress: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: AddToMusicPlaylistViewModel = viewModel()
    vm.init(accountViewModel, trackAddress)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringRes(R.string.add_to_music_playlist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    Button(
                        onClick = { nav.popBack() },
                        colors = ButtonDefaults.textButtonColors(),
                    ) {
                        Text(stringRes(R.string.add_to_music_playlist_done))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding()
                    .fillMaxWidth(),
        ) {
            NewPlaylistRow(vm = vm, accountViewModel = accountViewModel)

            HorizontalDivider()

            val playlists by vm.ownedPlaylists
            if (playlists.isEmpty()) {
                Text(
                    text = stringRes(R.string.add_to_music_playlist_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(playlists, key = { it.address.toValue() }) { summary ->
                        PlaylistPickerRow(
                            summary = summary,
                            onToggle = {
                                accountViewModel.launchSigner {
                                    vm.toggle(summary.address)
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun NewPlaylistRow(
    vm: AddToMusicPlaylistViewModel,
    accountViewModel: AccountViewModel,
) {
    var newName by rememberSaveable { mutableStateOf("") }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            placeholder = { Text(stringRes(R.string.music_playlist_new_title_placeholder)) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        )

        Spacer(Modifier.width(12.dp))

        Button(
            onClick = {
                val name = newName.trim()
                if (name.isNotBlank()) {
                    accountViewModel.launchSigner {
                        if (vm.createWithTrack(name) != null) {
                            newName = ""
                        }
                    }
                }
            },
            enabled = newName.trim().isNotBlank() && !vm.isWorking.value,
        ) {
            Text(stringRes(R.string.add_to_music_playlist_new))
        }
    }
}

@Composable
private fun PlaylistPickerRow(
    summary: OwnedPlaylistSummary,
    onToggle: () -> Unit,
) {
    val trackCount = summary.trackCount

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = summary.containsTrack, onCheckedChange = { onToggle() })
        Column(
            modifier = Modifier.padding(start = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = summary.title.ifBlank { stringRes(R.string.add_to_music_playlist_new) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.music_playlist_track_count_short, trackCount, trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
