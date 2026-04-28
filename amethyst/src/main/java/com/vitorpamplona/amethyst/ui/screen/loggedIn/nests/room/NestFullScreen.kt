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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.viewmodels.BroadcastUiState
import com.vitorpamplona.amethyst.commons.viewmodels.ConnectionUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.buildParticipantGrid
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.toNAddr
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlinx.coroutines.launch

/**
 * Full-screen layout for [NestActivity]. Renders title + summary,
 * host/speaker/audience rows (with active-speaker rings), connection chip
 * + mute, talk row (when allowed), hand-raise, and a Leave button that
 * finishes the activity.
 *
 * The PIP variant lives in [NestPipScreen]; the activity flips
 * between them based on `isInPipMode`.
 */
@Composable
internal fun NestFullScreen(
    event: MeetingSpaceEvent,
    roomNote: com.vitorpamplona.amethyst.model.AddressableNote,
    onStage: List<ParticipantTag>,
    viewModel: NestViewModel,
    ui: NestUiState,
    accountViewModel: AccountViewModel,
    handRaised: Boolean,
    onHandRaisedChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    var showEditSheet by rememberSaveable { mutableStateOf(false) }
    var showHostMenu by rememberSaveable { mutableStateOf(false) }
    var showHostLeaveConfirm by rememberSaveable { mutableStateOf(false) }
    val isHost = accountViewModel.account.signer.pubKey == event.pubKey
    val leaveScope = rememberCoroutineScope()
    val topBarContext = LocalContext.current

    // Scaffold owns safeDrawing insets via its `contentWindowInsets`
    // default, so we don't manage them manually anymore. The
    // container is transparent because NestThemedScope's outer
    // Surface already paints the themed background (and overlays
    // the optional `bg` image on top of it); painting again here
    // would double up.
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            NestTopAppBar(
                title = event.room().orEmpty(),
                isHost = isHost,
                showHostMenu = showHostMenu,
                onMenuOpen = { showHostMenu = true },
                onMenuDismiss = { showHostMenu = false },
                onShare = {
                    showHostMenu = false
                    shareRoomNaddr(topBarContext, event)
                },
                onEdit = {
                    showHostMenu = false
                    showEditSheet = true
                },
            )
        },
    ) { padding ->
        // Inner column is just the two weighted siblings — top
        // metadata (scrolls internally if overflow) and the chat
        // panel (takes the rest). Title and overflow menu live in
        // the TopAppBar above.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
            ) {
                event.summary()?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Listener counter — counts every active kind-10312 presence
                // in the room. Hidden until the aggregator has at least one
                // entry so the placeholder doesn't flash on entry.
                val presences by viewModel.presences.collectAsState()

                val reactionsByPubkey by viewModel.recentReactions.collectAsState()
                var hostMenuTarget by rememberSaveable { mutableStateOf<String?>(null) }
                // Long-press opens the participant context sheet for ANYONE
                // (T2 #2). The sheet's own gating decides which rows to show
                // (follow/mute always; promote/demote/kick host-only).
                val onLongPressParticipant: ((String) -> Unit) = { target ->
                    if (target != accountViewModel.account.signer.pubKey) hostMenuTarget = target
                }
                // Tier-2 #1: replace the two LazyRow sections with a single
                // pure-projection ParticipantGrid. The `absent` flag (member
                // promoted in the kind-30312 but never emitted a kind-10312)
                // greys out at 50 % alpha, matching nostrnests' web client.
                val participantGrid =
                    androidx.compose.runtime.remember(event, presences) {
                        buildParticipantGrid(
                            participants = event.participants(),
                            presences = presences,
                        )
                    }
                ParticipantsGrid(
                    grid = participantGrid,
                    speakingNow = ui.speakingNow,
                    accountViewModel = accountViewModel,
                    onStageLabel = stringRes(R.string.nest_stage),
                    audienceLabel = stringRes(R.string.nest_audience),
                    reactionsByPubkey = reactionsByPubkey,
                    connectingSpeakers = ui.connectingSpeakers,
                    onLongPressParticipant = onLongPressParticipant,
                )
                val speakerCatalogs by viewModel.speakerCatalogs.collectAsState()
                hostMenuTarget?.let { target ->
                    ParticipantHostActionsSheet(
                        target = target,
                        event = event,
                        accountViewModel = accountViewModel,
                        onDismiss = { hostMenuTarget = null },
                        catalog = speakerCatalogs[target],
                    )
                }

                if (isHost) {
                    HandRaiseQueueSection(
                        event = event,
                        viewModel = viewModel,
                        accountViewModel = accountViewModel,
                    )
                }

                ConnectionRow(viewModel = viewModel, ui = ui)

                val myPubkey = accountViewModel.account.signer.pubKey
                if (viewModel.canBroadcast && onStage.any { it.pubKey == myPubkey }) {
                    TalkRow(viewModel = viewModel, ui = ui, speakerPubkeyHex = myPubkey)
                }

                var showReactionPicker by rememberSaveable { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconToggleButton(
                        checked = handRaised,
                        onCheckedChange = onHandRaisedChange,
                    ) {
                        Icon(
                            symbol = MaterialSymbols.PanTool,
                            contentDescription =
                                stringRes(
                                    if (handRaised) R.string.nest_lower_hand else R.string.nest_raise_hand,
                                ),
                        )
                    }
                    OutlinedButton(onClick = { showReactionPicker = true }) {
                        Text(stringRes(R.string.nest_reactions_button))
                    }
                    OutlinedButton(
                        onClick = {
                            if (isHost) {
                                showHostLeaveConfirm = true
                            } else {
                                onLeave()
                            }
                        },
                    ) {
                        Text(stringRes(R.string.nest_leave))
                    }
                }
                if (showReactionPicker) {
                    RoomReactionPickerSheet(
                        onPick = { emoji ->
                            accountViewModel.reactToOrDelete(roomNote, emoji)
                        },
                        onDismiss = { showReactionPicker = false },
                    )
                }

                if (showHostLeaveConfirm) {
                    AlertDialog(
                        onDismissRequest = { showHostLeaveConfirm = false },
                        title = { Text(stringRes(R.string.nest_leave_host_title)) },
                        text = { Text(stringRes(R.string.nest_leave_host_body)) },
                        confirmButton = {
                            TextButton(
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                onClick = {
                                    showHostLeaveConfirm = false
                                    leaveScope.launch {
                                        val ok = closeMeetingSpace(accountViewModel, event)
                                        if (!ok) {
                                            accountViewModel.toastManager.toast(
                                                R.string.nests,
                                                R.string.nest_leave_host_close_failed,
                                            )
                                        }
                                        onLeave()
                                    }
                                },
                            ) {
                                Text(stringRes(R.string.nest_leave_host_close))
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showHostLeaveConfirm = false
                                    onLeave()
                                },
                            ) {
                                Text(stringRes(R.string.nest_leave_host_just_leave))
                            }
                        },
                    )
                }
            }

            NestChatPanel(
                event = event,
                viewModel = viewModel,
                accountViewModel = accountViewModel,
                modifier = Modifier.weight(1f),
            )
        }
    }

    // EditNestSheet renders as a ModalBottomSheet — placement in
    // the tree doesn't affect layout, but keeping it adjacent to
    // the Scaffold makes the dialog/sheet boundary obvious.
    if (showEditSheet) {
        EditNestSheet(
            accountViewModel = accountViewModel,
            event = event,
            onDismiss = { showEditSheet = false },
        )
    }
}

