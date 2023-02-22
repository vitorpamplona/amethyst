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
import com.vitorpamplona.amethyst.service.relays.Relay
import fr.acinq.secp256k1.Hex
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

  val antiSpam = AntiSpamFilter()

  val users = ConcurrentHashMap<HexKey, User>()
  val notes = ConcurrentHashMap<HexKey, Note>()
  val channels = ConcurrentHashMap<HexKey, Channel>()

  fun checkGetOrCreateUser(key: String): User? {
    return try {
      val checkHex = Hex.decode(key).toNpub() // Checks if this is a valid Hex
      getOrCreateUser(key)
    } catch (e: IllegalArgumentException) {
      Log.e("LocalCache", "Invalid Key to create user: $key", e)
      null
    }
  }

  @Synchronized
  fun getOrCreateUser(key: HexKey): User {
    return users[key] ?: run {
      val answer = User(key)
      users.put(key, answer)
      answer
    }
  }

  fun checkGetOrCreateNote(key: String): Note? {
    return try {
      val checkHex = Hex.decode(key).toNote() // Checks if this is a valid Hex
      getOrCreateNote(key)
    } catch (e: IllegalArgumentException) {
      Log.e("LocalCache", "Invalid Key to create note: $key", e)
      null
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

  fun checkGetOrCreateChannel(key: String): Channel? {
    return try {
      val checkHex = Hex.decode(key).toNote() // Checks if this is a valid Hex
      getOrCreateChannel(key)
    } catch (e: IllegalArgumentException) {
      Log.e("LocalCache", "Invalid Key to create channel: $key", e)
      null
    }
  }


  @Synchronized
  fun getOrCreateChannel(key: String): Channel {
    return channels[key] ?: run {
      val answer = Channel(key)
      channels.put(key, answer)
      answer
    }
  }


  fun consume(event: MetadataEvent) {
    // new event
    val oldUser = getOrCreateUser(event.pubKey.toHexKey())
    if (oldUser.info == null || event.createdAt > oldUser.info!!.updatedMetadataAt) {
      val newUser = try {
        metadataParser.readValue(
          ByteArrayInputStream(event.content.toByteArray(Charsets.UTF_8)),
          UserMetadata::class.java
        )
      } catch (e: Exception) {
        e.printStackTrace()
        Log.w("MT", "Content Parse Error ${e.localizedMessage} ${event.content}")
        return
      }

      oldUser.updateUserInfo(newUser, event)
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
    if (antiSpam.isSpam(event)) return

    val note = getOrCreateNote(event.id.toHex())
    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelayBeingUsed(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val mentions = event.mentions.mapNotNull { checkGetOrCreateUser(it) }
    val replyTo = replyToWithoutCitations(event).mapNotNull { checkGetOrCreateNote(it) }

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

  private fun replyToWithoutCitations(event: TextNoteEvent): List<String> {
    var citations = mutableSetOf<String>()
    // Removes citations from replies:
    val matcher = tagSearch.matcher(event.content)
    while (matcher.find()) {
      try {
        val tag = matcher.group(1)?.let { event.tags[it.toInt()] }
        if (tag != null && tag[0] == "e") {
          citations.add(tag[1])
        }
      } catch (e: Exception) {

      }
    }

    return event.replyTos.filter { it !in citations }
  }

  fun consume(event: RecommendRelayEvent) {
    //Log.d("RR", event.toJson())
  }

  fun consume(event: ContactListEvent) {
    val user = getOrCreateUser(event.pubKey.toHexKey())

    if (event.createdAt > user.updatedFollowsAt && event.follows.isNotEmpty()) {
      // Saves relay list only if it's a user that is currently been seen
      user.latestContactList = event

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

      // Saves relay list only if it's a user that is currently been seen
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
        println("relay import issue")
        e.printStackTrace()
      }

      Log.d(
        "CL",
        "AAA ${user.toBestDisplayName()} ${event.follows.size}"
      )
    }
  }

  fun consume(event: PrivateDmEvent, relay: Relay?) {
    val note = getOrCreateNote(event.id.toHex())
    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelayBeingUsed(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val recipient = event.recipientPubKey?.let { getOrCreateUser(it.toHexKey()) }

    //Log.d("PM", "${author.toBestDisplayName()} to ${recipient?.toBestDisplayName()}")

    val repliesTo = event.tags.filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }.mapNotNull { checkGetOrCreateNote(it) }
    val mentions = event.tags.filter { it.firstOrNull() == "p" }.mapNotNull { it.getOrNull(1) }.mapNotNull { checkGetOrCreateUser(it) }

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
    val mentions = event.originalAuthor.mapNotNull { checkGetOrCreateUser(it) }
    val repliesTo = event.boostedPost.mapNotNull { checkGetOrCreateNote(it) }

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
    val mentions = event.originalAuthor.mapNotNull { checkGetOrCreateUser(it) }
    val repliesTo = event.originalPost.mapNotNull { checkGetOrCreateNote(it) }

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
    val mentions = event.reportedAuthor.mapNotNull { checkGetOrCreateUser(it) }
    val repliesTo = event.reportedPost.mapNotNull { checkGetOrCreateNote(it) }

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
    val oldChannel = checkGetOrCreateChannel(event.channel) ?: return
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
    if (antiSpam.isSpam(event)) return

    val channel = checkGetOrCreateChannel(event.channel) ?: return

    val note = getOrCreateNote(event.id.toHex())
    channel.addNote(note)

    val author = getOrCreateUser(event.pubKey.toHexKey())

    if (relay != null) {
      author.addRelayBeingUsed(relay, event.createdAt)
      note.addRelay(relay)
    }

    // Already processed this event.
    if (note.event != null) return

    val mentions = event.mentions.mapNotNull { checkGetOrCreateUser(it) }
    val replyTo = event.replyTos
      .mapNotNull { checkGetOrCreateNote(it) }
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
    val mentions = event.zappedAuthor.mapNotNull { checkGetOrCreateUser(it) }
    val repliesTo = event.zappedPost.mapNotNull { checkGetOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    val zapRequest = event.containedPost?.id?.toHexKey()?.let { getOrCreateNote(it) }
    if (zapRequest == null) {
      Log.e("ZP","Zap Request not found. Unable to process Zap {${event.toJson()}}")
      return
    }

    //Log.d("ZP", "New ZapEvent ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

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
    val mentions = event.zappedAuthor.mapNotNull { checkGetOrCreateUser(it) }
    val repliesTo = event.zappedPost.mapNotNull { checkGetOrCreateNote(it) }

    note.loadEvent(event, author, mentions, repliesTo)

    //Log.d("ZP", "New Zap Request ${event.content} (${notes.size},${users.size}) ${note.author?.toBestDisplayName()} ${formattedDateTime(event.createdAt)}")

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
          (it.anyNameStartsWith(username))
        || it.pubkeyHex.startsWith(username, true)
        || it.pubkeyNpub().startsWith(username, true)
    }
  }

  fun findNotesStartingWith(text: String): List<Note> {
    return notes.values.filter {
      (it.event is TextNoteEvent && it.event?.content?.contains(text, true) ?: false)
        || (it.event is ChannelMessageEvent && it.event?.content?.contains(text, true) ?: false)
        || it.idHex.startsWith(text, true)
        || it.idNote().startsWith(text, true)
    }
  }

  fun findChannelsStartingWith(text: String): List<Channel> {
    return channels.values.filter {
        it.anyNameStartsWith(text)
        || it.idHex.startsWith(text, true)
        || it.idNote().startsWith(text, true)
    }
  }

  fun cleanObservers() {
    notes.forEach {
      it.value.clearLive()
    }

    users.forEach {
      it.value.clearLive()
    }
  }

  fun pruneOldAndHiddenMessages(account: Account) {
    channels.forEach {
      val toBeRemoved = it.value.pruneOldAndHiddenMessages(account)

      toBeRemoved.forEach {
        notes.remove(it.idHex)
        // Doesn't need to clean up the replies and mentions.. Too small to matter.
      }

      println("PRUNE: ${toBeRemoved.size} messages removed from ${it.value.info.name}")
    }
  }

  fun pruneNonFollows(account: Account) {
    val follows = account.userProfile().follows
    val knownPMs = account.userProfile().privateChatrooms.filter {
      account.userProfile().hasSentMessagesTo(it.key) && account.isAcceptable(it.key)
    }

    val followsFollow = follows.map {
      it.follows
    }.flatten()

    val followSet = follows.plus(knownPMs).plus(account.userProfile()).plus(followsFollow)

    val toBeRemoved = notes
      .filter {
        (it.value.author == null || it.value.author!! !in followSet) && it.value.event?.kind == TextNoteEvent.kind && it.value.liveSet?.isInUse() != true
      }

    toBeRemoved.forEach {
      notes.remove(it.key)
    }

    val toBeRemovedUsers = users
      .filter {
        (it.value !in followSet) && it.value.liveSet?.isInUse() != true
      }

    toBeRemovedUsers.forEach {
      users.remove(it.key)
    }

    println("PRUNE: ${toBeRemoved.size} messages removed because they came from NonFollows")
    println("PRUNE: ${toBeRemovedUsers.size} users removed because are NonFollows")
  }

  fun pruneHiddenMessages(account: Account) {
    val toBeRemoved = account.hiddenUsers.map {
      users[it]?.notes ?: emptySet()
    }.flatten()

    account.hiddenUsers.forEach {
      users[it]?.clearNotes()
    }

    toBeRemoved.forEach {
      notes.remove(it.idHex)
    }

    println("PRUNE: ${toBeRemoved.size} messages removed because they were Hidden")
  }

  // Observers line up here.
  val live: LocalCacheLiveData = LocalCacheLiveData(this)

  private fun refreshObservers() {
    live.invalidateData()
  }
}

class LocalCacheLiveData(val cache: LocalCache): LiveData<LocalCacheState>(LocalCacheState(cache)) {

  // Refreshes observers in batches.
  var handlerWaiting = AtomicBoolean()

  @Synchronized
  fun invalidateData() {
    if (!hasActiveObservers()) return
    if (handlerWaiting.getAndSet(true)) return

    handlerWaiting.set(true)
    val scope = CoroutineScope(Job() + Dispatchers.Main)
    scope.launch {
      try {
        delay(100)
        refresh()
      } finally {
        withContext(NonCancellable) {
          handlerWaiting.set(false)
        }
      }
    }
  }

  private fun refresh() {
    postValue(LocalCacheState(cache))
  }
}

class LocalCacheState(val cache: LocalCache) {

}