package com.vitorpamplona.amethyst.service

import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.service.model.ReactionEvent
import com.vitorpamplona.amethyst.service.model.RepostEvent
import java.util.Collections
import nostr.postr.JsonFilter
import nostr.postr.events.TextNoteEvent

object NostrThreadDataSource: NostrDataSource<Note>("SingleThreadFeed") {
  private var eventsToWatch = setOf<String>()

  fun createRepliesAndReactionsFilter(): JsonFilter? {
    if (eventsToWatch.isEmpty()) {
      return null
    }

    return JsonFilter(
      kinds = listOf(TextNoteEvent.kind, ReactionEvent.kind, RepostEvent.kind),
      tags = mapOf("e" to eventsToWatch.toList())
    )
  }

  fun createLoadEventsIfNotLoadedFilter(): JsonFilter? {
    val nodes = eventsToWatch.map { LocalCache.getOrCreateNote(it) }

    val eventsToLoad = nodes
      .filter { it.event == null }
      .map { it.idHex.substring(0, 8) }

    if (eventsToLoad.isEmpty()) {
      return null
    }

    return JsonFilter(
      ids = eventsToLoad
    )
  }

  val loadEventsChannel = requestNewChannel()

  override fun feed(): List<Note> {
    // Currently orders by date of each event, descending, at each level of the reply stack
    val order = compareByDescending<Note> { it.replyLevelSignature() }

    return eventsToWatch.map {
      LocalCache.getOrCreateNote(it)
    }.sortedWith(order)
  }

  override fun updateChannelFilters() {
    loadEventsChannel.filter = listOfNotNull(createLoadEventsIfNotLoadedFilter(), createRepliesAndReactionsFilter()).ifEmpty { null }
  }

  fun searchRoot(note: Note, testedNotes: MutableSet<Note> = mutableSetOf()): Note? {
    if (note.replyTo == null || note.replyTo?.isEmpty() == true) return note

    val markedAsRoot = note.event?.tags?.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }?.getOrNull(1)
    if (markedAsRoot != null) return LocalCache.getOrCreateNote(markedAsRoot)

    val hasNoReplyTo = note.replyTo?.firstOrNull { it.replyTo?.isEmpty() == true }
    if (hasNoReplyTo != null) return hasNoReplyTo

    testedNotes.add(note)

    // recursive
    val roots = note.replyTo?.map {
      if (it !in testedNotes)
        searchRoot(it, testedNotes)
      else
        null
    }?.filterNotNull()

    if (roots != null && roots.isNotEmpty()) {
      return roots[0]
    }

    return null
  }

  fun loadThread(noteId: String) {
    val note = LocalCache.getOrCreateNote(noteId)

    if (note.event != null) {
      val thread = mutableListOf<Note>()
      val threadSet = mutableSetOf<Note>()

      val threadRoot = searchRoot(note) ?: note

      loadDown(threadRoot, thread, threadSet)

      eventsToWatch = thread.map { it.idHex }.toSet()
    } else {
      eventsToWatch = setOf(noteId)
    }

    invalidateFilters()
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