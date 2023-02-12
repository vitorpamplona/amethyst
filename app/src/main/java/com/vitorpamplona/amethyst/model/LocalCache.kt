package com.vitorpamplona.amethyst.model

import android.util.Log
import androidx.lifecycle.LiveData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.google.gson.reflect.TypeToken
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelHideMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ChannelMuteUserEvent
import com.vitorpamplona.amethyst.service.model.LnZapEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import com.vitorpamplona.amethyst.service.relays.Relay
import nostr.postr.events.ContactListEvent
import nostr.postr.events.DeletionEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.events.PrivateDmEvent
import nostr.postr.events.RecommendRelayEvent
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex
import nostr.postr.toNpub


object LocalCache {
  val metadataParser = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .readerFor(UserMetadata::class.java)

  val users = ConcurrentHashMap<HexKey, User>()
  val notes = ConcurrentHashMap<HexKey, Note>()
  val channels = ConcurrentHashMap<HexKey, Channel>()

  @Synchronized
  fun getOrCreateUser(key: HexKey): User {
    return users[key] ?: run {
      val answer = User(key)
      users.put(key, answer)
      answer
    }
  }

  @Synchronized
  fun getOrCreateNote(idHex: String): Note {
    return notes[idHex] ?: run {
      val answer = Note(idHex)
      notes.put(idHex, answer)
      answer
    }
  }

  @Synchronized
  fun getOrCreateChannel(key: String): Channel {
    return channels[key] ?: run {
      val answer = Channel(key.toByteArray())
      channels.put(key, answer)
      answer
    }
  }


  fun consume(event: MetadataEvent) {
    // new event
    val oldUser = getOrCreateUser(event.pubKey.toHexKey())
    if (event.createdAt > oldUser.updatedMetadataAt) {
      val newUser = try {
        metadataParser.readValue<UserMetadata>(ByteArrayInputStream(event.content.toByteArray(Charsets.UTF_8)), UserMetadata::class.java)
      } catch (e: Exception) {
        e.printStackTrace()
        Log.w("MT", "Content Parse Error ${e.localizedMessage} ${event.content}")
        return
      }

      oldUser.updateUserInfo(newUser, event.createdAt)
      oldUser.latestMetadata = event

      //Log.d("MT", "New User Metadata ${oldUser.pubkeyDisplayHex} ${oldUser.toBestDisplayName()}")
    } else {
      //Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
    }
  }

