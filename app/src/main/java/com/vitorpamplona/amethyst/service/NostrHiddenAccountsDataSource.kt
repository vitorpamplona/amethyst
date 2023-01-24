package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.User
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrHiddenAccountsDataSource: NostrDataSource<User>("HiddenAccounts") {
  lateinit var account: Account

  override fun feed() = account.hiddenUsers()

  override fun updateChannelFilters() {}
}