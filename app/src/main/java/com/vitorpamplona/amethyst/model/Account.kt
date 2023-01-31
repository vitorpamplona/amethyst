package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.Constants
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.Client
import com.vitorpamplona.amethyst.service.relays.Relay
import com.vitorpamplona.amethyst.service.relays.RelayPool
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nostr.postr.Contact
import nostr.postr.Persona
import nostr.postr.Utils
import nostr.postr.events.ContactListEvent
import nostr.postr.events.Event
import nostr.postr.events.MetadataEvent
import nostr.postr.events.PrivateDmEvent
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex

val DefaultChannels = setOf(
  "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb", // -> Anigma's Nostr
  "42224859763652914db53052103f0b744df79dfc4efef7e950fc0802fc3df3c5"  // -> Amethyst's Group
)

class Account(
  val loggedIn: Persona,
  val followingChannels: MutableSet<String> = DefaultChannels.toMutableSet(),
  val hiddenUsers: MutableSet<String> = mutableSetOf()
) {

  fun userProfile(): User {
    return LocalCache.getOrCreateUser(loggedIn.pubKey.toHexKey())
  }

  fun followingChannels(): List<Channel> {
    return followingChannels.map { LocalCache.getOrCreateChannel(it) }
  }

  fun hiddenUsers(): List<User> {
    return hiddenUsers.map { LocalCache.getOrCreateUser(it) }
  }

  fun isWriteable(): Boolean {
    return loggedIn.privKey != null
  }

  fun sendNewRelayList(relays: Map<String, ContactListEvent.ReadWrite>) {
    if (!isWriteable()) return

    val lastestContactList = userProfile().latestContactList
    val event = if (lastestContactList != null) {
      ContactListEvent.create(
        lastestContactList.follows,
        relays,
        loggedIn.privKey!!)
    } else {
      ContactListEvent.create(listOf(), relays, loggedIn.privKey!!)
    }

    Client.send(event)
    LocalCache.consume(event)
  }

  fun sendNewUserMetadata(toString: String) {
    if (!isWriteable()) return

    loggedIn.privKey?.let {
      val createdAt = Date().time / 1000
      val content = toString
      val pubKey = Utils.pubkeyCreate(it)
      val tags = listOf<List<String>>()
      val id = Event.generateId(pubKey, createdAt, MetadataEvent.kind, tags, content)
      val sig = Utils.sign(id, it)
      val event = MetadataEvent(id, pubKey, createdAt, tags, content, sig)

      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun reactTo(note: Note) {
    if (!isWriteable()) return

    if (note.reactions.firstOrNull { it.author == userProfile() && it.event?.content == "+" } != null) {
      // has already liked this note
      return
    }

    note.event?.let {
      val event = ReactionEvent.createLike(it, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun report(note: Note, type: ReportEvent.ReportType) {
    if (!isWriteable()) return

    if (
      note.reactions.firstOrNull { it.author == userProfile() && it.event?.content == "⚠️"} != null
    ) {
      // has already liked this note
      return
    }

    note.event?.let {
      val event = ReactionEvent.createWarning(it, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }

    note.event?.let {
      val event = ReportEvent.create(it, type, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun report(user: User, type: ReportEvent.ReportType) {
    if (!isWriteable()) return

    if (
      user.reports.firstOrNull { it.author == userProfile() && it.event is ReportEvent && (it.event as ReportEvent).reportType.contains(type) } != null
    ) {
      // has already reported this note
      return
    }

    val event = ReportEvent.create(user.pubkeyHex, type, loggedIn.privKey!!)
    Client.send(event)
    LocalCache.consume(event)
  }

  fun boost(note: Note) {
    if (!isWriteable()) return

    val currentTime = Date().time / 1000

    if (
      note.boosts.firstOrNull { it.author == userProfile() && (it?.event?.createdAt ?: 0) > currentTime - (60 * 5)} != null // 5 minute protection
    ) {
      // has already bosted in the past 5mins 
      return
    }

    note.event?.let {
      val event = RepostEvent.create(it, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun broadcast(note: Note) {
    note.event?.let {
      Client.send(it)
    }
  }

  fun follow(user: User) {
    if (!isWriteable()) return

    val lastestContactList = userProfile().latestContactList
    val event = if (lastestContactList != null) {
      ContactListEvent.create(
        lastestContactList.follows.plus(Contact(user.pubkeyHex, null)),
        userProfile().relays,
        loggedIn.privKey!!)
    } else {
      val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
      ContactListEvent.create(
        listOf(Contact(user.pubkeyHex, null)),
        relays,
        loggedIn.privKey!!
      )
    }

    Client.send(event)
    LocalCache.consume(event)
  }

  fun unfollow(user: User) {
    if (!isWriteable()) return

    val lastestContactList = userProfile().latestContactList
    if (lastestContactList != null) {
      val event = ContactListEvent.create(
        lastestContactList.follows.filter { it.pubKeyHex != user.pubkeyHex },
        userProfile().relays,
        loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun sendPost(message: String, replyTo: List<Note>?, mentions: List<User>?) {
    if (!isWriteable()) return

    val repliesToHex = replyTo?.map { it.idHex }
    val mentionsHex = mentions?.map { it.pubkeyHex }

    val signedEvent = TextNoteEvent.create(
      msg = message,
      replyTos = repliesToHex,
      mentions = mentionsHex,
      privateKey = loggedIn.privKey!!
    )
    Client.send(signedEvent)
    LocalCache.consume(signedEvent)
  }

  fun sendChannelMeesage(message: String, toChannel: String, replyingTo: Note? = null, mentions: List<User>?) {
    if (!isWriteable()) return

    val repliesToHex = listOfNotNull(replyingTo?.idHex).ifEmpty { null }
    val mentionsHex = mentions?.map { it.pubkeyHex }

    val signedEvent = ChannelMessageEvent.create(
      message = message,
      channel = toChannel,
      replyTos = repliesToHex,
      mentions = mentionsHex,
      privateKey = loggedIn.privKey!!
    )
    Client.send(signedEvent)
    LocalCache.consume(signedEvent)
  }

  fun sendPrivateMeesage(message: String, toUser: String, replyingTo: Note? = null) {
    if (!isWriteable()) return
    val user = LocalCache.users[toUser] ?: return

    val signedEvent = PrivateDmEvent.create(
      recipientPubKey = user.pubkey,
      publishedRecipientPubKey = user.pubkey,
      msg = message,
      privateKey = loggedIn.privKey!!,
      advertiseNip18 = false
    )
    Client.send(signedEvent)
    LocalCache.consume(signedEvent)
  }

  fun sendCreateNewChannel(name: String, about: String, picture: String) {
    if (!isWriteable()) return

    val metadata = ChannelCreateEvent.ChannelData(
      name, about, picture
    )

    val event = ChannelCreateEvent.create(
      channelInfo = metadata,
      privateKey = loggedIn.privKey!!
    )

    Client.send(event)
    LocalCache.consume(event)

    joinChannel(event.id.toHex())
  }

  fun joinChannel(idHex: String) {
    followingChannels.add(idHex)
    invalidateData()
  }

  fun leaveChannel(idHex: String) {
    followingChannels.remove(idHex)
    invalidateData()
  }

  fun hideUser(pubkeyHex: String) {
    hiddenUsers.add(pubkeyHex)
    invalidateData()
  }

  fun showUser(pubkeyHex: String) {
    hiddenUsers.remove(pubkeyHex)
    invalidateData()
  }

  fun sendChangeChannel(name: String, about: String, picture: String, channel: Channel) {
    if (!isWriteable()) return

    val metadata = ChannelCreateEvent.ChannelData(
      name, about, picture
    )

    val event = ChannelMetadataEvent.create(
      newChannelInfo = metadata,
      originalChannelIdHex = channel.idHex,
      privateKey = loggedIn.privKey!!
    )

    Client.send(event)
    LocalCache.consume(event)

    followingChannels.add(event.id.toHex())
  }

  fun decryptContent(note: Note): String? {
    val event = note.event
    return if (event is PrivateDmEvent && loggedIn.privKey != null) {
      var pubkeyToUse = event.pubKey

      val recepientPK = event.recipientPubKey

      if (note.author == userProfile() && recepientPK != null)
        pubkeyToUse = recepientPK

      return try {
        val sharedSecret = Utils.getSharedSecret(loggedIn.privKey!!, pubkeyToUse)

        val retVal = Utils.decrypt(event.content, sharedSecret)

        if (retVal.startsWith(PrivateDmEvent.nip18Advertisement)) {
          retVal.substring(16)
        } else {
          retVal
        }

      } catch (e: Exception) {
        e.printStackTrace()
        null
      }
    } else {
      event?.content
    }
  }

  fun activeRelays(): Array<Relay>? {
    return userProfile().relays?.map { Relay(it.key, it.value.read, it.value.write) }?.toTypedArray()
  }

  init {
    userProfile().subscribe(object: User.Listener() {
      override fun onRelayChange() {
        Client.disconnect()
        Client.connect(activeRelays() ?: Constants.defaultRelays)
        RelayPool.requestAndWatch()
      }
    })
  }

  // Observers line up here.
  val live: AccountLiveData = AccountLiveData(this)

  // Refreshes observers in batches.
  var handlerWaiting = false
  @Synchronized
  fun invalidateData() {
    if (handlerWaiting) return

    handlerWaiting = true
    val scope = CoroutineScope(Job() + Dispatchers.Default)
    scope.launch {
      delay(100)
      live.refresh()
      handlerWaiting = false
    }
  }

  fun isHidden(user: User) = user !in hiddenUsers()

  fun isAcceptable(user: User): Boolean {
    return user !in hiddenUsers()  // if user hasn't hided this author
        && user.reportsBy( userProfile() ).isEmpty() // if user has not reported this post
        && user.reportsBy( userProfile().follows ).size < 5
  }

  fun isAcceptableDirect(note: Note): Boolean {
    return note.reportsBy( userProfile() ).isEmpty()  // if user has not reported this post
        && note.reportsBy( userProfile().follows ).size < 5 // if it has 5 reports by reliable users
  }

  fun isAcceptable(note: Note): Boolean {
    return note.author?.let { isAcceptable(it) } ?: true // if user hasn't hided this author
        && isAcceptableDirect(note)
        && (note.event !is RepostEvent
          || (note.event is RepostEvent && note.replyTo?.firstOrNull { isAcceptableDirect(note) } != null)
        ) // is not a reaction about a blocked post
  }

}

class AccountLiveData(private val account: Account): LiveData<AccountState>(AccountState(account)) {
  fun refresh() {
    postValue(AccountState(account))
  }
}

class AccountState(val account: Account)