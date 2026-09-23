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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.commons.model.navigation.Route
import com.vitorpamplona.amethyst.commons.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.commons.ui.theme.placeholderText
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsDiff
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListDiff
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListDiff
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListDiff
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListDiff
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListDiff
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayListDiff
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListDiff
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletDiff
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoDiff
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListDiff
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListDiff
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataDiff
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListDiff
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListDiff

/**
 * Cards at the top of Home's feed, one per open [ReplaceableBackupConflict]: the user
 * changed one of the account's backed-up lists (or the profile) in another app, and
 * Amethyst asks to confirm before its backup moves on. They can't be dismissed. They stay
 * until the user decides on the review screen they open ([BackupConflictReviewScreen]).
 *
 * The most recent conflict gets a lead card with a headline and a picture of the change
 * (the follow list's bar, or removed/added counts); the rest are slim pills. They are the
 * first item of the feed, so they scroll away with it instead of covering posts.
 */
@Composable
fun BackupConflictCards(
    conflictMap: Map<String, ReplaceableBackupConflict>,
    nav: INav,
    onAccept: (ReplaceableBackupConflict) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (conflictMap.isEmpty()) return

    val conflicts = remember(conflictMap) { conflictMap.values.sortedByDescending { it.cause.createdAt } }
    val open = { conflict: ReplaceableBackupConflict -> nav.nav(Route.BackupConflictReview(conflict.slot)) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val lead = conflicts.first()
        LeadConflictCard(lead, onAccept = { onAccept(lead) }) { open(lead) }
        conflicts.drop(1).forEach { conflict -> ConflictPill(conflict, onAccept = { onAccept(conflict) }) { open(conflict) } }
    }
}

/** A headline that says what happened, in the event's own terms. */
@Composable
private fun headlineOf(
    conflict: ReplaceableBackupConflict,
    counts: ConflictCounts,
): String {
    val diff = conflict.diff
    val specific: String? =
        when (diff) {
            // Counted, not "shrank": a version that drops 3 follows and adds 100 still raises
            // a conflict, and calling that a shrink contradicts the card's own bar.
            is ContactListDiff -> plural(R.plurals.backup_card_follows_dropped, diff.follows.removed.size)
            is MuteListDiff -> plural(R.plurals.backup_card_mutes_visible, diff.publicMutes.removed.size)
            is MetadataDiff -> plural(R.plurals.backup_card_profile_lost_fields, counts.removed)
            is AdvertisedRelayListDiff -> plural(R.plurals.backup_card_nip65_lost, diff.relays.removed.size)
            is RelayListDiff -> relayListHeadline(conflict.eventType, diff.relays.removed.size)
            is ChannelListDiff -> plural(R.plurals.backup_card_leave_chats, diff.channels.removed.size)
            is CommunityListDiff -> plural(R.plurals.backup_card_leave_communities, diff.communities.removed.size)
            is EphemeralChatListDiff -> plural(R.plurals.backup_card_leave_rooms, diff.rooms.removed.size)
            is SimpleGroupListDiff -> plural(R.plurals.backup_card_leave_groups, diff.groups.removed.size)
            is FavoriteAlgoFeedsListDiff -> plural(R.plurals.backup_card_feeds_removed, diff.feeds.removed.size)
            is HashtagListDiff -> plural(R.plurals.backup_card_hashtags_unfollowed, diff.hashtags.removed.size)
            is GeohashListDiff -> plural(R.plurals.backup_card_places_unfollowed, diff.geohashes.removed.size)
            is TrustProviderListDiff -> plural(R.plurals.backup_card_trust_services_changed, diff.providers.removed.size + diff.providers.changed.size)
            is NutzapInfoDiff ->
                when {
                    diff.p2pkPubkey?.after == null && diff.p2pkPubkey != null -> stringRes(R.string.backup_card_nutzap_key_removed)
                    diff.p2pkPubkey != null -> stringRes(R.string.backup_card_nutzap_key_replaced)
                    else -> plural(R.plurals.backup_card_nutzap_mints_removed, diff.mints.removed.size)
                }
            is PaymentTargetsDiff -> plural(R.plurals.backup_card_payment_targets_removed, diff.targets.removed.size)
            is Bolt12OfferListDiff -> plural(R.plurals.backup_card_offers_removed, diff.offers.removed.size)
            is CashuWalletDiff -> encryptedHeadline(R.string.backup_card_wallet_emptied, R.string.backup_card_wallet_rewritten, diff.wallet)
            is ConcordCommunityListDiff -> encryptedHeadline(R.string.backup_card_concord_emptied, R.string.backup_card_concord_rewritten, diff.communities)
            is AppSpecificDataDiff -> stringRes(R.string.backup_card_app_settings)
            else -> null
        }
    // A headline counting removed items reads wrong when only encrypted items were lost.
    if (specific.isNullOrEmpty()) return stringRes(R.string.backup_conflict_title, stringRes(eventTypeName(conflict.eventType)))
    // Headlines lead with the loss; what the other app added to the same list is said too,
    // so a list that dropped 3 and gained 40 doesn't read as if it only shrank. The wording
    // follows the headline's: following people, muting, joining chats, blocking relays.
    val added = addedToHeadlinedList(diff, conflict.eventType)
    if (added <= 0) return specific
    val tail =
        when {
            diff is ContactListDiff -> R.plurals.backup_card_and_followed
            diff is MuteListDiff -> R.plurals.backup_card_and_muted
            diff is ChannelListDiff || diff is CommunityListDiff || diff is EphemeralChatListDiff || diff is SimpleGroupListDiff -> R.plurals.backup_card_and_joined
            conflict.eventType == BackupEventType.BLOCKED_RELAYS -> R.plurals.backup_card_and_blocked
            else -> R.plurals.backup_card_and_added
        }
    return pluralStringResource(tail, added, specific, added)
}

