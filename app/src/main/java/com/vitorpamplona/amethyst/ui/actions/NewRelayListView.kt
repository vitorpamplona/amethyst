package com.vitorpamplona.amethyst.ui.actions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import java.lang.Math.round

@Composable
fun NewRelayListView(onClose: () -> Unit, accountViewModel: AccountViewModel, relayToAdd: String = "") {
    val postViewModel: NewRelayListViewModel = viewModel()
    val feedState by postViewModel.relays.collectAsState()

    LaunchedEffect(Unit) {
        postViewModel.load(accountViewModel.account)
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface() {
            Column(
                modifier = Modifier.padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloseButton(onCancel = {
                        postViewModel.clear()
                        onClose()
                    })

                    PostButton(
                        onPost = {
                            postViewModel.create()
                            onClose()
                        },
                        true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = 10.dp,
                            bottom = 10.dp
                        )
                    ) {
                        itemsIndexed(feedState, key = { _, item -> item.url }) { index, item ->
                            if (index == 0) {
                                ServerConfigHeader()
                            }
                            ServerConfig(
                                item,
                                onToggleDownload = { postViewModel.toggleDownload(it) },
                                onToggleUpload = { postViewModel.toggleUpload(it) },

                                onToggleFollows = { postViewModel.toggleFollows(it) },
                                onTogglePrivateDMs = { postViewModel.toggleMessages(it) },
                                onTogglePublicChats = { postViewModel.togglePublicChats(it) },
                                onToggleGlobal = { postViewModel.toggleGlobal(it) },
                                onToggleSearch = { postViewModel.toggleSearch(it) },

                                onDelete = { postViewModel.deleteRelay(it) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                EditableServerConfig(relayToAdd) {
                    postViewModel.addRelay(it)
                }
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
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(Modifier.weight(1.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.size(30.dp))

                    Text(
                        text = stringResource(R.string.bytes),
                        maxLines = 1,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(id = R.string.bytes),
                        maxLines = 1,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.errors),
                        maxLines = 1,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.spam),
                        maxLines = 1,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                    )

                    Spacer(modifier = Modifier.size(2.dp))
                }
            }
        }

        Divider(
            thickness = 0.25.dp
        )
    }
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

    onDelete: (RelaySetupInfo) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 5.dp)
        ) {
            Column() {
                IconButton(
                    modifier = Modifier.size(30.dp),
                    onClick = { onDelete(item) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        null,
                        modifier = Modifier
                            .padding(end = 5.dp)
                            .size(15.dp),
                        tint = Color.Red
                    )
                }
            }

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.url.removePrefix("wss://"),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onToggleFollows(item) }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_home),
                                    stringResource(R.string.home_feed),
                                    modifier = Modifier
                                        .padding(end = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.feedTypes.contains(FeedType.FOLLOWS)) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onTogglePrivateDMs(item) }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_dm),
                                    stringResource(R.string.private_message_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.feedTypes.contains(FeedType.PRIVATE_DMS)) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onTogglePublicChats(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    stringResource(R.string.public_chat_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.feedTypes.contains(FeedType.PUBLIC_CHATS)) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onToggleGlobal(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    stringResource(R.string.global_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.feedTypes.contains(FeedType.GLOBAL)) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }

                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onToggleSearch(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    stringResource(R.string.search_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.feedTypes.contains(FeedType.SEARCH)) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }
                        }
                    }

                    Column(Modifier.weight(1.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onToggleDownload(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    null,
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.read) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }

                            Text(
                                text = "${countToHumanReadable(item.downloadCountInBytes)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1.2f),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { onToggleUpload(item) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    null,
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp),
                                    tint = if (item.write) {
                                        Color.Green
                                    } else {
                                        MaterialTheme.colors.onSurface.copy(
                                            alpha = 0.32f
                                        )
                                    }
                                )
                            }

                            Text(
                                text = "${countToHumanReadable(item.uploadCountInBytes)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1.2f),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                null,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .size(15.dp),
                                tint = if (item.errorCount > 0) Color.Yellow else Color.Green
                            )

                            Text(
                                text = "${countToHumanReadable(item.errorCount)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )

                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                null,
                                modifier = Modifier.padding(horizontal = 5.dp).size(15.dp),
                                tint = if (item.spamCount > 0) Color.Yellow else Color.Green
                            )

                            Text(
                                text = "${countToHumanReadable(item.spamCount)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                            )
                        }
                    }
                }
            }
        }

        Divider(
            thickness = 0.25.dp
        )
    }
}

@Composable
fun EditableServerConfig(relayToAdd: String, onNewRelay: (RelaySetupInfo) -> Unit) {
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
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.32f),
                    maxLines = 1
                )
            },
            singleLine = true
        )

        IconButton(onClick = { read = !read }) {
            Icon(
                imageVector = Icons.Default.Download,
                null,
                modifier = Modifier
                    .size(35.dp)
                    .padding(horizontal = 5.dp),
                tint = if (read) Color.Green else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }

        IconButton(onClick = { write = !write }) {
            Icon(
                imageVector = Icons.Default.Upload,
                null,
                modifier = Modifier
                    .size(35.dp)
                    .padding(horizontal = 5.dp),
                tint = if (write) Color.Green else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
            )
        }

        Button(
            onClick = {
                if (url.isNotBlank() && url != "/") {
                    var addedWSS = if (!url.startsWith("wss://") && !url.startsWith("ws://")) "wss://$url" else url
                    if (url.endsWith("/")) addedWSS = addedWSS.dropLast(1)
                    onNewRelay(RelaySetupInfo(addedWSS, read, write, feedTypes = FeedType.values().toSet()))
                    url = ""
                    write = true
                    read = true
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults
                .buttonColors(
                    backgroundColor = if (url.isNotBlank()) MaterialTheme.colors.primary else MaterialTheme.colors.onSurface.copy(alpha = 0.32f)
                )
        ) {
            Text(text = stringResource(id = R.string.add), color = Color.White)
        }
    }
}

fun countToHumanReadable(counter: Int) = when {
    counter >= 1000000000 -> "${round(counter / 1000000000f)}G"
    counter >= 1000000 -> "${round(counter / 1000000f)}M"
    counter >= 1000 -> "${round(counter / 1000f)}k"
    else -> "$counter"
}
