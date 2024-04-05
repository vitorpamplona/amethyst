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
package com.vitorpamplona.amethyst.ui.actions

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayBriefInfoCache
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.Nip11CachedRetriever
import com.vitorpamplona.amethyst.service.Nip11Retriever
import com.vitorpamplona.amethyst.service.relays.Constants
import com.vitorpamplona.amethyst.service.relays.Constants.defaultRelays
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.ui.note.RenderRelayIcon
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.DividerThickness
import com.vitorpamplona.amethyst.ui.theme.FeedPadding
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.HalfHorzPadding
import com.vitorpamplona.amethyst.ui.theme.HalfStartPadding
import com.vitorpamplona.amethyst.ui.theme.ReactionRowHeightChat
import com.vitorpamplona.amethyst.ui.theme.Size30Modifier
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.WarningColor
import com.vitorpamplona.amethyst.ui.theme.allGoodColor
import com.vitorpamplona.amethyst.ui.theme.largeRelayIconModifier
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.theme.warningColor
import kotlinx.coroutines.launch
import java.lang.Math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRelayListView(
    onClose: () -> Unit,
    accountViewModel: AccountViewModel,
    relayToAdd: String = "",
    nav: (String) -> Unit,
) {
    val postViewModel: NewRelayListViewModel = viewModel()
    val feedState by postViewModel.relays.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { postViewModel.load(accountViewModel.account) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = StdHorzSpacer)

                            Button(
                                onClick = {
                                    postViewModel.deleteAll()
                                    defaultRelays.forEach { postViewModel.addRelay(it) }
                                    postViewModel.loadRelayDocuments()
                                },
                            ) {
                                Text(stringResource(R.string.default_relays))
                            }

                            SaveButton(
                                onPost = {
                                    postViewModel.create()
                                    onClose()
                                },
                                true,
                            )
                        }
                    },
                    navigationIcon = {
                        Spacer(modifier = StdHorzSpacer)
                        CloseButton(
                            onPress = {
                                postViewModel.clear()
                                onClose()
                            },
                        )
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
        ) { pad ->
            Column(
                modifier =
                    Modifier.padding(
                        16.dp,
                        pad.calculateTopPadding(),
                        16.dp,
                        pad.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        contentPadding = FeedPadding,
                    ) {
                        itemsIndexed(feedState, key = { _, item -> item.url }) { index, item ->
                            ServerConfig(
                                item,
                                onToggleDownload = { postViewModel.toggleDownload(it) },
                                onToggleUpload = { postViewModel.toggleUpload(it) },
                                onToggleFollows = { postViewModel.toggleFollows(it) },
                                onTogglePrivateDMs = { postViewModel.toggleMessages(it) },
                                onTogglePublicChats = { postViewModel.togglePublicChats(it) },
                                onToggleGlobal = { postViewModel.toggleGlobal(it) },
                                onToggleSearch = { postViewModel.toggleSearch(it) },
                                onDelete = { postViewModel.deleteRelay(it) },
                                accountViewModel = accountViewModel,
                            ) {
                                onClose()
                                nav(it)
                            }
                        }
                    }
                }

                Spacer(modifier = StdVertSpacer)

                EditableServerConfig(relayToAdd) { postViewModel.addRelay(it) }
            }
        }
    }
}

@Composable
fun ServerConfigHeader() {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.relay_address),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Column(Modifier.weight(1.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.size(30.dp))

                    Text(
                        text = stringResource(R.string.bytes),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(id = R.string.bytes),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.errors),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.spam),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.placeholderText,
                    )

                    Spacer(modifier = Modifier.size(2.dp))
                }
            }
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}

@Preview
@Composable
fun ServerConfigPreview() {
    ServerConfigClickableLine(
        loadProfilePicture = true,
        item =
            RelaySetupInfo(
                url = "nostr.mom",
                read = true,
                write = true,
                errorCount = 23,
                downloadCountInBytes = 10000,
                uploadCountInBytes = 10000000,
                spamCount = 10,
                feedTypes = Constants.activeTypesGlobalChats,
                paidRelay = true,
            ),
        onDelete = {},
        onToggleDownload = {},
        onToggleUpload = {},
        onToggleFollows = {},
        onTogglePrivateDMs = {},
        onTogglePublicChats = {},
        onToggleGlobal = {},
        onToggleSearch = {},
        onClick = {},
    )
}

