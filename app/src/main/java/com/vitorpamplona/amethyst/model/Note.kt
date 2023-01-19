package com.vitorpamplona.amethyst.model

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.ui.note.toShortenHex
import fr.acinq.secp256k1.Hex
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nostr.postr.events.Event

class Note(val idHex: String) {
    // These fields are always available.
    // They are immutable
    val id = Hex.decode(idHex)
    val idDisplayHex = id.toShortenHex()

    // These fields are only available after the Text Note event is received.
    // They are immutable after that.
    var event: Event? = null
    var author: User? = null
    var mentions: List<User>? = null
    var replyTo: MutableList<Note>? = null

    // These fields are updated every time an event related to this note is received.
    val replies = Collections.synchronizedSet(mutableSetOf<Note>())
    val reactions = Collections.synchronizedSet(mutableSetOf<Note>())
    val boosts = Collections.synchronizedSet(mutableSetOf<Note>())

    var channel: Channel? = null

    fun loadEvent(event: Event, author: User, mentions: List<User>, replyTo: MutableList<Note>) {
        this.event = event
        this.author = author
        this.mentions = mentions
        this.replyTo = replyTo

        invalidateData()
    }

    fun formattedDateTime(timestamp: Long): String {
        return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HH:mm:ss"))
    }

    fun replyLevelSignature(): String {
        val replyTo = replyTo
        if (replyTo == null || replyTo.isEmpty()) {
            return "/" + formattedDateTime(event?.createdAt ?: 0) + ";"
        }

        return replyTo
            .map { it.replyLevelSignature() }
            .maxBy { it.length }.removeSuffix(";") + "/" + formattedDateTime(event?.createdAt ?: 0) + ";"
    }

    fun replyLevel(): Int {
        val replyTo = replyTo
        if (replyTo == null || replyTo.isEmpty()) {
            return 0
        }

        return replyTo.maxOf {
            it.replyLevel()
        } + 1
    }

    fun addReply(note: Note) {
        if (replies.add(note))
            invalidateData()
    }

    fun addBoost(note: Note) {
        if (boosts.add(note))
            invalidateData()
    }

    fun addReaction(note: Note) {
        if (reactions.add(note))
            invalidateData()
    }

    fun isReactedBy(user: User): Boolean {
        return reactions.any { it.author == user }
    }

    fun isBoostedBy(user: User): Boolean {
        return boosts.any { it.author == user }
    }

    // Observers line up here.
    val live: NoteLiveData = NoteLiveData(this)

    // Refreshes observers in batches.
    var handlerWaiting = false
    @Synchronized
    fun invalidateData() {
        if (handlerWaiting) return

        handlerWaiting = true
        val scope = CoroutineScope(Job() + Dispatchers.Main)
        scope.launch {
            delay(100)
            live.refresh()
            handlerWaiting = false
        }
    }
}

class NoteLiveData(val note: Note): LiveData<NoteState>(NoteState(note)) {
    fun refresh() {
        postValue(NoteState(note))
    }

    override fun onActive() {
        super.onActive()
        NostrSingleEventDataSource.add(note.idHex)
    }

    override fun onInactive() {
        super.onInactive()
        NostrSingleEventDataSource.remove(note.idHex)
    }
}

class NoteState(val note: Note)
