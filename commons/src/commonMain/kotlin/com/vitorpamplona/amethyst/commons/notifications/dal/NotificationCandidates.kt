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
package com.vitorpamplona.amethyst.commons.notifications.dal

import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.model.AddressableNote
import com.vitorpamplona.amethyst.commons.model.Note
import com.vitorpamplona.amethyst.commons.model.cache.EventCache
import com.vitorpamplona.amethyst.commons.model.observables.NoteSetMatchingFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

/**
 * Every cached note that could be one of [account]'s notifications, kept current as notes arrive:
 * the notes [NotificationFeedFilter.mightBeANotification] lets through. Shared by the account's
 * notification feeds, which rebuild by scanning this set instead of the whole cache.
 *
 * The rule here is only "could this reach me", which does not change with mutes, the Web of Trust
 * or the list picked at the top. Those stay in [NotificationFeedFilter.acceptableEvent], applied
 * over this set, so changing them costs a pass over the candidates, not a walk over the cache.
 */
class NotificationCandidates(
    private val account: Account,
    private val cache: EventCache,
) {
    private val observer =
        NoteSetMatchingFilter(
            filter = { note -> NotificationFeedFilter.mightBeANotification(note, me()) },
            dependents = ::childrenOfMyNote,
        )

    private fun me() = account.userProfile().pubkeyHex

    /** The candidates, or null until the first walk of the cache has finished. */
    fun snapshot(): Set<Note>? = observer.snapshot()

    val size: Int get() = observer.size

    /** Walks the cache once, off the main thread, then follows it until [scope] ends. */
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                cache.registerNoteSet(observer)
                awaitCancellation()
            } finally {
                cache.unregisterNoteSet(observer)
            }
        }
    }

    /**
     * When a note of mine arrives, what already points at it may only now reach me: a Buzz like
     * with no `p` tag, or a public-chat reply, which reach me through the note they answer. Those
     * are its reactions, boosts and zaps, and the replies down a chat chain as deep as
     * [NotificationFeedFilter.isNotifiablePublicChatReply] looks.
     */
    private fun childrenOfMyNote(note: Note): Collection<Note> {
        val author = (note as? AddressableNote)?.address?.pubKeyHex ?: note.author?.pubkeyHex
        if (author != me()) return emptyList()

        val found = ArrayList<Note>()
        val seen = HashSet<Note>()
        val queue = ArrayDeque<Pair<Note, Int>>()
        queue.add(note to 0)
        while (queue.isNotEmpty() && found.size < MAX_DEPENDENTS) {
            val (current, depth) = queue.removeFirst()
            current.reactions.values.forEach { found.addAll(it) }
            found.addAll(current.boosts)
            current.zaps.values.forEach { receipt -> receipt?.let(found::add) }
            if (depth < CHAT_CHAIN_DEPTH) {
                current.replies.forEach { reply ->
                    if (seen.add(reply)) {
                        found.add(reply)
                        queue.add(reply to depth + 1)
                    }
                }
            }
        }
        return found
    }

    companion object {
        private const val CHAT_CHAIN_DEPTH = 30
        private const val MAX_DEPENDENTS = 2_000
    }
}