@Composable
fun ServerConfig(
    item: RelaySetupInfo,
    onToggleDownload: (RelaySetupInfo) -> Unit,
    onToggleUpload: (RelaySetupInfo) -> Unit,
    onToggleFollows: (RelaySetupInfo) -> Unit,
    onTogglePrivateDMs: (RelaySetupInfo) -> Unit,
    onTogglePublicChats: (RelaySetupInfo) -> Unit,
    onToggleGlobal: (RelaySetupInfo) -> Unit,
    onToggleSearch: (RelaySetupInfo) -> Unit,
    onDelete: (RelaySetupInfo) -> Unit,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit,
) {
    var relayInfo: RelayInfoDialog? by remember { mutableStateOf(null) }
    val context = LocalContext.current

    relayInfo?.let {
        RelayInformationDialog(
            onClose = { relayInfo = null },
            relayInfo = it.relayInfo,
            relayBriefInfo = it.relayBriefInfo,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }

    val automaticallyShowProfilePicture =
        remember {
            accountViewModel.settings.showProfilePictures.value
        }

    ServerConfigClickableLine(
        item = item,
        loadProfilePicture = automaticallyShowProfilePicture,
        onToggleDownload = onToggleDownload,
        onToggleUpload = onToggleUpload,
        onToggleFollows = onToggleFollows,
        onTogglePrivateDMs = onTogglePrivateDMs,
        onTogglePublicChats = onTogglePublicChats,
        onToggleGlobal = onToggleGlobal,
        onToggleSearch = onToggleSearch,
        onDelete = onDelete,
        onClick = {
            accountViewModel.retrieveRelayDocument(
                item.url,
                onInfo = { relayInfo = RelayInfoDialog(RelayBriefInfoCache.RelayBriefInfo(item.url), it) },
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

@Composable
fun ServerConfigClickableLine(
    item: RelaySetupInfo,
    loadProfilePicture: Boolean,
    onToggleDownload: (RelaySetupInfo) -> Unit,
    onToggleUpload: (RelaySetupInfo) -> Unit,
    onToggleFollows: (RelaySetupInfo) -> Unit,
    onTogglePrivateDMs: (RelaySetupInfo) -> Unit,
    onTogglePublicChats: (RelaySetupInfo) -> Unit,
    onToggleGlobal: (RelaySetupInfo) -> Unit,
    onToggleSearch: (RelaySetupInfo) -> Unit,
    onDelete: (RelaySetupInfo) -> Unit,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 5.dp),
        ) {
            Column(Modifier.clickable(onClick = onClick)) {
                val iconUrlFromRelayInfoDoc =
                    remember(item) {
                        Nip11CachedRetriever.getFromCache(item.url)?.icon
                    }

                RenderRelayIcon(
                    item.briefInfo.displayUrl,
                    iconUrlFromRelayInfoDoc ?: item.briefInfo.favIcon,
                    loadProfilePicture,
                    MaterialTheme.colorScheme.largeRelayIconModifier,
                )
            }

            Spacer(modifier = HalfHorzPadding)

            Column(Modifier.weight(1f)) {
                FirstLine(item, onClick, onDelete, ReactionRowHeightChat.fillMaxWidth())

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = ReactionRowHeightChat.fillMaxWidth(),
                ) {
                    RenderActiveToggles(
                        item = item,
                        onToggleFollows = onToggleFollows,
                        onTogglePrivateDMs = onTogglePrivateDMs,
                        onTogglePublicChats = onTogglePublicChats,
                        onToggleGlobal = onToggleGlobal,
                        onToggleSearch = onToggleSearch,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = ReactionRowHeightChat.fillMaxWidth(),
                ) {
                    RenderStatusRow(
                        item = item,
                        onToggleDownload = onToggleDownload,
                        onToggleUpload = onToggleUpload,
                        modifier = HalfStartPadding.weight(1f),
                    )
                }
            }
        }

        HorizontalDivider(thickness = DividerThickness)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderStatusRow(
    item: RelaySetupInfo,
    onToggleDownload: (RelaySetupInfo) -> Unit,
    onToggleUpload: (RelaySetupInfo) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Icon(
        imageVector = Icons.Default.Download,
        contentDescription = stringResource(R.string.read_from_relay),
        modifier =
            Modifier.size(15.dp)
                .combinedClickable(
                    onClick = { onToggleDownload(item) },
                    onLongClick = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.read_from_relay),
                                Toast.LENGTH_SHORT,
                            )
                                .show()
                        }
                    },
                ),
        tint =
            if (item.read) {
                MaterialTheme.colorScheme.allGoodColor
            } else {
                MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.32f,
                )
            },
    )

    Text(
        text = countToHumanReadableBytes(item.downloadCountInBytes),
        maxLines = 1,
        fontSize = 12.sp,
        modifier = modifier,
        color = MaterialTheme.colorScheme.placeholderText,
    )

    Icon(
        imageVector = Icons.Default.Upload,
        stringResource(R.string.write_to_relay),
        modifier =
            Modifier.size(15.dp)
                .combinedClickable(
                    onClick = { onToggleUpload(item) },
                    onLongClick = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.write_to_relay),
                                Toast.LENGTH_SHORT,
                            )
                                .show()
                        }
                    },
                ),
        tint =
            if (item.write) {
                MaterialTheme.colorScheme.allGoodColor
            } else {
                MaterialTheme.colorScheme.onSurface.copy(
                    alpha = 0.32f,
                )
            },
    )

    Text(
        text = countToHumanReadableBytes(item.uploadCountInBytes),
        maxLines = 1,
        fontSize = 12.sp,
        modifier = modifier,
        color = MaterialTheme.colorScheme.placeholderText,
    )

    Icon(
        imageVector = Icons.Default.SyncProblem,
        stringResource(R.string.errors),
        modifier =
            Modifier.size(15.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.errors),
                                Toast.LENGTH_SHORT,
                            )
                                .show()
                        }
                    },
                ),
        tint =
            if (item.errorCount > 0) {
                MaterialTheme.colorScheme.warningColor
            } else {
                MaterialTheme.colorScheme.allGoodColor
            },
    )

    Text(
        text = countToHumanReadable(item.errorCount, "errors"),
        maxLines = 1,
        fontSize = 12.sp,
        modifier = modifier,
        color = MaterialTheme.colorScheme.placeholderText,
    )

    Icon(
        imageVector = Icons.Default.DeleteSweep,
        stringResource(R.string.spam),
        modifier =
            Modifier.size(15.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        scope.launch {
                            Toast.makeText(
                                context,
                                context.getString(R.string.spam),
                                Toast.LENGTH_SHORT,
                            )
                                .show()
                        }
                    },
                ),
        tint =
            if (item.spamCount > 0) {
                MaterialTheme.colorScheme.warningColor
            } else {
                MaterialTheme.colorScheme.allGoodColor
            },
    )

    Text(
        text = countToHumanReadable(item.spamCount, "spam"),
        maxLines = 1,
        fontSize = 12.sp,
        modifier = modifier,
        color = MaterialTheme.colorScheme.placeholderText,
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun RenderActiveToggles(
    item: RelaySetupInfo,
    onToggleFollows: (RelaySetupInfo) -> Unit,
    onTogglePrivateDMs: (RelaySetupInfo) -> Unit,
    onTogglePublicChats: (RelaySetupInfo) -> Unit,
    onToggleGlobal: (RelaySetupInfo) -> Unit,
    onToggleSearch: (RelaySetupInfo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Text(
        text = stringResource(id = R.string.active_for),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.placeholderText,
        modifier = Modifier.padding(start = 2.dp, end = 5.dp),
        fontSize = 14.sp,
    )

    IconButton(
        modifier = Size30Modifier,
        onClick = { onToggleFollows(item) },
    ) {
        Icon(
            painterResource(R.drawable.ic_home),
            stringResource(R.string.home_feed),
            modifier =
                Modifier.padding(horizontal = 5.dp)
                    .size(15.dp)
                    .combinedClickable(
                        onClick = { onToggleFollows(item) },
                        onLongClick = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.home_feed),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                    ),
            tint =
                if (item.feedTypes.contains(FeedType.FOLLOWS)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.32f,
                    )
                },
        )
    }
    IconButton(
        modifier = Size30Modifier,
        onClick = { onTogglePrivateDMs(item) },
    ) {
        Icon(
            painterResource(R.drawable.ic_dm),
            stringResource(R.string.private_message_feed),
            modifier =
                Modifier.padding(horizontal = 5.dp)
                    .size(15.dp)
                    .combinedClickable(
                        onClick = { onTogglePrivateDMs(item) },
                        onLongClick = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.private_message_feed),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                    ),
            tint =
                if (item.feedTypes.contains(FeedType.PRIVATE_DMS)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.32f,
                    )
                },
        )
    }
    IconButton(
        modifier = Size30Modifier,
        onClick = { onTogglePublicChats(item) },
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = stringResource(R.string.public_chat_feed),
            modifier =
                Modifier.padding(horizontal = 5.dp)
                    .size(15.dp)
                    .combinedClickable(
                        onClick = { onTogglePublicChats(item) },
                        onLongClick = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.public_chat_feed),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                    ),
            tint =
                if (item.feedTypes.contains(FeedType.PUBLIC_CHATS)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.32f,
                    )
                },
        )
    }
    IconButton(
        modifier = Size30Modifier,
        onClick = { onToggleGlobal(item) },
    ) {
        Icon(
            imageVector = Icons.Default.Public,
            stringResource(R.string.global_feed),
            modifier =
                Modifier.padding(horizontal = 5.dp)
                    .size(15.dp)
                    .combinedClickable(
                        onClick = { onToggleGlobal(item) },
                        onLongClick = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.global_feed),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                    ),
            tint =
                if (item.feedTypes.contains(FeedType.GLOBAL)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.32f,
                    )
                },
        )
    }

    IconButton(
        modifier = Size30Modifier,
        onClick = { onToggleSearch(item) },
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            stringResource(R.string.search_feed),
            modifier =
                Modifier.padding(horizontal = 5.dp)
                    .size(15.dp)
                    .combinedClickable(
                        onClick = { onToggleSearch(item) },
                        onLongClick = {
                            scope.launch {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.search_feed),
                                    Toast.LENGTH_SHORT,
                                )
                                    .show()
                            }
                        },
                    ),
            tint =
                if (item.feedTypes.contains(FeedType.SEARCH)) {
                    MaterialTheme.colorScheme.allGoodColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(
                        alpha = 0.32f,
                    )
                },
        )
    }
}

