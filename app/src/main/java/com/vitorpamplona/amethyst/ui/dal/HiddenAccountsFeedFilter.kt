package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object HiddenAccountsFeedFilter: FeedFilter<User>() {
  lateinit var account: Account

  override fun feed() = account.hiddenUsers()
}