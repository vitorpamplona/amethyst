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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.nests.room.participants

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel
import com.vitorpamplona.amethyst.commons.viewmodels.RoomSpeakerCatalog
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.user.observeUserInfo
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapCustomDialog
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE

/**
 * Per-participant context sheet (T2 #2). Long-press on any participant
 * avatar — or single-tap on an audience / hand-raise avatar — opens
 * this sheet.
 *
 * Top-level layout: a [ParticipantSheetHeader] followed by
 * [AudienceActions] (View profile / Zap / Follow / Mute / Local hush)
 * and, when the local user is the host, [HostActions]
 * (Promote / Demote / Force-mute / Kick).
 *
 * Destructive host actions (Kick, Force-mute) and the Zap flow are
 * confirmed via a top-level [SheetDialog] state so the sub-composables
 * never own dialog window state themselves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantHostActionsSheet(
    target: String,
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    nestViewModel: NestViewModel,
    onDismiss: () -> Unit,
    isLocalUserHost: Boolean = accountViewModel.account.signer.pubKey == event.pubKey,
    catalog: RoomSpeakerCatalog? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val targetUser = remember(target) { LocalCache.getOrCreateUser(target) }

    // Reactive display name — UsernameDisplay observes this for the
    // header, and we reuse the same flow for toast text so the header
    // and toast never disagree (e.g. metadata arriving mid-sheet).
    val userInfo by observeUserInfo(targetUser, accountViewModel)
    val displayName = userInfo?.info?.bestName() ?: targetUser.pubkeyDisplayHex()
    val toasts = rememberParticipantToasts(displayName)

    var openDialog by rememberSaveable(stateSaver = SheetDialogSaver) {
        mutableStateOf<SheetDialog?>(null)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ParticipantSheetHeader(targetUser, catalog, accountViewModel)
            Spacer(Modifier.height(8.dp))
            AudienceActions(
                target = target,
                targetUser = targetUser,
                accountViewModel = accountViewModel,
                nestViewModel = nestViewModel,
                toasts = toasts,
                onZapRequested = { openDialog = SheetDialog.Zap },
                onDismiss = onDismiss,
            )
            if (isLocalUserHost && target != event.pubKey) {
                HostActions(
                    target = target,
                    event = event,
                    accountViewModel = accountViewModel,
                    nestViewModel = nestViewModel,
                    toasts = toasts,
                    onForceMuteRequested = { openDialog = SheetDialog.ForceMute },
                    onKickRequested = { openDialog = SheetDialog.Kick },
                    onDismiss = onDismiss,
                )
            }
        }
    }

    // Top-level dialog dispatch so the sub-composables never own
    // window state and the destructive actions live next to the
    // confirm dialog they belong to.
    when (openDialog) {
        SheetDialog.Zap -> {
            ParticipantZapDialog(
                target = target,
                accountViewModel = accountViewModel,
                zapSplitUnsupported = toasts.zapSplitUnsupported,
                onClose = {
                    openDialog = null
                    onDismiss()
                },
            )
        }

        SheetDialog.Kick -> {
            DestructiveConfirmDialog(
                title = stringRes(R.string.nest_confirm_kick_title),
                body = stringRes(R.string.nest_confirm_kick_body, displayName),
                confirmLabel = stringRes(R.string.nest_confirm_kick_confirm),
                onConfirm = {
                    openDialog = null
                    val roomATag = ATag(event.kind, event.pubKey, event.dTag(), null)
                    // Two-step kick mirroring nostrnests' ProfileCard.tsx:
                    //   1. ephemeral kind-4312 ["action","kick"] kicks the
                    //      user off the audio plane
                    //   2. re-published kind-30312 with the target's p-tag
                    //      dropped removes them from the participant grid
                    //      so future presence events don't re-render them
                    broadcast(accountViewModel, AdminCommandEvent.kick(roomATag, target))
                    broadcast(accountViewModel, RoomParticipantActions.removeParticipant(event, target))
                    accountViewModel.toastManager.toast(toasts.title, toasts.kickSent)
                    onDismiss()
                },
                onDismiss = { openDialog = null },
            )
        }

        SheetDialog.ForceMute -> {
            DestructiveConfirmDialog(
                title = stringRes(R.string.nest_confirm_force_mute_title),
                body = stringRes(R.string.nest_confirm_force_mute_body, displayName),
                confirmLabel = stringRes(R.string.nest_confirm_force_mute_confirm),
                onConfirm = {
                    openDialog = null
                    val roomATag = ATag(event.kind, event.pubKey, event.dTag(), null)
                    broadcast(accountViewModel, AdminCommandEvent.forceMute(roomATag, target))
                    accountViewModel.toastManager.toast(toasts.title, toasts.forceMuteSent)
                    onDismiss()
                },
                onDismiss = { openDialog = null },
            )
        }

        null -> {
            Unit
        }
    }
}

@Composable
private fun ParticipantSheetHeader(
    targetUser: User,
    catalog: RoomSpeakerCatalog?,
    accountViewModel: AccountViewModel,
) {
    UsernameDisplay(baseUser = targetUser, accountViewModel = accountViewModel)
    // moq-lite catalog summary, when the speaker has published one.
    // Tells the audience what codec / sample rate / channel count this
    // broadcast carries — visible cue that the seat is actually a live
    // audio source vs a silent stage slot.
    catalog?.primaryAudio()?.describe()?.let { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AudienceActions(
    target: String,
    targetUser: User,
    accountViewModel: AccountViewModel,
    nestViewModel: NestViewModel,
    toasts: ParticipantToasts,
    onZapRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Live state — read via collectAsState so the Follow / Mute labels
    // update if the kind-3 / mute-list publish lands while the sheet is
    // still on screen. Snapshotting `.flow.value` left stale labels:
    // re-opening the sheet right after a follow still showed "Follow".
    val followState by accountViewModel.account.kind3FollowList.flow
        .collectAsState()
    val hiddenState by accountViewModel.account.hiddenUsers.flow
        .collectAsState()
    val nestUi by nestViewModel.uiState.collectAsState()
    val nestPresences by nestViewModel.presences.collectAsState()

    val isFollowing = target in followState.authors
    val isHidden = target in hiddenState.hiddenUsers
    val isSelf = target == accountViewModel.account.signer.pubKey
    val targetIsBroadcasting = nestPresences[target]?.publishing == true

    // View Profile — NestActivity is a separate Android Activity
    // without its own nav stack, so the deep-link path goes through
    // MainActivity. Launching `nostr:npub1...` via ACTION_VIEW lets the
    // existing MainActivity URI handler route to Route.Profile (matches
    // the path an external app or clicked link inside the feed takes).
    // NEW_TASK + REORDER_TO_FRONT brings the already-running MainActivity
    // instance forward; the audio-room foreground service keeps audio
    // alive while the user is on the profile screen.
    ActionRow(stringRes(R.string.nest_participant_view_profile)) {
        openProfileInMainActivity(context, target, accountViewModel, toasts.noAppMessage)
        onDismiss()
    }

    ActionRow(stringRes(R.string.nest_participant_zap)) { onZapRequested() }

    ActionRow(
        text =
            stringRes(
                if (isFollowing) R.string.nest_participant_unfollow else R.string.nest_participant_follow,
            ),
    ) {
        if (isFollowing) {
            accountViewModel.unfollow(targetUser)
            accountViewModel.toastManager.toast(toasts.title, toasts.unfollowSent)
        } else {
            accountViewModel.follow(targetUser)
            accountViewModel.toastManager.toast(toasts.title, toasts.followSent)
        }
        onDismiss()
    }

    ActionRow(
        text =
            stringRes(
                if (isHidden) R.string.nest_participant_unmute else R.string.nest_participant_mute,
            ),
    ) {
        if (isHidden) {
            accountViewModel.show(targetUser)
            accountViewModel.toastManager.toast(toasts.title, toasts.unmuteSent)
        } else {
            accountViewModel.hide(targetUser)
            accountViewModel.toastManager.toast(toasts.title, toasts.muteSent)
        }
        onDismiss()
    }

    // Local hush — silences only this speaker in our own playback.
    // Available to anyone (not host-only) since it affects nothing on
    // the wire. Skip self because we don't subscribe to our own
    // broadcast loopback. Hidden when the target isn't currently
    // broadcasting: the volume gate attaches to an active subscription,
    // so a hush against a pure listener was a silent no-op.
    if (!isSelf && targetIsBroadcasting) {
        val isHushedLocally = target in nestUi.locallyHushed
        ActionRow(
            text =
                stringRes(
                    if (isHushedLocally) R.string.nest_hush_local_restore else R.string.nest_hush_local,
                ),
        ) {
            nestViewModel.setLocalHushed(target, !isHushedLocally)
            onDismiss()
        }
    }
}

@Composable
private fun HostActions(
    target: String,
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    nestViewModel: NestViewModel,
    toasts: ParticipantToasts,
    onForceMuteRequested: () -> Unit,
    onKickRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Pull the participant tag once; we use its role for Promote /
    // Demote visibility and canSpeak() — defined on ParticipantTag —
    // for the Force-mute gate.
    val targetTag = remember(event, target) { event.participants().firstOrNull { it.pubKey == target } }
    val targetRole = targetTag?.effectiveRole()
    val targetCanSpeak = targetTag?.canSpeak() == true
    val nestPresences by nestViewModel.presences.collectAsState()
    val targetIsBroadcasting = nestPresences[target]?.publishing == true

    fun hostAction(
        template: EventTemplate<out Event>?,
        toastMsg: String,
    ) {
        broadcast(accountViewModel, template)
        accountViewModel.toastManager.toast(toasts.title, toastMsg)
        onDismiss()
    }

    Spacer(Modifier.height(4.dp))

    // Hide Promote / Demote rows when the target already has that
    // role — re-publishing kind-30312 with the same role string is
    // wasted relay traffic and an obvious UX tell.
    if (targetRole != ROLE.SPEAKER) {
        ActionRow(stringRes(R.string.nest_promote_speaker)) {
            hostAction(RoomParticipantActions.setRole(event, target, ROLE.SPEAKER), toasts.promoteSpeakerSent)
        }
    }
    if (targetRole != ROLE.MODERATOR) {
        ActionRow(stringRes(R.string.nest_promote_moderator)) {
            hostAction(RoomParticipantActions.setRole(event, target, ROLE.MODERATOR), toasts.promoteModeratorSent)
        }
    }
    // Demote only when the target actually has a role above listener —
    // otherwise the call is a no-op (RoomParticipantActions.demoteToListener)
    // and the visible row sets up a misleading expectation.
    if (targetRole != null && targetRole != ROLE.PARTICIPANT) {
        ActionRow(stringRes(R.string.nest_demote_listener)) {
            hostAction(RoomParticipantActions.demoteToListener(event, target), toasts.demoteListenerSent)
        }
    }
    // Force-mute is honor-based: the target's client must honor the
    // verb. Hidden unless the target can speak (by role) AND is
    // broadcasting right now — the command has nothing to mute
    // otherwise. The subtitle surfaces the "may be ignored" caveat so
    // the host doesn't expect a hard mute against non-Amethyst clients.
    if (targetCanSpeak && targetIsBroadcasting) {
        ActionRow(
            text = stringRes(R.string.nest_force_mute),
            color = MaterialTheme.colorScheme.error,
        ) { onForceMuteRequested() }
        Text(
            text = stringRes(R.string.nest_force_mute_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, bottom = 4.dp),
        )
    }
    ActionRow(
        text = stringRes(R.string.nest_kick_action),
        color = MaterialTheme.colorScheme.error,
    ) { onKickRequested() }
}

@Composable
private fun ParticipantZapDialog(
    target: String,
    accountViewModel: AccountViewModel,
    zapSplitUnsupported: String,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val targetMetadataNote =
        remember(target) {
            LocalCache.getOrCreateAddressableNote(MetadataEvent.createAddress(target))
        }
    ZapCustomDialog(
        onZapStarts = {},
        onClose = onClose,
        onError = { _, message, user ->
            accountViewModel.toastManager.toast(
                R.string.error_dialog_zap_error,
                UserBasedErrorMessage(message, user),
            )
        },
        onProgress = { /* no progress UI inside the sheet — single-payable handoff is fast */ },
        onPayViaIntent = { payables ->
            if (payables.size == 1) {
                val payable = payables.first()
                payViaIntent(payable.invoice, context, { }) { error ->
                    accountViewModel.toastManager.toast(
                        R.string.error_dialog_zap_error,
                        UserBasedErrorMessage(error, payable.info.user),
                    )
                }
            } else {
                // Split-zap targets are uncommon for personal LN addresses
                // and the ModalBottomSheet doesn't host a nav graph, so
                // we route the user back to the profile screen instead
                // of half-implementing the split flow here.
                accountViewModel.toastManager.toast(
                    R.string.error_dialog_zap_error,
                    UserBasedErrorMessage(zapSplitUnsupported, null),
                )
            }
        },
        accountViewModel = accountViewModel,
        baseNote = targetMetadataNote,
    )
}

