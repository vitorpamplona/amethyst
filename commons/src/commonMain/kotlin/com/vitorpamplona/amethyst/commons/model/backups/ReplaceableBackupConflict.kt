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
package com.vitorpamplona.amethyst.commons.model.backups

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.concord.cord02Community.ConcordCommunityListEvent
import com.vitorpamplona.quartz.experimental.ephemChat.list.EphemeralChatListEvent
import com.vitorpamplona.quartz.experimental.nipA3.PaymentTargetsEvent
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageRelayListEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip17Dm.settings.ChatMessageRelayListEvent
import com.vitorpamplona.quartz.nip28PublicChat.list.ChannelListEvent
import com.vitorpamplona.quartz.nip37Drafts.privateOutbox.PrivateOutboxRelayListEvent
import com.vitorpamplona.quartz.nip50Search.SearchRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.favoriteAlgoFeedsList.FavoriteAlgoFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.geohashList.GeohashListEvent
import com.vitorpamplona.quartz.nip51Lists.hashtagList.HashtagListEvent
import com.vitorpamplona.quartz.nip51Lists.muteList.MuteListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.BlockedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.IndexerRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.RelayFeedsListEvent
import com.vitorpamplona.quartz.nip51Lists.relayLists.TrustedRelayListEvent
import com.vitorpamplona.quartz.nip51Lists.simpleGroupList.SimpleGroupListEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip72ModCommunities.follow.CommunityListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nipB1Bolt12Zaps.offer.Bolt12OfferListEvent

/** Which of the account's backed-up events a conflict is about. Drives the dialog's wording. */
enum class BackupEventType {
    PROFILE,
    FOLLOW_LIST,
    MUTE_LIST,
    OUTBOX_INBOX_RELAYS,
    DM_RELAYS,
    KEY_PACKAGE_RELAYS,
    SEARCH_RELAYS,
    INDEXER_RELAYS,
    RELAY_FEEDS,
    BLOCKED_RELAYS,
    TRUSTED_RELAYS,
    PRIVATE_OUTBOX_RELAYS,
    APP_SETTINGS,
    PUBLIC_CHATS,
    COMMUNITIES,
    HASHTAGS,
    GEOHASHES,
    FAVORITE_ALGO_FEEDS,
    EPHEMERAL_CHATS,
    RELAY_GROUPS,
    CONCORD_COMMUNITIES,
    TRUST_PROVIDERS,
    CASHU_WALLET,
    NUTZAP_INFO,
    PAYMENT_TARGETS,
    BOLT12_OFFERS,
    OTHER,
    ;

    companion object {
        fun of(kind: Int): BackupEventType =
            when (kind) {
                MetadataEvent.KIND -> PROFILE
                ContactListEvent.KIND -> FOLLOW_LIST
                MuteListEvent.KIND -> MUTE_LIST
                AdvertisedRelayListEvent.KIND -> OUTBOX_INBOX_RELAYS
                ChatMessageRelayListEvent.KIND -> DM_RELAYS
                KeyPackageRelayListEvent.KIND -> KEY_PACKAGE_RELAYS
                SearchRelayListEvent.KIND -> SEARCH_RELAYS
                IndexerRelayListEvent.KIND -> INDEXER_RELAYS
                RelayFeedsListEvent.KIND -> RELAY_FEEDS
                BlockedRelayListEvent.KIND -> BLOCKED_RELAYS
                TrustedRelayListEvent.KIND -> TRUSTED_RELAYS
                PrivateOutboxRelayListEvent.KIND -> PRIVATE_OUTBOX_RELAYS
                AppSpecificDataEvent.KIND -> APP_SETTINGS
                ChannelListEvent.KIND -> PUBLIC_CHATS
                CommunityListEvent.KIND -> COMMUNITIES
                HashtagListEvent.KIND -> HASHTAGS
                GeohashListEvent.KIND -> GEOHASHES
                FavoriteAlgoFeedsListEvent.KIND -> FAVORITE_ALGO_FEEDS
                EphemeralChatListEvent.KIND -> EPHEMERAL_CHATS
                SimpleGroupListEvent.KIND -> RELAY_GROUPS
                ConcordCommunityListEvent.KIND -> CONCORD_COMMUNITIES
                TrustProviderListEvent.KIND -> TRUST_PROVIDERS
                CashuWalletEvent.KIND -> CASHU_WALLET
                NutzapInfoEvent.KIND -> NUTZAP_INFO
                PaymentTargetsEvent.KIND -> PAYMENT_TARGETS
                Bolt12OfferListEvent.KIND -> BOLT12_OFFERS
                else -> OTHER
            }
    }
}

/**
 * A newer version of one of the account's backed-up replaceable events arrived from outside
 * this app and dropped data the saved version had. The backup keeps [saved] until the user
 * either accepts [incoming] or re-signs [saved] on top of it.
 */
@Immutable
class ReplaceableBackupConflict(
    val saved: Event,
    val incoming: Event,
    val diff: EventDiff,
    private val acceptIncoming: () -> Unit,
) {
    /** One conflict per replaceable slot: kind for replaceables, kind + d-tag for addressables. */
    val slot: String = backupSlot(incoming)

    val eventType: BackupEventType get() = BackupEventType.of(incoming.kind)

    fun keepIncoming() = acceptIncoming()
}

fun backupSlot(event: Event): String = event.kind.toString() + ":" + (event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: "")

object ReplaceableBackupDiff {
    /**
     * What a newer [incoming] version of a backed-up event removed from the [saved] one, as
     * computed by the event itself ([Event.diffFrom]), or null when it removed nothing: it
     * only added or edited entries, which means the other app did read the previous version.
     */
    fun detectLoss(
        saved: Event,
        incoming: Event,
    ): EventDiff? {
        if (saved.id == incoming.id) return null
        // Older or same-age versions never replace the backup in LocalCache anyway.
        if (incoming.createdAt <= saved.createdAt) return null
        return incoming.diffFrom(saved)?.takeIf { it.removesData() }
    }
}
