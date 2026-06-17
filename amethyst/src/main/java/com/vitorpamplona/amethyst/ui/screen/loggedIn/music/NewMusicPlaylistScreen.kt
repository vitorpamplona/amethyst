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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SendingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.persistentListOf

@Composable
fun NewMusicPlaylistScreen(
    editDTag: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewMusicPlaylistViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(accountViewModel) {
        vm.init(accountViewModel, editDTag)
    }

    StrippingFailureDialog(vm.strippingFailureConfirmation)

    var wantsToPickCover by remember { mutableStateOf(false) }
    if (wantsToPickCover) {
        GallerySelectSingle(
            onImageUri = { picked ->
                wantsToPickCover = false
                vm.setPickedCover(
                    if (picked != null) persistentListOf(picked) else persistentListOf(),
                )
            },
        )
    }

    val isBusy = vm.isSending.value

    // The send coroutine lives on accountViewModel.viewModelScope so it survives the screen,
    // but the screen still owns the pop-back. Subscribe to the one-shot completion flow: if the
    // user is still here when the upload finishes, we pop back; if they've already left, no one
    // is listening and nothing happens here.
    LaunchedEffect(vm) {
        vm.completionEvents.collect { nav.popBack() }
    }

    Scaffold(
        topBar = {
            SendingTopBar(
                titleRes = if (vm.isEditing) R.string.edit_music_playlist else R.string.new_music_playlist,
                onCancel = { nav.popBack() },
                isActive = { vm.isValid() && !isBusy },
                onPost = {
                    if (!vm.isValid() || isBusy) return@SendingTopBar
                    vm.saveAndPublish(
                        context = context,
                        accountViewModel = accountViewModel,
                    )
                },
            )
        },
    ) { pad ->
        Column(
            modifier =
                Modifier
                    .padding(pad)
                    .consumeWindowInsets(pad)
                    .imePadding()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isBusy) UploadInProgressBanner(R.string.music_playlist_uploading_banner)

            CoverImagePicker(
                cover = vm.coverMedia.value,
                onPick = { wantsToPickCover = true },
                onDelete = { vm.clearPickedCover() },
                accountViewModel = accountViewModel,
                enabled = !isBusy,
                ctaRes = R.string.music_playlist_cover_upload_cta,
                hintRes = R.string.music_playlist_cover_upload_hint,
            )

            OutlinedTextField(
                value = vm.title.value,
                onValueChange = { vm.title.value = it },
                label = { Text(stringRes(R.string.music_playlist_title_label)) },
                placeholder = { Text(stringRes(R.string.music_playlist_new_title_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                isError = vm.title.value.isBlank(),
            )

            OutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(R.string.music_playlist_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = vm.notes.value,
                onValueChange = { vm.notes.value = it },
                label = { Text(stringRes(R.string.music_playlist_notes_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            LabeledSwitchRow(
                iconSymbol = if (vm.isPrivate.value) MaterialSymbols.Lock else MaterialSymbols.Public,
                title = stringRes(R.string.music_playlist_private),
                subtitle = stringRes(R.string.music_playlist_private_hint),
                checked = vm.isPrivate.value,
                enabled = !isBusy,
                onCheckedChange = { vm.isPrivate.value = it },
            )

            LabeledSwitchRow(
                iconSymbol = MaterialSymbols.Groups,
                title = stringRes(R.string.music_playlist_collaborative),
                subtitle = stringRes(R.string.music_playlist_collaborative_hint),
                checked = vm.isCollaborative.value,
                enabled = !isBusy,
                onCheckedChange = { vm.isCollaborative.value = it },
            )

            if (vm.isEditing) {
                DeleteMusicPlaylistRow(
                    vm = vm,
                    onDeleted = { nav.popBack() },
                    accountViewModel = accountViewModel,
                )
            }
        }
    }
}

@Composable
private fun LabeledSwitchRow(
    iconSymbol: MaterialSymbol,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            symbol = iconSymbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun DeleteMusicPlaylistRow(
    vm: NewMusicPlaylistViewModel,
    onDeleted: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }

    OutlinedButton(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
    ) {
        Text(text = stringRes(R.string.music_playlist_delete))
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringRes(R.string.music_playlist_delete)) },
            text = { Text(stringRes(R.string.music_playlist_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    accountViewModel.launchSigner {
                        if (vm.deleteLoaded()) onDeleted()
                    }
                }) {
                    Text(
                        text = stringRes(R.string.music_playlist_delete),
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

@Preview
@Composable
private fun NewMusicPlaylistScreenPreview() {
    ThemeComparisonColumn {
        NewMusicPlaylistScreen(
            editDTag = null,
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
