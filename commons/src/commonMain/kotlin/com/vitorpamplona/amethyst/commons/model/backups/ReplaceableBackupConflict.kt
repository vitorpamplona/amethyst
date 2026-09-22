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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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

/** What a single entry of a backed-up event represents, so the UI can label and render it. */
enum class BackupEntryType {
    PROFILE_FIELD,
    PERSON,
    HASHTAG,
    WORD,
    THREAD,
    PUBLIC_CHAT,
    COMMUNITY,
    ALGO_FEED,
    LOCATION,
    RELAY,
    RELAY_GROUP,
    CHAT_ROOM,
    TRUST_PROVIDER,
    MINT,
    NUTZAP_KEY,
    PAYMENT_TARGET,
    OFFER,
    EVENT,
    ADDRESS,
    OTHER,
}

/**
 * One entry of a backed-up event.
 *
 * @property value the identity of the entry: a pubkey, relay url, hashtag, profile field name…
 * @property detail extra context: the relay marker (read/write), a group's name, a profile
 * field's value, the service a trust provider serves…
 */
@Immutable
class BackupEntry(
    val type: BackupEntryType,
    val value: String,
    val detail: String?,
)

/** An entry present in both versions whose details differ (a new relay marker, an edited bio). */
@Immutable
class BackupEntryChange(
    val before: BackupEntry,
    val after: BackupEntry,
)

/**
 * Everything that differs between the version of an event saved on this device and a newer
 * version from another client.
 *
 * @property privateItemsCleared the saved version had encrypted private items and the new one has none.
 * @property privateItemsChanged the encrypted private items differ but were not cleared; they can't
 * be compared without decrypting.
 */
@Immutable
class BackupDiff(
    val eventType: BackupEventType,
    val removed: List<BackupEntry>,
    val added: List<BackupEntry>,
    val changed: List<BackupEntryChange>,
    val privateItemsCleared: Boolean,
    val privateItemsChanged: Boolean,
) {
    /** The new version dropped something the saved one had: the only case worth asking about. */
    fun losesData() = removed.isNotEmpty() || privateItemsCleared
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
    val diff: BackupDiff,
    private val acceptIncoming: () -> Unit,
) {
    /** One conflict per replaceable slot: kind for replaceables, kind + d-tag for addressables. */
    val slot: String = backupSlot(incoming)

    fun keepIncoming() = acceptIncoming()
}

fun backupSlot(event: Event): String = event.kind.toString() + ":" + (event.tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1) ?: "")

object ReplaceableBackupDiff {
    /**
     * Tag names that clients rewrite for their own bookkeeping. Dropping them loses nothing
     * the user entered, so they are left out of the diff.
     */
    private val IGNORED_TAG_NAMES = setOf("alt", "client", "d", "expiration")

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Compares the [saved] backup with an [incoming] replacement of the same slot and reports
     * what it removed, added and changed, or null when it removed nothing (it only added or
     * edited entries, which means the other app did look at the previous version).
     */
    fun detectLoss(
        saved: Event,
        incoming: Event,
    ): BackupDiff? = diff(saved, incoming)?.takeIf { it.losesData() }

    /**
     * Full diff between [saved] and a newer [incoming] version of the same slot. Null when
     * they aren't comparable (different kind/author) or [incoming] isn't newer.
     *
     * Tags are matched by name + value, so a changed relay hint, marker or petname on the same
     * entry is a change, not a removal plus an addition.
     */
    fun diff(
        saved: Event,
        incoming: Event,
    ): BackupDiff? {
        if (saved.id == incoming.id) return null
        if (saved.kind != incoming.kind || saved.pubKey != incoming.pubKey) return null
        // Older or same-age versions never replace the backup in LocalCache anyway.
        if (incoming.createdAt <= saved.createdAt) return null

        val eventType = BackupEventType.of(saved.kind)

        val savedTags = keyedTags(saved)
        val incomingTags = keyedTags(incoming)

        val removed = mutableListOf<BackupEntry>()
        val added = mutableListOf<BackupEntry>()
        val changed = mutableListOf<BackupEntryChange>()

        savedTags.forEach { (key, tag) ->
            val other = incomingTags[key]
            if (other == null) {
                removed.add(entryOf(eventType, tag))
            } else if (!other.contentEquals(tag)) {
                val before = entryOf(eventType, tag)
                val after = entryOf(eventType, other)
                if (before.detail != after.detail) changed.add(BackupEntryChange(before, after))
            }
        }
        incomingTags.forEach { (key, tag) ->
            if (key !in savedTags) added.add(entryOf(eventType, tag))
        }

        var privateItemsCleared = false
        var privateItemsChanged = false

        when (eventType) {
            BackupEventType.PROFILE -> diffProfile(saved.content, incoming.content, removed, added, changed)
            // kind:3 content is a deprecated relay map many clients drop on purpose.
            BackupEventType.FOLLOW_LIST -> {}
            else -> {
                privateItemsCleared = saved.content.isNotBlank() && incoming.content.isBlank()
                privateItemsChanged = !privateItemsCleared && saved.content != incoming.content && saved.content.isNotBlank()
            }
        }

        return BackupDiff(eventType, removed, added, changed, privateItemsCleared, privateItemsChanged)
    }

