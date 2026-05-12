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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
import com.vitorpamplona.amethyst.ui.note.UsernameDisplay
import com.vitorpamplona.amethyst.ui.note.ZapCustomDialog
import com.vitorpamplona.amethyst.ui.note.payViaIntent
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nests.admin.AdminCommandEvent
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.tags.ROLE

/**
 * Per-participant context sheet (T2 #2). Always shows the
 * audience-friendly rows (View profile / Follow-Unfollow / Mute);
 * appends the host management rows (Promote / Demote / Kick) when
 * the local user is the room's host.
 *
 *   * Promote to speaker / moderator — hidden when the target already
 *     has that role (no wasted relay roundtrip).
 *   * Demote to listener — hidden when the target has no participant
 *     row or is already PARTICIPANT (see [RoomParticipantActions.demoteToListener]).
 *   * Force-mute — gated to currently-broadcasting speakers, and
 *     surfaced with a "may be ignored by other clients" note since
 *     not every client honors the verb (see [AdminCommandEvent.Action.MUTE]).
 *   * Kick — sends an AdminCommandEvent.kick plus a kind-30312
 *     republish that drops the target's p-tag. Confirms first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantHostActionsSheet(
    target: String,
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    nestViewModel: com.vitorpamplona.amethyst.commons.viewmodels.NestViewModel? = null,
    isLocalUserHost: Boolean = accountViewModel.account.signer.pubKey == event.pubKey,
    catalog: com.vitorpamplona.amethyst.commons.viewmodels.RoomSpeakerCatalog? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val roomATag =
        ATag(
            kind = event.kind,
            pubKeyHex = event.pubKey,
            dTag = event.dTag(),
            relay = null,
        )

    // Go through accountViewModel.launchSigner so the broadcast runs on
    // viewModelScope (survives onDismiss() removing the sheet from
    // composition) and signer errors surface as toasts instead of being
    // silently swallowed.
    fun broadcast(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<out com.vitorpamplona.quartz.nip01Core.core.Event>?) {
        template ?: return
        accountViewModel.launchSigner { accountViewModel.account.signAndComputeBroadcast(template) }
    }

    val targetUser =
        remember(target) {
            com.vitorpamplona.amethyst.model.LocalCache
                .getOrCreateUser(target)
        }
    // Live state — read via collectAsState so the Follow/Mute labels
    // update if the kind-3 / mute-list publish lands while the sheet
    // is still on screen. Snapshotting `.flow.value` (the previous
    // pattern) left stale labels: opening the sheet after a follow
    // request still showed "Follow".
    val followState by accountViewModel.account.kind3FollowList.flow
        .collectAsState()
    val hiddenState by accountViewModel.account.hiddenUsers.flow
        .collectAsState()
    val isFollowing = target in followState.authors
    val isHidden = target in hiddenState.hiddenUsers

    // Resolve the target's current role from the kind-30312
    // participant list. Drives both the visibility of Promote /
    // Demote rows (hidden when the target is already at that role)
    // and the Force-mute gate (only meaningful for someone with the
    // floor). null = pure-audience listener with no participant tag.
    val targetRole =
        remember(event, target) {
            event.participants().firstOrNull { it.pubKey == target }?.effectiveRole()
        }
    val targetCanSpeakByRole =
        targetRole == ROLE.HOST || targetRole == ROLE.MODERATOR || targetRole == ROLE.SPEAKER

    val nestUi = nestViewModel?.uiState?.collectAsState()?.value
    val nestPresences = nestViewModel?.presences?.collectAsState()?.value
    val targetIsBroadcasting = nestPresences?.get(target)?.publishing == true

    // Pre-resolve display name + toast templates so non-composable
    // callbacks (the row onClicks) can format strings without
    // calling stringRes() from outside a @Composable scope. The
    // sheet is short-lived, so we snapshot once at composition; if
    // metadata arrives mid-sheet, the toast falls back to the
    // truncated npub — same as the in-feed UsernameDisplay would
    // show before observe-flow emission.
    val displayName = remember(targetUser) { targetUser.toBestDisplayName() }
    val toastTitleResId = R.string.nest_toast_action_title
    val followSentMsg = stringRes(R.string.nest_toast_follow_sent, displayName)
    val unfollowSentMsg = stringRes(R.string.nest_toast_unfollow_sent, displayName)
    val muteSentMsg = stringRes(R.string.nest_toast_mute_sent, displayName)
    val unmuteSentMsg = stringRes(R.string.nest_toast_unmute_sent, displayName)
    val promoteSpeakerSentMsg = stringRes(R.string.nest_toast_promote_speaker_sent, displayName)
    val promoteModeratorSentMsg = stringRes(R.string.nest_toast_promote_moderator_sent, displayName)
    val demoteListenerSentMsg = stringRes(R.string.nest_toast_demote_listener_sent, displayName)
    val forceMuteSentMsg = stringRes(R.string.nest_toast_force_mute_sent, displayName)
    val kickSentMsg = stringRes(R.string.nest_toast_kick_sent, displayName)
    val toastTitle = stringRes(toastTitleResId)
    val noAppMessage = stringRes(R.string.nest_no_app_to_open_link)
    val splitUnsupportedMsg = stringRes(R.string.nest_participant_zap_split_unsupported)

    fun toastAction(message: String) {
        accountViewModel.toastManager.toast(toastTitle, message)
    }

    var showZapDialog by rememberSaveable { mutableStateOf(false) }
    var showKickConfirm by rememberSaveable { mutableStateOf(false) }
    var showForceMuteConfirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header: best-effort display name + short npub fallback
            // (UsernameDisplay handles "no metadata yet" by rendering
            // the truncated npub itself). The previous hex stub
            // `target.take(8) + "…"` was unreadable for non-technical
            // users — they had no idea who they were about to mute.
            UsernameDisplay(
                baseUser = targetUser,
                accountViewModel = accountViewModel,
            )
            // moq-lite catalog summary, when the speaker has published
            // one. Tells the audience what codec / sample rate / channel
            // count this broadcast carries — visible cue that the seat
            // is actually a live audio source vs a silent stage slot.
            catalog?.primaryAudio()?.describe()?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            // View Profile — NestActivity is a separate Android
            // Activity without its own nav stack, so the deep-link
            // path goes through MainActivity. Launching `nostr:npub1...`
            // via ACTION_VIEW lets the existing MainActivity URI
            // handler route to Route.Profile (matches the path that
            // an external app or a clicked link inside the feed
            // would take). NEW_TASK + REORDER_TO_FRONT brings the
            // already-running MainActivity instance forward; the
            // audio-room foreground service keeps audio alive while
            // the user is on the profile screen.
            ActionRow(stringRes(R.string.nest_participant_view_profile)) {
                val npub = NPub.create(target)
                val launched =
                    runCatching {
                        context.startActivity(
                            android.content
                                .Intent(android.content.Intent.ACTION_VIEW)
                                .apply {
                                    data = android.net.Uri.parse("nostr:$npub")
                                    setClass(context, MainActivity::class.java)
                                    addFlags(
                                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                                            android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                                    )
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
                onDismiss()
            }
            // Zap — opens the standard ZapCustomDialog targeting the
            // user's kind-0 metadata note (the canonical "zap a user"
            // base note throughout amethyst). Single-payable invoices
            // hand off to a wallet via payViaIntent; split-zap targets
            // (uncommon for personal LN addresses) are routed back to
            // the profile screen rather than wired into a non-nav-host
            // ModalBottomSheet.
            ActionRow(stringRes(R.string.nest_participant_zap)) {
                showZapDialog = true
            }
            if (showZapDialog) {
                val targetMetadataNote =
                    remember(target) {
                        com.vitorpamplona.amethyst.model.LocalCache
                            .getOrCreateAddressableNote(MetadataEvent.createAddress(target))
                    }
                ZapCustomDialog(
                    onZapStarts = {},
                    onClose = {
                        showZapDialog = false
                        onDismiss()
                    },
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
                            accountViewModel.toastManager.toast(
                                R.string.error_dialog_zap_error,
                                UserBasedErrorMessage(splitUnsupportedMsg, null),
                            )
                        }
                    },
                    accountViewModel = accountViewModel,
                    baseNote = targetMetadataNote,
                )
            }
            ActionRow(
                text =
                    stringRes(
                        if (isFollowing) {
                            R.string.nest_participant_unfollow
                        } else {
                            R.string.nest_participant_follow
                        },
                    ),
            ) {
                if (isFollowing) {
                    accountViewModel.unfollow(targetUser)
                    toastAction(unfollowSentMsg)
                } else {
                    accountViewModel.follow(targetUser)
                    toastAction(followSentMsg)
                }
                onDismiss()
            }
            ActionRow(
                text =
                    stringRes(
                        if (isHidden) {
                            R.string.nest_participant_unmute
                        } else {
                            R.string.nest_participant_mute
                        },
                    ),
            ) {
                if (isHidden) {
                    accountViewModel.show(targetUser)
                    toastAction(unmuteSentMsg)
                } else {
                    accountViewModel.hide(targetUser)
                    toastAction(muteSentMsg)
                }
                onDismiss()
            }

            // Local hush — silences only this speaker in our own
            // playback. Available to anyone (not host-only) since it
            // affects nothing on the wire. Skip self because we don't
            // subscribe to our own broadcast loopback. Hidden when
            // the target isn't currently broadcasting: the volume gate
            // attaches to an active subscription, so a hush against a
            // pure listener is a silent no-op — the row was misleading.
            if (
                nestViewModel != null &&
                target != accountViewModel.account.signer.pubKey &&
                targetIsBroadcasting
            ) {
                val isHushedLocally = target in (nestUi?.locallyHushed ?: emptySet())
                ActionRow(
                    text =
                        stringRes(
                            if (isHushedLocally) {
                                R.string.nest_hush_local_restore
                            } else {
                                R.string.nest_hush_local
                            },
                        ),
                ) {
                    nestViewModel.setLocalHushed(target, !isHushedLocally)
                    onDismiss()
                }
            }

            // Host-only rows.
            if (isLocalUserHost && target != event.pubKey) {
                Spacer(Modifier.height(4.dp))
                // Hide Promote/Demote rows when the target is already
                // at that role — re-publishing kind-30312 with the same
                // role string is wasted relay traffic and an obvious UX
                // tell that the host doesn't know who they're acting on.
                if (targetRole != ROLE.SPEAKER) {
                    ActionRow(stringRes(R.string.nest_promote_speaker)) {
                        broadcast(RoomParticipantActions.setRole(event, target, ROLE.SPEAKER))
                        toastAction(promoteSpeakerSentMsg)
                        onDismiss()
                    }
                }
                if (targetRole != ROLE.MODERATOR) {
                    ActionRow(stringRes(R.string.nest_promote_moderator)) {
                        broadcast(RoomParticipantActions.setRole(event, target, ROLE.MODERATOR))
                        toastAction(promoteModeratorSentMsg)
                        onDismiss()
                    }
                }
                // Demote only when the target actually has a role
                // above listener — otherwise the call is a no-op (see
                // RoomParticipantActions.demoteToListener) and the
                // visible row sets up a misleading expectation.
                if (targetRole != null && targetRole != ROLE.PARTICIPANT) {
                    ActionRow(stringRes(R.string.nest_demote_listener)) {
                        broadcast(RoomParticipantActions.demoteToListener(event, target))
                        toastAction(demoteListenerSentMsg)
                        onDismiss()
                    }
                }
                // Force-mute is honor-based: the target's client must
                // honor the verb. Hidden unless the target is a current
                // speaker (by role) AND broadcasting right now — the
                // command has nothing to mute otherwise. A subtitle
                // surfaces the "may be ignored" caveat so the host
                // doesn't expect a hard mute against non-Amethyst
                // clients.
                if (targetCanSpeakByRole && targetIsBroadcasting) {
                    ActionRow(
                        text = stringRes(R.string.nest_force_mute),
                        color = MaterialTheme.colorScheme.error,
                    ) {
                        showForceMuteConfirm = true
                    }
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
                ) {
                    showKickConfirm = true
                }
            }
        }
    }

    // Confirm dialogs render outside the bottom sheet so they overlay
    // on top of it. Both fire-and-forget the broadcast then dismiss
    // the whole sheet — the host gets a toast as positive confirmation
    // since the actions are fire-and-forget through launchSigner and
    // have no synchronous success signal.
    if (showKickConfirm) {
        AlertDialog(
            onDismissRequest = { showKickConfirm = false },
            title = { Text(stringRes(R.string.nest_confirm_kick_title)) },
            text = { Text(stringRes(R.string.nest_confirm_kick_body, displayName)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showKickConfirm = false
                        // Two-step kick mirroring nostrnests' ProfileCard.tsx:
                        //   1. ephemeral kind-4312 ["action","kick"] kicks the
                        //      user off the audio plane
                        //   2. re-published kind-30312 with the target's p-tag
                        //      dropped removes them from the participant grid
                        //      so future presence events don't re-render them
                        broadcast(AdminCommandEvent.kick(roomATag, target))
                        broadcast(RoomParticipantActions.removeParticipant(event, target))
                        toastAction(kickSentMsg)
                        onDismiss()
                    },
                ) {
                    Text(stringRes(R.string.nest_confirm_kick_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showKickConfirm = false }) {
                    Text(stringRes(R.string.nest_confirm_cancel))
                }
            },
        )
    }

    if (showForceMuteConfirm) {
        AlertDialog(
            onDismissRequest = { showForceMuteConfirm = false },
            title = { Text(stringRes(R.string.nest_confirm_force_mute_title)) },
            text = { Text(stringRes(R.string.nest_confirm_force_mute_body, displayName)) },
            confirmButton = {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showForceMuteConfirm = false
                        broadcast(AdminCommandEvent.forceMute(roomATag, target))
                        toastAction(forceMuteSentMsg)
                        onDismiss()
                    },
                ) {
                    Text(stringRes(R.string.nest_confirm_force_mute_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceMuteConfirm = false }) {
                    Text(stringRes(R.string.nest_confirm_cancel))
                }
            },
        )
    }
}

@Composable
private fun ActionRow(
    text: String,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
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