@Composable
private fun DestructiveConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = onConfirm,
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.nest_confirm_cancel))
            }
        },
    )
}

@Composable
private fun ActionRow(
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
    )
}

/** Fire-and-forget broadcast; null templates short-circuit. */
private fun broadcast(
    accountViewModel: AccountViewModel,
    template: EventTemplate<out Event>?,
) {
    template ?: return
    // viewModelScope keeps the coroutine alive past onDismiss() removing
    // the sheet from composition; signer errors surface as toasts via
    // launchSigner's catch arms.
    accountViewModel.launchSigner { accountViewModel.account.signAndComputeBroadcast(template) }
}

private fun openProfileInMainActivity(
    context: Context,
    target: String,
    accountViewModel: AccountViewModel,
    noAppMessage: String,
) {
    val npub = NPub.create(target)
    val launched =
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("nostr:$npub")
                    setClass(context, MainActivity::class.java)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                },
            )
        }.isSuccess
    if (!launched) {
        accountViewModel.toastManager.toast(
            R.string.nest_chat_send_failed_title,
            noAppMessage,
            user = null,
        )
    }
}

/**
 * Mutually-exclusive dialog selector for the sheet. Serialised as the
 * enum constant name so `rememberSaveable` survives configuration
 * changes without a custom Parcelable wrapper.
 */
