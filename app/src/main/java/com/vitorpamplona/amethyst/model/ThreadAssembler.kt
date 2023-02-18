package com.vitorpamplona.amethyst.model

import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadAssembler {

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

  @OptIn(ExperimentalTime::class)
  fun findThreadFor(noteId: String): Set<Note> {
    val (result, elapsed) = measureTimedValue {
      val note = LocalCache.getOrCreateNote(noteId)

      if (note.event != null) {
        val thread = mutableListOf<Note>()
        val threadSet = mutableSetOf<Note>()

        val threadRoot = searchRoot(note) ?: note

        loadDown(threadRoot, thread, threadSet)

        thread.toSet()
      } else {
        setOf(note)
      }
    }

    println("Model Refresh: Thread loaded in ${elapsed}")

    return result
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