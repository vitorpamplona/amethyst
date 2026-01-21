/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.nip28PublicChat.metadata

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip28PublicChats.PublicChatChannel
import com.vitorpamplona.amethyst.ui.actions.uploads.SelectSingleFromGallery
import com.vitorpamplona.amethyst.ui.navigation.navs.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.routeFor
import com.vitorpamplona.amethyst.ui.navigation.topbars.CreatingTopBar
import com.vitorpamplona.amethyst.ui.navigation.topbars.SavingTopBar
import com.vitorpamplona.amethyst.ui.note.LoadPublicChatChannel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.SettingsCategory
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoDialog
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.RelayUrlEditField
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.relaySetupInfoBuilder
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.DoubleVertSpacer
import com.vitorpamplona.amethyst.ui.theme.SettingsCategoryFirstModifier
import com.vitorpamplona.amethyst.ui.theme.SettingsCategorySpacingModifier
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonColumn
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions

@Composable
fun ChannelMetadataScreen(
    channelId: HexKey? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    if (channelId == null) {
        ChannelMetadataScreen(null as PublicChatChannel?, accountViewModel, nav)
    } else {
        LoadPublicChatChannel(channelId, accountViewModel) {
            ChannelMetadataScreen(it, accountViewModel, nav)
        }
    }
}

@Composable
fun ChannelMetadataScreen(
    channel: PublicChatChannel? = null,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val postViewModel: ChannelMetadataViewModel = viewModel()
    postViewModel.init(accountViewModel)

    if (channel != null) {
        LaunchedEffect(postViewModel) {
            postViewModel.load(channel)
        }
    } else {
        LaunchedEffect(postViewModel) {
            postViewModel.new()
        }
    }

    ChannelMetadataScaffold(
        postViewModel = postViewModel,
        accountViewModel = accountViewModel,
        nav = nav,
    )
}

@Preview
@Composable
private fun DialogContentPreview() {
    val accountViewModel = mockAccountViewModel()
    val postViewModel: ChannelMetadataViewModel = viewModel()
    postViewModel.init(accountViewModel)

    ThemeComparisonColumn {
        ChannelMetadataScaffold(
            postViewModel = postViewModel,
            accountViewModel = accountViewModel,
            nav = EmptyNav(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelMetadataScaffold(
    postViewModel: ChannelMetadataViewModel,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Scaffold(
        topBar = {
            if (postViewModel.isNewChannel()) {
                CreatingTopBar(
                    titleRes = R.string.public_chat,
                    isActive = postViewModel::canPost,
                    onCancel = {
                        postViewModel.clear()
                        nav.popBack()
                    },
                    onPost = {
                        try {
                            postViewModel.createOrUpdate {
                                nav.nav(routeFor(it))
                            }
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
                    titleRes = R.string.public_chat,
                    isActive = postViewModel::canPost,
                    onCancel = {
                        postViewModel.clear()
                        nav.popBack()
                    },
                    onPost = {
                        try {
                            postViewModel.createOrUpdate { }
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
        },
    ) { pad ->
        val feedState by postViewModel.channelRelays.collectAsStateWithLifecycle()

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
                    R.string.public_chat_title,
                    R.string.public_chat_explainer,
                    SettingsCategoryFirstModifier,
                )

                ChannelName(postViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Picture(postViewModel, accountViewModel)

                Spacer(modifier = DoubleVertSpacer)

                Description(postViewModel)

                SettingsCategory(
                    R.string.public_chat_relays_title,
                    R.string.public_chat_relays_explainer,
                    SettingsCategorySpacingModifier,
                )
            }

            itemsIndexed(feedState, key = { _, item -> "ChatRelays" + item.relay }) { index, item ->
                BasicRelaySetupInfoDialog(
                    item,
                    onDelete = { postViewModel.deleteHomeRelay(item) },
                    accountViewModel = accountViewModel,
                    nav,
                )
            }

            item {
                RelayUrlEditField { postViewModel.addHomeRelay(relaySetupInfoBuilder(it)) }

                Spacer(modifier = DoubleVertSpacer)
            }
        }
    }
}

@Composable
private fun Description(postViewModel: ChannelMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.description)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.channelDescription.value,
        onValueChange = { postViewModel.channelDescription.value = it },
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
    postViewModel: ChannelMetadataViewModel,
    accountViewModel: AccountViewModel,
) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.picture_url)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.channelPicture.value,
        onValueChange = { postViewModel.channelPicture.value = it },
        placeholder = {
            Text(
                text = "http://mygroup.com/logo.jpg",
                color = MaterialTheme.colorScheme.placeholderText,
            )
        },
        leadingIcon = {
            val context = LocalContext.current
            SelectSingleFromGallery(
                isUploading = postViewModel.isUploadingImageForPicture,
                tint = MaterialTheme.colorScheme.placeholderText,
                modifier = Modifier.padding(start = 2.dp),
            ) {
                postViewModel.uploadForPicture(it, context, onError = accountViewModel.toastManager::toast)
            }
        },
    )
}

@Composable
private fun ChannelName(postViewModel: ChannelMetadataViewModel) {
    OutlinedTextField(
        label = { Text(text = stringRes(R.string.channel_name)) },
        modifier = Modifier.fillMaxWidth(),
        value = postViewModel.channelName.value,
        onValueChange = { postViewModel.channelName.value = it },
        placeholder = {
            Text(
                text = stringRes(R.string.my_awesome_group),
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
