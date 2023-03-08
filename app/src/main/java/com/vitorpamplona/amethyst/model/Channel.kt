package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleChannelDataSource
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import java.util.concurrent.ConcurrentHashMap

class Channel(val idHex: String) {
    var creator: User? = null
    var info = ChannelCreateEvent.ChannelData(null, null, null)

    var updatedMetadataAt: Long = 0

    val notes = ConcurrentHashMap<HexKey, Note>()

    fun id() = Hex.decode(idHex)
    fun idNote() = id().toNote()
    fun idDisplayNote() = idNote().toShortenHex()

    fun toBestDisplayName(): String {
        return info.name ?: idDisplayNote()
    }

    fun addNote(note: Note) {
        notes[note.idHex] = note
    }

    fun updateChannelInfo(creator: User, channelInfo: ChannelCreateEvent.ChannelData, updatedAt: Long) {
        this.creator = creator
        this.info = channelInfo
        this.updatedMetadataAt = updatedAt

        live.refresh()
    }

    fun profilePicture(): String? {
        if (info.picture.isNullOrBlank()) info.picture = null
        return info.picture
    }

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(info.name, info.about)
            .filter { it.startsWith(prefix, true) }.isNotEmpty()
    }

    // Observers line up here.
    val live: ChannelLiveData = ChannelLiveData(this)

    fun pruneOldAndHiddenMessages(account: Account): Set<Note> {
        val important = notes.values
            .filter { it.author?.let { it1 -> account.isHidden(it1) } == false }
            .sortedBy { it.createdAt() }
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
    fun refresh() {
        postValue(ChannelState(channel))
    }

    override fun onActive() {
        super.onActive()
        NostrSingleChannelDataSource.add(channel.idHex)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleChannelDataSource.remove(channel.idHex)
    }
}

class ChannelState(val channel: Channel)