/**
 * How many items were added to the list the headline counts removals from, or 0 when the
 * headline isn't a count of list items (a key swap, an encrypted rewrite, a profile).
 */
private fun addedToHeadlinedList(
    diff: EventDiff,
    type: BackupEventType,
): Int =
    when (diff) {
        is ContactListDiff -> diff.follows.added.size
        is MuteListDiff -> diff.publicMutes.added.size
        is AdvertisedRelayListDiff -> diff.relays.added.size
        is RelayListDiff -> if (relayListHasHeadline(type)) diff.relays.added.size else 0
        is ChannelListDiff -> diff.channels.added.size
        is CommunityListDiff -> diff.communities.added.size
        is EphemeralChatListDiff -> diff.rooms.added.size
        is SimpleGroupListDiff -> diff.groups.added.size
        is FavoriteAlgoFeedsListDiff -> diff.feeds.added.size
        is HashtagListDiff -> diff.hashtags.added.size
        is GeohashListDiff -> diff.geohashes.added.size
        is NutzapInfoDiff -> if (diff.p2pkPubkey == null) diff.mints.added.size else 0
        is PaymentTargetsDiff -> diff.targets.added.size
        is Bolt12OfferListDiff -> diff.offers.added.size
        else -> 0
    }

/** The relay lists [relayListHeadline] has a counted headline for. */
private fun relayListHasHeadline(type: BackupEventType): Boolean =
    when (type) {
        BackupEventType.DM_RELAYS, BackupEventType.KEY_PACKAGE_RELAYS, BackupEventType.SEARCH_RELAYS,
        BackupEventType.INDEXER_RELAYS, BackupEventType.RELAY_FEEDS, BackupEventType.PRIVATE_OUTBOX_RELAYS,
        BackupEventType.TRUSTED_RELAYS, BackupEventType.BLOCKED_RELAYS,
        -> true
        else -> false
    }

/** The count's plural, or "" when nothing of that kind was removed. */
@Composable
private fun plural(
    id: Int,
    count: Int,
): String = if (count > 0) pluralStringResource(id, count, count) else ""

