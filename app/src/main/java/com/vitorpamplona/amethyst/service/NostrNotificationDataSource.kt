package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMessageEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import nostr.postr.JsonFilter

object NostrNotificationDataSource: NostrDataSource<Note>("NotificationFeed") {
  lateinit var account: Account

  override fun feed(): List<Note> {
    return account.userProfile().taggedPosts
      .filter { it.event != null }
      .filter { account.isAcceptable(it) }
      .filter {
           it.event !is ChannelCreateEvent
        && it.event !is ChannelMetadataEvent
      }
      .sortedBy { it.event?.createdAt }
      .reversed()
  }

  override fun updateChannelFilters() {}
}