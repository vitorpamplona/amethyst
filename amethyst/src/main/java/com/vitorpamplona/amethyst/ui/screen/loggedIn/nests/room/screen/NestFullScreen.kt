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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.viewmodels.NestUiState
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.buildParticipantGrid
import com.vitorpamplona.amethyst.ui.navigation.topbars.ShorterTopAppBar
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.chat.NestChatPanel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.edit.EditNestSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.edit.EditNestViewModel
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.participants.ParticipantHostActionsSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.reactions.RoomReactionPickerSheet
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage.AudienceGrid
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage.HandRaiseQueueSection
import com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.stage.StageGrid
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.toNAddr
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.tags.StatusTag
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ParticipantTag
import kotlinx.coroutines.launch

/**
 * Full-screen layout for [com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.activity.NestActivity]. Vertically split into:
 *
 *   1. TopAppBar — room title + overflow menu (Share, host's Edit).
 *   2. Header strip — LIVE chip, listener count, optional 1-line summary.
 *   3. Stage — vertical adaptive grid of host/speakers (height-bounded
 *      so a 30-speaker room scrolls inside the strip and never pushes
 *      the chat below the fold).
 *   4. Tabs — `Chat | Audience · N | Hands · N` (Hands host-only,
 *      shown only while there's at least one raised hand).
 *   5. Tab content — fills the remaining vertical space:
 *        - Chat: the kind-1311 transcript + composer
 *        - Audience: lazy vertical grid (handles 1,000+ listeners)
 *        - Hands: host's promote-to-speaker queue
 *   6. Sticky action bar — connection / talk / hand / react / leave
 *      controls; never scrolls.
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
    var showReactionPicker by rememberSaveable { mutableStateOf(false) }
    var hostMenuTarget by rememberSaveable { mutableStateOf<String?>(null) }
    // Tab selection survives configuration changes and PIP transitions.
    // Stored as ordinal so rememberSaveable can persist it without a
    // custom Saver.
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }

    val isHost = accountViewModel.account.signer.pubKey == event.pubKey
    val myPubkey = accountViewModel.account.signer.pubKey
    val isOnStageMe = remember(onStage, myPubkey) { onStage.any { it.pubKey == myPubkey } }
    val leaveScope = rememberCoroutineScope()
    val topBarContext = LocalContext.current

    val presences by viewModel.presences.collectAsState()
    val reactionsByPubkey by viewModel.recentReactions.collectAsState()
    val speakerCatalogs by viewModel.speakerCatalogs.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()

    val onStageKeys = remember(onStage) { onStage.map { it.pubKey }.toSet() }
    val participantGrid =
        remember(event, presences) {
            buildParticipantGrid(
                participants = event.participants(),
                presences = presences,
            )
        }
    // Same logic HandRaiseQueueSection uses internally — duplicated
    // here so the tab label can show a count without coupling the
    // section to the screen.
    val handsCount =
        remember(presences, onStageKeys) {
            presences.values.count { it.handRaised && it.pubkey !in onStageKeys }
        }
    val showHandsTab = isHost && handsCount > 0

    // Tab roster changes when the Hands tab appears/disappears.
    // If the user was on Hands and the queue empties, fall back to
    // Chat — kept as a stable default rather than Audience because
    // chat is the room's primary engagement surface.
    val tabs =
        remember(showHandsTab) {
            buildList {
                add(NestTab.Chat)
                add(NestTab.Audience)
                if (showHandsTab) add(NestTab.Hands)
            }
        }
    val effectiveTab = tabs.getOrNull(selectedTabIndex) ?: NestTab.Chat

    // Long-press on any participant opens the host-actions sheet
    // (T2 #2). The sheet itself gates which rows render based on
    // host status. Skip self.
    val onLongPressParticipant: ((String) -> Unit) = { target ->
        if (target != myPubkey) hostMenuTarget = target
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
        bottomBar = {
            NestActionBar(
                viewModel = viewModel,
                ui = ui,
                isOnStage = isOnStageMe,
                canBroadcast = viewModel.canBroadcast,
                speakerPubkeyHex = myPubkey,
                handRaised = handRaised,
                onHandRaisedChange = onHandRaisedChange,
                onShowReactionPicker = { showReactionPicker = true },
                onLeave = {
                    if (isHost) {
                        showHostLeaveConfirm = true
                    } else {
                        onLeave()
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            RoomHeaderStrip(
                summary = event.summary(),
                listenerCount = presences.size,
            )
            StageGrid(
                members = participantGrid.onStage,
                speakingNow = ui.speakingNow,
                audioLevels = audioLevels,
                accountViewModel = accountViewModel,
                reactionsByPubkey = reactionsByPubkey,
                connectingSpeakers = ui.connectingSpeakers,
                onLongPressParticipant = onLongPressParticipant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            NestTabRow(
                tabs = tabs,
                selectedTab = effectiveTab,
                audienceCount = participantGrid.audience.size,
                handsCount = handsCount,
                onSelect = { tab -> selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0) },
                modifier = Modifier.padding(top = 8.dp),
            )
            when (effectiveTab) {
                NestTab.Chat -> {
                    NestChatPanel(
                        event = event,
                        viewModel = viewModel,
                        accountViewModel = accountViewModel,
                        modifier =
                            Modifier
                                .weight(1f),
                    )
                }

                NestTab.Audience -> {
                    AudienceGrid(
                        members = participantGrid.audience,
                        accountViewModel = accountViewModel,
                        onLongPressParticipant = onLongPressParticipant,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                    )
                }

                NestTab.Hands -> {
                    HandRaiseQueueSection(
                        event = event,
                        viewModel = viewModel,
                        accountViewModel = accountViewModel,
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }

    // Sheets and dialogs render alongside the Scaffold so they
    // cover the room content (including the action bar) when
    // open. ParticipantHostActionsSheet stays outside the Column
    // for the same reason — modal bottom sheets are not affected
    // by parent layout.
    hostMenuTarget?.let { target ->
        ParticipantHostActionsSheet(
            target = target,
            event = event,
            accountViewModel = accountViewModel,
            nestViewModel = viewModel,
            onDismiss = { hostMenuTarget = null },
            catalog = speakerCatalogs[target],
        )
    }

    if (showReactionPicker) {
        RoomReactionPickerSheet(
            onPick = { emoji -> accountViewModel.reactToOrDelete(roomNote, emoji) },
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

    if (showEditSheet) {
        EditNestSheet(
            accountViewModel = accountViewModel,
            event = event,
            onDismiss = { showEditSheet = false },
        )
    }
}

/**
 * Header strip rendered between the TopAppBar and the stage grid.
 * Carries the LIVE chip + listener count and (when present) a single-
 * line ellipsised summary. Lives at the screen level rather than in
 * the TopAppBar so the chip + count have room to breathe and the
 * summary's typography matches the body, not the title.
 */
