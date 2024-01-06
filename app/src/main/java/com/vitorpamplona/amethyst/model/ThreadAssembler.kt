/**
 * Copyright (c) 2023 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.service.checkNotInMainThread
import com.vitorpamplona.quartz.events.GenericRepostEvent
import com.vitorpamplona.quartz.events.RepostEvent
import kotlin.time.measureTimedValue

class ThreadAssembler {
    private fun searchRoot(
        note: Note,
        testedNotes: MutableSet<Note> = mutableSetOf(),
    ): Note? {
        if (note.replyTo == null || note.replyTo?.isEmpty() == true) return note

        if (note.event is RepostEvent || note.event is GenericRepostEvent) return note

        testedNotes.add(note)

        val markedAsRoot =
            note.event
                ?.tags()
                ?.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }
                ?.getOrNull(1)
        if (markedAsRoot != null) {
            // Check to ssee if there is an error in the tag and the root has replies
            if (LocalCache.getNoteIfExists(markedAsRoot)?.replyTo?.isEmpty() == true) {
                return LocalCache.checkGetOrCreateNote(markedAsRoot)
            }
        }

        val hasNoReplyTo = note.replyTo?.reversed()?.firstOrNull { it.replyTo?.isEmpty() == true }
        if (hasNoReplyTo != null) return hasNoReplyTo

        // recursive
        val roots =
            note.replyTo
                ?.map {
                    if (it !in testedNotes) {
                        searchRoot(it, testedNotes)
                    } else {
                        null
                    }
                }
                ?.filterNotNull()

        if (roots != null && roots.isNotEmpty()) {
            return roots[0]
        }

        return null
    }

    fun findThreadFor(noteId: String): Set<Note> {
        checkNotInMainThread()

        val (result, elapsed) =
            measureTimedValue {
                val note = LocalCache.checkGetOrCreateNote(noteId) ?: return emptySet<Note>()

                if (note.event != null) {
                    val thread = mutableSetOf<Note>()

                    val threadRoot = searchRoot(note, thread) ?: note

                    loadDown(threadRoot, thread)
                    // adds the replies of the note in case the search for Root
                    // did not added them.
                    note.replies.forEach { loadDown(it, thread) }

                    thread.toSet()
                } else {
                    setOf(note)
                }
            }

        println("Model Refresh: Thread loaded in $elapsed")

        return result
    }

    fun loadDown(
        note: Note,
        thread: MutableSet<Note>,
    ) {
        if (note !in thread) {
            thread.add(note)

            note.replies.forEach { loadDown(it, thread) }
        }
    }
}
