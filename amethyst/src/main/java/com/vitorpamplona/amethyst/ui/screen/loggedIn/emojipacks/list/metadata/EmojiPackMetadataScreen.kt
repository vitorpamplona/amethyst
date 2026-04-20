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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.emojipacks.list.metadata

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Composable
fun EmojiPackMetadataScreen(
    packIdentifier: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val viewModel: EmojiPackMetadataViewModel = viewModel()
    viewModel.init(accountViewModel)

    if (packIdentifier != null) {
        LaunchedEffect(viewModel) {
            viewModel.load(packIdentifier)
        }
    } else {
        LaunchedEffect(viewModel) {
            viewModel.new()
        }
    }

    EmojiPackMetadataScaffold(viewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmojiPackMetadataScaffold(
    viewModel: EmojiPackMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            EmojiPackMetadataTopBar(
                viewModel = viewModel,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(
                    start = 10.dp,
                    end = 10.dp,
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding(),
                ).consumeWindowInsets(pad)
                .imePadding(),
        ) {
            item {
                PackName(viewModel)
                Spacer(modifier = DoubleVertSpacer)

                PackImage(viewModel, accountViewModel)
                Spacer(modifier = DoubleVertSpacer)

                PackDescription(viewModel)
            }
        }
    }
}

@Composable
private fun EmojiPackMetadataTopBar(
    viewModel: EmojiPackMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (viewModel.isNewPack) {
        CreatingTopBar(
            titleRes = R.string.new_emoji_pack,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    viewModel.createOrUpdate()
                    nav.popBack()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        R.string.read_only_user,
                        R.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    } else {
        SavingTopBar(
            titleRes = R.string.edit_emoji_pack,
            isActive = viewModel::canPost,
            onCancel = {
                viewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    viewModel.createOrUpdate()
                    nav.popBack()
                } catch (e: SignerExceptions.ReadOnlyException) {
                    accountViewModel.toastManager.toast(
                        R.string.read_only_user,
                        R.string.login_with_a_private_key_to_be_able_to_sign_events,
                    )
                }
            },
        )
    }
}

@Composable
private fun PackName(viewModel: EmojiPackMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.emoji_pack_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = viewModel.name.value,
        onValueChange = { viewModel.name.value = it },
        placeholder = {
            Text(
                text = stringRes(R.string.emoji_pack_name_label),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
    )
}

@Composable
private fun PackImage(
    viewModel: EmojiPackMetadataViewModel,
    accountViewModel: AccountViewModel,
) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.emoji_pack_image_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = viewModel.picture.value,
        onValueChange = { viewModel.picture.value = it },
        placeholder = {
            Text(
                text = "https://example.com/cover.jpg",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        leadingIcon = {
            val context = LocalContext.current
            SelectSingleFromGallery(
                isUploading = viewModel.isUploadingImageForPicture,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(start = 2.dp),
            ) {
                viewModel.uploadForPicture(it, context, onError = accountViewModel.toastManager::toast)
            }
        },
    )
}

@Composable
private fun PackDescription(viewModel: EmojiPackMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.emoji_pack_description_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = viewModel.description.value,
        onValueChange = { viewModel.description.value = it },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 3,
    )
}
