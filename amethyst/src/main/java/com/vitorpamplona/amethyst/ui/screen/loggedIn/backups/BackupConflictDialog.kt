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
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.DiffChange
import com.vitorpamplona.quartz.nip01Core.diff.DiffEntry
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff

private const val MAX_ITEMS_PER_GROUP = 6
private const val MAX_VALUE_LENGTH = 80

/**
 * Asks the user what to do when another client replaced one of the account's backed-up
 * replaceable events with a version that lost data: keep the new one, or re-sign the
 * version saved on this device over it.
 *
 * The dialog is specific to the event that changed: it names it, says what it is for, and
 * lists what was removed, added and changed, grouped by what each entry is (people, relays,
 * hashtags, profile fields…).
 *
 * One conflict at a time; "decide later" only hides the question for this session: the
 * conflict stays open, so the backup stays frozen on the saved version and the question
 * comes back on the next launch.
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
    val diff = conflict.diff
    val eventType = conflict.eventType

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(eventType)))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringRes(eventTypeExplainer(eventType)),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringRes(R.string.backup_conflict_intro, timeAbsolute(conflict.incoming.createdAt, context)))

                DiffDetails(diff, eventType)

                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringRes(R.string.backup_conflict_restore_explainer),
                    style = MaterialTheme.typography.bodySmall,
                )
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

@Composable
private fun DiffDetails(
    diff: EventDiff,
    eventType: BackupEventType,
) {
    if (diff.removed.isNotEmpty() || diff.content == ContentChange.CLEARED) {
        SectionHeader(
            stringRes(R.string.backup_conflict_removed, diff.removed.size.toString()),
            MaterialTheme.colorScheme.error,
        )
        if (diff.content == ContentChange.CLEARED) {
            ContentNote(if (diff.contentEncrypted) R.string.backup_conflict_private_cleared else R.string.backup_conflict_content_cleared)
        }
        EntryGroups(diff.removed, eventType)
    }

    if (diff.added.isNotEmpty() || diff.content == ContentChange.ADDED) {
        SectionHeader(
            stringRes(R.string.backup_conflict_added, diff.added.size.toString()),
            MaterialTheme.colorScheme.primary,
        )
        if (diff.content == ContentChange.ADDED) {
            ContentNote(if (diff.contentEncrypted) R.string.backup_conflict_private_added else R.string.backup_conflict_content_added)
        }
        EntryGroups(diff.added, eventType)
    }

    if (diff.changed.isNotEmpty() || diff.content == ContentChange.CHANGED) {
        SectionHeader(
            stringRes(R.string.backup_conflict_changed, diff.changed.size.toString()),
            MaterialTheme.colorScheme.tertiary,
        )
        if (diff.content == ContentChange.CHANGED) {
            ContentNote(if (diff.contentEncrypted) R.string.backup_conflict_private_changed else R.string.backup_conflict_content_changed)
        }
        ChangeGroups(diff.changed, eventType)
    }
}

@Composable
private fun ContentNote(textRes: Int) {
    Text("• " + stringRes(textRes), modifier = Modifier.padding(start = 8.dp))
}

@Composable
private fun SectionHeader(
    text: String,
    color: Color,
) {
    Spacer(Modifier.height(12.dp))
    Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
}

@Composable
private fun GroupLabel(labelRes: Int) {
    Text(
        text = stringRes(labelRes),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Item(text: String) {
    Text(
        text = "• $text",
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp),
    )
}

@Composable
private fun MoreItems(count: Int) {
    if (count > 0) {
        Text(
            text = stringRes(R.string.backup_conflict_more_items, count.toString()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun EntryGroups(
    entries: List<DiffEntry>,
    eventType: BackupEventType,
) {
    entries.groupBy { groupLabel(it, eventType) }.forEach { (label, group) ->
        GroupLabel(label)
        group.take(MAX_ITEMS_PER_GROUP).forEach { Item(describe(it)) }
        MoreItems(group.size - MAX_ITEMS_PER_GROUP)
    }
}

@Composable
private fun ChangeGroups(
    changes: List<DiffChange>,
    eventType: BackupEventType,
) {
    changes.groupBy { groupLabel(it.before, eventType) }.forEach { (label, group) ->
        GroupLabel(label)
        group.take(MAX_ITEMS_PER_GROUP).forEach { Item(describe(it)) }
        MoreItems(group.size - MAX_ITEMS_PER_GROUP)
    }
}

/** One line per changed entry: what it is, then its details before → after. */
@Composable
private fun describe(change: DiffChange): String {
    val before = change.before
    val after = change.after
    return when {
        before is DiffEntry.ProfileField && after is DiffEntry.ProfileField ->
            profileFieldName(before.name) + ": " + clip(before.value) + " → " + clip(after.value)
        before is DiffEntry.Relay && after is DiffEntry.Relay ->
            before.url + ": " + relayMarker(before) + " → " + relayMarker(after)
        before is DiffEntry.Person && after is DiffEntry.Person ->
            personName(before.pubKey) + ": " + listOfNotNull(before.petName, before.relayHint).joinToString() +
                " → " + listOfNotNull(after.petName, after.relayHint).joinToString()
        before is DiffEntry.Mint && after is DiffEntry.Mint ->
            before.url + ": " + before.units.joinToString() + " → " + after.units.joinToString()
        before is DiffEntry.RelayGroup && after is DiffEntry.RelayGroup ->
            (before.name ?: before.groupId) + " → " + (after.name ?: after.groupId)
        else -> describe(before) + " → " + describe(after)
    }
}

