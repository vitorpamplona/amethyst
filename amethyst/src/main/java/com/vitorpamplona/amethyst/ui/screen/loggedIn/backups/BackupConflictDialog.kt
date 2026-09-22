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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.backups

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent

private const val MAX_LISTED_ITEMS = 8

/**
 * Asks the user what to do when another client replaced one of the account's backed-up
 * replaceable events with a version that lost data: keep the new one, or re-sign the
 * version saved on this device over it. One conflict at a time; "decide later" only hides the
 * question for this session: the conflict stays open, so the backup stays frozen on the saved
 * version and the question comes back on the next launch.
 */
@Composable
fun BackupConflictDialog(accountViewModel: AccountViewModel) {
    val conflicts by accountViewModel.account.settings.backupConflicts
        .collectAsStateWithLifecycle()

    val snoozed = remember { mutableStateSetOf<HexKey>() }

    val conflict = conflicts.values.firstOrNull { it.incoming.id !in snoozed } ?: return

    BackupConflictDialog(
        conflict = conflict,
        onRestore = { accountViewModel.launchSigner { accountViewModel.account.restoreBackupOver(conflict) } },
        onKeepNew = { accountViewModel.account.acceptExternalVersion(conflict) },
        onDismiss = { snoozed.add(conflict.incoming.id) },
    )
}

@Composable
private fun BackupConflictDialog(
    conflict: ReplaceableBackupConflict,
    onRestore: () -> Unit,
    onKeepNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val what = backupLabel(conflict.saved)
    val missing = remember(conflict) { describeLoss(conflict) }
    val clearedLabel = stringRes(R.string.backup_conflict_private_items_cleared)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.backup_conflict_title, what)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringRes(
                        R.string.backup_conflict_explainer,
                        what,
                        timeAbsolute(conflict.incoming.createdAt, context),
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringRes(R.string.backup_conflict_missing_items), style = MaterialTheme.typography.titleSmall)
                if (conflict.loss.contentCleared) {
                    Text("• $clearedLabel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                missing.take(MAX_LISTED_ITEMS).forEach {
                    Text("• $it", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (missing.size > MAX_LISTED_ITEMS) {
                    Text(stringRes(R.string.backup_conflict_more_items, (missing.size - MAX_LISTED_ITEMS).toString()))
                }
            }
        },
        confirmButton = {
            Button(onClick = onRestore) { Text(stringRes(R.string.backup_conflict_restore_mine)) }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onKeepNew) { Text(stringRes(R.string.backup_conflict_keep_new)) }
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.backup_conflict_decide_later)) }
            }
        },
    )
}

private fun describeLoss(conflict: ReplaceableBackupConflict): List<String> =
    conflict.loss.removedFields +
        conflict.loss.removedTags.map { tag ->
            val value = tag.getOrNull(1) ?: ""
            if (tag[0] == "p") {
                LocalCache.getUserIfExists(value)?.toBestDisplayName() ?: value
            } else {
                "${tag[0]}: $value"
            }
        }

@Composable
private fun backupLabel(event: Event): String =
    when (event) {
        is MetadataEvent -> stringRes(R.string.backup_kind_profile)
        is ContactListEvent -> stringRes(R.string.backup_kind_follow_list)
        is MuteListEvent -> stringRes(R.string.backup_kind_mute_list)
        is AdvertisedRelayListEvent,
        is ChatMessageRelayListEvent,
        is KeyPackageRelayListEvent,
        is SearchRelayListEvent,
        is IndexerRelayListEvent,
        is RelayFeedsListEvent,
        is BlockedRelayListEvent,
        is TrustedRelayListEvent,
        is PrivateOutboxRelayListEvent,
        -> stringRes(R.string.backup_kind_relay_settings)
        is AppSpecificDataEvent -> stringRes(R.string.backup_kind_app_settings)
        is CashuWalletEvent,
        is NutzapInfoEvent,
        is PaymentTargetsEvent,
        is Bolt12OfferListEvent,
        -> stringRes(R.string.backup_kind_wallet)
        else -> stringRes(R.string.backup_kind_generic_list, event.kind.toString())
    }
