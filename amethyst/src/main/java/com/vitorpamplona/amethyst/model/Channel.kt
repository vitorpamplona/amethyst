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
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.commons.data.LargeCache
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.ammolite.relays.BundledUpdate
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.quartz.nip01Core.HexKey
import com.vitorpamplona.quartz.nip01Core.tags.addressables.ATag
import com.vitorpamplona.quartz.nip19Bech32.toNAddr
import com.vitorpamplona.quartz.nip19Bech32.toNEvent
import com.vitorpamplona.quartz.nip28PublicChat.admin.ChannelCreateEvent
import com.vitorpamplona.quartz.nip28PublicChat.base.ChannelData
import com.vitorpamplona.quartz.nip53LiveActivities.streaming.LiveActivitiesEvent
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.Dispatchers

@Stable
class PublicChatChannel(
    idHex: String,
) : Channel(idHex) {
    var event: ChannelCreateEvent? = null
    var info = ChannelData(null, null, null, null)

    override fun relays() = info.relays ?: super.relays()

    fun updateChannelInfo(
        creator: User,
        channelInfo: ChannelCreateEvent,
        updatedAt: Long,
    ) {
        this.event = channelInfo
        updateChannelInfo(creator, channelInfo.channelInfo(), updatedAt)
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

    override fun anyNameStartsWith(prefix: String): Boolean = listOfNotNull(info.name, info.about).filter { it.contains(prefix, true) }.isNotEmpty()
}

@Stable
class LiveActivitiesChannel(
    val address: ATag,
) : Channel(address.toTag()) {
    var info: LiveActivitiesEvent? = null

    override fun idNote() = address.toNAddr()

    override fun idDisplayNote() = idNote().toShortenHex()

    fun address() = address

    override fun relays() = info?.allRelayUrls() ?: super.relays()

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

    open fun id() = Hex.decode(idHex)

    open fun idNote() = id().toNEvent()

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

        live.invalidateData()
    }

    @Synchronized
    fun addRelaySync(briefInfo: RelayBriefInfoCache.RelayBriefInfo) {
        if (briefInfo !in relays) {
            relays = relays + Pair(briefInfo, Counter(1))
        }
    }

    fun addNote(
        note: Note,
        relay: Relay? = null,
    ) {
        notes.put(note.idHex, note)

        if ((note.createdAt() ?: 0) > lastNoteCreatedAt) {
            lastNoteCreatedAt = note.createdAt() ?: 0
        }

        if (relay != null) {
            val counter = relays[relay.brief]
            if (counter != null) {
                counter.number++
            } else {
                addRelaySync(relay.brief)
            }
        }
    }

    fun removeNote(note: Note) {
        notes.remove(note.idHex)
    }

    fun removeNote(noteHex: String) {
        notes.remove(noteHex)
    }

    abstract fun anyNameStartsWith(prefix: String): Boolean

    // Observers line up here.
    val live: ChannelLiveData = ChannelLiveData(this)

    fun pruneOldAndHiddenMessages(account: Account): Set<Note> {
        val important =
            notes
                .filter { key, it ->
                    it.author?.let { author -> account.isHidden(author) } == false
                }.sortedWith(DefaultFeedOrder)
                .take(500)
                .toSet()

        val toBeRemoved = notes.filter { key, it -> it !in important }

        toBeRemoved.forEach { notes.remove(it.idHex) }

        return toBeRemoved.toSet()
    }
}

class ChannelLiveData(
    val channel: Channel,
) : LiveData<ChannelState>(ChannelState(channel)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate {
            checkNotInMainThread()
            if (hasActiveObservers()) {
                postValue(ChannelState(channel))
            }
        }
    }

    override fun onActive() {
        super.onActive()
        NostrSingleChannelDataSource.add(channel)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleChannelDataSource.remove(channel)
    }
}

class ChannelState(
    val channel: Channel,
)
