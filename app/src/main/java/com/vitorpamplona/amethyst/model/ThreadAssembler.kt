package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.amethyst.service.model.ATag
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

class ThreadAssembler {

    private fun searchRoot(note: Note, testedNotes: MutableSet<Note> = mutableSetOf()): Note? {
        if (note.replyTo == null || note.replyTo?.isEmpty() == true) return note

        testedNotes.add(note)

        val markedAsRoot = note.event?.tags()?.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }?.getOrNull(1)
        if (markedAsRoot != null) return LocalCache.checkGetOrCreateNote(markedAsRoot)

        val hasNoReplyTo = note.replyTo?.firstOrNull { it.replyTo?.isEmpty() == true }
        if (hasNoReplyTo != null) return hasNoReplyTo

        // recursive
        val roots = note.replyTo?.map {
            if (it !in testedNotes) {
                searchRoot(it, testedNotes)
            } else {
                null
            }
        }?.filterNotNull()

        if (roots != null && roots.isNotEmpty()) {
            return roots[0]
        }

        return null
    }

    @OptIn(ExperimentalTime::class)
    fun findThreadFor(noteId: String): Set<Note> {
        checkNotInMainThread()

        val (result, elapsed) = measureTimedValue {
            val note = if (noteId.contains(":")) {
                val aTag = ATag.parse(noteId, null)
                if (aTag != null) {
                    LocalCache.getOrCreateAddressableNote(aTag)
                } else {
                    return emptySet()
                }
            } else {
                LocalCache.getOrCreateNote(noteId)
            }

            if (note.event != null) {
                val thread = mutableSetOf<Note>()

                val threadRoot = searchRoot(note, thread) ?: note

                loadDown(threadRoot, thread)
                // adds the replies of the note in case the search for Root
                // did not added them.
                note.replies.forEach {
                    loadDown(it, thread)
                }

                thread.toSet()
            } else {
                setOf(note)
            }
        }

        println("Model Refresh: Thread loaded in $elapsed")

        return result
    }

    fun loadDown(note: Note, thread: MutableSet<Note>) {
        if (note !in thread) {
            thread.add(note)

            note.replies.forEach {
                loadDown(it, thread)
            }
        }
    }
}
