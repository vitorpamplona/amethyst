package com.vitorpamplona.amethyst.ui.actions

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.Paid
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.model.RelayInformation
import com.vitorpamplona.amethyst.model.RelaySetupInfo
import com.vitorpamplona.amethyst.service.relays.Constants.defaultRelays
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.theme.ButtonBorder
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.Size35dp
import com.vitorpamplona.amethyst.ui.theme.StdVertSpacer
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.Math.round

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewRelayListView(onClose: () -> Unit, accountViewModel: AccountViewModel, relayToAdd: String = "", nav: (String) -> Unit) {
    val postViewModel: NewRelayListViewModel = viewModel()
    val feedState by postViewModel.relays.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        postViewModel.load(accountViewModel.account)
        postViewModel.relays.value.forEach { item ->
            loadRelayInfo(item.url, context, scope) {
                postViewModel.togglePaidRelay(item, it.limitation?.payment_required ?: false)
            }
        }
    }

    Dialog(
        onDismissRequest = { onClose() },
        properties = DialogProperties(
            decorFitsSystemWindows = false,
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(modifier = Modifier.imePadding()) {
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

                    Button(
                        onClick = {
                            postViewModel.deleteAll()
                            defaultRelays.forEach {
                                postViewModel.addRelay(it)
                            }
                            postViewModel.relays.value.forEach { item ->
                                loadRelayInfo(item.url, context, scope) {
                                    postViewModel.togglePaidRelay(item, it.limitation?.payment_required ?: false)
                                }
                            }
                        }
                    ) {
                        Text(stringResource(R.string.default_relays))
                    }

                    PostButton(
                        onPost = {
                            postViewModel.create()
                            onClose()
                        },
                        true
                    )
                }

                Spacer(modifier = StdVertSpacer)

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

                                onDelete = { postViewModel.deleteRelay(it) },
                                accountViewModel = accountViewModel,
                                nav = nav,
                                scope = scope,
                                context = context
                            )
                        }
                    }
                }

                Spacer(modifier = StdVertSpacer)

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
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colors.placeholderText
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(id = R.string.bytes),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1.2f),
                        color = MaterialTheme.colors.placeholderText
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.errors),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.placeholderText
                    )

                    Spacer(modifier = Modifier.size(5.dp))

                    Text(
                        text = stringResource(R.string.spam),
                        maxLines = 1,
                        fontSize = Font14SP,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colors.placeholderText
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

@OptIn(ExperimentalFoundationApi::class)
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
    context: Context,
    scope: CoroutineScope
) {
    var relayInfo: RelayInformation? by remember { mutableStateOf(null) }

    if (relayInfo != null) {
        RelayInformationDialog(
            onClose = {
                relayInfo = null
            },
            relayInfo = relayInfo!!,
            accountViewModel,
            nav
        )
    }

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
                    if (item.paidRelay) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            null,
                            modifier = Modifier
                                .padding(end = 5.dp)
                                .size(14.dp),
                            tint = Color.Green
                        )
                    }

                    Text(
                        text = item.url.removePrefix("wss://").removeSuffix("/"),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                loadRelayInfo(item.url, context, scope) {
                                    relayInfo = it
                                }
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                modifier = Modifier
                                    .size(30.dp),
                                onClick = { }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_home),
                                    stringResource(R.string.home_feed),
                                    modifier = Modifier
                                        .padding(end = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onToggleFollows(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.home_feed),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                onClick = { }
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_dm),
                                    stringResource(R.string.private_message_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onTogglePrivateDMs(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.private_message_feed),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                onClick = { }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Groups,
                                    stringResource(R.string.public_chat_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onTogglePublicChats(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.public_chat_feed),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                onClick = { }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    stringResource(R.string.global_feed),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onToggleGlobal(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.global_feed),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onToggleSearch(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.search_feed),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                onClick = { }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    stringResource(R.string.read_from_relay),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onToggleDownload(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.read_from_relay),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                color = MaterialTheme.colors.placeholderText
                            )

                            IconButton(
                                modifier = Modifier.size(30.dp),
                                onClick = { }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    stringResource(R.string.write_to_relay),
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .size(15.dp)
                                        .combinedClickable(
                                            onClick = { onToggleUpload(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    Toast
                                                        .makeText(
                                                            context,
                                                            context.getString(R.string.write_to_relay),
                                                            Toast.LENGTH_SHORT
                                                        )
                                                        .show()
                                                }
                                            }
                                        ),
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
                                color = MaterialTheme.colors.placeholderText
                            )

                            Icon(
                                imageVector = Icons.Default.SyncProblem,
                                stringResource(R.string.errors),
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .size(15.dp)
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = {
                                            scope.launch {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.errors),
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        }
                                    ),
                                tint = if (item.errorCount > 0) Color.Yellow else Color.Green
                            )

                            Text(
                                text = "${countToHumanReadable(item.errorCount)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colors.placeholderText
                            )

                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                stringResource(R.string.spam),
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .size(15.dp)
                                    .combinedClickable(
                                        onClick = { },
                                        onLongClick = {
                                            scope.launch {
                                                Toast
                                                    .makeText(
                                                        context,
                                                        context.getString(R.string.spam),
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        }
                                    ),
                                tint = if (item.spamCount > 0) Color.Yellow else Color.Green
                            )

                            Text(
                                text = "${countToHumanReadable(item.spamCount)}",
                                maxLines = 1,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colors.placeholderText
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
                    color = MaterialTheme.colors.placeholderText,
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
                    .size(Size35dp)
                    .padding(horizontal = 5.dp),
                tint = if (read) Color.Green else MaterialTheme.colors.placeholderText
            )
        }

        IconButton(onClick = { write = !write }) {
            Icon(
                imageVector = Icons.Default.Upload,
                null,
                modifier = Modifier
                    .size(Size35dp)
                    .padding(horizontal = 5.dp),
                tint = if (write) Color.Green else MaterialTheme.colors.placeholderText
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
            shape = ButtonBorder,
            colors = ButtonDefaults
                .buttonColors(
                    backgroundColor = if (url.isNotBlank()) MaterialTheme.colors.primary else MaterialTheme.colors.placeholderText
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