/** One line per entry, resolving people and chats to their names when this device knows them. */
@Composable
private fun describe(entry: DiffEntry): String =
    when (entry) {
        is DiffEntry.ProfileField -> profileFieldName(entry.name) + ": " + clip(entry.value)
        is DiffEntry.Person -> personName(entry.pubKey) + (entry.petName?.let { " ($it)" } ?: "")
        is DiffEntry.Hashtag -> "#" + entry.hashtag
        is DiffEntry.Word -> "\"" + entry.word + "\""
        is DiffEntry.Geohash -> entry.geohash
        is DiffEntry.Relay -> entry.url + " (" + relayMarker(entry) + ")"
        is DiffEntry.EventRef ->
            LocalCache.getPublicChatChannelIfExists(entry.eventId)?.toBestDisplayName() ?: entry.eventId.toShortDisplay()
        is DiffEntry.AddressRef -> Address.parse(entry.address)?.dTag?.ifBlank { null } ?: entry.address.toShortDisplay()
        is DiffEntry.RelayGroup -> entry.name ?: entry.groupId
        is DiffEntry.ChatRoom -> entry.roomId + " @ " + entry.relay
        is DiffEntry.TrustProvider -> entry.service.substringAfter(':') + ": " + personName(entry.pubKey)
        is DiffEntry.Mint -> entry.url
        is DiffEntry.NutzapKey -> entry.pubKey.toShortDisplay()
        is DiffEntry.PaymentTarget -> entry.type + ": " + clip(entry.authority)
        is DiffEntry.Bolt12Offer -> entry.offer.toShortDisplay()
        is DiffEntry.OtherTag -> entry.name + ": " + clip(entry.value)
    }

