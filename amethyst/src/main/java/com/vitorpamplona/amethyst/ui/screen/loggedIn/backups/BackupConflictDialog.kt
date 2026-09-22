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
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsDiff
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ItemChange
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip01Core.metadata.Birthday
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListDiff
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListDiff
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListDiff
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.EventTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.HashtagTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.MuteTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.UserTag
import com.vitorpamplona.quartz.nip51Lists.muteList.tags.WordTag
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayListDiff
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListDiff
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletDiff
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip65RelayList.tags.AdvertisedRelayType
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListDiff
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataDiff
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListDiff
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListDiff

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
 * One conflict at a time; "decide later" snoozes it while the account stays loaded (so it
 * survives rotation) and until another app changes that event again: the conflict stays
 * open, so the backup stays frozen on the saved version and the question comes back on the
 * next launch.
 */
@Composable
fun BackupConflictDialog(accountViewModel: AccountViewModel) {
    val settings = accountViewModel.account.settings
    val conflicts by settings.backupConflicts.collectAsStateWithLifecycle()
    val snoozed by settings.snoozedBackupConflicts.collectAsStateWithLifecycle()

    val conflict = conflicts.values.firstOrNull { it.slot !in snoozed } ?: return

    BackupConflictDialog(
        conflict = conflict,
        onRestore = { accountViewModel.launchSigner { accountViewModel.account.restoreBackupOver(conflict) } },
        onKeepNew = { accountViewModel.account.acceptExternalVersion(conflict) },
        onDismiss = { settings.snoozeBackupConflict(conflict) },
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
                Text(stringRes(R.string.backup_conflict_intro, timeAbsolute(conflict.cause.createdAt, context)))

                DiffDetails(conflict.diff)

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

/**
 * One labelled group of differences (people, relays, profile fields…), already turned into
 * display lines. Built per event type from that event's own diff class.
 */
private class DiffGroup(
    val label: Int,
    val removed: List<String>,
    val added: List<String>,
    val changed: List<String>,
)

/** A group from a [ListDiff] of an event's parsed items. */
private fun <T> listGroup(
    label: Int,
    diff: ListDiff<T>,
    describe: (T) -> String,
    describeChange: (ItemChange<T>) -> String = { describe(it.before) + " → " + describe(it.after) },
) = DiffGroup(label, diff.removed.map(describe), diff.added.map(describe), diff.changed.map(describeChange))

/** Keeps only the items of [T] from a list diff of a sealed family (e.g. one kind of [MuteTag]). */
private inline fun <reified T> ListDiff<*>.only() =
    ListDiff(
        removed.filterIsInstance<T>(),
        added.filterIsInstance<T>(),
        changed.filter { it.before is T }.map { ItemChange(it.before as T, it.after as T) },
    )

/** A group from single-valued fields: a field set only after is added, only before is removed. */
private fun fieldGroup(
    label: Int,
    fields: List<Pair<String, ValueChange<String>?>>,
): DiffGroup {
    val removed = mutableListOf<String>()
    val added = mutableListOf<String>()
    val changed = mutableListOf<String>()
    fields.forEach { (name, change) ->
        if (change == null) return@forEach
        val before = change.before
        val after = change.after
        when {
            before != null && after == null -> removed.add(name + ": " + clip(before))
            before == null && after != null -> added.add(name + ": " + clip(after))
            else -> changed.add(name + ": " + clip(before) + " → " + clip(after))
        }
    }
    return DiffGroup(label, removed, added, changed)
}

/** Turns each event's own diff into display groups, plus the change to its encrypted content. */
@Composable
private fun presentation(diff: EventDiff): Pair<List<DiffGroup>, ContentChange> =
    when (diff) {
        is MetadataDiff -> {
            val birthday = diff.birthday?.let { ValueChange(it.before?.let(::birthdayText), it.after?.let(::birthdayText)) }
            val bot = diff.bot?.let { ValueChange(it.before?.toString(), it.after?.toString()) }
            listOf(
                fieldGroup(
                    R.string.backup_entry_profile_field,
                    listOf(
                        stringRes(R.string.backup_profile_field_name) to diff.name,
                        stringRes(R.string.backup_profile_field_display_name) to diff.displayName,
                        stringRes(R.string.backup_profile_field_about) to diff.about,
                        stringRes(R.string.backup_profile_field_picture) to diff.picture,
                        stringRes(R.string.backup_profile_field_banner) to diff.banner,
                        stringRes(R.string.backup_profile_field_website) to diff.website,
                        stringRes(R.string.backup_profile_field_nip05) to diff.nip05,
                        stringRes(R.string.backup_profile_field_lud16) to diff.lud16,
                        stringRes(R.string.backup_profile_field_lud06) to diff.lud06,
                        stringRes(R.string.backup_profile_field_clink_offer) to diff.clinkOffer,
                        stringRes(R.string.backup_profile_field_pronouns) to diff.pronouns,
                        stringRes(R.string.backup_profile_field_birthday) to birthday,
                        stringRes(R.string.backup_profile_field_bot) to bot,
                    ),
                ),
                listGroup(R.string.backup_entry_identity_claim, diff.identityClaims, { it.platformIdentity() }),
                listGroup(R.string.backup_entry_other, diff.otherFields, { it.first + ": " + clip(it.second) }),
            ) to ContentChange.NONE
        }
        is ContactListDiff ->
            listOf(
                listGroup(
                    R.string.backup_entry_person,
                    diff.follows,
                    { personName(it.pubKey) + (it.petname?.let { name -> " ($name)" } ?: "") },
                    {
                        personName(it.before.pubKey) + ": " +
                            listOfNotNull(it.before.petname, it.before.relayUri?.url).joinToString() + " → " +
                            listOfNotNull(it.after.petname, it.after.relayUri?.url).joinToString()
                    },
                ),
            ) to ContentChange.NONE
        is MuteListDiff ->
            listOf(
                listGroup(R.string.backup_entry_person, diff.publicMutes.only<UserTag>(), { personName(it.pubKey) }),
                listGroup(R.string.backup_entry_word, diff.publicMutes.only<WordTag>(), { "\"" + it.word + "\"" }),
                listGroup(R.string.backup_entry_hashtag, diff.publicMutes.only<HashtagTag>(), { "#" + it.hashtag }),
                listGroup(R.string.backup_entry_thread, diff.publicMutes.only<EventTag>(), { it.eventId.toShortDisplay() }),
            ) to diff.privateItems
        is AdvertisedRelayListDiff -> {
            val types =
                mapOf(
                    AdvertisedRelayType.BOTH to stringRes(R.string.backup_conflict_relay_read_write),
                    AdvertisedRelayType.READ to stringRes(R.string.backup_conflict_relay_read_only),
                    AdvertisedRelayType.WRITE to stringRes(R.string.backup_conflict_relay_write_only),
                )
            listOf(
                listGroup(
                    R.string.backup_entry_relay,
                    diff.relays,
                    { it.relayUrl.url + " (" + types[it.type] + ")" },
                    { it.before.relayUrl.url + ": " + types[it.before.type] + " → " + types[it.after.type] },
                ),
            ) to ContentChange.NONE
        }
        is RelayListDiff -> listOf(listGroup(R.string.backup_entry_relay, diff.relays, { it.url })) to diff.privateRelays
        is ChannelListDiff ->
            listOf(
                listGroup(R.string.backup_entry_public_chat, diff.channels, { channelName(it.eventId) }),
            ) to diff.privateItems
        is CommunityListDiff ->
            listOf(
                listGroup(R.string.backup_entry_community, diff.communities, { it.address.dTag.ifBlank { it.address.toValue() } }),
            ) to diff.privateItems
        is HashtagListDiff -> listOf(listGroup(R.string.backup_entry_hashtag, diff.hashtags, { "#$it" })) to diff.privateItems
        is GeohashListDiff -> listOf(listGroup(R.string.backup_entry_location, diff.geohashes, { it })) to diff.privateItems
        is FavoriteAlgoFeedsListDiff ->
            listOf(
                listGroup(R.string.backup_entry_algo_feed, diff.feeds, { it.address.dTag.ifBlank { it.address.toValue() } }),
            ) to diff.privateItems
        is EphemeralChatListDiff ->
            listOf(
                listGroup(R.string.backup_entry_chat_room, diff.rooms, { it.id + " @ " + it.relayUrl.url }),
            ) to diff.privateItems
        is SimpleGroupListDiff ->
            listOf(
                listGroup(
                    R.string.backup_entry_relay_group,
                    diff.groups,
                    { (it.name ?: it.groupId) + " @ " + it.relayUrl },
                    { (it.before.name ?: it.before.groupId) + " → " + (it.after.name ?: it.after.groupId) },
                ),
            ) to diff.privateItems
        is TrustProviderListDiff ->
            listOf(
                listGroup(
                    R.string.backup_entry_trust_provider,
                    diff.providers,
                    { it.service.type + ": " + personName(it.pubkey) },
                    { it.before.service.type + ": " + it.before.relayUrl.url + " → " + it.after.relayUrl.url },
                ),
            ) to diff.privateItems
        is NutzapInfoDiff ->
            listOf(
                listGroup(
                    R.string.backup_entry_mint,
                    diff.mints,
                    { it.mintUrl + (if (it.units.isEmpty()) "" else " (" + it.units.joinToString() + ")") },
                    { it.before.mintUrl + ": " + it.before.units.joinToString() + " → " + it.after.units.joinToString() },
                ),
                listGroup(R.string.backup_entry_relay, diff.relays, { it.url }),
                fieldGroup(
                    R.string.backup_entry_nutzap_key,
                    listOf(
                        stringRes(R.string.backup_entry_nutzap_key) to
                            diff.p2pkPubkey?.let { ValueChange(it.before?.toShortDisplay(), it.after?.toShortDisplay()) },
                    ),
                ),
            ) to ContentChange.NONE
        is PaymentTargetsDiff ->
            listOf(
                listGroup(R.string.backup_entry_payment_target, diff.targets, { it.type + ": " + clip(it.authority) }),
            ) to ContentChange.NONE
        is Bolt12OfferListDiff -> listOf(listGroup(R.string.backup_entry_offer, diff.offers, { it.toShortDisplay() })) to ContentChange.NONE
        is CashuWalletDiff -> emptyList<DiffGroup>() to diff.wallet
        is ConcordCommunityListDiff -> emptyList<DiffGroup>() to diff.communities
        is AppSpecificDataDiff -> emptyList<DiffGroup>() to diff.data
        else -> emptyList<DiffGroup>() to ContentChange.NONE
    }

@Composable
private fun DiffDetails(diff: EventDiff) {
    val (allGroups, content) = presentation(diff)
    val groups = allGroups.filter { it.removed.isNotEmpty() || it.added.isNotEmpty() || it.changed.isNotEmpty() }

    val removed = groups.sumOf { it.removed.size }
    if (removed > 0 || content == ContentChange.CLEARED) {
        SectionHeader(stringRes(R.string.backup_conflict_removed, removed.toString()), MaterialTheme.colorScheme.error)
        if (content == ContentChange.CLEARED) ContentNote(R.string.backup_conflict_private_cleared)
        groups.forEach { ItemGroup(it.label, it.removed) }
    }

    val added = groups.sumOf { it.added.size }
    if (added > 0 || content == ContentChange.ADDED) {
        SectionHeader(stringRes(R.string.backup_conflict_added, added.toString()), MaterialTheme.colorScheme.primary)
        if (content == ContentChange.ADDED) ContentNote(R.string.backup_conflict_private_added)
        groups.forEach { ItemGroup(it.label, it.added) }
    }

    val changed = groups.sumOf { it.changed.size }
    if (changed > 0 || content == ContentChange.CHANGED) {
        SectionHeader(stringRes(R.string.backup_conflict_changed, changed.toString()), MaterialTheme.colorScheme.tertiary)
        if (content == ContentChange.CHANGED) ContentNote(R.string.backup_conflict_private_changed)
        groups.forEach { ItemGroup(it.label, it.changed) }
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
private fun ItemGroup(
    labelRes: Int,
    items: List<String>,
) {
    if (items.isEmpty()) return
    Text(
        text = stringRes(labelRes),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = 6.dp),
    )
    items.take(MAX_ITEMS_PER_GROUP).forEach {
        Text(
            text = "• $it",
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    val more = items.size - MAX_ITEMS_PER_GROUP
    if (more > 0) {
        Text(
            text = stringRes(R.string.backup_conflict_more_items, more.toString()),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun personName(pubkey: String): String = LocalCache.getUserIfExists(pubkey)?.toBestDisplayName() ?: pubkey.toShortDisplay()

private fun channelName(eventId: String): String = LocalCache.getPublicChatChannelIfExists(eventId)?.toBestDisplayName() ?: eventId.toShortDisplay()

private fun birthdayText(birthday: Birthday): String = listOfNotNull(birthday.year, birthday.month, birthday.day).joinToString("-")

private fun clip(text: String?): String {
    if (text == null) return ""
    val oneLine = text.replace('\n', ' ')
    return if (oneLine.length > MAX_VALUE_LENGTH) oneLine.take(MAX_VALUE_LENGTH) + "…" else oneLine
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