@Composable
private fun RoomHeaderStrip(
    summary: String?,
    listenerCount: Int,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveChip()
            Text(
                text = pluralStringResource(R.plurals.nest_listener_count, listenerCount, listenerCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!summary.isNullOrBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LiveChip() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringRes(R.string.nest_live_chip),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Tab-bar between the stage and the tab content. Audience / Hands
 * tabs carry a count badge so the user knows whether switching tabs
 * is worthwhile (e.g. "Hands · 3" tells the host they have a queue
 * to attend to without ever leaving the chat).
 */
@Composable
private fun NestTabRow(
    tabs: List<NestTab>,
    selectedTab: NestTab,
    audienceCount: Int,
    handsCount: Int,
    onSelect: (NestTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    PrimaryTabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
    ) {
        tabs.forEach { tab ->
            val (label, count) =
                when (tab) {
                    NestTab.Chat -> stringRes(R.string.nest_tab_chat) to 0
                    NestTab.Audience -> stringRes(R.string.nest_tab_audience) to audienceCount
                    NestTab.Hands -> stringRes(R.string.nest_tab_hands) to handsCount
                }
            Tab(
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                text = {
                    if (count > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text(text = count.toString()) }
                            },
                        ) {
                            Text(text = label)
                        }
                    } else {
                        Text(text = label)
                    }
                },
            )
        }
    }
}

private enum class NestTab {
    Chat,
    Audience,
    Hands,
}

/**
 * TopAppBar for the room screen — hosts the room name and the
 * overflow menu (Share for everyone, Edit for the host). The menu
 * state lives at the screen level so the EditNestSheet can be
 * triggered from here and rendered alongside the Scaffold.
 *
 * Container color is transparent so the surface beneath the
 * Scaffold (set by the activity / parent theme) shows through.
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
