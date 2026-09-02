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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.User
import com.vitorpamplona.amethyst.commons.model.cache.filter
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.EventHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.PubKeyHintProvider
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip17Dm.base.BaseDMGroupEvent
import com.vitorpamplona.quartz.nip29RelayGroups.isGroupScoped
import com.vitorpamplona.quartz.nip37Drafts.DraftWrapEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.BookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.bookmarkList.OldBookmarkListEvent
import com.vitorpamplona.quartz.nip51Lists.labeledBookmarkList.LabeledBookmarkListEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingRoomEvent
import com.vitorpamplona.quartz.nip53LiveActivities.meetingSpaces.MeetingSpaceEvent
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.nip59Giftwrap.seals.SealedRumorEvent
import com.vitorpamplona.quartz.nip59Giftwrap.wraps.GiftWrapEvent
import com.vitorpamplona.quartz.nip65RelayList.AdvertisedRelayListEvent
import com.vitorpamplona.quartz.nip78AppData.AppSpecificDataEvent
import com.vitorpamplona.quartz.nip88Polls.poll.PollEvent

/**
 * The sign-and-publish choke point for an [Account]: computes the relay set an
 * event should be broadcast to (NIP-65 outbox model, relay hints, channel home
 * relays, broadcast lists, DM inboxes) and owns every publish path - automatic,
 * outbox-only, everywhere, private-relay-list, anonymous, and rebroadcast.
 *
 * Feature orchestration on [Account] (and the Account*Actions classes) should
 * funnel every publish through this class instead of calling the relay client
 * directly.
 */
