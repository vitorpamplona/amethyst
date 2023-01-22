package com.vitorpamplona.amethyst.model

import android.util.Log
import androidx.lifecycle.LiveData
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelHideMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ChannelMuteUserEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import nostr.postr.events.ContactListEvent
import nostr.postr.events.DeletionEvent
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
  fun getOrCreateUser(pubkey: ByteArray): User {
    val key = pubkey.toHexKey()
    return users[key] ?: run {
      val answer = User(pubkey)
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
    val oldUser = getOrCreateUser(event.pubKey)
    if (event.createdAt > oldUser.updatedMetadataAt) {
      //Log.d("MT", "New User ${users.size} ${event.contactMetaData.name}")

      val newUser = try {
        metadataParser.readValue<UserMetadata>(ByteArrayInputStream(event.content.toByteArray(Charsets.UTF_8)), UserMetadata::class.java)
      } catch (e: Exception) {
        e.printStackTrace()
        Log.w("MT", "Content Parse Error ${e.localizedMessage} ${event.content}")
        return
      }

      oldUser.updateUserInfo(newUser, event.createdAt)
      oldUser.latestMetadata = event
    } else {
      //Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
    }
  }

  fun formattedDateTime(timestamp: Long): String {
    return Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
      .format(DateTimeFormatter.ofPattern("uuuu MMM d hh:mm a"))
  }

  fun consume(event: TextNoteEvent) {
    val note = getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey)
    val mentions = Collections.synchronizedList(event.mentions.map { getOrCreateUser(decodePublicKey(it)) })
    val replyTo = Collections.synchronizedList(event.replyTos.map { getOrCreateNote(it) }.toMutableList())

    note.loadEvent(event, author, mentions, replyTo)

    //Log.d("TN", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content?.take(100)} ${formattedDateTime(event.createdAt)}")

    // Prepares user's profile view.
    author.notes.add(note)

    // Adds notifications to users.
    mentions.forEach {
      it.taggedPosts.add(note)
    }
    replyTo.forEach {
      it.author?.taggedPosts?.add(note)
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
    val user = getOrCreateUser(event.pubKey)

    if (event.createdAt > user.updatedFollowsAt) {
      //Log.d("CL", "AAA ${user.toBestDisplayName()} ${event.follows.size}")
      user.updateFollows(
        event.follows.map {
          try {
            val pubKey = decodePublicKey(it.pubKeyHex)
            getOrCreateUser(pubKey)
          } catch (e: Exception) {
            println("Could not parse Hex key: ${it.pubKeyHex}")
            println(event.toJson())
            e.printStackTrace()
            null
          }
        }.filterNotNull().toSet(),
        event.createdAt
      )

      user.latestContactList = event
    }

    refreshObservers()
  }

  fun consume(event: PrivateDmEvent) {
    val note = getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey)
    val recipient = event.recipientPubKey?.let { getOrCreateUser(it) }

    //Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

    val repliesTo = event.tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }.map { getOrCreateNote(it) }.toMutableList()
    val mentions = event.tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }.map { getOrCreateUser(decodePublicKey(it)) }

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

    val author = getOrCreateUser(event.pubKey)
    val mentions = event.originalAuthor.map { getOrCreateUser(decodePublicKey(it)) }.toList()
    val repliesTo = event.boostedPost.map { getOrCreateNote(it) }.toMutableList()

    note.loadEvent(event, author, mentions, repliesTo)

    // Prepares user's profile view.
    author.notes.add(note)

    // Adds notifications to users.
    mentions.forEach {
      it.taggedPosts.add(note)
    }
    repliesTo.forEach {
      it.author?.taggedPosts?.add(note)
    }

    // Counts the replies
    repliesTo.forEach {
      it.addBoost(note)
    }

    refreshObservers()
  }

  fun consume(event: ReactionEvent) {
    val note = getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey)
    val mentions = event.originalAuthor.map { getOrCreateUser(decodePublicKey(it)) }
    val repliesTo = event.originalPost.map { getOrCreateNote(it) }.toMutableList()

    note.loadEvent(event, author, mentions, repliesTo)

    //Log.d("RE", "New Reaction ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.taggedPosts.add(note)
    }
    repliesTo.forEach {
      it.author?.taggedPosts?.add(note)
    }

    if (event.content == "" || event.content == "+" || event.content == "\uD83E\uDD19") {
      // Counts the replies
      repliesTo.forEach {
        it.addReaction(note)
      }
    }
  }

  fun consume(event: ChannelCreateEvent) {
    //Log.d("MT", "New Event ${event.content} ${event.id.toHex()}")
    // new event
    val oldChannel = getOrCreateChannel(event.id.toHex())
    val author = getOrCreateUser(event.pubKey)
    if (event.createdAt > oldChannel.updatedMetadataAt) {
      if (oldChannel.creator == null || oldChannel.creator == author) {
        oldChannel.updateChannelInfo(author, event.channelInfo, event.createdAt)

        val note = oldChannel.getOrCreateNote(event.id.toHex())
        note.channel = oldChannel
        note.loadEvent(event, author, emptyList(), mutableListOf())
      }
    } else {
      // older data, does nothing
    }

    refreshObservers()
  }
  fun consume(event: ChannelMetadataEvent) {
    //Log.d("MT", "New User ${users.size} ${event.contactMetaData.name}")
    if (event.channel.isNullOrBlank()) return

    // new event
    val oldChannel = getOrCreateChannel(event.channel)
    val author = getOrCreateUser(event.pubKey)
    if (event.createdAt > oldChannel.updatedMetadataAt) {
      if (oldChannel.creator == null || oldChannel.creator == author) {
        oldChannel.updateChannelInfo(author, event.channelInfo, event.createdAt)

        val note = oldChannel.getOrCreateNote(event.id.toHex())
        note.channel = oldChannel
        note.loadEvent(event, author, emptyList(), mutableListOf())
      }
    } else {
      //Log.d("MT","Relay sent a previous Metadata Event ${oldUser.toBestDisplayName()} ${formattedDateTime(event.createdAt)} > ${formattedDateTime(oldUser.updatedAt)}")
    }

    refreshObservers()
  }

  fun consume(event: ChannelMessageEvent) {
    if (event.channel.isNullOrBlank()) return

    val channel = getOrCreateChannel(event.channel)

    val note = channel.getOrCreateNote(event.id.toHex())

    // Already processed this event.
    if (note.event != null) return

    val author = getOrCreateUser(event.pubKey)
    val mentions = Collections.synchronizedList(event.mentions.map { getOrCreateUser(decodePublicKey(it)) })
    val replyTo = Collections.synchronizedList(
      event.replyTos
        .map { channel.getOrCreateNote(it) }
        .filter { it.event !is ChannelCreateEvent }
        .toMutableList()
    )

    note.channel = channel
    note.loadEvent(event, author, mentions, replyTo)

    //Log.d("CM", "New Note (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${note.event?.content} ${formattedDateTime(event.createdAt)}")

    // Adds notifications to users.
    mentions.forEach {
      it.taggedPosts.add(note)
    }
    replyTo.forEach {
      it.author?.taggedPosts?.add(note)
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

  fun findUsersStartingWith(username: String): List<User> {
    return users.values.filter {
           it.info.anyNameStartsWith(username)
        || it.pubkeyHex.startsWith(username, true)
        || it.pubkey.toNpub().startsWith(username, true)
    }
  }

  fun findNotesStartingWith(text: String): List<Note> {
    return notes.values.filter {
      (it.event is TextNoteEvent && it.event?.content?.contains(text) ?: false)
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