package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.ReportEvent
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

  fun createAccountReportsFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(ReportEvent.kind),
      authors = listOf(account.userProfile().pubkeyHex)
    )
  }

  fun createNotificationFilter() = JsonFilter(
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
    limit = 200
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
    accountChannel.filter = listOf(
      createAccountMetadataFilter(),
      createAccountContactListFilter(),
      createNotificationFilter(),
      createAccountReportsFilter()
    ).ifEmpty { null }
  }
}