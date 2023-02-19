package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import nostr.postr.JsonFilter

object NotificationFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account

  override fun feed(): List<Note> {
    return account.userProfile().taggedPosts
      .filter { it.author == null || !account.isHidden(it.author!!) }
      .filter {
           it.event !is ChannelCreateEvent
        && it.event !is ChannelMetadataEvent
        && it.event !is LnZapRequestEvent
      }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }
}