private enum class SheetDialog { Zap, Kick, ForceMute }

private val SheetDialogSaver: Saver<SheetDialog?, String> =
    Saver(
        save = { it?.name ?: "" },
        restore = { name -> name.takeIf { it.isNotEmpty() }?.let(SheetDialog::valueOf) },
    )

/**
 * Pre-resolved strings for the sheet's success toasts and inline error
 * fallbacks. Composable `stringRes(...)` can only be called from
 * @Composable scope, so the formatted-with-displayName variants are
 * computed once at composition and held in this holder; row onClicks
 * (which are not @Composable) read them by reference.
 */
private data class ParticipantToasts(
    val title: String,
    val followSent: String,
    val unfollowSent: String,
    val muteSent: String,
    val unmuteSent: String,
    val promoteSpeakerSent: String,
    val promoteModeratorSent: String,
    val demoteListenerSent: String,
    val forceMuteSent: String,
    val kickSent: String,
    val noAppMessage: String,
    val zapSplitUnsupported: String,
)

@Composable
private fun rememberParticipantToasts(displayName: String): ParticipantToasts =
    ParticipantToasts(
        title = stringRes(R.string.nest_toast_action_title),
        followSent = stringRes(R.string.nest_toast_follow_sent, displayName),
        unfollowSent = stringRes(R.string.nest_toast_unfollow_sent, displayName),
        muteSent = stringRes(R.string.nest_toast_mute_sent, displayName),
        unmuteSent = stringRes(R.string.nest_toast_unmute_sent, displayName),
        promoteSpeakerSent = stringRes(R.string.nest_toast_promote_speaker_sent, displayName),
        promoteModeratorSent = stringRes(R.string.nest_toast_promote_moderator_sent, displayName),
        demoteListenerSent = stringRes(R.string.nest_toast_demote_listener_sent, displayName),
        forceMuteSent = stringRes(R.string.nest_toast_force_mute_sent, displayName),
        kickSent = stringRes(R.string.nest_toast_kick_sent, displayName),
        noAppMessage = stringRes(R.string.nest_no_app_to_open_link),
        zapSplitUnsupported = stringRes(R.string.nest_participant_zap_split_unsupported),
    )
