package com.vitorpamplona.amethyst.model

import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap

@Stable
class PublicChatChannel(idHex: String) : Channel(idHex) {
    var info = ChannelCreateEvent.ChannelData(null, null, null)

    fun updateChannelInfo(creator: User, channelInfo: ChannelCreateEvent.ChannelData, updatedAt: Long) {
        this.creator = creator
        this.info = channelInfo
        this.updatedMetadataAt = updatedAt

        live.invalidateData()
    }

    override fun toBestDisplayName(): String {
        return info.name ?: super.toBestDisplayName()
    }

    override fun summary(): String? {
        return info.about
    }

    override fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) return super.profilePicture()
        return info.picture ?: super.profilePicture()
    }

    override fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(info.name, info.about)
            .filter { it.contains(prefix, true) }.isNotEmpty()
    }
}

@Stable
class LiveActivitiesChannel(val address: ATag) : Channel(address.toTag()) {
    var info: LiveActivitiesEvent? = null

    override fun idNote() = address.toNAddr()
    override fun idDisplayNote() = idNote().toShortenHex()
    fun address() = address

    fun updateChannelInfo(creator: User, channelInfo: LiveActivitiesEvent, updatedAt: Long) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
    }

    override fun toBestDisplayName(): String {
        return info?.title() ?: super.toBestDisplayName()
    }

    override fun summary(): String? {
        return info?.summary()
    }

    override fun profilePicture(): String? {
        return info?.image()?.ifBlank { null }
    }

    override fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(info?.title(), info?.summary())
            .filter { it.contains(prefix, true) }.isNotEmpty()
    }
}

@Stable
abstract class Channel(val idHex: String) {
    var creator: User? = null

    var updatedMetadataAt: Long = 0

    val notes = ConcurrentHashMap<HexKey, Note>()

    open fun id() = Hex.decode(idHex)
    open fun idNote() = id().toNote()
    open fun idDisplayNote() = idNote().toShortenHex()

    open fun toBestDisplayName(): String {
        return idDisplayNote()
    }

    open fun summary(): String? {
        return null
    }

    open fun creatorName(): String? {
        return creator?.toBestDisplayName()
    }

    open fun profilePicture(): String? {
        return creator?.profilePicture()
    }

    open fun updateChannelInfo(creator: User, updatedAt: Long) {
        this.creator = creator
        this.updatedMetadataAt = updatedAt

        live.invalidateData()
    }

    fun addNote(note: Note) {
        notes[note.idHex] = note
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
        val important = notes.values
            .filter { it.author?.let { it1 -> account.isHidden(it1) } == false }
            .sortedWith(compareBy({ it.createdAt() }, { it.idHex }))
            .reversed()
            .take(1000)
            .toSet()

        val toBeRemoved = notes.values.filter { it !in important }.toSet()

        toBeRemoved.forEach {
            notes.remove(it.idHex)
        }

        return toBeRemoved
    }
}

class ChannelLiveData(val channel: Channel) : LiveData<ChannelState>(ChannelState(channel)) {
    // Refreshes observers in batches.
    private val bundler = BundledUpdate(300, Dispatchers.IO)

    fun invalidateData() {
        checkNotInMainThread()

        bundler.invalidate() {
            checkNotInMainThread()
            if (hasActiveObservers()) {
                postValue(ChannelState(channel))
            }
        }
    }

    override fun onActive() {
        super.onActive()
        if (channel is PublicChatChannel) {
            NostrSingleChannelDataSource.add(channel.idHex)
        } else {
            NostrSingleChannelDataSource.add(channel.idHex)
        }
    }

    override fun onInactive() {
        super.onInactive()
        if (channel is PublicChatChannel) {
            NostrSingleChannelDataSource.remove(channel.idHex)
        } else {
            NostrSingleChannelDataSource.remove(channel.idHex)
        }
    }
}

class ChannelState(val channel: Channel)