@Composable
private fun relayListHeadline(
    type: BackupEventType,
    removed: Int,
): String =
    when (type) {
        BackupEventType.DM_RELAYS -> plural(R.plurals.backup_card_dm_relays_lost, removed)
        BackupEventType.KEY_PACKAGE_RELAYS -> plural(R.plurals.backup_card_key_package_relays_lost, removed)
        BackupEventType.SEARCH_RELAYS -> plural(R.plurals.backup_card_search_relays_lost, removed)
        BackupEventType.INDEXER_RELAYS -> plural(R.plurals.backup_card_indexer_relays_lost, removed)
        BackupEventType.RELAY_FEEDS -> plural(R.plurals.backup_card_relay_feeds_lost, removed)
        BackupEventType.PRIVATE_OUTBOX_RELAYS -> plural(R.plurals.backup_card_private_outbox_lost, removed)
        BackupEventType.TRUSTED_RELAYS -> plural(R.plurals.backup_card_trusted_lost, removed)
        BackupEventType.BLOCKED_RELAYS -> plural(R.plurals.backup_card_unblocked, removed)
        else -> ""
    }

@Composable
private fun encryptedHeadline(
    emptied: Int,
    rewritten: Int,
    change: ContentChange,
): String = if (change == ContentChange.CLEARED) stringRes(emptied) else stringRes(rewritten)

@Composable
private fun LeadConflictCard(
    conflict: ReplaceableBackupConflict,
    onAccept: () -> Unit,
    onClick: () -> Unit,
) {
    val tones = conflictTones()
    val counts = remember(conflict) { countsOf(conflict.diff) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            Modifier.border(1.dp, tones.removed.copy(alpha = 0.35f), RoundedCornerShape(22.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(tones.removedContainer()), contentAlignment = Alignment.Center) {
                    Icon(symbol = MaterialSymbols.SyncProblem, contentDescription = null, tint = tones.removed)
                }
                Column(Modifier.weight(1f)) {
                    Text(headlineOf(conflict, counts), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringRes(R.string.backup_review_changed_by_other_app, timeAgoNoDot(conflict.cause.createdAt)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
                AcceptButton(onAccept)
            }
            val diff = conflict.diff
            if (diff is ContactListDiff) {
                val (saved, new) = conflict.followCounts
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(saved.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    SplitBar(
                        listOf(
                            BarSegment(saved - diff.follows.removed.size, tones.kept),
                            BarSegment(diff.follows.removed.size, tones.removed.copy(alpha = 0.6f)),
                            BarSegment(diff.follows.added.size, tones.added),
                        ),
                        Modifier.weight(1f),
                        height = 8.dp,
                    )
                    Text(new.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (new < saved) tones.removed else tones.added)
                }
            } else {
                CountChips(counts)
            }
        }
    }
}

@Composable
private fun CountChips(counts: ConflictCounts) {
    val tones = conflictTones()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (counts.removed > 0) StatusTag(stringRes(R.string.backup_conflict_count_removed, counts.removed.toString()), tones.removed)
        if (counts.added > 0) StatusTag(stringRes(R.string.backup_conflict_count_added, counts.added.toString()), tones.added)
        if (counts.changed > 0) StatusTag(stringRes(R.string.backup_conflict_count_changed, counts.changed.toString()), tones.changed)
    }
}

@Composable
private fun ConflictPill(
    conflict: ReplaceableBackupConflict,
    onAccept: () -> Unit,
    onClick: () -> Unit,
) {
    val tones = conflictTones()
    val counts = remember(conflict) { countsOf(conflict.diff) }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(tones.changedContainer()), contentAlignment = Alignment.Center) {
                Icon(symbol = MaterialSymbols.SyncProblem, contentDescription = null, tint = tones.changed, modifier = Modifier.size(18.dp))
            }
            Text(headlineOf(conflict, counts), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AcceptButton(onAccept)
        }
    }
}

/**
 * Confirms the change right from Home: the change was made on purpose in another app, so
 * keeping it is the quick path. Reverting needs the review screen, one tap on the card away.
 */
@Composable
private fun AcceptButton(onAccept: () -> Unit) {
    FilledTonalButton(
        onClick = onAccept,
        modifier = Modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(stringRes(R.string.backup_card_accept), fontWeight = FontWeight.SemiBold)
    }
}
