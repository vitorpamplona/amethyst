/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.FeatureSetType
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.ui.actions.relays.RelayInformationDialog
import com.vitorpamplona.amethyst.ui.components.ClickableBox
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.RelayIconFilter
import com.vitorpamplona.amethyst.ui.theme.Size15Modifier
import com.vitorpamplona.amethyst.ui.theme.Size17dp
import com.vitorpamplona.amethyst.ui.theme.StdStartPadding
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.relayIconModifier

@Composable
public fun RelayBadgesHorizontal(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val expanded = remember { mutableStateOf(false) }

    if (expanded.value) {
        RenderAllRelayList(baseNote, StdStartPadding, verticalArrangement = Arrangement.Center, accountViewModel, nav)
    } else {
        RenderClosedRelayList(baseNote, StdStartPadding, verticalAlignment = Alignment.CenterVertically, accountViewModel = accountViewModel, nav = nav)
        ShouldShowExpandButton(baseNote, accountViewModel) {
            ChatRelayExpandButton { expanded.value = true }
        }
    }
}

@Composable
fun ShouldShowExpandButton(
    baseNote: Note,
    accountViewModel: AccountViewModel,
    content: @Composable () -> Unit,
) {
    val showExpandButton by accountViewModel.createMustShowExpandButtonFlows(baseNote).collectAsStateWithLifecycle()

    if (showExpandButton) {
        content()
    }
}

@Composable
fun ChatRelayExpandButton(onClick: () -> Unit) {
    ClickableBox(
        modifier = Size15Modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringRes(id = R.string.expand_relay_list),
            modifier = Size15Modifier,
            tint = MaterialTheme.colorScheme.placeholderText,
        )
    }
}

@Composable
fun RenderRelay(
    relay: RelayBriefInfoCache.RelayBriefInfo,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val relayInfo by
        produceState(
            initialValue = Nip11CachedRetriever.getFromCache(relay.url),
        ) {
            if (value == null) {
                accountViewModel.retrieveRelayDocument(
                    relay.url,
                    onInfo = {
                        value = it
                    },
                    onError = { url, errorCode, exceptionMessage ->
                    },
                )
            }
        }

    var openRelayDialog by remember { mutableStateOf(false) }

    if (openRelayDialog && relayInfo != null) {
        RelayInformationDialog(
            onClose = { openRelayDialog = false },
            relayInfo = relayInfo!!,
            relayBriefInfo = relay,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    val clickableModifier =
        remember(relay) {
            Modifier
                .size(Size17dp)
                .clickable(
                    onClick = {
                        accountViewModel.retrieveRelayDocument(
                            relay.url,
                            onInfo = {
                                openRelayDialog = true
                            },
                            onError = { url, errorCode, exceptionMessage ->
                                accountViewModel.toast(
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
                                    url,
                                    exceptionMessage ?: errorCode.toString(),
                                )
                            },
                        )
                    },
                )
        }

    Box(
        modifier = clickableModifier,
        contentAlignment = Alignment.Center,
    ) {
        RenderRelayIcon(
            displayUrl = relay.displayUrl,
            iconUrl = relayInfo?.icon ?: relay.favIcon,
            loadProfilePicture = accountViewModel.settings.showProfilePictures.value,
            loadRobohash = accountViewModel.settings.featureSet != FeatureSetType.PERFORMANCE,
        )
    }
}

@Composable
fun RenderRelayIcon(
    displayUrl: String,
    iconUrl: String?,
    loadProfilePicture: Boolean,
    loadRobohash: Boolean,
    iconModifier: Modifier = MaterialTheme.colorScheme.relayIconModifier,
) {
    RobohashFallbackAsyncImage(
        robot = displayUrl,
        model = iconUrl,
        contentDescription = stringRes(id = R.string.relay_info, displayUrl),
        colorFilter = RelayIconFilter,
        modifier = iconModifier,
        loadProfilePicture = loadProfilePicture,
        loadRobohash = loadRobohash,
    )
}