/** Which group an entry is listed under. The same tag means different things in different lists. */
private fun groupLabel(
    entry: DiffEntry,
    eventType: BackupEventType,
): Int =
    when (entry) {
        is DiffEntry.ProfileField -> R.string.backup_entry_profile_field
        is DiffEntry.Person -> R.string.backup_entry_person
        is DiffEntry.Hashtag -> R.string.backup_entry_hashtag
        is DiffEntry.Word -> R.string.backup_entry_word
        is DiffEntry.Geohash -> R.string.backup_entry_location
        is DiffEntry.Relay -> R.string.backup_entry_relay
        is DiffEntry.EventRef ->
            when (eventType) {
                BackupEventType.MUTE_LIST -> R.string.backup_entry_thread
                BackupEventType.PUBLIC_CHATS -> R.string.backup_entry_public_chat
                else -> R.string.backup_entry_event
            }
        is DiffEntry.AddressRef ->
            when (eventType) {
                BackupEventType.COMMUNITIES -> R.string.backup_entry_community
                BackupEventType.FAVORITE_ALGO_FEEDS -> R.string.backup_entry_algo_feed
                else -> R.string.backup_entry_address
            }
        is DiffEntry.RelayGroup -> R.string.backup_entry_relay_group
        is DiffEntry.ChatRoom -> R.string.backup_entry_chat_room
        is DiffEntry.TrustProvider -> R.string.backup_entry_trust_provider
        is DiffEntry.Mint -> R.string.backup_entry_mint
        is DiffEntry.NutzapKey -> R.string.backup_entry_nutzap_key
        is DiffEntry.PaymentTarget -> R.string.backup_entry_payment_target
        is DiffEntry.Bolt12Offer -> R.string.backup_entry_offer
        is DiffEntry.OtherTag -> R.string.backup_entry_other
    }

private fun personName(pubkey: String): String = LocalCache.getUserIfExists(pubkey)?.toBestDisplayName() ?: pubkey.toShortDisplay()

private fun clip(text: String?): String {
    if (text == null) return ""
    val oneLine = text.replace('\n', ' ')
    return if (oneLine.length > MAX_VALUE_LENGTH) oneLine.take(MAX_VALUE_LENGTH) + "…" else oneLine
}

@Composable
private fun relayMarker(relay: DiffEntry.Relay): String =
    when {
        relay.read && !relay.write -> stringRes(R.string.backup_conflict_relay_read_only)
        relay.write && !relay.read -> stringRes(R.string.backup_conflict_relay_write_only)
        else -> stringRes(R.string.backup_conflict_relay_read_write)
    }

@Composable
private fun profileFieldName(field: String): String =
    when (field) {
        "name" -> stringRes(R.string.backup_profile_field_name)
        "display_name", "displayName" -> stringRes(R.string.backup_profile_field_display_name)
        "about" -> stringRes(R.string.backup_profile_field_about)
        "picture", "image" -> stringRes(R.string.backup_profile_field_picture)
        "banner" -> stringRes(R.string.backup_profile_field_banner)
        "website" -> stringRes(R.string.backup_profile_field_website)
        "nip05" -> stringRes(R.string.backup_profile_field_nip05)
        "lud16" -> stringRes(R.string.backup_profile_field_lud16)
        "lud06" -> stringRes(R.string.backup_profile_field_lud06)
        "pronouns" -> stringRes(R.string.backup_profile_field_pronouns)
        "birthday" -> stringRes(R.string.backup_profile_field_birthday)
        "bot" -> stringRes(R.string.backup_profile_field_bot)
        else -> field
    }