    private fun keyedTags(event: Event): Map<String, Array<String>> {
        val result = LinkedHashMap<String, Array<String>>(event.tags.size)
        event.tags.forEach { tag ->
            if (tag.isNotEmpty() && tag[0] !in IGNORED_TAG_NAMES) {
                val key = if (tag.size > 1) tag[0] + "\u0000" + tag[1] else tag[0]
                result.putIfAbsent(key, tag)
            }
        }
        return result
    }

    private fun entryOf(
        eventType: BackupEventType,
        tag: Array<String>,
    ): BackupEntry {
        val name = tag[0]
        val value = tag.getOrNull(1) ?: ""
        return when {
            // NIP-85 provider entries are named "<kind>:<service>".
            name.contains(':') -> BackupEntry(BackupEntryType.TRUST_PROVIDER, value, name.substringAfter(':'))
            name == "p" -> BackupEntry(BackupEntryType.PERSON, value, null)
            name == "t" -> BackupEntry(BackupEntryType.HASHTAG, value, null)
            name == "word" -> BackupEntry(BackupEntryType.WORD, value, null)
            name == "g" -> BackupEntry(BackupEntryType.LOCATION, value, null)
            // NIP-65 marks read-only / write-only relays; no marker means both.
            name == "r" || name == "relay" -> BackupEntry(BackupEntryType.RELAY, value, tag.getOrNull(2)?.ifBlank { null })
            name == "mint" -> BackupEntry(BackupEntryType.MINT, value, null)
            name == "pubkey" -> BackupEntry(BackupEntryType.NUTZAP_KEY, value, null)
            name == "payto" -> BackupEntry(BackupEntryType.PAYMENT_TARGET, value, tag.getOrNull(2))
            name == "offer" -> BackupEntry(BackupEntryType.OFFER, value, tag.getOrNull(2))
            name == "group" && eventType == BackupEventType.EPHEMERAL_CHATS ->
                BackupEntry(BackupEntryType.CHAT_ROOM, value, tag.getOrNull(2))
            // NIP-51 ["group", id, relay, name?]
            name == "group" -> BackupEntry(BackupEntryType.RELAY_GROUP, value, tag.getOrNull(3) ?: tag.getOrNull(2))
            name == "e" ->
                when (eventType) {
                    BackupEventType.MUTE_LIST -> BackupEntry(BackupEntryType.THREAD, value, null)
                    BackupEventType.PUBLIC_CHATS -> BackupEntry(BackupEntryType.PUBLIC_CHAT, value, tag.getOrNull(2))
                    else -> BackupEntry(BackupEntryType.EVENT, value, null)
                }
            name == "a" ->
                when (eventType) {
                    BackupEventType.COMMUNITIES -> BackupEntry(BackupEntryType.COMMUNITY, value, tag.getOrNull(2))
                    BackupEventType.FAVORITE_ALGO_FEEDS -> BackupEntry(BackupEntryType.ALGO_FEED, value, tag.getOrNull(2))
                    else -> BackupEntry(BackupEntryType.ADDRESS, value, null)
                }
            else -> BackupEntry(BackupEntryType.OTHER, value, name)
        }
    }

    private fun parseObject(content: String): JsonObject? = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()

    private fun JsonObject.filledFields(): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        forEach { (key, value) ->
            val text = if (value is JsonPrimitive) value.content else value.toString()
            if (text.isNotBlank() && text != "null") result[key] = text
        }
        return result
    }

    private fun diffProfile(
        savedContent: String,
        incomingContent: String,
        removed: MutableList<BackupEntry>,
        added: MutableList<BackupEntry>,
        changed: MutableList<BackupEntryChange>,
    ) {
        val saved = parseObject(savedContent)?.filledFields() ?: return
        val incoming = parseObject(incomingContent)?.filledFields() ?: emptyMap()

        saved.keys.sorted().forEach { key ->
            val before = BackupEntry(BackupEntryType.PROFILE_FIELD, key, saved[key])
            val newValue = incoming[key]
            if (newValue == null) {
                removed.add(before)
            } else if (newValue != saved[key]) {
                changed.add(BackupEntryChange(before, BackupEntry(BackupEntryType.PROFILE_FIELD, key, newValue)))
            }
        }
        incoming.keys.sorted().forEach { key ->
            if (key !in saved) added.add(BackupEntry(BackupEntryType.PROFILE_FIELD, key, incoming[key]))
        }
    }
}
