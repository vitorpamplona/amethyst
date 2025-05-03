/**
 * Copyright (c) 2024 Vitor Pamplona
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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHint
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.toNEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelData
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class EphemeralChatChannel(
    val roomId: RoomId,
) : Channel(roomId.toKey()) {
    override fun idNote() = roomId.toDisplayKey()

    override fun idDisplayNote() = idNote().toShortenHex()

    override fun relays() = listOf(roomId.relayUrl)

    override fun toBestDisplayName() = roomId.toDisplayKey()

    override fun summary(): String? = null

    override fun profilePicture(): String? = null

    override fun anyNameStartsWith(prefix: String): Boolean = roomId.id.contains(prefix, true)
}

@Stable
class PublicChatChannel(
    idHex: String,
) : Channel(idHex) {
    var event: ChannelCreateEvent? = null
    var infoTags = EmptyTagList
    var info = ChannelData(null, null, null, null)

    override fun relays() = info.relays ?: super.relays()

    fun toNEvent() = NEvent.create(idHex, event?.pubKey, ChannelCreateEvent.KIND, *relays().toTypedArray())

    fun toNostrUri() = "nostr:${toNEvent()}"

    fun toEventHint() = event?.let { EventHintBundle<ChannelCreateEvent>(it, relays().firstOrNull(), null) }

    fun toEventId() = EventIdHint(idHex, relays().firstOrNull())

    fun updateChannelInfo(
        creator: User,
        event: ChannelCreateEvent,
    ) {
        this.event = event
        this.infoTags = event.tags.toImmutableListOfLists()
        updateChannelInfo(creator, event.channelInfo(), event.createdAt)
    }

    fun updateChannelInfo(
        creator: User,
        event: ChannelMetadataEvent,
    ) {
        this.infoTags = event.tags.toImmutableListOfLists()
        updateChannelInfo(creator, event.channelInfo(), event.createdAt)
    }

    fun updateChannelInfo(
        creator: User,
        channelInfo: ChannelData,
        updatedAt: Long,
    ) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
    }

    override fun toBestDisplayName(): String = info.name ?: super.toBestDisplayName()

    override fun summary(): String? = info.about

    override fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) return super.profilePicture()
        return info.picture ?: super.profilePicture()
    }

    override fun anyNameStartsWith(prefix: String): Boolean = listOfNotNull(info.name, info.about).any { it.contains(prefix, true) }
}

@Stable
class LiveActivitiesChannel(
    val address: Address,
) : Channel(address.toValue()) {
    var info: LiveActivitiesEvent? = null

    override fun idNote() = toNAddr()

    override fun idDisplayNote() = idNote().toShortenHex()

    fun address() = address

    override fun relays() = info?.allRelayUrls() ?: super.relays()

    fun relayHintUrl() = relays().firstOrNull()

    fun updateChannelInfo(
        creator: User,
        channelInfo: LiveActivitiesEvent,
        updatedAt: Long,
    ) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
    }

    override fun toBestDisplayName(): String = info?.title() ?: super.toBestDisplayName()

    override fun summary(): String? = info?.summary()

    override fun profilePicture(): String? = info?.image()?.ifBlank { null }

    override fun anyNameStartsWith(prefix: String): Boolean =
        listOfNotNull(info?.title(), info?.summary())
            .filter { it.contains(prefix, true) }
            .isNotEmpty()

    fun toNAddr() = NAddress.create(address.kind, address.pubKeyHex, address.dTag, *relays().toTypedArray())

    fun toATag() = ATag(address, relayHintUrl())

    fun toNostrUri() = "nostr:${toNAddr()}"
}

data class Counter(
    var number: Int = 0,
)

@Stable
abstract class Channel(
    val idHex: String,
) {
    var creator: User? = null
    var updatedMetadataAt: Long = 0
    val notes = LargeCache<HexKey, Note>()
    var lastNoteCreatedAt: Long = 0
    private var relays = mapOf<RelayBriefInfoCache.RelayBriefInfo, Counter>()

    open fun idNote() = Hex.decode(idHex).toNEvent()

    open fun idDisplayNote() = idNote().toShortenHex()

    open fun toBestDisplayName(): String = idDisplayNote()

    open fun summary(): String? = null

    open fun creatorName(): String? = creator?.toBestDisplayName()

    open fun profilePicture(): String? = creator?.info?.banner

    open fun relays() =
        relays.keys
            .toSortedSet { o1, o2 ->
                val o1Count = relays[o1]?.number ?: 0
                val o2Count = relays[o2]?.number ?: 0
                o2Count.compareTo(o1Count) // descending
            }.map { it.url }

    open fun updateChannelInfo(
        creator: User,
        updatedAt: Long,
    ) {
        this.creator = creator
        this.updatedMetadataAt = updatedAt

        flowSet?.metadata?.invalidateData()
    }

    @Synchronized
    fun addRelaySync(briefInfo: RelayBriefInfoCache.RelayBriefInfo) {
        if (briefInfo !in relays) {
            relays = relays + Pair(briefInfo, Counter(1))
        }
    }

    fun addRelay(relay: RelayBriefInfoCache.RelayBriefInfo) {
        val counter = relays[relay]
        if (counter != null) {
            counter.number++
        } else {
            addRelaySync(relay)
        }
    }

    fun addNote(
        note: Note,
        relay: RelayBriefInfoCache.RelayBriefInfo? = null,
    ) {
        notes.put(note.idHex, note)

        if ((note.createdAt() ?: 0) > lastNoteCreatedAt) {
            lastNoteCreatedAt = note.createdAt() ?: 0
        }

        if (relay != null) {
            addRelay(relay)
        }

        flowSet?.notes?.invalidateData()
    }

    fun removeNote(note: Note) {
        notes.remove(note.idHex)
    }

    fun removeNote(noteHex: String) {
        notes.remove(noteHex)
    }

    abstract fun anyNameStartsWith(prefix: String): Boolean

    fun pruneOldMessages(): Set<Note> {
        val important =
            notes
                .values()
                .sortedWith(DefaultFeedOrder)
                .take(500)
                .toSet()

        val toBeRemoved = notes.filter { key, it -> it !in important }

        toBeRemoved.forEach { notes.remove(it.idHex) }

        flowSet?.notes?.invalidateData()

        return toBeRemoved.toSet()
    }

    fun pruneHiddenMessages(account: Account): Set<Note> {
        val hidden =
            notes
                .filter { key, it ->
                    it.author?.let { author -> account.isHidden(author) } == true
                }.toSet()

        hidden.forEach { notes.remove(it.idHex) }

        flowSet?.notes?.invalidateData()

        return hidden.toSet()
    }

    var flowSet: ChannelFlowSet? = null

    @Synchronized
    fun createOrDestroyFlowSync(create: Boolean) {
        if (create) {
            if (flowSet == null) {
                flowSet = ChannelFlowSet(this)
            }
        } else {
            if (flowSet != null && flowSet?.isInUse() == false) {
                flowSet?.destroy()
                flowSet = null
            }
        }
    }

    fun flow(): ChannelFlowSet {
        if (flowSet == null) {
            createOrDestroyFlowSync(true)
        }
        return flowSet!!
    }

    fun clearFlow() {
        if (flowSet != null && flowSet?.isInUse() == false) {
            createOrDestroyFlowSync(false)
        }
    }
}

@Stable
class ChannelFlowSet(
    u: Channel,
) {
    // Observers line up here.
    val metadata = ChannelFlow(u)
    val notes = ChannelFlow(u)

    fun isInUse(): Boolean =
        metadata.hasObservers() ||
            notes.hasObservers()

    fun destroy() {
        metadata.destroy()
        notes.destroy()
    }
}

class ChannelFlow(
    val channel: Channel,
) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)
    val stateFlow = MutableStateFlow(ChannelState(channel))

    fun invalidateData() {
        bundler.invalidate {
            stateFlow.emit(ChannelState(channel))
        }
    }

    fun destroy() {
        bundler.cancel()
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

class ChannelState(
    val channel: Channel,
)
