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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.podcasts.authoring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.StrippingFailureDialog
import com.vitorpamplona.amethyst.ui.actions.uploads.GallerySelectSingle
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.SendingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.CoverImagePicker
import com.vitorpamplona.amethyst.ui.screen.loggedIn.music.UploadInProgressBanner
import com.vitorpamplona.amethyst.ui.stringRes
import kotlinx.collections.immutable.persistentListOf

/**
 * Editor for the creator's Podcasting-2.0 show metadata (`kind:30078`, `d="podcast-metadata"`).
 * There is one show per account, so this is always create-or-edit of the same event. Cover upload
 * plus the channel fields; explicit / complete / locked as switches; episodic vs serial as a toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPodcastShowScreen(
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val vm: EditPodcastShowViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(accountViewModel) { vm.init(accountViewModel) }

    StrippingFailureDialog(vm.strippingFailureConfirmation)

    var wantsToPickCover by remember { mutableStateOf(false) }
    if (wantsToPickCover) {
        GallerySelectSingle(
            onImageUri = { picked ->
                wantsToPickCover = false
                vm.setPickedCover(if (picked != null) persistentListOf(picked) else persistentListOf())
            },
        )
    }

    val isBusy = vm.isSending.value
    LaunchedEffect(vm) { vm.completionEvents.collect { nav.popBack() } }

    Scaffold(
        topBar = {
            SendingTopBar(
                titleRes = R.string.podcast_edit_show,
                onCancel = { nav.popBack() },
                isActive = { vm.isValid() && !isBusy },
                onPost = {
                    if (!vm.isValid() || isBusy) return@SendingTopBar
                    vm.saveAndPublish(context, accountViewModel)
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
            if (isBusy) UploadInProgressBanner(R.string.podcast_publishing_banner)

            CoverImagePicker(
                cover = vm.coverMedia.value,
                existingUrl = vm.coverUrl.value,
                onPick = { wantsToPickCover = true },
                onDelete = { vm.clearPickedCover() },
                accountViewModel = accountViewModel,
                enabled = !isBusy,
                ctaRes = R.string.podcast_show_cover_cta,
                hintRes = R.string.podcast_show_cover_hint,
            )

            Field(vm.title, R.string.podcast_show_title_label, R.string.podcast_show_title_placeholder, isError = vm.title.value.isBlank())

            OutlinedTextField(
                value = vm.description.value,
                onValueChange = { vm.description.value = it },
                label = { Text(stringRes(R.string.podcast_show_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            Field(vm.author, R.string.podcast_show_author_label, null, capitalization = KeyboardCapitalization.Words)
            Field(vm.email, R.string.podcast_show_email_label, null, keyboardType = KeyboardType.Email)
            Field(vm.website, R.string.podcast_show_website_label, null, keyboardType = KeyboardType.Uri)
            Field(vm.categories, R.string.podcast_show_categories_label, R.string.podcast_show_categories_placeholder)
            Field(vm.funding, R.string.podcast_show_funding_label, R.string.podcast_show_funding_placeholder, keyboardType = KeyboardType.Uri)
            Field(vm.language, R.string.podcast_show_language_label, R.string.podcast_show_language_placeholder)
            Field(vm.copyright, R.string.podcast_show_copyright_label, null)

            // Episodic vs serial.
            Text(
                text = stringRes(R.string.podcast_show_type_label),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = vm.type.value == "episodic" || vm.type.value.isBlank(),
                    onClick = { vm.type.value = "episodic" },
                    label = { Text(stringRes(R.string.podcast_show_type_episodic)) },
                )
                FilterChip(
                    selected = vm.type.value == "serial",
                    onClick = { vm.type.value = "serial" },
                    label = { Text(stringRes(R.string.podcast_show_type_serial)) },
                )
            }

            SwitchRow(stringRes(R.string.podcast_show_explicit), vm.explicit.value) { vm.explicit.value = it }
            SwitchRow(stringRes(R.string.podcast_show_complete), vm.complete.value) { vm.complete.value = it }
            SwitchRow(stringRes(R.string.podcast_show_locked), vm.locked.value) { vm.locked.value = it }

            V4VSplitEditor(vm.splitEditor, accountViewModel)
        }
    }
}

@Composable
private fun Field(
    state: androidx.compose.runtime.MutableState<String>,
    labelRes: Int,
    placeholderRes: Int?,
    isError: Boolean = false,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { state.value = it },
        label = { Text(stringRes(labelRes)) },
        placeholder = placeholderRes?.let { { Text(stringRes(it)) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(capitalization = capitalization, keyboardType = keyboardType),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
