package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrThreadDataSource: NostrDataSource<Note>("SingleThreadFeed") {
  val eventsToWatch = Collections.synchronizedList(mutableListOf<String>())

  fun createRepliesAndReactionsFilter(): JsonFilter? {
    val reactionsToWatch = eventsToWatch.map { it }

    if (reactionsToWatch.isEmpty()) {
      return null
    }

    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind),
      tags = mapOf("e" to reactionsToWatch)
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val nodes = synchronized(eventsToWatch) {
      eventsToWatch.map { LocalCache.notes[it] }
    }

    val eventsToLoad = nodes
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
    return synchronized(eventsToWatch) {
      eventsToWatch.map {
        LocalCache.notes[it]
      }.filterNotNull()
    }
  }

  override fun updateChannelFilters() {
    repliesAndReactionsChannel.filter = listOfNotNull(createRepliesAndReactionsFilter()).ifEmpty { null }
    loadEventsChannel.filter = listOfNotNull(createLoadEventsIfNotLoadedFilter()).ifEmpty { null }
  }

  fun loadThread(noteId: String) {
    val note = LocalCache.notes[noteId]

    if (note != null) {
      val thread = mutableListOf<Note>()
      val threadSet = mutableSetOf<Note>()

      val threadRoot = note.replyTo?.firstOrNull() ?: note

      loadDown(threadRoot, thread, threadSet)

      // Currently orders by date of each event, descending, at each level of the reply stack
      val order = compareByDescending<Note> { it.replyLevelSignature() }

      eventsToWatch.clear()
      eventsToWatch.addAll(thread.sortedWith(order).map { it.idHex })
    } else {
      eventsToWatch.clear()
      eventsToWatch.add(noteId)
    }

    resetFilters()
  }

  fun loadDown(note: Note, thread: MutableList<Note>, threadSet: MutableSet<Note>) {
    if (note !in threadSet) {
      thread.add(note)
      threadSet.add(note)

      synchronized(note.replies) {
        note.replies.toList()
      }.forEach {
        loadDown(it, thread, threadSet)
      }
    }
  }
}