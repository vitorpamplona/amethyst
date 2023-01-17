package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.RepostEvent
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex

object NostrHomeDataSource: NostrDataSource<Note>("HomeFeed") {
  lateinit var account: Account

  private val cacheListener: (UserState) -> Unit = {
    resetFilters()
  }

  override fun start() {
    if (this::account.isInitialized)
      account.userProfile().live.observeForever(cacheListener)
    super.start()
  }

  override fun stop() {
    super.stop()
    if (this::account.isInitialized)
      account.userProfile().live.removeObserver(cacheListener)
  }

  fun createFollowAccountsFilter(): JsonFilter? {
    val follows = account.userProfile().follows ?: emptySet()

    val followKeys = synchronized(follows) {
      follows.map {
        it.pubkey.toHex().substring(0, 6)
      }
    }

    val followSet = followKeys.plus(account.userProfile().pubkeyHex.substring(0, 6))

    if (followSet.isEmpty()) return null

    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind, RepostEvent.kind),
      authors = followSet,
      since = System.currentTimeMillis() / 1000 - (60 * 60 * 24 * 1), // 24 hours
    )
  }

  val followAccountChannel = requestNewChannel()

  fun <T> equalsIgnoreOrder(list1:List<T>?, list2:List<T>?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return list1.size == list2.size && list1.toSet() == list2.toSet()
  }

  fun equalAuthors(list1:JsonFilter?, list2:JsonFilter?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return equalsIgnoreOrder(list1.authors, list2.authors)
  }

  override fun feed(): List<Note> {
    val user = account.userProfile()

    val follows = user.follows
    val followKeys = synchronized(follows) {
      follows.map { it.pubkeyHex }
    }

    val allowSet = followKeys.plus(user.pubkeyHex).toSet()

    return LocalCache.notes.values
      .filter { (it.event is TextNoteEvent || it.event is RepostEvent) && it.author?.pubkeyHex in allowSet }
      .sortedBy { it.event!!.createdAt }
      .reversed()
  }

  override fun updateChannelFilters() {
    val newFollowAccountsFilter = createFollowAccountsFilter()

    if (!equalAuthors(newFollowAccountsFilter, followAccountChannel.filter)) {
      followAccountChannel.filter = newFollowAccountsFilter
    }
  }
}