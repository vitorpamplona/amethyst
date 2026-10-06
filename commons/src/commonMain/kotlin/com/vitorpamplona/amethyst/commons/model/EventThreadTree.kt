/*
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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip10Notes.BaseThreadedEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.threadRootIdOrSelf
import com.vitorpamplona.quartz.nip22Comments.CommentEvent

/**
 * Thread structure over raw [Event]s — the cache-free sibling of [ThreadAssembler],
 * which walks `Note`s in `LocalCache`. For callers that hold events but no cache
 * (amy, tests, a one-shot thread fetch): give it the events of a conversation and
 * it answers "who replies to whom" and lays them out depth-first.
 *
 * Only threaded kinds (NIP-10 kind:1, NIP-22 kind:1111 and other
 * [BaseThreadedEvent]s) take part; reactions, reposts and zaps that point at a
 * note are not replies and are left out.
 */
object EventThreadTree {
    /** The event [event] directly answers, or null for a thread root / non-threaded kind. */
    fun parentOf(event: Event): HexKey? = (event as? BaseThreadedEvent)?.replyingTo()

    /** The id of the conversation root [event] belongs to, or null when it is the root itself. */
    fun rootOf(event: Event): HexKey? =
        when (event) {
            is CommentEvent -> event.rootEventIds().firstOrNull()
            is TextNoteEvent -> event.threadRootIdOrSelf().takeIf { it != event.id }
            is BaseThreadedEvent -> event.root()?.eventId ?: event.replyingTo()
            else -> null
        }?.takeIf { it != event.id }

    class Entry(
        val event: Event,
        /** 0 for the root, 1 for its direct replies, and so on. */
        val depth: Int,
        val parentId: HexKey?,
        /** True when [parentId] names an event that was not among the inputs; the entry is hung under the root. */
        val parentMissing: Boolean,
    )

    /**
     * Lays [events] out depth-first from [rootId], children oldest-first (the order
     * a conversation is read in). A reply whose parent was not fetched is hung
     * directly under the root, marked [Entry.parentMissing], instead of being dropped.
     * When [rootId] itself is not among [events], every top-level reply starts at
     * depth 1 so depths stay comparable.
     */
    fun layout(
        rootId: HexKey,
        events: Collection<Event>,
    ): List<Entry> {
        val byId = LinkedHashMap<HexKey, Event>()
        events.forEach { if (it is BaseThreadedEvent || it.id == rootId) byId.putIfAbsent(it.id, it) }

        val children = HashMap<HexKey, MutableList<Pair<Event, Boolean>>>()
        byId.values.forEach { event ->
            if (event.id == rootId) return@forEach
            val parent = parentOf(event)
            val (attachTo, missing) =
                when {
                    parent == null -> rootId to false
                    parent == rootId || byId.containsKey(parent) -> parent to false
                    else -> rootId to true
                }
            children.getOrPut(attachTo) { mutableListOf() }.add(event to missing)
        }
        children.values.forEach { list -> list.sortWith(compareBy({ it.first.createdAt }, { it.first.id })) }

        val out = mutableListOf<Entry>()
        val visited = HashSet<HexKey>()

        fun visit(
            id: HexKey,
            depth: Int,
        ) {
            children[id]?.forEach { (child, missing) ->
                if (!visited.add(child.id)) return@forEach
                out.add(Entry(child, depth, parentOf(child), missing))
                visit(child.id, depth + 1)
            }
        }

        val root = byId[rootId]
        if (root != null) {
            visited.add(rootId)
            out.add(Entry(root, 0, null, false))
        }
        visit(rootId, 1)

        // Anything left is part of a reply cycle (A answers B answers A) unreachable from the
        // root; surface it under the root rather than losing it.
        byId.values
            .filter { it.id !in visited }
            .sortedBy { it.createdAt }
            .forEach { event ->
                visited.add(event.id)
                out.add(Entry(event, 1, parentOf(event), true))
            }
        return out
    }
}