/**
 * TopAppBar for the room screen — hosts the room name and the
 * overflow menu (Share for everyone, Edit for the host). The menu
 * state lives at the screen level so the EditNestSheet can be
 * triggered from here and rendered alongside the Scaffold.
 *
 * Container color is transparent so the themed background painted
 * by [NestThemedScope]'s Surface (and any optional `bg` image) shows
 * through cleanly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NestTopAppBar(
    title: String,
    isHost: Boolean,
    showHostMenu: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
) {
    ShorterTopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            Box {
                IconButton(onClick = onMenuOpen) {
                    Icon(
                        symbol = MaterialSymbols.MoreVert,
                        contentDescription = stringRes(R.string.nest_overflow_menu),
                    )
                }
                DropdownMenu(
                    expanded = showHostMenu,
                    onDismissRequest = onMenuDismiss,
                ) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.nest_share_action)) },
                        onClick = onShare,
                    )
                    if (isHost) {
                        DropdownMenuItem(
                            text = { Text(stringRes(R.string.nest_edit_title)) },
                            onClick = onEdit,
                        )
                    }
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
    )
}

@Composable
private fun ConnectionRow(
    viewModel: NestViewModel,
    ui: NestUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val connection = ui.connection) {
            is ConnectionUiState.Idle, is ConnectionUiState.Closed -> {
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.nest_connect))
                }
            }

            is ConnectionUiState.Connecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(connectingLabel(connection)) },
                )
            }

            is ConnectionUiState.Reconnecting -> {
                // The wrapper retries on its own; the user typically
                // doesn't need to do anything. Keep the mute toggle
                // hidden during this window — there's no live session
                // to apply mute against, and showing it implies a
                // healthier connection than we have.
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.nest_reconnecting)) },
                )
            }

            is ConnectionUiState.Connected -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.nest_connected)) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                )
                FilledTonalIconToggleButton(
                    checked = ui.isMuted,
                    onCheckedChange = { viewModel.setMuted(it) },
                ) {
                    Icon(
                        symbol = if (ui.isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                        contentDescription =
                            stringRes(
                                if (ui.isMuted) R.string.nest_unmute else R.string.nest_mute,
                            ),
                    )
                }
            }

            is ConnectionUiState.Failed -> {
                Text(
                    text = stringRes(R.string.nest_audio_failed, connection.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(onClick = { viewModel.connect() }) {
                    Text(stringRes(R.string.nest_connect))
                }
            }
        }
    }
}

@Composable
private fun TalkRow(
    viewModel: NestViewModel,
    ui: NestUiState,
    speakerPubkeyHex: String,
) {
    // Render whenever the user can act on broadcast state. The
    // speaker session is independent of the listener: a transient
    // listener Reconnecting must NOT hide the mic-mute / Stop
    // controls if the broadcast is in flight, otherwise the user
    // can't pause their own mic during a network blip.
    val canAct =
        ui.connection is ConnectionUiState.Connected ||
            ui.broadcast is BroadcastUiState.Broadcasting ||
            ui.broadcast is BroadcastUiState.Connecting
    if (!canAct) return
    val context = LocalContext.current
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                permissionDenied = false
                viewModel.startBroadcast(speakerPubkeyHex)
            } else {
                permissionDenied = true
            }
        }
    // If the user grants RECORD_AUDIO via the system Settings deep-link
    // and returns to the activity, the permissionLauncher callback never
    // fires and `permissionDenied` would otherwise stay true. Recompute
    // every time the permission state could have changed (audit round-2
    // Android #12).
    val showDenialWarning =
        permissionDenied &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (val broadcast = ui.broadcast) {
            BroadcastUiState.Idle -> {
                Button(onClick = {
                    val granted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        viewModel.startBroadcast(speakerPubkeyHex)
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Text(stringRes(R.string.nest_talk))
                }
                OutlinedButton(onClick = { viewModel.setOnStage(false) }) {
                    Text(stringRes(R.string.nest_leave_stage))
                }
                if (showDenialWarning) {
                    Text(
                        text = stringRes(R.string.nest_record_permission_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    // After "Don't ask again" the permission launcher
                    // silently returns false. Give the user a path to
                    // toggle the permission in system settings (audit
                    // Android #14).
                    OutlinedButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content
                                    .Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.fromParts("package", context.packageName, null),
                                    ).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    },
                            )
                        }
                    }) {
                        Text(stringRes(R.string.nest_open_settings))
                    }
                }
            }

            BroadcastUiState.Connecting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.nest_broadcast_connecting)) },
                )
            }

            is BroadcastUiState.Broadcasting -> {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringRes(R.string.nest_broadcasting)) },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            disabledLabelColor = MaterialTheme.colorScheme.error,
                        ),
                )
                FilledTonalIconToggleButton(
                    checked = broadcast.isMuted,
                    onCheckedChange = { viewModel.setMicMuted(it) },
                ) {
                    Icon(
                        symbol = if (broadcast.isMuted) MaterialSymbols.AutoMirrored.VolumeOff else MaterialSymbols.AutoMirrored.VolumeUp,
                        contentDescription =
                            stringRes(
                                if (broadcast.isMuted) R.string.nest_mic_unmute else R.string.nest_mic_mute,
                            ),
                    )
                }
                OutlinedButton(onClick = { viewModel.stopBroadcast() }) {
                    Text(stringRes(R.string.nest_stop_talking))
                }
                OutlinedButton(onClick = {
                    viewModel.stopBroadcast()
                    viewModel.setOnStage(false)
                }) {
                    Text(stringRes(R.string.nest_leave_stage))
                }
            }

            is BroadcastUiState.Failed -> {
                Text(
                    text = stringRes(R.string.nest_broadcast_failed, broadcast.reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(onClick = { viewModel.startBroadcast(speakerPubkeyHex) }) {
                    Text(stringRes(R.string.nest_talk))
                }
            }
        }
    }
}

/**
 * Re-publish the host's [event] with `status="closed"`, preserving every
 * other field verbatim (room name, summary, image, service, endpoint,
 * participants). Returns true on success.
 *
 * Reuses [EditNestViewModel.buildEditTemplate] so the close-on-leave
 * path emits the same shape as the explicit Edit → Close room action.
 * Decoupled from [EditNestViewModel] state so the host's unsaved
 * edits in the Edit sheet (if any) cannot accidentally leak into the
 * close payload.
 */
