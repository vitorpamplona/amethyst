package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleUserDataSource
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import nostr.postr.events.ContactListEvent

class Channel(val id: ByteArray) {
    val idHex = id.toHexKey()
    val idDisplayHex = id.toShortenHex()

    var creator: User? = null
    var info = ChannelCreateEvent.ChannelData(null, null, null)

    var updatedMetadataAt: Long = 0;

    val notes = ConcurrentHashMap<HexKey, Note>()

    @Synchronized
    fun getOrCreateNote(idHex: String): Note {
        return notes[idHex] ?: run {
            val answer = Note(idHex)
            notes.put(idHex, answer)
            answer
        }
    }

    fun updateChannelInfo(creator: User, channelInfo: ChannelCreateEvent.ChannelData, updatedAt: Long) {
        this.creator = creator
        this.info = channelInfo
        this.updatedMetadataAt = updatedAt

        live.refresh()
    }

    fun profilePicture(): String {
        if (info.picture.isNullOrBlank()) info.picture = null
        return info.picture ?: "https://robohash.org/${idHex}.png"
    }

    fun anyNameStartsWith(prefix: String): Boolean {
        return listOfNotNull(info.name, info.about)
            .filter { it.startsWith(prefix, true) }.isNotEmpty()
    }

    // Observers line up here.
    val live: ChannelLiveData = ChannelLiveData(this)

    private fun refreshObservers() {
        live.refresh()
    }
}


class ChannelLiveData(val channel: Channel): LiveData<ChannelState>(ChannelState(channel)) {
    fun refresh() {
        postValue(ChannelState(channel))
    }
}

class ChannelState(val channel: Channel)
