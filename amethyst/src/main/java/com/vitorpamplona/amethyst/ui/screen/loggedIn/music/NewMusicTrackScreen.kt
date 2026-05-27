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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMusicTrackScreen(
    editDTag: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewMusicTrackViewModel = viewModel()
    vm.init(accountViewModel, editDTag)

    Scaffold(
        topBar = {
            SavingTopBar(
                titleRes = R.string.new_music_track,
                onCancel = { nav.popBack() },
                onPost = {
                    accountViewModel.launchSigner {
                        if (vm.publish()) {
                            nav.popBack()
                        }
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = pad.calculateTopPadding(),
                        bottom = pad.calculateBottomPadding(),
                    ).consumeWindowInsets(pad)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(R.string.music_track_title_label)) },
                placeholder = { Text(stringRes(R.string.music_track_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                isError = vm.title.value.isBlank(),
            )

            OutlinedTextField(
                value = vm.artist.value,
                onValueChange = { vm.artist.value = it },
                label = { Text(stringRes(R.string.music_track_artist_label)) },
                placeholder = { Text(stringRes(R.string.music_track_artist_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = vm.artist.value.isBlank(),
            )

            OutlinedTextField(
                value = vm.audioUrl.value,
                onValueChange = { vm.audioUrl.value = it },
                label = { Text(stringRes(R.string.music_track_audio_url_label)) },
                placeholder = { Text(stringRes(R.string.music_track_audio_url_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = vm.audioUrl.value.isBlank(),
            )

            OutlinedTextField(
                value = vm.coverUrl.value,
                onValueChange = { vm.coverUrl.value = it },
                label = { Text(stringRes(R.string.music_track_image_url_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            OutlinedTextField(
                value = vm.album.value,
                onValueChange = { vm.album.value = it },
                label = { Text(stringRes(R.string.music_track_album_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            OutlinedTextField(
                value = vm.durationSeconds.value,
                onValueChange = { input -> vm.durationSeconds.value = input.filter { it.isDigit() } },
                label = { Text(stringRes(R.string.music_track_duration_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            OutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(R.string.music_track_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            if (vm.isEditing) {
                DeleteMusicTrackRow(
                    vm = vm,
                    onDeleted = { nav.popBack() },
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun DeleteMusicTrackRow(
    vm: NewMusicTrackViewModel,
    onDeleted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text = stringRes(R.string.music_track_delete))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(R.string.music_track_delete)) },
            text = { Text(stringRes(R.string.music_track_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    accountViewModel.launchSigner {
                        if (vm.deleteLoaded()) onDeleted()
                    }
                }) {
                    Text(
                        text = stringRes(R.string.music_track_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringRes(R.string.cancel))
                }
            },
        )
    }
}
