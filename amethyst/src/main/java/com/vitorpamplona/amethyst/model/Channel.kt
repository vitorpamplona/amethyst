/**
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

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.note.toShortDisplay
import com.vitorpamplona.quartz.experimental.ephemChat.chat.RoomId
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.hints.types.EventIdHintOptional
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address
import com.vitorpamplona.quartz.nip02FollowList.EmptyTagList
import com.vitorpamplona.quartz.nip02FollowList.toImmutableListOfLists
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelMetadataEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelDataNorm
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.LargeCache
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class EphemeralChatChannel(
    val roomId: RoomId,
) : Channel() {
    override fun relays() = setOf(roomId.relayUrl)

    override fun toBestDisplayName() = roomId.toDisplayKey()

    override fun summary(): String? = null

    override fun profilePicture(): String? = null

    override fun anyNameStartsWith(prefix: String): Boolean = roomId.id.contains(prefix, true)
}

@Stable
class PublicChatChannel(
    val idHex: String,
) : Channel() {
    var event: ChannelCreateEvent? = null
    var infoTags = EmptyTagList
    var info = ChannelDataNorm(null, null, null, null)

    override fun relays() = info.relays?.toSet() ?: super.relays()

    fun relayHintUrls() = relays().take(3)

    fun relayHintUrl() = relays().firstOrNull()

    fun toNEvent() = NEvent.create(idHex, event?.pubKey, ChannelCreateEvent.KIND, relayHintUrls())

    fun toNostrUri() = "nostr:${toNEvent()}"

    fun toEventHint() = event?.let { EventHintBundle(it, relayHintUrl(), null) }

    fun toEventId() = EventIdHintOptional(idHex, relayHintUrl())

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
        channelInfo: ChannelDataNorm,
        updatedAt: Long,
    ) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
    }

    override fun toBestDisplayName(): String = info.name ?: toNEvent().toShortDisplay()

    override fun summary(): String? = info.about

    override fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) return super.profilePicture()
        return info.picture ?: super.profilePicture()
    }

    override fun anyNameStartsWith(prefix: String): Boolean =
        idHex.startsWith(prefix) ||
            info.name?.contains(prefix, true) == true ||
            info.about?.contains(prefix, true) == true
}

@Stable
class LiveActivitiesChannel(
    val address: Address,
) : Channel() {
    var info: LiveActivitiesEvent? = null

    fun address() = address

    override fun relays() = info?.allRelayUrls()?.toSet() ?: super.relays()

    fun relayHintUrl() = relays().firstOrNull()

    fun relayHintUrls() = relays().take(3)

    fun updateChannelInfo(
        creator: User,
        channelInfo: LiveActivitiesEvent,
        updatedAt: Long,
    ) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
    }

    override fun toBestDisplayName(): String = info?.title() ?: toNAddr().toShortDisplay()

    override fun summary(): String? = info?.summary()

    override fun profilePicture(): String? = info?.image()?.ifBlank { null }

    override fun anyNameStartsWith(prefix: String): Boolean =
        info?.title()?.contains(prefix, true) == true ||
            info?.summary()?.contains(prefix, true) == true

    fun toNAddr() = NAddress.create(address.kind, address.pubKeyHex, address.dTag, relayHintUrls())

    fun toATag() = ATag(address, relayHintUrl())

    fun toNostrUri() = "nostr:${toNAddr()}"
}

data class Counter(
    var number: Int = 0,
)

@Stable
abstract class Channel {
    var creator: User? = null
    var updatedMetadataAt: Long = 0
    val notes = LargeCache<HexKey, Note>()
    var lastNote: Note? = null

    private var relays = mapOf<NormalizedRelayUrl, Counter>()

    abstract fun toBestDisplayName(): String

    open fun summary(): String? = null

    open fun creatorName(): String? = creator?.toBestDisplayName()

    open fun profilePicture(): String? = creator?.info?.banner

    open fun relays(): Set<NormalizedRelayUrl> =
        relays.keys
            .toSortedSet { o1, o2 ->
                val o1Count = relays[o1]?.number ?: 0
                val o2Count = relays[o2]?.number ?: 0
                o2Count.compareTo(o1Count) // descending
            }

    open fun updateChannelInfo(
        creator: User,
        updatedAt: Long,
    ) {
        this.creator = creator
        this.updatedMetadataAt = updatedAt

        flowSet?.metadata?.invalidateData()
    }

    @Synchronized
    fun addRelaySync(briefInfo: NormalizedRelayUrl) {
        if (briefInfo !in relays) {
            relays = relays + Pair(briefInfo, Counter(1))
        }
    }

    fun addRelay(relay: NormalizedRelayUrl) {
        val counter = relays[relay]
        if (counter != null) {
            counter.number++
        } else {
            addRelaySync(relay)
        }
    }

    fun addNote(
        note: Note,
        relay: NormalizedRelayUrl? = null,
    ) {
        notes.put(note.idHex, note)

        if ((note.createdAt() ?: 0) > (lastNote?.createdAt() ?: 0)) {
            lastNote = note
        }

        if (relay != null) {
            addRelay(relay)
        }

        flowSet?.notes?.invalidateData()
    }

    fun removeNote(note: Note) {
        notes.remove(note.idHex)
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
}

class ChannelFlow(
    val channel: Channel,
) {
    val stateFlow = MutableStateFlow(ChannelState(channel))

    fun invalidateData() {
        stateFlow.tryEmit(ChannelState(channel))
    }

    fun hasObservers() = stateFlow.subscriptionCount.value > 0
}

class ChannelState(
    val channel: Channel,
)