class EventBroadcaster(
    private val account: Account,
) {
    private fun computeRelayListForLinkedUser(user: User): Set<NormalizedRelayUrl> =
        if (user == account.userProfile()) {
            account.notificationRelays.flow.value
        } else {
            user.inboxRelays()?.ifEmpty { null }?.toSet()
                ?: (
                    account.cache.relayHints
                        .hintsForKey(user.pubkeyHex)
                        .toSet() + user.allUsedRelays()
                )
        }

    private fun computeRelayListForLinkedUser(pubkey: HexKey): Set<NormalizedRelayUrl> =
        if (pubkey == account.userProfile().pubkeyHex) {
            account.notificationRelays.flow.value
        } else {
            account.cache
                .getUserIfExists(pubkey)
                ?.inboxRelays()
                ?.ifEmpty { null }
                ?.toSet()
                ?: account.cache.relayHints
                    .hintsForKey(pubkey)
                    .toSet()
        }

    private fun computeRelaysForChannels(event: Event): Set<NormalizedRelayUrl> = account.cache.getAnyChannel(event)?.relays() ?: emptySet()

    // Personal events the user stores just for themselves — drafts, app settings, bookmark
    // lists — and channel/community events that already declare their own home relays
    // should not be replicated to the user's broadcasting relays. Channel/community events
    // that don't define any home relays fall through to broadcast, since there's nowhere
    // else for them to land.
    private fun wantsBroadcastRelays(event: Event): Boolean {
        if (event is DraftWrapEvent ||
            event is AppSpecificDataEvent ||
            event is BookmarkListEvent ||
            event is OldBookmarkListEvent ||
            event is LabeledBookmarkListEvent
        ) {
            return false
        }
        if (event is PollEvent && event.relays().isNotEmpty()) return false
        if (event is MeetingSpaceEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is MeetingRoomEvent && event.allRelayUrls().isNotEmpty()) return false
        if (event is LiveActivitiesEvent && event.allRelayUrls().isNotEmpty()) return false

        val channelRelays = account.cache.getAnyChannel(event)?.relays()
        if (channelRelays != null && channelRelays.isNotEmpty()) return false

        // A group-scoped event whose room this cache doesn't know yet: it still must not go to the
        // broadcast list. Its `h` tag names a room only its host can serve, so broadcasting it says
        // "I am in this group" to relays that can do nothing with the content.
        if (event.isGroupScoped()) return false

        return true
    }

    fun computeRelayListToBroadcast(event: Event): Set<NormalizedRelayUrl> = computeRelayListToBroadcast(event, mutableSetOf())

    private fun computeRelayListToBroadcast(
        event: Event,
        visited: MutableSet<HexKey>,
    ): Set<NormalizedRelayUrl> {
        // a-tagged events can form cycles; without this the two recursive descents stack-overflow.
        if (!visited.add(event.id)) return emptySet()

        if (event is GiftWrapEvent) {
            val receiver = event.recipientPubKey()
            return if (receiver != null) {
                val relayList =
                    account.cache
                        .getOrCreateUser(receiver)
                        .dmInboxRelayList()
                        ?.relays()
                        ?.ifEmpty { null }
                relayList?.toSet() ?: computeRelayListForLinkedUser(receiver)
            } else {
                emptySet()
            }
        }
        // Seals, inner DM messages, and unsigned rumors never get broadcast
        // relays: they only travel inside gift wraps.
        if (event is SealedRumorEvent || event is BaseDMGroupEvent || event.sig.isEmpty()) {
            return emptySet()
        }

        // NIP-29 group content, and everything that refers to it — a kind-9 message, a kind-1111 comment,
        // a like, a zap request — exists in a room on a host relay and nowhere else. The room's members
        // read it there; the author's outbox and the broadcast list can neither serve it to them nor do
        // anything else useful with it, and for a private or closed group publishing it there advertises
        // who is in which room. So the host wins outright rather than being one more relay in the union.
        // Same rule the group reply composer already applies (CommentPostViewModel), applied to every
        // group-scoped event instead of just that one path.
        val groupHosts = account.cache.relayGroupHostsFor(event)
        if (groupHosts.isNotEmpty()) return groupHosts

        val includeBroadcast = wantsBroadcastRelays(event)
        val broadcastRelays = if (includeBroadcast) account.broadcastRelayList.flow.value else emptySet()

        if (event is MetadataEvent || event is AdvertisedRelayListEvent) {
            // everywhere
            return account.followPlusAllMineWithIndex.flow.value + account.client.availableRelaysFlow().value + broadcastRelays
        }

        val relayList = mutableSetOf<NormalizedRelayUrl>()
        relayList.addAll(broadcastRelays)

        val author = account.cache.getUserIfExists(event.pubKey)

        if (author != null) {
            if (author == account.userProfile()) {
                if (includeBroadcast) {
                    relayList.addAll(account.outboxRelays.flow.value)
                } else {
                    // account.outboxRelays mixes in the broadcast list; for personal/channel events
                    // we want the user's NIP-65 / private / local outbox without it.
                    relayList.addAll(account.nip65RelayList.outboxFlow.value)
                    relayList.addAll(account.privateStorageRelayList.flow.value)
                    relayList.addAll(account.localRelayList.flow.value)
                }
            } else {
                val relays =
                    author.outboxRelays()?.ifEmpty { null }
                        ?: author.allUsedRelaysOrNull()
                        ?: account.cache.relayHints.hintsForKey(author.pubkeyHex)

                relayList.addAll(relays)
            }
        } else {
            relayList.addAll(account.cache.relayHints.hintsForKey(event.pubKey))
        }

        if (event is PubKeyHintProvider) {
            event.pubKeyHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedPubKeys().forEach { pubkey ->
                relayList.addAll(computeRelayListForLinkedUser(pubkey))
            }
        }

        if (event is EventHintProvider) {
            event.eventHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedEventIds().forEach { eventId ->
                account.cache.getNoteIfExists(eventId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        if (event is AddressHintProvider) {
            event.addressHints().forEach {
                relayList.add(it.relay)
            }
            event.linkedAddressIds().forEach { addressId ->
                account.cache.getAddressableNoteIfExists(addressId)?.let { linkedNote ->
                    val linkedNoteAuthor = linkedNote.author

                    if (linkedNoteAuthor != null) {
                        relayList.addAll(computeRelayListForLinkedUser(linkedNoteAuthor))
                    } else {
                        relayList.addAll(linkedNote.relays.toSet())
                    }

                    linkedNote.event?.let { linkedEvent ->
                        relayList.addAll(computeRelayListToBroadcast(linkedEvent, visited))
                    }
                }
            }
        }

        if (event is PollEvent) {
            relayList.addAll(event.relays())
        }

        if (event is MeetingSpaceEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is MeetingRoomEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        if (event is LiveActivitiesEvent) {
            relayList.addAll(event.allRelayUrls())
        }

        relayList.addAll(computeRelaysForChannels(event))

        return relayList
    }

    fun computeRelayListToBroadcast(note: Note): Set<NormalizedRelayUrl> {
        val noteEvent = note.event
        return if (noteEvent != null) {
            computeRelayListToBroadcast(noteEvent)
        } else {
            note.relays.toSet()
        }
    }

    suspend fun broadcast(note: Note) {
        note.event?.let { noteEvent ->
            val host = note.rumorHost
            if (host != null) {
                // Rumors are rebroadcast as their delivering envelope: the
                // cached copy is content-stripped, so download it and send it.
                // A just-sent note has no relays until its self-wrap echoes
                // back — fall back to our own DM inbox relays. Bare seals
                // (kind 13) carry no p tag, so that filter is wrap-only.
                val relays =
                    note.relays.ifEmpty {
                        account.dmRelays.flow.value
                            .toList()
                    }
                val filter =
                    if (host.kind == SealedRumorEvent.KIND) {
                        Filter(
                            kinds = listOf(host.kind),
                            ids = listOf(host.id),
                        )
                    } else {
                        Filter(
                            kinds = listOf(host.kind),
                            tags = mapOf("p" to listOf(account.pubKey)),
                            ids = listOf(host.id),
                        )
                    }
                account.client
                    .fetchFirst(
                        filters = relays.associateWith { _ -> listOf(filter) },
                    )?.let { downloadedEvent ->
                        val toRelays = computeRelayListToBroadcast(downloadedEvent)
                        account.client.publish(downloadedEvent, toRelays)
                    }
            } else if (noteEvent.sig.isEmpty()) {
                // Rumor with no known wrap: publishing it would disclose the
                // private content to relays even though they reject the
                // missing signature.
                return
            } else {
                account.client.publish(noteEvent, computeRelayListToBroadcast(note))
            }
        }
    }

    fun sendAutomatic(events: List<Event>) = events.forEach { sendAutomatic(it) }

    fun sendAutomatic(event: Event?) {
        if (event == null) return
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, computeRelayListToBroadcast(event))
    }

    fun sendMyPublicAndPrivateOutbox(event: Event?) {
        if (event == null) return
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, account.outboxRelays.flow.value)
    }

    fun sendMyPublicAndPrivateOutbox(events: List<Event>) {
        events.forEach {
            account.client.publish(it, account.outboxRelays.flow.value)
            account.cache.justConsumeMyOwnEvent(it)
        }
    }

    fun sendLiterallyEverywhere(event: Event) {
        account.client.publish(event, account.followPlusAllMineWithIndex.flow.value + account.client.availableRelaysFlow().value)
        account.cache.justConsumeMyOwnEvent(event)
    }

    suspend fun <T : Event> signAndSendPrivately(
        template: EventTemplate<T>,
        relayList: Set<NormalizedRelayUrl>,
    ) {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        account.client.publish(event, relayList)
    }

    /**
     * Sign [template] with an arbitrary [signer] (e.g. a per-geohash ephemeral
     * identity that is deliberately NOT this account's key) and publish to exactly
     * [relayList]. Used by geohash location chat, where authorship inside a cell
     * must not be linkable to the user's npub.
     */
    suspend fun <T : Event> signWithAndSendPrivately(
        template: EventTemplate<T>,
        signer: NostrSigner,
        relayList: Set<NormalizedRelayUrl>,
    ): T {
        val event = signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        if (relayList.isNotEmpty()) account.client.publish(event, relayList)
        return event
    }

    suspend fun <T : Event> signAndSendPrivatelyOrBroadcast(
        template: EventTemplate<T>,
        relayList: (T) -> List<NormalizedRelayUrl>?,
    ): T {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        val relays = relayList(event)
        val targets =
            if (!relays.isNullOrEmpty()) {
                relays.toSet()
            } else {
                computeRelayListToBroadcast(event)
            }
        account.chatDeliveryTracker.trackPublic(event.id, targets)
        account.client.publish(event, targets)
        return event
    }

    suspend fun <T : Event> signAndComputeBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
    ): T {
        val event = account.signer.sign(template)
        account.cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                account.cache.getOrCreateAddressableNote(event.address())
            } else {
                account.cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        account.client.publish(event, relayList)

        broadcast.forEach { account.client.publish(it, relayList) }

        return event
    }

    suspend fun <T : Event> signAnonymouslyAndBroadcast(
        template: EventTemplate<T>,
        broadcast: List<Event> = emptyList(),
        anonymousSigner: NostrSigner = NostrSignerInternal(KeyPair()),
    ): T {
        val event = anonymousSigner.sign(template)

        account.cache.justConsumeMyOwnEvent(event)
        val note =
            if (event is AddressableEvent) {
                account.cache.getOrCreateAddressableNote(event.address())
            } else {
                account.cache.getOrCreateNote(event.id)
            }

        val relayList = computeRelayListToBroadcast(note)

        account.client.publish(event, relayList)

        broadcast.forEach { account.client.publish(it, relayList) }

        return event
    }

    fun republishEventsTo(
        events: List<Event>,
        relays: Set<NormalizedRelayUrl>,
    ) {
        if (relays.isEmpty() || events.isEmpty()) return
        events.forEach { account.client.publish(it, relays) }
    }
}
