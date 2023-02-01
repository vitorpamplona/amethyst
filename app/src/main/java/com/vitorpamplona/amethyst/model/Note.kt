package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.NostrSingleEventDataSource
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
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
import com.vitorpamplona.amethyst.service.relays.Relay
import java.util.regex.Matcher
import java.util.regex.Pattern
import nostr.postr.events.Event

val tagSearch = Pattern.compile("(?:\\s|\\A)\\#\\[([0-9]+)\\]")

class Note(val idHex: String) {
    // These fields are always available.
    // They are immutable
    val id = Hex.decode(idHex)
    val idDisplayNote = id.toNote().toShortenHex()

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
    val reports = Collections.synchronizedSet(mutableSetOf<Note>())

    var channel: Channel? = null

    fun loadEvent(event: Event, author: User, mentions: List<User>, replyTo: MutableList<Note>) {
        this.event = event
        this.author = author
        this.mentions = mentions
        this.replyTo = replyTo

        invalidateData(live)
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
            invalidateData(liveReplies)
    }

    fun addBoost(note: Note) {
        if (boosts.add(note))
            invalidateData(liveBoosts)
    }

    fun addReaction(note: Note) {
        if (reactions.add(note))
            invalidateData(liveReactions)
    }

    fun addReport(note: Note) {
        if (reports.add(note))
            invalidateData(liveReports)
    }

    fun isReactedBy(user: User): Boolean {
        return synchronized(reactions) {
            reactions.any { it.author == user }
        }
    }

    fun isBoostedBy(user: User): Boolean {
        return synchronized(boosts) {
            boosts.any { it.author == user }
        }
    }

    fun reportsBy(user: User): List<Note> {
        return synchronized(reports) {
            reports.filter { it.author == user }
        }
    }

    fun reportsBy(users: Set<User>): List<Note> {
        return synchronized(reports) {
            reports.filter { it.author in users }
        }
    }

    fun directlyCiteUsers(): Set<User> {
        val matcher = tagSearch.matcher(event?.content ?: "")
        val returningList = mutableSetOf<User>()
        while (matcher.find()) {
            try {
                val tag = event?.tags?.get(matcher.group(1).toInt())
                if (tag != null && tag[0] == "p") {
                    returningList.add(LocalCache.getOrCreateUser(tag[1]))
                }
            } catch (e: Exception) {

            }
        }
        return returningList
    }

    fun directlyCites(userProfile: User): Boolean {
        return author == userProfile
          || (userProfile in directlyCiteUsers())
          || (event is ReactionEvent && replyTo?.lastOrNull()?.directlyCites(userProfile) == true)
          || (event is RepostEvent && replyTo?.lastOrNull()?.directlyCites(userProfile) == true)
    }

    // Observers line up here.
    val live: NoteLiveData = NoteLiveData(this)

    val liveReactions: NoteLiveData = NoteLiveData(this)
    val liveBoosts: NoteLiveData = NoteLiveData(this)
    val liveReplies: NoteLiveData = NoteLiveData(this)
    val liveReports: NoteLiveData = NoteLiveData(this)

    // Refreshes observers in batches.
    var handlerWaiting = false
    @Synchronized
    fun invalidateData(live: NoteLiveData) {
        if (handlerWaiting) return

        handlerWaiting = true
        val scope = CoroutineScope(Job() + Dispatchers.Default)
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
