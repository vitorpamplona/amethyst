package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import java.util.Collections
import nostr.postr.JsonFilter

object NostrThreadDataSource: NostrDataSource<Note>("SingleThreadFeed") {
  val eventsToWatch = Collections.synchronizedList(mutableListOf<String>())

  fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = eventsToWatch.map { it.substring(0, 8) }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    return JsonFilter(
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val eventsToLoad = eventsToWatch
      .map { LocalCache.notes[it] }
      .filterNotNull()
      .filter { it.event == null }
      .map { it.idHex.substring(0, 8) }

    if (eventsToLoad.isEmpty()) {
      return null
    }

    return JsonFilter(
      ids = eventsToLoad
    )
  }

  val repliesAndReactionsChannel = requestNewChannel()
  val loadEventsChannel = requestNewChannel()

  override fun feed(): List<Note> {
    return eventsToWatch.map {
      LocalCache.notes[it]
    }.filterNotNull()
  }

  override fun updateChannelFilters() {
    repliesAndReactionsChannel.filter = createRepliesAndReactionsFilter()
    loadEventsChannel.filter = createLoadEventsIfNotLoadedFilter()
  }

  fun loadThread(noteId: String) {
    val note = LocalCache.notes[noteId] ?: return

    val thread = mutableListOf<Note>()
    val threadSet = mutableSetOf<Note>()

    val threadRoot = note.replyTo?.firstOrNull() ?: note

    loadDown(threadRoot, thread, threadSet)

    // Currently orders by date of each event, descending, at each level of the reply stack
    val order = compareByDescending<Note> { it.replyLevelSignature() }

    eventsToWatch.clear()
    eventsToWatch.addAll(thread.sortedWith(order).map { it.idHex })

    resetFilters()
  }

  fun loadDown(note: Note, thread: MutableList<Note>, threadSet: MutableSet<Note>) {
    if (note !in threadSet) {
      thread.add(note)
      threadSet.add(note)

      note.replies.forEach {
        loadDown(it, thread, threadSet)
      }
    }
  }
}