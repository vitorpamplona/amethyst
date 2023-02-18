package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Channel
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.relays.FeedType
import com.vitorpamplona.amethyst.service.relays.TypedFilter
import nostr.postr.JsonFilter

object ChannelFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account
  lateinit var channel: Channel

  fun loadMessagesBetween(accountLoggedIn: Account, channelId: String) {
    account = accountLoggedIn
    channel = LocalCache.getOrCreateChannel(channelId)
  }

  // returns the last Note of each user.
  override fun feed(): List<Note> {
    return channel?.notes?.values?.filter { account.isAcceptable(it) }?.sortedBy { it.event?.createdAt }?.reversed() ?: emptyList()
  }
}