private fun eventTypeName(type: BackupEventType): Int =
    when (type) {
        BackupEventType.PROFILE -> R.string.backup_type_profile
        BackupEventType.FOLLOW_LIST -> R.string.backup_type_follow_list
        BackupEventType.MUTE_LIST -> R.string.backup_type_mute_list
        BackupEventType.OUTBOX_INBOX_RELAYS -> R.string.backup_type_outbox_inbox_relays
        BackupEventType.DM_RELAYS -> R.string.backup_type_dm_relays
        BackupEventType.KEY_PACKAGE_RELAYS -> R.string.backup_type_key_package_relays
        BackupEventType.SEARCH_RELAYS -> R.string.backup_type_search_relays
        BackupEventType.INDEXER_RELAYS -> R.string.backup_type_indexer_relays
        BackupEventType.RELAY_FEEDS -> R.string.backup_type_relay_feeds
        BackupEventType.BLOCKED_RELAYS -> R.string.backup_type_blocked_relays
        BackupEventType.TRUSTED_RELAYS -> R.string.backup_type_trusted_relays
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> R.string.backup_type_private_outbox_relays
        BackupEventType.APP_SETTINGS -> R.string.backup_type_app_settings
        BackupEventType.PUBLIC_CHATS -> R.string.backup_type_public_chats
        BackupEventType.COMMUNITIES -> R.string.backup_type_communities
        BackupEventType.HASHTAGS -> R.string.backup_type_hashtags
        BackupEventType.GEOHASHES -> R.string.backup_type_geohashes
        BackupEventType.FAVORITE_ALGO_FEEDS -> R.string.backup_type_favorite_algo_feeds
        BackupEventType.EPHEMERAL_CHATS -> R.string.backup_type_ephemeral_chats
        BackupEventType.RELAY_GROUPS -> R.string.backup_type_relay_groups
        BackupEventType.CONCORD_COMMUNITIES -> R.string.backup_type_concord_communities
        BackupEventType.TRUST_PROVIDERS -> R.string.backup_type_trust_providers
        BackupEventType.CASHU_WALLET -> R.string.backup_type_cashu_wallet
        BackupEventType.NUTZAP_INFO -> R.string.backup_type_nutzap_info
        BackupEventType.PAYMENT_TARGETS -> R.string.backup_type_payment_targets
        BackupEventType.BOLT12_OFFERS -> R.string.backup_type_bolt12_offers
        BackupEventType.OTHER -> R.string.backup_type_other
    }

private fun eventTypeExplainer(type: BackupEventType): Int =
    when (type) {
        BackupEventType.PROFILE -> R.string.backup_type_profile_explainer
        BackupEventType.FOLLOW_LIST -> R.string.backup_type_follow_list_explainer
        BackupEventType.MUTE_LIST -> R.string.backup_type_mute_list_explainer
        BackupEventType.OUTBOX_INBOX_RELAYS -> R.string.backup_type_outbox_inbox_relays_explainer
        BackupEventType.DM_RELAYS -> R.string.backup_type_dm_relays_explainer
        BackupEventType.KEY_PACKAGE_RELAYS -> R.string.backup_type_key_package_relays_explainer
        BackupEventType.SEARCH_RELAYS -> R.string.backup_type_search_relays_explainer
        BackupEventType.INDEXER_RELAYS -> R.string.backup_type_indexer_relays_explainer
        BackupEventType.RELAY_FEEDS -> R.string.backup_type_relay_feeds_explainer
        BackupEventType.BLOCKED_RELAYS -> R.string.backup_type_blocked_relays_explainer
        BackupEventType.TRUSTED_RELAYS -> R.string.backup_type_trusted_relays_explainer
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> R.string.backup_type_private_outbox_relays_explainer
        BackupEventType.APP_SETTINGS -> R.string.backup_type_app_settings_explainer
        BackupEventType.PUBLIC_CHATS -> R.string.backup_type_public_chats_explainer
        BackupEventType.COMMUNITIES -> R.string.backup_type_communities_explainer
        BackupEventType.HASHTAGS -> R.string.backup_type_hashtags_explainer
        BackupEventType.GEOHASHES -> R.string.backup_type_geohashes_explainer
        BackupEventType.FAVORITE_ALGO_FEEDS -> R.string.backup_type_favorite_algo_feeds_explainer
        BackupEventType.EPHEMERAL_CHATS -> R.string.backup_type_ephemeral_chats_explainer
        BackupEventType.RELAY_GROUPS -> R.string.backup_type_relay_groups_explainer
        BackupEventType.CONCORD_COMMUNITIES -> R.string.backup_type_concord_communities_explainer
        BackupEventType.TRUST_PROVIDERS -> R.string.backup_type_trust_providers_explainer
        BackupEventType.CASHU_WALLET -> R.string.backup_type_cashu_wallet_explainer
        BackupEventType.NUTZAP_INFO -> R.string.backup_type_nutzap_info_explainer
        BackupEventType.PAYMENT_TARGETS -> R.string.backup_type_payment_targets_explainer
        BackupEventType.BOLT12_OFFERS -> R.string.backup_type_bolt12_offers_explainer
        BackupEventType.OTHER -> R.string.backup_type_other_explainer
    }
