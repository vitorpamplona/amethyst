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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.ui.MainActivity
import com.vitorpamplona.amethyst.ui.components.toasts.multiline.UserBasedErrorMessage
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
import kotlinx.coroutines.launch

/**
 * Per-participant context sheet (T2 #2). Always shows the
 * audience-friendly rows (View profile / Follow-Unfollow / Mute);
 * appends the host management rows (Promote / Demote / Kick) when
 * the local user is the room's host.
 *
 *   * Promote to speaker — idempotent re-publish.
 *   * Demote to listener — no-op for the host themselves
 *     (RoomParticipantActions.demoteToListener returns null in
 *     that case).
 *   * Kick — sends an AdminCommandEvent.kick. Relay just forwards;
 *     recipients gate on signer being host/moderator.
 *
 * The kick path doesn't ALSO demote the target — that's
 * nostrnests' behaviour: the kicked user's session ends and any
 * future presence events from them get dropped by the recipient's
 * client-side filter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ParticipantHostActionsSheet(
    target: String,
    event: MeetingSpaceEvent,
    accountViewModel: AccountViewModel,
    onDismiss: () -> Unit,
    isLocalUserHost: Boolean = accountViewModel.account.signer.pubKey == event.pubKey,
    catalog: com.vitorpamplona.amethyst.commons.viewmodels.RoomSpeakerCatalog? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val roomATag =
        ATag(
            kind = event.kind,
            pubKeyHex = event.pubKey,
            dTag = event.dTag(),
            relay = null,
        )

    fun broadcast(template: com.vitorpamplona.quartz.nip01Core.signers.EventTemplate<out com.vitorpamplona.quartz.nip01Core.core.Event>?) {
        template ?: return
        scope.launch { runCatching { accountViewModel.account.signAndComputeBroadcast(template) } }
    }

    val targetUser =
        remember(target) {
            com.vitorpamplona.amethyst.model.LocalCache
                .getOrCreateUser(target)
        }
    val isFollowing = accountViewModel.isFollowing(target)
    val isHidden =
        remember(target) {
            accountViewModel.account.hiddenUsers.flow.value.hiddenUsers
                .contains(target)
        }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = target.take(8) + "…",
                style = MaterialTheme.typography.titleSmall,
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
            val context = LocalContext.current
            val noAppMessage = stringRes(R.string.nest_no_app_to_open_link)
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
            var showZapDialog by rememberSaveable { mutableStateOf(false) }
            ActionRow(stringRes(R.string.nest_participant_zap)) {
                showZapDialog = true
            }
            if (showZapDialog) {
                val targetMetadataNote =
                    remember(target) {
                        com.vitorpamplona.amethyst.model.LocalCache
                            .getOrCreateAddressableNote(MetadataEvent.createAddress(target))
                    }
                // Pre-resolved at composition because callbacks below run
                // outside a @Composable scope (stringRes is composable-only).
                val splitUnsupportedMsg = stringRes(R.string.nest_participant_zap_split_unsupported)
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
                if (isFollowing) accountViewModel.unfollow(targetUser) else accountViewModel.follow(targetUser)
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
                if (isHidden) accountViewModel.show(targetUser) else accountViewModel.hide(targetUser)
                onDismiss()
            }

            // Host-only rows.
            if (isLocalUserHost && target != event.pubKey) {
                Spacer(Modifier.height(4.dp))
                ActionRow(stringRes(R.string.nest_promote_speaker)) {
                    broadcast(RoomParticipantActions.setRole(event, target, ROLE.SPEAKER))
                    onDismiss()
                }
                ActionRow(stringRes(R.string.nest_demote_listener)) {
                    broadcast(RoomParticipantActions.demoteToListener(event, target))
                    onDismiss()
                }
                ActionRow(
                    text = stringRes(R.string.nest_kick_action),
                    color = MaterialTheme.colorScheme.error,
                ) {
                    broadcast(AdminCommandEvent.kick(roomATag, target))
                    onDismiss()
                }
            }
        }
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