private suspend fun closeMeetingSpace(
    accountViewModel: AccountViewModel,
    event: MeetingSpaceEvent,
): Boolean {
    val verbatim =
        EditNestViewModel.FormState(
            dTag = event.dTag(),
            roomName = event.room().orEmpty(),
            summary = event.summary().orEmpty(),
            imageUrl = event.image().orEmpty(),
            endpointUrl = event.endpoint().orEmpty(),
            serviceUrl = event.service().orEmpty(),
            isPublishing = false,
            error = null,
        )
    return try {
        val template =
            EditNestViewModel.buildEditTemplate(event, verbatim, StatusTag.STATUS.CLOSED)
        accountViewModel.account.signAndComputeBroadcast(template)
        true
    } catch (_: Throwable) {
        false
    }
}

/**
 * Build an `nostr:naddr1...` URI for the room and hand it to the
 * system share sheet. Includes the room title in the share text
 * so the receiving app can render a preview without round-tripping
 * the relay.
 */
private fun shareRoomNaddr(
    context: android.content.Context,
    event: MeetingSpaceEvent,
) {
    val aTag = ATag(event.kind, event.pubKey, event.dTag(), null)
    val naddr = aTag.toNAddr()
    val title = event.room().orEmpty()
    val text = if (title.isNotBlank()) "$title — nostr:$naddr" else "nostr:$naddr"
    val intent =
        android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
    context.startActivity(android.content.Intent.createChooser(intent, null))
}
