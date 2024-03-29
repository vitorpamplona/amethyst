/**
 * Copyright (c) 2024 Vitor Pamplona
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
import com.vitorpamplona.quartz.encoders.ATag
import com.vitorpamplona.quartz.events.AddressableEvent
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
                    val thread = OnlyLatestVersionSet()

                    val threadRoot = searchRoot(note, thread) ?: note

                    loadDown(threadRoot, thread)
                    // adds the replies of the note in case the search for Root
                    // did not added them.
                    note.replies.forEach { loadDown(it, thread) }

                    thread
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

class OnlyLatestVersionSet : MutableSet<Note> {
    val map = hashMapOf<ATag, Long>()
    val set = hashSetOf<Note>()

    override fun add(element: Note): Boolean {
        val loadedCreatedAt = element.createdAt()
        val noteEvent = element.event

        return if (element is AddressableNote && loadedCreatedAt != null) {
            innerAdd(element.address, element, loadedCreatedAt)
        } else if (noteEvent is AddressableEvent && loadedCreatedAt != null) {
            innerAdd(noteEvent.address(), element, loadedCreatedAt)
        } else {
            set.add(element)
        }
    }

    private fun innerAdd(
        address: ATag,
        element: Note,
        loadedCreatedAt: Long,
    ): Boolean {
        val existing = map.get(address)
        return if (existing == null) {
            map.put(address, loadedCreatedAt)
            set.add(element)
        } else {
            if (loadedCreatedAt > existing) {
                map.put(address, loadedCreatedAt)
                set.add(element)
            } else {
                false
            }
        }
    }

    override fun addAll(elements: Collection<Note>): Boolean {
        return elements.map { add(it) }.any()
    }

    override val size: Int
        get() = set.size

    override fun clear() {
        set.clear()
        map.clear()
    }

    override fun isEmpty(): Boolean {
        return set.isEmpty()
    }

    override fun containsAll(elements: Collection<Note>): Boolean {
        return set.containsAll(elements)
    }

    override fun contains(element: Note): Boolean {
        return set.contains(element)
    }

    override fun iterator(): MutableIterator<Note> {
        return set.iterator()
    }

    override fun retainAll(elements: Collection<Note>): Boolean {
        return set.retainAll(elements)
    }

    override fun removeAll(elements: Collection<Note>): Boolean {
        return elements.map { remove(it) }.any()
    }

    override fun remove(element: Note): Boolean {
        element.address()?.let {
            map.remove(it)
        }
        (element.event as? AddressableEvent)?.address()?.let {
            map.remove(it)
        }

        return set.remove(element)
    }
}
