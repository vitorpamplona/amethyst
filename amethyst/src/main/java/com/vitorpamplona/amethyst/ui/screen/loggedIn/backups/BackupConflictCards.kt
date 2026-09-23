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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.icons.symbols.Icon
import com.vitorpamplona.amethyst.commons.icons.symbols.MaterialSymbols
import com.vitorpamplona.amethyst.commons.model.backups.BackupEventType
import com.vitorpamplona.amethyst.commons.model.backups.ReplaceableBackupConflict
import com.vitorpamplona.amethyst.ui.navigation.navs.INav
import com.vitorpamplona.amethyst.ui.navigation.routes.Route
import com.vitorpamplona.amethyst.ui.note.timeAgoNoDot
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.amethyst.ui.stringRes
import com.vitorpamplona.amethyst.ui.theme.placeholderText
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListDiff
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListDiff
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsDiff
import com.vitorpamplona.quartz.nip01Core.diff.ContentChange
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListDiff
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
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
 * Cards at the top of Home, one per open [ReplaceableBackupConflict]: another app replaced
 * one of the account's backed-up lists (or the profile) with a version that lost data. They
 * can't be dismissed. They stay until the user decides on the review screen they open
 * ([BackupConflictReviewScreen]), where the differences can be inspected.
 *
 * The most recent conflict gets a lead card with a headline and a picture of the change
 * (the follow list's shrink bar, or lost/gained counts); the rest collapse into slim pills
 * so they don't cover the feed.
 */
@Composable
fun BackupConflictCards(
    accountViewModel: AccountViewModel,
    nav: INav,
    modifier: Modifier = Modifier,
) {
    val conflictMap by accountViewModel.account.settings.backupConflicts
        .collectAsStateWithLifecycle()
    if (conflictMap.isEmpty()) return

    val conflicts = conflictMap.values.sortedByDescending { it.cause.createdAt }
    val open = { conflict: ReplaceableBackupConflict -> nav.nav(Route.BackupConflictReview(conflict.slot)) }

    // Home's padding only covers the top bar; in landscape a side 3-button navigation bar
    // or a display cutout would otherwise sit on top of the cards.
    Column(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LeadConflictCard(conflicts.first()) { open(conflicts.first()) }

        // The cards float over the feed, so they can't grow with the number of conflicts:
        // a few pills show, the rest wait behind a toggle and scroll inside a capped area.
        val rest = conflicts.drop(1)
        var expanded by rememberSaveable { mutableStateOf(false) }
        if (rest.size <= MAX_PILLS || expanded) {
            Column(
                Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rest.forEach { conflict -> ConflictPill(conflict) { open(conflict) } }
            }
            if (rest.size > MAX_PILLS) MoreConflictsToggle(stringRes(R.string.backup_card_show_less)) { expanded = false }
        } else {
            rest.take(MAX_PILLS).forEach { conflict -> ConflictPill(conflict) { open(conflict) } }
            val hidden = rest.size - MAX_PILLS
            MoreConflictsToggle(pluralStringResource(R.plurals.backup_card_more_lists, hidden, hidden)) { expanded = true }
        }
    }
}

private const val MAX_PILLS = 2

@Composable
private fun MoreConflictsToggle(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
    )
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
                        stringRes(R.string.backup_review_changed_by_other_app, timeAgoNoDot(conflict.cause.createdAt, LocalContext.current)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.placeholderText,
                    )
                }
                Icon(symbol = MaterialSymbols.ChevronRight, contentDescription = stringRes(R.string.backup_conflict_review), tint = MaterialTheme.colorScheme.placeholderText)
            }
            val diff = conflict.diff
            if (diff is ContactListDiff) {
                val saved = (conflict.saved as? ContactListEvent)?.uniqueFollowCount() ?: 0
                val new = (conflict.incoming as? ContactListEvent)?.uniqueFollowCount() ?: 0
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
            if (counts.removed > 0) StatusTag("\u2212" + counts.removed, tones.removed)
            if (counts.added > 0) StatusTag("+" + counts.added, tones.added)
        }
    }
}
