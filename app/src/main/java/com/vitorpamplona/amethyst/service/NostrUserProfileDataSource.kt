package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.toByteArray
import nostr.postr.JsonFilter
import nostr.postr.events.MetadataEvent
import nostr.postr.events.TextNoteEvent

object NostrUserProfileDataSource: NostrDataSource<Note>("UserProfileFeed") {
  lateinit var account: Account
  var user: User? = null

  fun loadUserProfile(userId: String) {
    user = LocalCache.getOrCreateUser(userId)
    resetFilters()
  }

  fun createUserInfoFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(MetadataEvent.kind),
      authors = listOf(user!!.pubkeyHex),
      limit = 1
    )
  }

  fun createUserPostsFilter(): JsonFilter {
    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind),
      authors = listOf(user!!.pubkeyHex),
      limit = 100
    )
  }

  val userInfoChannel = requestNewChannel()
  val notesChannel = requestNewChannel()

  override fun feed(): List<Note> {
    val notes = user?.notes ?: return emptyList()
    val sortedNotes = synchronized(notes) {
      notes.filter { account.isAcceptable(it) }.sortedBy { it.event?.createdAt }
    }
    return sortedNotes.reversed()
  }

  override fun updateChannelFilters() {
    userInfoChannel.filter = listOf(createUserInfoFilter()).ifEmpty { null }
    notesChannel.filter = listOf(createUserPostsFilter()).ifEmpty { null }
  }
}