@Composable
private fun FirstLine(
    item: RelaySetupInfo,
    onClick: () -> Unit,
    onDelete: (RelaySetupInfo) -> Unit,
    modifier: Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.briefInfo.displayUrl,
                modifier = Modifier.clickable(onClick = onClick),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (item.paidRelay) {
                Icon(
                    imageVector = Icons.Default.Paid,
                    null,
                    modifier = Modifier.padding(start = 5.dp, top = 1.dp).size(14.dp),
                    tint = MaterialTheme.colorScheme.allGoodColor,
                )
            }
        }

        IconButton(
            modifier = Modifier.size(30.dp),
            onClick = { onDelete(item) },
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = stringResource(id = R.string.remove),
                modifier = Modifier.padding(start = 10.dp).size(15.dp),
                tint = WarningColor,
            )
        }
    }
}

@Composable
fun EditableServerConfig(
    relayToAdd: String,
    onNewRelay: (RelaySetupInfo) -> Unit,
) {
    var url by remember { mutableStateOf<String>(relayToAdd) }
    var read by remember { mutableStateOf(true) }
    var write by remember { mutableStateOf(true) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            label = { Text(text = stringResource(R.string.add_a_relay)) },
            modifier = Modifier.weight(1f),
            value = url,
            onValueChange = { url = it },
            placeholder = {
                Text(
                    text = "server.com",
                    color = MaterialTheme.colorScheme.placeholderText,
                    maxLines = 1,
                )
            },
            singleLine = true,
        )

        IconButton(onClick = { read = !read }) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = stringResource(id = R.string.read_from_relay),
                modifier = Modifier.size(Size35dp).padding(horizontal = 5.dp),
                tint =
                    if (read) {
                        MaterialTheme.colorScheme.allGoodColor
                    } else {
                        MaterialTheme.colorScheme.placeholderText
                    },
            )
        }

        IconButton(onClick = { write = !write }) {
            Icon(
                imageVector = Icons.Default.Upload,
                contentDescription = stringResource(id = R.string.write_to_relay),
                modifier = Modifier.size(Size35dp).padding(horizontal = 5.dp),
                tint =
                    if (write) {
                        MaterialTheme.colorScheme.allGoodColor
                    } else {
                        MaterialTheme.colorScheme.placeholderText
                    },
            )
        }

        Button(
            onClick = {
                if (url.isNotBlank() && url != "/") {
                    var addedWSS =
                        if (!url.startsWith("wss://") && !url.startsWith("ws://")) {
                            if (url.endsWith(".onion") || url.endsWith(".onion/")) {
                                "ws://$url"
                            } else {
                                "wss://$url"
                            }
                        } else {
                            url
                        }
                    if (url.endsWith("/")) addedWSS = addedWSS.dropLast(1)
                    onNewRelay(RelaySetupInfo(addedWSS, read, write, feedTypes = FeedType.values().toSet()))
                    url = ""
                    write = true
                    read = true
                }
            },
            shape = ButtonBorder,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor =
                        if (url.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.placeholderText
                        },
                ),
        ) {
            Text(text = stringResource(id = R.string.add), color = Color.White)
        }
    }
}

fun countToHumanReadableBytes(counter: Int) =
    when {
        counter >= 1000000000 -> "${round(counter / 1000000000f)} GB"
        counter >= 1000000 -> "${round(counter / 1000000f)} MB"
        counter >= 1000 -> "${round(counter / 1000f)} KB"
        else -> "$counter"
    }

fun countToHumanReadable(
    counter: Int,
    str: String,
) = when {
    counter >= 1000000000 -> "${round(counter / 1000000000f)}G $str"
    counter >= 1000000 -> "${round(counter / 1000000f)}M $str"
    counter >= 1000 -> "${round(counter / 1000f)}K $str"
    else -> "$counter $str"
}
