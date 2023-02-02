package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.RepostEvent
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrAccountDataSource: NostrDataSource<Note>("AccountData") {
  lateinit var account: Account

  fun createAccountContactListFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(ContactListEvent.kind),
      authors = listOf(account.userProfile().pubkeyHex),
      limit = 1
    )
  }

  fun createAccountMetadataFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(MetadataEvent.kind),
      authors = listOf(account.userProfile().pubkeyHex),
      limit = 1
    )
  }

  fun createNotificationFilter() = JsonFilter(
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
    limit = 100
  )

  val accountChannel = requestNewChannel()

  override fun feed(): List<Note> {
    val user = account.userProfile()

    val follows = user.follows
    val followKeys = synchronized(follows) {
      follows.map { it.pubkeyHex }
    }
    val allowSet = followKeys.plus(user.pubkeyHex).toSet()

    return LocalCache.notes.values
      .filter { (it.event is TextNoteEvent || it.event is RepostEvent) && it.author?.pubkeyHex in allowSet }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }

  override fun updateChannelFilters() {
    // gets everthing about the user logged in
    accountChannel.filter = listOf(
      createAccountMetadataFilter(),
      createAccountContactListFilter(),
      createNotificationFilter()
    ).ifEmpty { null }
  }
}