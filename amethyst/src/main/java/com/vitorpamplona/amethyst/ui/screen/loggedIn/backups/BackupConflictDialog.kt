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
import com.vitorpamplona.amethyst.commons.model.backups.BackupDiff
import com.vitorpamplona.amethyst.commons.model.backups.BackupEntry
import com.vitorpamplona.amethyst.commons.model.backups.BackupEntryChange
import com.vitorpamplona.amethyst.commons.model.backups.BackupEntryType
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.util.toShortDisplay
import com.vitorpamplona.amethyst.ui.note.timeAbsolute
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.HexKey

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(diff.eventType)))) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringRes(eventTypeExplainer(diff.eventType)),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringRes(R.string.backup_conflict_intro, timeAbsolute(conflict.incoming.createdAt, context)))

                DiffDetails(diff)

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
private fun DiffDetails(diff: BackupDiff) {
    if (diff.removed.isNotEmpty() || diff.privateItemsCleared) {
        SectionHeader(
            stringRes(R.string.backup_conflict_removed, diff.removed.size.toString()),
            MaterialTheme.colorScheme.error,
        )
        if (diff.privateItemsCleared) {
            Text("• " + stringRes(R.string.backup_conflict_private_cleared))
        }
        EntryGroups(diff.removed)
    }

    if (diff.added.isNotEmpty()) {
        SectionHeader(
            stringRes(R.string.backup_conflict_added, diff.added.size.toString()),
            MaterialTheme.colorScheme.primary,
        )
        EntryGroups(diff.added)
    }

    if (diff.changed.isNotEmpty() || diff.privateItemsChanged) {
        SectionHeader(
            stringRes(R.string.backup_conflict_changed, diff.changed.size.toString()),
            MaterialTheme.colorScheme.tertiary,
        )
        if (diff.privateItemsChanged) {
            Text("• " + stringRes(R.string.backup_conflict_private_changed))
        }
        ChangeGroups(diff.changed)
    }
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
private fun GroupLabel(type: BackupEntryType) {
    Text(
        text = stringRes(entryTypeName(type)),
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
private fun EntryGroups(entries: List<BackupEntry>) {
    entries.groupBy { it.type }.forEach { (type, group) ->
        GroupLabel(type)
        group.take(MAX_ITEMS_PER_GROUP).forEach { Item(describe(it)) }
        MoreItems(group.size - MAX_ITEMS_PER_GROUP)
    }
}

@Composable
private fun ChangeGroups(changes: List<BackupEntryChange>) {
    changes.groupBy { it.before.type }.forEach { (type, group) ->
        GroupLabel(type)
        group.take(MAX_ITEMS_PER_GROUP).forEach { Item(describe(it)) }
        MoreItems(group.size - MAX_ITEMS_PER_GROUP)
    }
}

@Composable
private fun describe(change: BackupEntryChange): String {
    val before = change.before
    val after = change.after
    return when (before.type) {
        BackupEntryType.PROFILE_FIELD ->
            profileFieldName(before.value) + ": " + clip(before.detail) + " → " + clip(after.detail)
        BackupEntryType.RELAY ->
            before.value + ": " + relayMarker(before.detail) + " → " + relayMarker(after.detail)
        else -> entryName(before) + ": " + clip(before.detail) + " → " + clip(after.detail)
    }
}

@Composable
private fun describe(entry: BackupEntry): String =
    when (entry.type) {
        BackupEntryType.PROFILE_FIELD -> profileFieldName(entry.value) + ": " + clip(entry.detail)
        BackupEntryType.RELAY -> entry.value + " (" + relayMarker(entry.detail) + ")"
        BackupEntryType.TRUST_PROVIDER -> (entry.detail ?: "") + ": " + personName(entry.value)
        BackupEntryType.PAYMENT_TARGET, BackupEntryType.OTHER ->
            if (entry.detail != null) entry.value + ": " + clip(entry.detail) else clip(entry.value)
        BackupEntryType.CHAT_ROOM ->
            if (entry.detail != null) entry.value + " @ " + entry.detail else entry.value
        else -> entryName(entry)
    }

private fun entryName(entry: BackupEntry): String =
    when (entry.type) {
        BackupEntryType.PERSON, BackupEntryType.NUTZAP_KEY -> personName(entry.value)
        BackupEntryType.HASHTAG -> "#" + entry.value
        BackupEntryType.WORD -> "\"" + entry.value + "\""
        BackupEntryType.PUBLIC_CHAT ->
            LocalCache.getPublicChatChannelIfExists(entry.value)?.toBestDisplayName() ?: entry.value.toShortDisplay()
        BackupEntryType.COMMUNITY, BackupEntryType.ALGO_FEED, BackupEntryType.ADDRESS ->
            Address.parse(entry.value)?.dTag?.ifBlank { null } ?: entry.value.toShortDisplay()
        BackupEntryType.RELAY_GROUP -> entry.detail ?: entry.value
        BackupEntryType.THREAD, BackupEntryType.EVENT, BackupEntryType.OFFER -> entry.value.toShortDisplay()
        else -> clip(entry.value)
    }

private fun personName(pubkey: String): String = LocalCache.getUserIfExists(pubkey)?.toBestDisplayName() ?: pubkey.toShortDisplay()

private fun clip(text: String?): String {
    if (text == null) return ""
    val oneLine = text.replace('\n', ' ')
    return if (oneLine.length > MAX_VALUE_LENGTH) oneLine.take(MAX_VALUE_LENGTH) + "…" else oneLine
}

@Composable
private fun relayMarker(marker: String?): String =
    when (marker) {
        "read" -> stringRes(R.string.backup_conflict_relay_read_only)
        "write" -> stringRes(R.string.backup_conflict_relay_write_only)
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

private fun entryTypeName(type: BackupEntryType): Int =
    when (type) {
        BackupEntryType.PROFILE_FIELD -> R.string.backup_entry_profile_field
        BackupEntryType.PERSON -> R.string.backup_entry_person
        BackupEntryType.HASHTAG -> R.string.backup_entry_hashtag
        BackupEntryType.WORD -> R.string.backup_entry_word
        BackupEntryType.THREAD -> R.string.backup_entry_thread
        BackupEntryType.PUBLIC_CHAT -> R.string.backup_entry_public_chat
        BackupEntryType.COMMUNITY -> R.string.backup_entry_community
        BackupEntryType.ALGO_FEED -> R.string.backup_entry_algo_feed
        BackupEntryType.LOCATION -> R.string.backup_entry_location
        BackupEntryType.RELAY -> R.string.backup_entry_relay
        BackupEntryType.RELAY_GROUP -> R.string.backup_entry_relay_group
        BackupEntryType.CHAT_ROOM -> R.string.backup_entry_chat_room
        BackupEntryType.TRUST_PROVIDER -> R.string.backup_entry_trust_provider
        BackupEntryType.MINT -> R.string.backup_entry_mint
        BackupEntryType.NUTZAP_KEY -> R.string.backup_entry_nutzap_key
        BackupEntryType.PAYMENT_TARGET -> R.string.backup_entry_payment_target
        BackupEntryType.OFFER -> R.string.backup_entry_offer
        BackupEntryType.EVENT -> R.string.backup_entry_event
        BackupEntryType.ADDRESS -> R.string.backup_entry_address
        BackupEntryType.OTHER -> R.string.backup_entry_other
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
