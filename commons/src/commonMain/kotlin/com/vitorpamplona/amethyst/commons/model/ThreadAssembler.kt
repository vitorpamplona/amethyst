/**
 * Copyright (c) 2025 Vitor Pamplona
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
package com.vitorpamplona.amethyst.commons.model

import androidx.compose.runtime.Stable
import com.vitorpamplona.amethyst.commons.model.cache.ICacheProvider
import com.vitorpamplona.amethyst.commons.threading.checkNotInMainThread
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.AddressableEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

class ThreadAssembler(
    private val cache: ICacheProvider,
) {
    private fun searchRoot(
        note: Note,
        testedNotes: MutableSet<Note> = mutableSetOf(),
    ): Note? {
        if (note.replyTo == null || note.replyTo?.isEmpty() == true) return note

        val noteEvent = note.event

        if (noteEvent is RepostEvent || noteEvent is GenericRepostEvent) return note

        testedNotes.add(note)

        val markedAsRoot =
            noteEvent
                ?.tags
                ?.firstOrNull { it[0] == "e" && it.size > 3 && it[3] == "root" }
                ?.getOrNull(1)
        if (markedAsRoot != null) {
            // Check to see if there is an error in the tag and the root has replies
            val rootNote = cache.getNoteIfExists(markedAsRoot) as? Note
            if (rootNote?.replyTo?.isEmpty() == true) {
                return cache.checkGetOrCreateNote(markedAsRoot) as? Note
            }
        }

        val hasNoReplyTo =
            note.replyTo?.lastOrNull {
                it.replyTo?.isEmpty() == true
            }
        if (hasNoReplyTo != null) return hasNoReplyTo

        // recursive
        val roots =
            note.replyTo?.mapNotNull {
                if (it !in testedNotes) {
                    searchRoot(it, testedNotes)
                } else {
                    null
                }
            }

        if (roots != null && roots.isNotEmpty()) {
            return roots[0]
        }

        return null
    }

    @Stable
    class ThreadInfo(
        val root: Note,
        val allNotes: ImmutableSet<Note>,
    )

    fun findRoot(noteId: String): Note? {
        val note = cache.checkGetOrCreateNote(noteId) as? Note ?: return null

        return if (note.event != null) {
            val thread = OnlyLatestVersionSet()

            searchRoot(note, thread) ?: note
        } else {
            note
        }
    }

    fun findThreadFor(noteId: String): ThreadInfo? {
        checkNotInMainThread()

        val note = cache.checkGetOrCreateNote(noteId) as? Note ?: return null

        return if (note.event != null) {
            val thread = OnlyLatestVersionSet()

            val threadRoot = searchRoot(note, thread) ?: note

            loadUp(note, thread)

            loadDown(threadRoot, thread)
            // adds the replies of the note in case the search for Root
            // did not added them.
            note.replies.forEach { loadDown(it, thread) }

            ThreadInfo(
                root = note,
                allNotes = thread.toImmutableSet(),
            )
        } else {
            ThreadInfo(
                root = note,
                allNotes = setOf(note).toImmutableSet(),
            )
        }
    }

    fun loadUp(
        note: Note,
        thread: MutableSet<Note>,
    ) {
        if (note !in thread) {
            thread.add(note)

            note.replyTo?.forEach { loadUp(it, thread) }
        }
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
    val map = hashMapOf<Address, Long>()
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
        address: Address,
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

    override fun addAll(elements: Collection<Note>): Boolean = elements.map { add(it) }.any()

    override val size: Int
        get() = set.size

    override fun clear() {
        set.clear()
        map.clear()
    }

    override fun isEmpty(): Boolean = set.isEmpty()

    override fun containsAll(elements: Collection<Note>): Boolean = set.containsAll(elements)

    override fun contains(element: Note): Boolean = set.contains(element)

    override fun iterator(): MutableIterator<Note> = set.iterator()

    override fun retainAll(elements: Collection<Note>): Boolean = set.retainAll(elements)

    override fun removeAll(elements: Collection<Note>): Boolean = elements.map { remove(it) }.any()

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
