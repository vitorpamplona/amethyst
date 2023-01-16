package com.vitorpamplona.amethyst.model

import androidx.lifecycle.LiveData
import com.vitorpamplona.amethyst.service.Constants
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.Client
import nostr.postr.Contact
import nostr.postr.Persona
import nostr.postr.Utils
import nostr.postr.events.ContactListEvent
import nostr.postr.events.PrivateDmEvent
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex

val DefaultChannels = setOf(
  "25e5c82273a271cb1a840d0060391a0bf4965cafeb029d5ab55350b418953fbb" // -> Anigma's Nostr
)

class Account(val loggedIn: Persona, val followingChannels: MutableSet<String> = DefaultChannels.toMutableSet()) {
  var seeReplies: Boolean = true

  fun userProfile(): User {
    return LocalCache.getOrCreateUser(loggedIn.pubKey)
  }

  fun followingChannels(): List<Channel> {
    return followingChannels.map { LocalCache.getOrCreateChannel(it) }
  }

  fun isWriteable(): Boolean {
    return loggedIn.privKey != null
  }

  fun reactTo(note: Note) {
    if (!isWriteable()) return

    if (note.reactions.firstOrNull { it.author == userProfile() } != null) {
      // has already liked this note
      return
    }

    note.event?.let {
      val event = ReactionEvent.createLike(it, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun boost(note: Note) {
    if (!isWriteable()) return

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

    val lastestContactList = userProfile().lastestContactList
    val event = if (lastestContactList != null) {
      ContactListEvent.create(lastestContactList.follows.plus(Contact(user.pubkeyHex, null)), lastestContactList.relayUse, loggedIn.privKey!!)
    } else {
      val relays = Constants.defaultRelays.associate { it.url to ContactListEvent.ReadWrite(it.read, it.write) }
      ContactListEvent.create(listOf(Contact(user.pubkeyHex, null)), relays, loggedIn.privKey!!)
    }

    Client.send(event)
    LocalCache.consume(event)
  }

  fun unfollow(user: User) {
    if (!isWriteable()) return

    val lastestContactList = userProfile().lastestContactList
    if (lastestContactList != null) {
      val event = ContactListEvent.create(lastestContactList.follows.filter { it.pubKeyHex != user.pubkeyHex }, lastestContactList.relayUse, loggedIn.privKey!!)
      Client.send(event)
      LocalCache.consume(event)
    }
  }

  fun sendPost(message: String, replyingTo: Note?) {
    if (!isWriteable()) return

    val replyToEvent = replyingTo?.event
    if (replyToEvent is TextNoteEvent) {
      val repliesTo = replyToEvent.replyTos.plus(replyToEvent.id.toHex())
      val mentions = replyToEvent.mentions.plus(replyToEvent.pubKey.toHex())

      val signedEvent = TextNoteEvent.create(
        msg = message,
        replyTos = repliesTo,
        mentions = mentions,
        privateKey = loggedIn.privKey!!
      )
      Client.send(signedEvent)
      LocalCache.consume(signedEvent)
    } else {
      val signedEvent = TextNoteEvent.create(
        msg = message,
        replyTos = null,
        mentions = null,
        privateKey = loggedIn.privKey!!
      )
      Client.send(signedEvent)
      LocalCache.consume(signedEvent)
    }
  }

  fun sendChannelMeesage(message: String, toChannel: String, replyingTo: Note? = null) {
    if (!isWriteable()) return

    val signedEvent = ChannelMessageEvent.create(
      message = message,
      channel = toChannel,
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

  // Observers line up here.
  val live: AccountLiveData = AccountLiveData(this)

  private fun refreshObservers() {
    live.refresh()
  }
}

class AccountLiveData(private val account: Account): LiveData<AccountState>(AccountState(account)) {
  fun refresh() {
    postValue(AccountState(account))
  }

  override fun onActive() {
    super.onActive()
  }

  override fun onInactive() {
    super.onInactive()
  }
}

class AccountState(val account: Account)