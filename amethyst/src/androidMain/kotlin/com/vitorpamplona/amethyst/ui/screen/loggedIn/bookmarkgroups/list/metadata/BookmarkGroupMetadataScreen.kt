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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.bookmarkgroups.list.metadata

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
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Composable
fun BookmarkGroupMetadataScreen(
    bookmarkGroupIdentifier: String?,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel = viewModel()
    bookmarkGroupInfoViewModel.init(accountViewModel)

    if (bookmarkGroupIdentifier != null) {
        LaunchedEffect(bookmarkGroupInfoViewModel) {
            bookmarkGroupInfoViewModel.load(bookmarkGroupIdentifier)
        }
    } else {
        LaunchedEffect(bookmarkGroupInfoViewModel) {
            bookmarkGroupInfoViewModel.new()
        }
    }

    BookmarkGroupMetadataScaffold(bookmarkGroupInfoViewModel, accountViewModel, nav)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookmarkGroupMetadataScaffold(
    bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            BookmarkGroupMetadataTopBar(
                bookmarkGroupInfoViewModel = bookmarkGroupInfoViewModel,
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
                SettingsCategory(
                    R.string.bookmark_list_edit_sub_title,
                    R.string.bookmark_list_explainer,
                    SettingsCategoryFirstModifier,
                )

                ListName(bookmarkGroupInfoViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Picture(bookmarkGroupInfoViewModel, accountViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Description(bookmarkGroupInfoViewModel)
            }
        }
    }
}

@Composable
fun BookmarkGroupMetadataTopBar(
    bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (bookmarkGroupInfoViewModel.isNewList) {
        CreatingTopBar(
            titleRes = R.string.bookmark_list_creation_screen_title,
            isActive = bookmarkGroupInfoViewModel::canPost,
            onCancel = {
                bookmarkGroupInfoViewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    bookmarkGroupInfoViewModel.createOrUpdate()
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
            titleRes = R.string.follow_set_edit_list_metadata,
            isActive = bookmarkGroupInfoViewModel::canPost,
            onCancel = {
                bookmarkGroupInfoViewModel.clear()
                nav.popBack()
            },
            onPost = {
                try {
                    bookmarkGroupInfoViewModel.createOrUpdate()
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
private fun Description(bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.follow_set_creation_desc_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = bookmarkGroupInfoViewModel.description.value,
        onValueChange = { bookmarkGroupInfoViewModel.description.value = it },
        placeholder = {
            Text(
                text = stringRes(R.string.about_us),
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        keyboardOptions =
            KeyboardOptions.Default.copy(
                capitalization = KeyboardCapitalization.Sentences,
            ),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        minLines = 3,
    )
}

@Composable
private fun Picture(
    bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel,
    accountViewModel: AccountViewModel,
) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.optional_picture_url)) },
        modifier = Modifier.fillMaxWidth(),
        value = bookmarkGroupInfoViewModel.picture.value,
        onValueChange = { bookmarkGroupInfoViewModel.picture.value = it },
        placeholder = {
            Text(
                text = "http://mygroup.com/logo.jpg",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        leadingIcon = {
            val context = LocalContext.current
            SelectSingleFromGallery(
                isUploading = bookmarkGroupInfoViewModel.isUploadingImageForPicture,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(start = 2.dp),
            ) {
                bookmarkGroupInfoViewModel.uploadForPicture(it, context, onError = accountViewModel.toastManager::toast)
            }
        },
    )
}

@Composable
private fun ListName(bookmarkGroupInfoViewModel: BookmarkGroupMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.follow_set_creation_name_label)) },
        modifier = Modifier.fillMaxWidth(),
        value = bookmarkGroupInfoViewModel.name.value,
        onValueChange = { bookmarkGroupInfoViewModel.name.value = it },
        placeholder = {
            Text(
                text = stringRes(R.string.follow_set_copy_name_label),
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
