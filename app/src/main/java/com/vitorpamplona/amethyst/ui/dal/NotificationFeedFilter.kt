package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ChannelCreateEvent
import com.vitorpamplona.amethyst.service.model.ChannelMetadataEvent
import com.vitorpamplona.amethyst.service.model.LnZapRequestEvent
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import com.vitorpamplona.amethyst.service.model.TextNoteEvent

object NotificationFeedFilter: FeedFilter<Note>() {
  lateinit var account: Account

  override fun feed(): List<Note> {
    return account.userProfile().taggedPosts
      .filter {
           it.author == null
        || (!account.isHidden(it.author!!) && it.author != account.userProfile())
      }
      .filter {
           it.event !is ChannelCreateEvent
        && it.event !is ChannelMetadataEvent
        && it.event !is LnZapRequestEvent
      }
      .filter {
        it.event !is TextNoteEvent
          ||
          (
            it.event is TextNoteEvent
              &&
              (
                it.replyTo?.any { it.author == account.userProfile() } == true
                  ||
                  account.userProfile() in it.directlyCiteUsers()
                )
            )
      }
      .filter {
        it.event !is ReactionEvent
          ||
          (
            it.event is ReactionEvent
              &&
              (
                it.replyTo?.lastOrNull()?.author == account.userProfile()
                  ||
                  account.userProfile() in it.directlyCiteUsers()
                )
            )
      }
      .filter {
        it.event !is RepostEvent
          ||
          (
            it.event is RepostEvent
              &&
              (
                it.replyTo?.lastOrNull()?.author == account.userProfile()
                  ||
                  account.userProfile() in it.directlyCiteUsers()
                )
            )
      }

      .sortedBy { it.event?.createdAt }
      .reversed()
  }
}