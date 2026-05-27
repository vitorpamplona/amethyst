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

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbol
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectedMedia
import com.vitorpamplona.amethyst.ui.actions.uploads.ShowImageUploadGallery
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SendingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import kotlinx.collections.immutable.persistentListOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMusicTrackScreen(
    editDTag: String? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: NewMusicTrackViewModel = viewModel()
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

    var wantsToPickAudio by remember { mutableStateOf(false) }
    if (wantsToPickAudio) {
        AudioFileSelect(
            onAudioPicked = { picked ->
                wantsToPickAudio = false
                vm.setPickedAudio(context, picked)
            },
        )
    }

    val isBusy = vm.isSending.value

    // The send coroutine lives on accountViewModel.viewModelScope so it survives the
    // screen, but the screen still owns the pop-back. Subscribe to the one-shot
    // completion flow: if the user is still here when the upload finishes, we pop back;
    // if they've already left, no one is listening and nothing happens here (the
    // launchSigner coroutine already wrote the cleared state + fired the toast).
    LaunchedEffect(vm) {
        vm.completionEvents.collect { nav.popBack() }
    }

    Scaffold(
        topBar = {
            SendingTopBar(
                titleRes = R.string.new_music_track,
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
        // Order matters: apply Scaffold's PaddingValues (system-bar insets + topBar height),
        // then consumeWindowInsets so a later imePadding() only adds what's NOT already
        // covered by `pad`. Without consumeWindowInsets, imePadding double-counts the nav
        // bar inset (the IME inset includes it on Android 11+) and the bottom field is
        // pushed too far up when the keyboard opens.
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
            // Inline send banner — only shown while the upload+publish is in flight. The
            // banner replaces the per-picker "Uploading…" message we used to hide behind
            // the placeholder; this way both the cover and the audio remain visible in
            // their tiles for the whole operation (so the user doesn't think one phase is
            // done while the other is still running), and the banner gives clear
            // top-of-screen feedback that something IS happening.
            if (isBusy) UploadInProgressBanner()

            CoverImagePicker(
                vm = vm,
                accountViewModel = accountViewModel,
                onPick = { wantsToPickCover = true },
                enabled = !isBusy,
            )

            AudioFilePicker(
                vm = vm,
                onPick = { wantsToPickAudio = true },
                enabled = !isBusy,
            )

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

            // No audio-URL or cover-URL text fields: the upload tiles at the top are the
            // single source of truth for both. When editing an existing track the
            // ViewModel still threads the previous `image` / `url` through to
            // MusicTrackEvent.edit, so we don't drop them on save when the user keeps
            // the existing files.

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
private fun CoverImagePicker(
    vm: NewMusicTrackViewModel,
    accountViewModel: AccountViewModel,
    onPick: () -> Unit,
    enabled: Boolean,
) {
    val picked = vm.coverMedia.value
    if (picked != null) {
        // Tap the preview to swap to a different image — same gesture as the Badge composer.
        // While the upload is in flight (enabled = false) we drop the tap handler so the
        // user can't trigger a re-pick mid-upload; the picker stays visible so they can
        // see what's being sent.
        Box(modifier = if (enabled) Modifier.clickable(onClick = onPick) else Modifier) {
            ShowImageUploadGallery(
                list = picked,
                onDelete = { if (enabled) vm.clearPickedCover() },
                accountViewModel = accountViewModel,
            )
        }
    } else {
        UploadPlaceholder(
            iconSymbol = MaterialSymbols.AddPhotoAlternate,
            ctaRes = R.string.music_track_cover_upload_cta,
            hintRes = R.string.music_track_cover_upload_hint,
            onClick = onPick,
            enabled = enabled,
        )
    }
}

@Composable
private fun AudioFilePicker(
    vm: NewMusicTrackViewModel,
    onPick: () -> Unit,
    enabled: Boolean,
) {
    val pickedName = vm.pickedAudioName.value
    if (pickedName != null) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    ).let { if (enabled) it.clickable(onClick = onPick) else it }
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                symbol = MaterialSymbols.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pickedName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringRes(R.string.music_track_audio_picked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (enabled) {
                TextButton(onClick = { vm.clearPickedAudio() }) {
                    Text(stringRes(R.string.cancel))
                }
            }
        }
    } else {
        UploadPlaceholder(
            iconSymbol = MaterialSymbols.MusicNote,
            ctaRes = R.string.music_track_audio_upload_cta,
            hintRes = R.string.music_track_audio_upload_hint,
            onClick = onPick,
            aspectRatio = null,
            enabled = enabled,
        )
    }
}

/**
 * Banner shown at the top of the form while the send coroutine is in flight. The
 * coroutine runs on `accountViewModel.viewModelScope` so it survives the screen, but
 * this banner is the user's primary feedback that something IS happening — without it
 * the previous flow looked like a no-op after pressing Send while the cover finished
 * uploading and reverted to the picker placeholder mid-flight.
 */
@Composable
private fun UploadInProgressBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = stringRes(R.string.music_track_uploading_banner),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Shared upload-placeholder card used by both the cover and the audio pickers. The cover
 * uses a 1:1 aspect ratio (square upload tile, matching the Badge composer); the audio
 * picker leaves [aspectRatio] null so the row hugs the icon + text content.
 */
@Composable
private fun UploadPlaceholder(
    iconSymbol: MaterialSymbol,
    ctaRes: Int,
    hintRes: Int,
    onClick: () -> Unit,
    aspectRatio: Float? = 1f,
    enabled: Boolean = true,
) {
    val baseModifier =
        Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp),
            ).let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(24.dp)

    val finalModifier =
        if (aspectRatio != null) baseModifier.aspectRatio(aspectRatio) else baseModifier

    Box(
        modifier = finalModifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                symbol = iconSymbol,
                contentDescription = null,
                modifier = Modifier.size(if (aspectRatio != null) 56.dp else 36.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringRes(ctaRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringRes(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// Single-file audio picker using OpenDocument restricted to audio MIME types. We don't
// reuse the shared FileSelect because that one also accepts application/pdf, which would
// let the user pick a PDF and try to publish it as a track — not what this screen is for.
@Composable
private fun AudioFileSelect(onAudioPicked: (SelectedMedia?) -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri: Uri? ->
                onAudioPicked(uri?.let { SelectedMedia(it, resolver.getType(it)) })
            },
        )
    LaunchedEffect(Unit) {
        launcher.launch(arrayOf("audio/*"))
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

@Preview
@Composable
private fun NewMusicTrackScreenPreview() {
    ThemeComparisonColumn {
        NewMusicTrackScreen(
            editDTag = null,
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav(),
        )
    }
}
