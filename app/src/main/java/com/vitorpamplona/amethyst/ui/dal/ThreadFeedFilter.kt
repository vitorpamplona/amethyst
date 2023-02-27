package com.vitorpamplona.amethyst.ui.dal

import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.ThreadAssembler

object ThreadFeedFilter: FeedFilter<Note>() {
  var noteId: String? = null

  override fun feed(): List<Note> {
    val cachedSignatures: MutableMap<Note, String> = mutableMapOf()
    val eventsToWatch = noteId?.let { ThreadAssembler().findThreadFor(it) } ?: emptySet()
    // Currently orders by date of each event, descending, at each level of the reply stack
    val order = compareByDescending<Note> { it.replyLevelSignature(cachedSignatures) }

    return eventsToWatch.sortedWith(order)
  }

  fun loadThread(noteId: String?) {
    this.noteId = noteId
  }
}