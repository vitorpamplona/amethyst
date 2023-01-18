package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.JsonFilter

object NostrNotificationDataSource: NostrDataSource<Note>("NotificationFeed") {
  lateinit var account: Account

  fun createNotificationFilter() = JsonFilter(
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex)),
    limit = 100
  )

  val notificationChannel = requestNewChannel()

  override fun feed(): List<Note> {
    val set = account.userProfile().taggedPosts
    val filtered = synchronized(set) {
      set.filter { it.event != null }
    }

    return filtered.sortedBy { it.event!!.createdAt }.reversed()
  }

  override fun updateChannelFilters() {
    notificationChannel.filter = listOf(createNotificationFilter()).ifEmpty { null }
  }
}