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
import com.vitorpamplona.amethyst.ui.components.BundledUpdate
import com.vitorpamplona.amethyst.ui.dal.DefaultFeedOrder
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.encoders.Hex
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.toNote
import com.vitorpamplona.quartz.events.ChannelCreateEvent
import com.vitorpamplona.quartz.events.LiveActivitiesEvent
import kotlinx.coroutines.Dispatchers

@Stable
class PublicChatChannel(idHex: String) : Channel(idHex) {
    var info = ChannelCreateEvent.ChannelData(null, null, null)

    fun updateChannelInfo(
        creator: User,
        channelInfo: ChannelCreateEvent.ChannelData,
        updatedAt: Long,
    ) {
        this.info = channelInfo
        super.updateChannelInfo(creator, updatedAt)
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
        return listOfNotNull(info.name, info.about).filter { it.contains(prefix, true) }.isNotEmpty()
    }
}

@Stable
class LiveActivitiesChannel(val address: ATag) : Channel(address.toTag()) {
    var info: LiveActivitiesEvent? = null

    override fun idNote() = address.toNAddr()

    override fun idDisplayNote() = idNote().toShortenHex()

    fun address() = address

    fun updateChannelInfo(
        creator: User,
        channelInfo: LiveActivitiesEvent,
        updatedAt: Long,
    ) {
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
            .filter { it.contains(prefix, true) }
            .isNotEmpty()
    }
}

@Stable
abstract class Channel(val idHex: String) {
    var creator: User? = null
    var updatedMetadataAt: Long = 0
    val notes = LargeCache<HexKey, Note>()
    var lastNoteCreatedAt: Long = 0

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
        return creator?.info?.banner
    }

    open fun updateChannelInfo(
        creator: User,
        updatedAt: Long,
    ) {
        this.creator = creator
        this.updatedMetadataAt = updatedAt

        live.invalidateData()
    }

    fun addNote(note: Note) {
        notes.put(note.idHex, note)

        if ((note.createdAt() ?: 0) > lastNoteCreatedAt) {
            lastNoteCreatedAt = note.createdAt() ?: 0
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
            notes.filter { key, it ->
                it.author?.let { author -> account.isHidden(author) } == false
            }
                .sortedWith(DefaultFeedOrder)
                .take(500)
                .toSet()

        val toBeRemoved = notes.filter { key, it -> it !in important }

        toBeRemoved.forEach { notes.remove(it.idHex) }

        return toBeRemoved.toSet()
    }
}

class ChannelLiveData(val channel: Channel) : LiveData<ChannelState>(ChannelState(channel)) {
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

class ChannelState(val channel: Channel)
