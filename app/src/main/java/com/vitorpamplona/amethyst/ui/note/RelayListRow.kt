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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.map
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.ui.actions.RelayInformationDialog
import com.vitorpamplona.amethyst.ui.components.RobohashFallbackAsyncImage
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
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

    RenderRelayList(baseNote, expanded, accountViewModel, nav)

    RenderExpandButton(baseNote, expanded) { ChatRelayExpandButton { expanded.value = true } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RenderRelayList(
    baseNote: Note,
    expanded: MutableState<Boolean>,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    val noteRelays by baseNote.live().relayInfo.observeAsState(baseNote.relays)

    FlowRow(StdStartPadding, verticalArrangement = Arrangement.Center) {
        if (expanded.value) {
            noteRelays?.forEach { RenderRelay(it, accountViewModel, nav) }
        } else {
            noteRelays?.getOrNull(0)?.let { RenderRelay(it, accountViewModel, nav) }
            noteRelays?.getOrNull(1)?.let { RenderRelay(it, accountViewModel, nav) }
            noteRelays?.getOrNull(2)?.let { RenderRelay(it, accountViewModel, nav) }
        }
    }
}

@Composable
fun RenderExpandButton(
    baseNote: Note,
    expanded: MutableState<Boolean>,
    content: @Composable () -> Unit,
) {
    val showExpandButton by
        baseNote.live().relays.map { it.note.relays.size > 3 }.observeAsState(baseNote.relays.size > 3)

    if (showExpandButton && !expanded.value) {
        content()
    }
}

@Composable
fun ChatRelayExpandButton(onClick: () -> Unit) {
    IconButton(
        modifier = Size15Modifier,
        onClick = onClick,
    ) {
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(id = R.string.expand_relay_list),
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

    val context = LocalContext.current

    val interactionSource = remember { MutableInteractionSource() }
    val ripple = rememberRipple(bounded = false, radius = Size17dp)

    val clickableModifier =
        remember(relay) {
            Modifier
                .size(Size17dp)
                .clickable(
                    role = Role.Button,
                    interactionSource = interactionSource,
                    indication = ripple,
                    onClick = {
                        accountViewModel.retrieveRelayDocument(
                            relay.url,
                            onInfo = {
                                openRelayDialog = true
                            },
                            onError = { url, errorCode, exceptionMessage ->
                                val msg =
                                    when (errorCode) {
                                        Nip11Retriever.ErrorCode.FAIL_TO_ASSEMBLE_URL ->
                                            context.getString(
                                                R.string.relay_information_document_error_assemble_url,
                                                url,
                                                exceptionMessage,
                                            )

                                        Nip11Retriever.ErrorCode.FAIL_TO_REACH_SERVER ->
                                            context.getString(
                                                R.string.relay_information_document_error_assemble_url,
                                                url,
                                                exceptionMessage,
                                            )

                                        Nip11Retriever.ErrorCode.FAIL_TO_PARSE_RESULT ->
                                            context.getString(
                                                R.string.relay_information_document_error_assemble_url,
                                                url,
                                                exceptionMessage,
                                            )

                                        Nip11Retriever.ErrorCode.FAIL_WITH_HTTP_STATUS ->
                                            context.getString(
                                                R.string.relay_information_document_error_assemble_url,
                                                url,
                                                exceptionMessage,
                                            )
                                    }

                                accountViewModel.toast(
                                    context.getString(R.string.unable_to_download_relay_document),
                                    msg,
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
        )
    }
}

@Composable
fun RenderRelayIcon(
    displayUrl: String,
    iconUrl: String?,
    loadProfilePicture: Boolean,
    iconModifier: Modifier = MaterialTheme.colorScheme.relayIconModifier,
) {
    RobohashFallbackAsyncImage(
        robot = displayUrl,
        model = iconUrl,
        contentDescription = stringResource(id = R.string.relay_info, displayUrl),
        colorFilter = RelayIconFilter,
        modifier = iconModifier,
        loadProfilePicture = loadProfilePicture,
    )
}
