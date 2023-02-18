package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.LocalCacheState
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.model.UserState
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent
import nostr.postr.toHex

object NostrHomeDataSource: NostrDataSource("HomeFeed") {
  lateinit var account: Account

  private val cacheListener: (UserState) -> Unit = {
    invalidateFilters()
  }

  override fun start() {
    if (this::account.isInitialized) {
      GlobalScope.launch(Dispatchers.Main) {
        account.userProfile().liveFollows.observeForever(cacheListener)
      }
    }
    super.start()
  }

  override fun stop() {
    super.stop()
    if (this::account.isInitialized) {
      GlobalScope.launch(Dispatchers.Main) {
        account.userProfile().liveFollows.removeObserver(cacheListener)
      }
    }
  }

  fun createFollowAccountsFilter(): TypedFilter {
    val follows = account.userProfile().follows

    val followKeys = follows.map {
      it.pubkey.toHex().substring(0, 6)
    }

    val followSet = followKeys.plus(account.userProfile().pubkeyHex.substring(0, 6))

    return TypedFilter(
      types = setOf(FeedType.FOLLOWS),
      filter = JsonFilter(
        kinds = listOf(TextNoteEvent.kind),
        authors = followSet,
        limit = 400
      )
    )
  }

  val followAccountChannel = requestNewChannel()

  override fun updateChannelFilters() {
    followAccountChannel.typedFilters = listOf(createFollowAccountsFilter()).ifEmpty { null }
  }
}