package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReportEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter
import nostr.postr.events.ContactListEvent
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrAccountDataSource: NostrDataSource<Note>("AccountData") {
  lateinit var account: Account

  fun createAccountContactListFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(ContactListEvent.kind),
        authors = listOf(account.userProfile().pubkeyHex),
        limit = 1
      )
    )
  }

  fun createAccountMetadataFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(MetadataEvent.kind),
        authors = listOf(account.userProfile().pubkeyHex),
        limit = 1
      )
    )
  }

  fun createAccountReportsFilter(): TypedFilter {
    return TypedFilter(
      types = FeedType.values().toSet(),
      filter = JsonFilter(
        kinds = listOf(ReportEvent.kind),
        authors = listOf(account.userProfile().pubkeyHex)
      )
    )
  }

  fun createNotificationFilter() = TypedFilter(
    types = FeedType.values().toSet(),
    filter = JsonFilter(
      tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
      limit = 200
    )
  )

  val accountChannel = requestNewChannel()

  override fun feed(): List<Note> {
    val user = account.userProfile()

    return LocalCache.notes.values
      .filter { (it.event is TextNoteEvent || it.event is RepostEvent) && it.author in user.follows }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }

  override fun updateChannelFilters() {
    // gets everthing about the user logged in
    accountChannel.typedFilters = listOf(
      createAccountMetadataFilter(),
      createAccountContactListFilter(),
      createNotificationFilter(),
      createAccountReportsFilter()
    ).ifEmpty { null }
  }
}