  fun formattedDateTime(timestamp: Long): String {
    return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))
  }

  fun consume(event: TextNoteEvent, relay: Relay? = null) {
    val note = getOrCreateNote(event.id.toHex())

    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelay(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val mentions = event.mentions.map { getOrCreateUser(it) }
    val replyTo = event.replyTos.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, replyTo)

    //Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content?.take(100)} ${formattedDateTime(event.createdAt)}")

    // Prepares user's profile view.
    author.addNote(note)

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    replyTo.forEach {
      it.author?.addTaggedPost(note)
    }

    // Counts the replies
    replyTo.forEach {
      it.addReply(note)
    }

    refreshObservers()
  }

  fun consume(event: RecommendRelayEvent) {
    //Log.d("RR", event.toJson())
  }

  fun consume(event: ContactListEvent) {
    val user = getOrCreateUser(event.pubKey.toHexKey())

    if (event.createdAt > user.updatedFollowsAt) {
      //Log.d("CL", "AAA ${user.toBestDisplayName()} ${event.follows.size}")
      user.updateFollows(
        event.follows.map {
          try {
            val pubKey = decodePublicKey(it.pubKeyHex)
            getOrCreateUser(pubKey.toHexKey())
          } catch (e: Exception) {
            println("Could not parse Hex key: ${it.pubKeyHex}")
            println("UpdateFollows: " + event.toJson())
            e.printStackTrace()
            null
          }
        }.filterNotNull().toSet(),
        event.createdAt
      )

      try {
        if (event.content.isNotEmpty()) {
          val relays: Map<String, ContactListEvent.ReadWrite> =
            Event.gson.fromJson(
              event.content,
              object : TypeToken<Map<String, ContactListEvent.ReadWrite>>() {}.type
            )

          user.updateRelays(relays)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }

      user.latestContactList = event
    }
  }

  fun consume(event: PrivateDmEvent, relay: Relay?) {
    val note = getOrCreateNote(event.id.toHex())
    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelay(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val recipient = event.recipientPubKey?.let { getOrCreateUser(it.toHexKey()) }

    //Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

    val repliesTo = event.tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }.map { getOrCreateNote(it) }
    val mentions = event.tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }.map { getOrCreateUser(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    if (recipient != null) {
      author.addMessage(recipient, note)
      recipient.addMessage(author, note)
    }

    refreshObservers()
  }

  fun consume(event: DeletionEvent) {
    //Log.d("DEL", event.toJson())
  }

  fun consume(event: RepostEvent) {
    val note = getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    //Log.d("TN", "New Boost (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

    val author = getOrCreateUser(event.pubKey.toHexKey())
    val mentions = event.originalAuthor.map { getOrCreateUser(it) }
    val repliesTo = event.boostedPost.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    // Prepares user's profile view.
    author.addNote(note)

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    repliesTo.forEach {
      it.author?.addTaggedPost(note)
    }

    // Counts the replies
    repliesTo.forEach {
      it.addBoost(note)
    }

    refreshObservers()
  }

  fun consume(event: ReactionEvent) {
    val note = getOrCreateNote(event.id.toHexKey())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey.toHexKey())
    val mentions = event.originalAuthor.map { getOrCreateUser(it) }
    val repliesTo = event.originalPost.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    //Log.d("RE", "New Reaction ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    repliesTo.forEach {
      it.author?.addTaggedPost(note)
    }

    if (
      event.content == "" ||
      event.content == "+" ||
      event.content == "\u2764\uFE0F" || // red heart
      event.content == "\uD83E\uDD19" || // call me hand
      event.content == "\uD83D\uDC4D" // thumbs up
    ) {
      // Counts the replies
      repliesTo.forEach {
        it.addReaction(note)
      }
    }

    if (event.content == "!" // nostr_console hide.
      || event.content == "\u26A0\uFE0F"  // Warning sign
    ) {
      // Counts the replies
      repliesTo.forEach {
        it.addReport(note)
      }
    }
  }

  fun consume(event: ReportEvent) {
    val note = getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey.toHexKey())
    val mentions = event.reportedAuthor.map { getOrCreateUser(it) }
    val repliesTo = event.reportedPost.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    //Log.d("RP", "New Report ${event.content} by ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")
    // Adds notifications to users.
    if (repliesTo.isEmpty()) {
      mentions.forEach {
        it.addReport(note)
      }
    }
    repliesTo.forEach {
      it.addReport(note)
    }
  }

  fun consume(event: ChannelCreateEvent) {
    //Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
    // new event
    val oldChannel = getOrCreateChannel(event.id.toHex())
    val author = getOrCreateUser(event.pubKey.toHexKey())
    if (event.createdAt > oldChannel.updatedMetadataAt) {
      if (oldChannel.creator == null || oldChannel.creator == author) {
        oldChannel.updateChannelInfo(author, event.channelInfo, event.createdAt)

        val note = getOrCreateNote(event.id.toHex())
        oldChannel.addNote(note)
        note.channel = oldChannel
        note.loadEvent(event, author, emptyList(), emptyList())

        refreshObservers()
      }
    } else {
      // older data, does nothing
    }
  }
  fun consume(event: ChannelMetadataEvent) {
    //Log.d("MT", "New User ${users.size} ${event.contactMetaData.name}")
    if (event.channel.isNullOrBlank()) return

    // new event
    val oldChannel = getOrCreateChannel(event.channel)
    val author = getOrCreateUser(event.pubKey.toHexKey())
    if (event.createdAt > oldChannel.updatedMetadataAt) {
      if (oldChannel.creator == null || oldChannel.creator == author) {
        oldChannel.updateChannelInfo(author, event.channelInfo, event.createdAt)

        val note = getOrCreateNote(event.id.toHex())
        oldChannel.addNote(note)
        note.channel = oldChannel
        note.loadEvent(event, author, emptyList(), emptyList())

        refreshObservers()
      }
    } else {
      //Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
    }
  }

  fun consume(event: ChannelMessageEvent, relay: Relay?) {
    if (event.channel.isNullOrBlank()) return

    val channel = getOrCreateChannel(event.channel)

    val note = getOrCreateNote(event.id.toHex())
    channel.addNote(note)

    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelay(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val mentions = event.mentions.map { getOrCreateUser(it) }
    val replyTo = event.replyTos
      .map { getOrCreateNote(it) }
      .filter { it.event !is ChannelCreateEvent }

    note.channel = channel
    note.loadEvent(event, author, mentions, replyTo)

    //Log.d("CM", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    replyTo.forEach {
      it.author?.addTaggedPost(note)
    }

    // Counts the replies
    replyTo.forEach {
      it.addReply(note)
    }

    refreshObservers()
  }

  fun consume(event: ChannelHideMessageEvent) {

  }

  fun consume(event: ChannelMuteUserEvent) {

  }

  fun consume(event: LnZapEvent) {
    val note = getOrCreateNote(event.id.toHexKey())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey.toHexKey())
    val mentions = event.zappedAuthor.map { getOrCreateUser(it) }
    val repliesTo = event.zappedPost.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    val zapRequest = event.containedPost?.id?.toHexKey()?.let { getOrCreateNote(it) }
    if (zapRequest == null) {
      Log.e("ZP","Zap Request not found. Unable to process Zap {${event.toJson()}}")
      return
    }

    Log.d("ZP", "New ZapEvent ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    repliesTo.forEach {
      it.author?.addTaggedPost(note)
    }

    repliesTo.forEach {
      it.addZap(zapRequest, note)
    }
    mentions.forEach {
      it.addZap(zapRequest, note)
    }
  }

  fun consume(event: LnZapRequestEvent) {
    val note = getOrCreateNote(event.id.toHexKey())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey.toHexKey())
    val mentions = event.zappedAuthor.map { getOrCreateUser(it) }
    val repliesTo = event.zappedPost.map { getOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    Log.d("ZP", "New Zap Request ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.addTaggedPost(note)
    }
    repliesTo.forEach {
      it.author?.addTaggedPost(note)
    }

    repliesTo.forEach {
      it.addZap(note, null)
    }
    mentions.forEach {
      it.addZap(note, null)
    }
  }

  fun findUsersStartingWith(username: String): List<User> {
    return users.values.filter {
           it.info.anyNameStartsWith(username)
        || it.pubkeyHex.startsWith(username, true)
        || it.pubkey.toNpub().startsWith(username, true)
    }
  }

  fun findNotesStartingWith(text: String): List<Note> {
    return notes.values.filter {
      (it.event is TextNoteEvent && it.event?.content?.contains(text, true) ?: false)
        || (it.event is ChannelMessageEvent && it.event?.content?.contains(text, true) ?: false)
        || it.idHex.startsWith(text, true)
        || it.id.toNote().startsWith(text, true)
    }
  }

  fun findChannelsStartingWith(text: String): List<Channel> {
    return channels.values.filter {
      it.anyNameStartsWith(text)
        || it.idHex.startsWith(text, true)
        || it.id.toNote().startsWith(text, true)
    }
  }

  // Observers line up here.
  val live: LocalCacheLiveData = LocalCacheLiveData(this)

  private fun refreshObservers() {
    live.refresh()
  }
}

class LocalCacheLiveData(val cache: LocalCache): LiveData<LocalCacheState>(LocalCacheState(cache)) {
  fun refresh() {
    postValue(LocalCacheState(cache))
  }
}

class LocalCacheState(val cache: LocalCache) {

}