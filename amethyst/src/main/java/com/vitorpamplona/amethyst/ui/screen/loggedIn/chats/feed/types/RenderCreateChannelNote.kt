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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.feed.types

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Constants
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.ui.components.CreateTextWithEmoji
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.components.TranslatableRichTextViewer
import com.vitorpamplona.amethyst.ui.navigation.EmptyNav
import com.vitorpamplona.amethyst.ui.navigation.INav
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.chats.publicChannels.ephemChat.header.loadRelayInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.mockAccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.RelayInformationDialog
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.ui.theme.Size20dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.ThemeComparisonRow
import com.vitorpamplona.amethyst.ui.theme.largeProfilePictureModifier
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.ImmutableListOfLists
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm

@Composable
fun RenderCreateChannelNote(
    note: Note,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val noteEvent = note.event as? ChannelCreateEvent ?: return
    val channelInfo = remember(noteEvent) { noteEvent.channelInfo() }
    val tags =
        remember(noteEvent) {
            noteEvent.tags.toImmutableListOfLists()
        }

    RenderChannelData(
        noteEvent.id,
        note.toNostrUri(),
        channelInfo,
        tags,
        bgColor,
        accountViewModel,
        nav,
    )
}

@Preview
@Composable
fun RenderChannelDataPreview() {
    ThemeComparisonRow {
        RenderChannelData(
            id = "bbaacc",
            uri = "nostr:nevent1...",
            channelInfo =
                ChannelDataNorm(
                    "My Group",
                    "Testing About me",
                    "http://test.com",
                    listOf(Constants.mom, Constants.nos),
                ),
            tags = EmptyTagList,
            bgColor = remember { mutableStateOf(Color.Transparent) },
            accountViewModel = mockAccountViewModel(),
            nav = EmptyNav,
        )
    }
}

@Composable
fun RenderChannelData(
    id: HexKey,
    uri: String,
    channelInfo: ChannelDataNorm,
    tags: ImmutableListOfLists<String>,
    bgColor: MutableState<Color>,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    Column {
        Row {
            TranslatableRichTextViewer(
                content = stringRes(R.string.changed_chat_profile_to),
                canPreview = true,
                quotesLeft = 1,
                modifier = Modifier,
                tags = tags,
                backgroundColor = bgColor,
                id = id,
                callbackUri = uri,
                accountViewModel = accountViewModel,
                nav = nav,
            )
        }

        channelInfo.picture?.let {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                RobohashFallbackAsyncImage(
                    robot = id,
                    model = it,
                    contentDescription = stringRes(R.string.channel_image),
                    modifier = MaterialTheme.colorScheme.largeProfilePictureModifier,
                    loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
                    loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
                )
            }
        }

        channelInfo.name?.let {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                CreateTextWithEmoji(
                    text = it,
                    tags = tags,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
            }
        }

        channelInfo.about?.let {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            ) {
                TranslatableRichTextViewer(
                    content = it,
                    canPreview = true,
                    quotesLeft = 1,
                    modifier = Modifier,
                    tags = tags,
                    backgroundColor = bgColor,
                    id = id,
                    callbackUri = uri,
                    accountViewModel = accountViewModel,
                    nav = nav,
                )
            }
        }

        channelInfo.relays?.let {
            Text(
                stringRes(R.string.public_chat_relays_title) + ": ",
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
            it.forEach {
                Spacer(StdVertSpacer)
                RenderRelayLinePublicChat(
                    it,
                    accountViewModel,
                    nav,
                )
            }
        }
    }
}

@Preview
@Composable
fun RenderRelayLinePreview() {
    ThemeComparisonRow {
        RenderRelayLine(
            "wss://nos.lol",
            "http://icon.com/icon.ico",
            Modifier,
            true,
            true,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RenderRelayLinePublicChat(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    @Suppress("ProduceStateDoesNotAssignValue")
    val relayInfo by loadRelayInfo(relay, accountViewModel)

    var openRelayDialog by remember { mutableStateOf(false) }

    if (openRelayDialog) {
        RelayInformationDialog(
            onClose = { openRelayDialog = false },
            relayInfo = relayInfo,
            relay = relay,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    val clipboardManager = LocalClipboardManager.current
    val clickableModifier =
        remember(relay) {
            Modifier.combinedClickable(
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(relay.url))
                },
                onClick = {
                    openRelayDialog = true
                    accountViewModel.retrieveRelayDocument(
                        relay = relay,
                        onInfo = {},
                        onError = { relay, errorCode, exceptionMessage ->
                            accountViewModel.toastManager.toast(
                                R.string.unable_to_download_relay_document,
                                when (errorCode) {
                                    Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                        R.string.relay_information_document_error_failed_to_assemble_url

                                    Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                        R.string.relay_information_document_error_failed_to_reach_server

                                    Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                        R.string.relay_information_document_error_failed_to_parse_response

                                    Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                        R.string.relay_information_document_error_failed_with_http
                                },
                                relay.url,
                                exceptionMessage ?: errorCode.toString(),
                            )
                        },
                    )
                },
            )
        }

    RenderRelayLine(
        relay.displayUrl(),
        relayInfo?.icon,
        clickableModifier,
        showPicture = accountViewModel.settings.showProfilePictures.value,
        loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
    )
}

@Composable
fun RenderRelayLine(
    url: String,
    icon: String?,
    modifier: Modifier = Modifier,
    showPicture: Boolean = true,
    loadRobohash: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier,
    ) {
        Text(" -")

        Spacer(modifier = StdHorzSpacer)

        RobohashFallbackAsyncImage(
            robot = url,
            model = icon,
            contentDescription = stringRes(id = R.string.relay_info, url),
            colorFilter = RelayIconFilter,
            modifier =
                Modifier
                    .size(Size20dp)
                    .clip(shape = CircleShape),
            loadProfilePicture = showPicture,
            loadRobohash = loadRobohash,
        )

        Spacer(modifier = StdHorzSpacer)

        Text(
            text = url,
        )
    }
}
