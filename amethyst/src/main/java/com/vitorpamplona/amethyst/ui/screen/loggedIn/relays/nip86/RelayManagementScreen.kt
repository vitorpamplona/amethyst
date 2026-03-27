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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.nip86

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.nip05DnsIdentifiers.Nip05State
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.nip86RelayManagement.Nip86Retriever
import com.vitorpamplona.amethyst.ui.layouts.listItem.SlimListItem
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.note.ClickableUserPicture
import com.vitorpamplona.amethyst.ui.note.ObserveAndRenderNIP05VerifiedSymbol
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.qrcode.BackButton
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.kindDisplayName
import com.vitorpamplona.amethyst.ui.theme.Font14SP
import com.vitorpamplona.amethyst.ui.theme.NIP05IconSize
import com.vitorpamplona.amethyst.ui.theme.Size55dp
import com.vitorpamplona.amethyst.ui.theme.StdHorzSpacer
import com.vitorpamplona.amethyst.ui.theme.nip05
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.nip86RelayManagement.rpc.Nip86Method
import kotlinx.collections.immutable.ImmutableList

@Composable
fun RelayManagementScreen(
    relayUrl: String,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    RelayUrlNormalizer.normalizeOrNull(relayUrl)?.let {
        RelayManagementScreen(
            relay = it,
            accountViewModel = accountViewModel,
            nav = nav,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayManagementScreen(
    relay: NormalizedRelayUrl,
    accountViewModel: AccountViewModel,
    nav: INav,
) {
    val retriever =
        remember {
            Nip86Retriever(Amethyst.instance.torEvaluatorFlow::okHttpClientForRelay)
        }

    val viewModel =
        remember(relay) {
            RelayManagementViewModel(
                relayUrl = relay,
                signer = accountViewModel.account.signer,
                retriever = retriever,
            )
        }

    LaunchedEffect(relay) {
        viewModel.loadSupportedMethods()
    }

    val supportedMethods by viewModel.supportedMethods.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(supportedMethods) {
        if (supportedMethods.isNotEmpty()) {
            viewModel.loadAllLists()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                actions = {},
                title = {
                    Text(
                        stringResource(R.string.relay_management_title, relay.displayUrl()),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    Row {
                        Spacer(modifier = StdHorzSpacer)
                        BackButton(onPress = nav::popBack)
                    }
                },
            )
        },
    ) { pad ->
        if (isLoading && supportedMethods.isEmpty()) {
            Column(
                modifier =
                    Modifier
                        .padding(pad)
                        .consumeWindowInsets(pad)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.relay_management_loading))
            }
        } else if (supportedMethods.isEmpty() && error != null) {
            Column(
                modifier =
                    Modifier
                        .padding(pad)
                        .consumeWindowInsets(pad)
                        .fillMaxSize()
                        .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.relay_management_error),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            RelayManagementContent(pad, viewModel, supportedMethods, error, accountViewModel)
        }
    }
}

@Composable
private fun RelayManagementContent(
    pad: PaddingValues,
    viewModel: RelayManagementViewModel,
    supportedMethods: ImmutableList<String>,
    error: String?,
    accountViewModel: AccountViewModel,
) {
    val tabs =
        remember(supportedMethods) {
            buildList {
                if (supportedMethods.any { it in listOf(Nip86Method.BAN_PUBKEY, Nip86Method.LIST_BANNED_PUBKEYS, Nip86Method.ALLOW_PUBKEY, Nip86Method.LIST_ALLOWED_PUBKEYS) }) {
                    add(ManagementTab.PUBKEYS)
                }
                if (supportedMethods.any {
                        it in listOf(Nip86Method.BAN_EVENT, Nip86Method.LIST_BANNED_EVENTS, Nip86Method.ALLOW_EVENT, Nip86Method.LIST_EVENTS_NEEDING_MODERATION)
                    }
                ) {
                    add(ManagementTab.EVENTS)
                }
                if (supportedMethods.any { it in listOf(Nip86Method.ALLOW_KIND, Nip86Method.DISALLOW_KIND, Nip86Method.LIST_ALLOWED_KINDS) }) {
                    add(ManagementTab.KINDS)
                }
                if (supportedMethods.any { it in listOf(Nip86Method.BLOCK_IP, Nip86Method.UNBLOCK_IP, Nip86Method.LIST_BLOCKED_IPS) }) {
                    add(ManagementTab.IPS)
                }
                if (supportedMethods.any { it in listOf(Nip86Method.CHANGE_RELAY_NAME, Nip86Method.CHANGE_RELAY_DESCRIPTION, Nip86Method.CHANGE_RELAY_ICON) }) {
                    add(ManagementTab.SETTINGS)
                }
            }
        }

    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier =
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
    ) {
        if (error != null) {
            Snackbar(
                modifier = Modifier.padding(8.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text(stringResource(R.string.relay_management_dismiss))
                    }
                },
            ) {
                Text(error)
            }
        }

        if (tabs.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab.coerceAtMost(tabs.size - 1),
                edgePadding = 8.dp,
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.titleRes)) },
                    )
                }
            }

            when (tabs.getOrNull(selectedTab)) {
                ManagementTab.PUBKEYS -> {
                    PubkeysTab(viewModel, supportedMethods, accountViewModel)
                }

                ManagementTab.EVENTS -> {
                    EventsTab(viewModel, supportedMethods)
                }

                ManagementTab.KINDS -> {
                    KindsTab(viewModel, supportedMethods)
                }

                ManagementTab.IPS -> {
                    IpsTab(viewModel, supportedMethods)
                }

                ManagementTab.SETTINGS -> {
                    SettingsTab(viewModel, supportedMethods)
                }

                null -> {}
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(R.string.relay_management_no_methods),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

private enum class ManagementTab(
    val titleRes: Int,
) {
    PUBKEYS(R.string.relay_management_tab_pubkeys),
    EVENTS(R.string.relay_management_tab_events),
    KINDS(R.string.relay_management_tab_kinds),
    IPS(R.string.relay_management_tab_ips),
    SETTINGS(R.string.relay_management_tab_settings),
}

// Pubkeys Tab
@Composable
private fun PubkeysTab(
    viewModel: RelayManagementViewModel,
    supportedMethods: List<String>,
    accountViewModel: AccountViewModel,
) {
    val bannedPubkeyUsers by viewModel.bannedPubkeyUsers.collectAsStateWithLifecycle(emptyList())
    val allowedPubkeyUsers by viewModel.allowedPubkeyUsers.collectAsStateWithLifecycle(emptyList())
    var showBanDialog by remember { mutableStateOf(false) }
    var showAllowDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (supportedMethods.contains(Nip86Method.LIST_BANNED_PUBKEYS)) {
            item {
                SectionHeaderWithAdd(
                    stringResource(R.string.relay_management_banned_pubkeys),
                    showAdd = supportedMethods.contains(Nip86Method.BAN_PUBKEY),
                    onAdd = { showBanDialog = true },
                )
            }

            if (bannedPubkeyUsers.isEmpty()) {
                item { EmptyListMessage(stringResource(R.string.relay_management_no_banned_pubkeys)) }
            } else {
                items(bannedPubkeyUsers, key = { it.user.pubkeyHex }) { entry ->
                    PubkeyUserCard(
                        entry = entry,
                        showRemove = supportedMethods.contains(Nip86Method.UNBAN_PUBKEY),
                        onRemove = { viewModel.unbanPubkey(entry.user.pubkeyHex) },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }

        if (supportedMethods.contains(Nip86Method.LIST_ALLOWED_PUBKEYS)) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SectionHeaderWithAdd(
                    stringResource(R.string.relay_management_allowed_pubkeys),
                    showAdd = supportedMethods.contains(Nip86Method.ALLOW_PUBKEY),
                    onAdd = { showAllowDialog = true },
                )
            }

            if (allowedPubkeyUsers.isEmpty()) {
                item { EmptyListMessage(stringResource(R.string.relay_management_no_allowed_pubkeys)) }
            } else {
                items(allowedPubkeyUsers, key = { it.user.pubkeyHex }) { entry ->
                    PubkeyUserCard(
                        entry = entry,
                        showRemove = supportedMethods.contains(Nip86Method.UNALLOW_PUBKEY),
                        onRemove = { viewModel.unallowPubkey(entry.user.pubkeyHex) },
                        accountViewModel = accountViewModel,
                    )
                }
            }
        }
    }

    if (showBanDialog) {
        HexInputDialog(
            title = stringResource(R.string.relay_management_ban_pubkey),
            label = stringResource(R.string.relay_management_pubkey_hex),
            onConfirm = { hex, reason ->
                viewModel.banPubkey(hex, reason.ifBlank { null })
                showBanDialog = false
            },
            onDismiss = { showBanDialog = false },
        )
    }

    if (showAllowDialog) {
        HexInputDialog(
            title = stringResource(R.string.relay_management_allow_pubkey),
            label = stringResource(R.string.relay_management_pubkey_hex),
            onConfirm = { hex, reason ->
                viewModel.allowPubkey(hex, reason.ifBlank { null })
                showAllowDialog = false
            },
            onDismiss = { showAllowDialog = false },
        )
    }
}

@Composable
private fun PubkeyUserCard(
    entry: PubkeyUser,
    showRemove: Boolean,
    onRemove: () -> Unit,
    accountViewModel: AccountViewModel,
) {
    SlimListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = {
            ClickableUserPicture(entry.user, Size55dp, accountViewModel = accountViewModel, onClick = null)
        },
        headlineContent = {
            UsernameDisplay(entry.user, accountViewModel = accountViewModel)
        },
        supportingContent = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    PubkeyNip05Row(entry.user, accountViewModel)
                }
                entry.reason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        trailingContent = {
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.relay_management_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun PubkeyNip05Row(
    user: User,
    accountViewModel: AccountViewModel,
) {
    val nip05StateMetadata by user.nip05State().flow.collectAsStateWithLifecycle()

    when (val nip05State = nip05StateMetadata) {
        is Nip05State.Exists -> {
            if (nip05State.nip05.name != "_") {
                Text(
                    text = remember(nip05State) { AnnotatedString(nip05State.nip05.name) },
                    fontSize = Font14SP,
                    color = MaterialTheme.colorScheme.nip05,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ObserveAndRenderNIP05VerifiedSymbol(nip05State, 1, NIP05IconSize, accountViewModel)

            Text(
                text = nip05State.nip05.domain,
                style = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.nip05, fontSize = Font14SP),
                maxLines = 1,
                overflow = TextOverflow.Visible,
            )
        }

        else -> {
            Text(
                text = user.pubkeyDisplayHex(),
                fontSize = Font14SP,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// Events Tab
@Composable
private fun EventsTab(
    viewModel: RelayManagementViewModel,
    supportedMethods: List<String>,
) {
    val bannedEvents by viewModel.bannedEvents.collectAsState()
    val eventsNeedingModeration by viewModel.eventsNeedingModeration.collectAsState()
    var showBanDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (supportedMethods.contains(Nip86Method.LIST_EVENTS_NEEDING_MODERATION)) {
            item {
                SectionHeaderWithAdd(
                    stringResource(R.string.relay_management_moderation_queue),
                    showAdd = false,
                    onAdd = {},
                )
            }

            if (eventsNeedingModeration.isEmpty()) {
                item { EmptyListMessage(stringResource(R.string.relay_management_no_moderation_events)) }
            } else {
                items(eventsNeedingModeration, key = { it.id }) { entry ->
                    ModerationEventCard(
                        eventId = entry.id,
                        reason = entry.reason,
                        canAllow = supportedMethods.contains(Nip86Method.ALLOW_EVENT),
                        canBan = supportedMethods.contains(Nip86Method.BAN_EVENT),
                        onAllow = { viewModel.allowEvent(entry.id) },
                        onBan = { viewModel.banEvent(entry.id) },
                    )
                }
            }
        }

        if (supportedMethods.contains(Nip86Method.LIST_BANNED_EVENTS)) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item {
                SectionHeaderWithAdd(
                    stringResource(R.string.relay_management_banned_events),
                    showAdd = supportedMethods.contains(Nip86Method.BAN_EVENT),
                    onAdd = { showBanDialog = true },
                )
            }

            if (bannedEvents.isEmpty()) {
                item { EmptyListMessage(stringResource(R.string.relay_management_no_banned_events)) }
            } else {
                items(bannedEvents, key = { it.id }) { entry ->
                    HexEntryCard(
                        hex = entry.id,
                        reason = entry.reason,
                        showRemove = false,
                        onRemove = {},
                    )
                }
            }
        }
    }

    if (showBanDialog) {
        HexInputDialog(
            title = stringResource(R.string.relay_management_ban_event),
            label = stringResource(R.string.relay_management_event_id_hex),
            onConfirm = { hex, reason ->
                viewModel.banEvent(hex, reason.ifBlank { null })
                showBanDialog = false
            },
            onDismiss = { showBanDialog = false },
        )
    }
}

// Kinds Tab
@Composable
private fun KindsTab(
    viewModel: RelayManagementViewModel,
    supportedMethods: List<String>,
) {
    val allowedKinds by viewModel.allowedKinds.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeaderWithAdd(
                stringResource(R.string.relay_management_allowed_kinds),
                showAdd = supportedMethods.contains(Nip86Method.ALLOW_KIND),
                onAdd = { showAddDialog = true },
            )
        }

        if (allowedKinds.isEmpty()) {
            item { EmptyListMessage(stringResource(R.string.relay_management_no_allowed_kinds)) }
        } else {
            items(allowedKinds, key = { it }) { kind ->
                KindEntryCard(
                    kind = kind,
                    showRemove = supportedMethods.contains(Nip86Method.DISALLOW_KIND),
                    onRemove = { viewModel.disallowKind(kind) },
                )
            }
        }
    }

    if (showAddDialog) {
        KindInputDialog(
            onConfirm = { kind ->
                viewModel.allowKind(kind)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

// IPs Tab
@Composable
private fun IpsTab(
    viewModel: RelayManagementViewModel,
    supportedMethods: List<String>,
) {
    val blockedIps by viewModel.blockedIps.collectAsState()
    var showBlockDialog by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            SectionHeaderWithAdd(
                stringResource(R.string.relay_management_blocked_ips),
                showAdd = supportedMethods.contains(Nip86Method.BLOCK_IP),
                onAdd = { showBlockDialog = true },
            )
        }

        if (blockedIps.isEmpty()) {
            item { EmptyListMessage(stringResource(R.string.relay_management_no_blocked_ips)) }
        } else {
            items(blockedIps, key = { it.ip }) { entry ->
                IpEntryCard(
                    ip = entry.ip,
                    reason = entry.reason,
                    showRemove = supportedMethods.contains(Nip86Method.UNBLOCK_IP),
                    onRemove = { viewModel.unblockIp(entry.ip) },
                )
            }
        }
    }

    if (showBlockDialog) {
        HexInputDialog(
            title = stringResource(R.string.relay_management_block_ip),
            label = stringResource(R.string.relay_management_ip_address),
            onConfirm = { ip, reason ->
                viewModel.blockIp(ip, reason.ifBlank { null })
                showBlockDialog = false
            },
            onDismiss = { showBlockDialog = false },
        )
    }
}

// Settings Tab
@Composable
private fun SettingsTab(
    viewModel: RelayManagementViewModel,
    supportedMethods: List<String>,
) {
    var relayName by remember { mutableStateOf("") }
    var relayDescription by remember { mutableStateOf("") }
    var relayIcon by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (supportedMethods.contains(Nip86Method.CHANGE_RELAY_NAME)) {
            item {
                SettingsField(
                    label = stringResource(R.string.relay_management_relay_name),
                    value = relayName,
                    onValueChange = { relayName = it },
                    onApply = { viewModel.changeRelayName(relayName) },
                )
            }
        }

        if (supportedMethods.contains(Nip86Method.CHANGE_RELAY_DESCRIPTION)) {
            item {
                SettingsField(
                    label = stringResource(R.string.relay_management_relay_description),
                    value = relayDescription,
                    onValueChange = { relayDescription = it },
                    onApply = { viewModel.changeRelayDescription(relayDescription) },
                )
            }
        }

        if (supportedMethods.contains(Nip86Method.CHANGE_RELAY_ICON)) {
            item {
                SettingsField(
                    label = stringResource(R.string.relay_management_relay_icon_url),
                    value = relayIcon,
                    onValueChange = { relayIcon = it },
                    onApply = { viewModel.changeRelayIcon(relayIcon) },
                )
            }
        }
    }
}

// Reusable components

@Composable
private fun SectionHeaderWithAdd(
    title: String,
    showAdd: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        if (showAdd) {
            IconButton(onClick = onAdd) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.relay_management_add),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun EmptyListMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HexEntryCard(
    hex: String,
    reason: String?,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    hex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                reason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.relay_management_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModerationEventCard(
    eventId: String,
    reason: String?,
    canAllow: Boolean,
    canBan: Boolean,
    onAllow: () -> Unit,
    onBan: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                eventId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            reason?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (canAllow) {
                    IconButton(onClick = onAllow) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.relay_management_allow),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (canBan) {
                    IconButton(onClick = onBan) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = stringResource(R.string.relay_management_ban),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KindEntryCard(
    kind: Int,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val nameResId = kindDisplayName(kind)
            val name = if (nameResId != -1) stringResource(nameResId) else ""

            Text(
                "Kind $kind: $name",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.relay_management_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun IpEntryCard(
    ip: String,
    reason: String?,
    showRemove: Boolean,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    ip,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                reason?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showRemove) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.relay_management_remove),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(
            onClick = onApply,
            modifier = Modifier.align(Alignment.End),
            enabled = value.isNotBlank(),
        ) {
            Text(stringResource(R.string.relay_management_apply))
        }
    }
}

@Composable
private fun HexInputDialog(
    title: String,
    label: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var hexValue by remember { mutableStateOf("") }
    var reasonValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { hexValue = it },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reasonValue,
                    onValueChange = { reasonValue = it },
                    label = { Text(stringResource(R.string.relay_management_reason_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(hexValue.trim(), reasonValue.trim()) },
                enabled = hexValue.isNotBlank(),
            ) {
                Text(stringResource(R.string.relay_management_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.relay_management_cancel))
            }
        },
    )
}

@Composable
private fun KindInputDialog(
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var kindValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.relay_management_allow_kind)) },
        text = {
            OutlinedTextField(
                value = kindValue,
                onValueChange = { kindValue = it.filter { c -> c.isDigit() } },
                label = { Text(stringResource(R.string.relay_management_kind_number)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    kindValue.toIntOrNull()?.let { onConfirm(it) }
                },
                enabled = kindValue.toIntOrNull() != null,
            ) {
                Text(stringResource(R.string.relay_management_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.relay_management_cancel))
            }
        },
    )
}
