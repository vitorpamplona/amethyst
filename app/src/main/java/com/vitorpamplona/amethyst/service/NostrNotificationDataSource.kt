package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.Note
import nostr.postr.JsonFilter

object NostrNotificationDataSource: NostrDataSource("GlobalFeed") {
  lateinit var account: Account

  fun createGlobalFilter() = JsonFilter(
    since = System.currentTimeMillis() / 1000 - (60 * 60 * 24 * 7), // 2 days
    tags = mapOf("p" to listOf(account.userProfile().pubkeyHex).filterNotNull())
  )

  val notificationChannel = requestNewChannel()

  fun <T> equalsIgnoreOrder(list1:List<T>?, list2:List<T>?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return list1.size == list2.size && list1.toSet() == list2.toSet()
  }

  fun equalFilters(list1:JsonFilter?, list2:JsonFilter?): Boolean {
    if (list1 == null && list2 == null) return true
    if (list1 == null) return false
    if (list2 == null) return false

    return equalsIgnoreOrder(list1.tags?.get("p"), list2.tags?.get("p"))
        && equalsIgnoreOrder(list1.tags?.get("e"), list2.tags?.get("e"))
  }

  override fun feed(): List<Note> {
    return account.userProfile().taggedPosts
      .filter { it.event != null }
      .sortedBy { it.event!!.createdAt }
      .reversed()
  }

  override fun updateChannelFilters() {
    val newFilter = createGlobalFilter()

    if (!equalFilters(newFilter, notificationChannel.filter)) {
      notificationChannel.filter = newFilter
    }
  }
}