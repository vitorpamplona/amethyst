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
import com.vitorpamplona.amethyst.commons.model.observables.CandidateSet
import com.vitorpamplona.quartz.nip28PublicChat.message.ChannelMessageEvent
import kotlinx.coroutines.CoroutineScope

/**
 * Every cached note that could be one of [account]'s notifications, kept current as notes arrive:
 * the notes [NotificationFeedFilter.mightBeANotification] lets through. Shared by the account's
 * notification feeds, which rebuild by scanning it instead of the whole cache.
 *
 * The rule here is only "could this reach me", which does not change with mutes, the Web of Trust
 * or the list picked at the top. Those stay in [NotificationFeedFilter.acceptableEvent], applied
 * over the set, so changing them costs a pass over the candidates, not a walk over the cache.
 * Held strongly: these are the notes that reach this account, a small part of the cache (chat in
 * a group channel, the one broad case, is held by its channel anyway).
 */
object NotificationCandidates {
    fun create(
        account: Account,
        cache: EventCache,
        scope: CoroutineScope,
    ): CandidateSet {
        val me = { account.userProfile().pubkeyHex }
        return CandidateSet(
            cache = cache,
            scope = scope,
            filter = NotificationFeedFilter.universeFor(account),
            dependents = { note -> childrenOfMyNote(note, me()) },
        )
    }

    /**
     * When a note of mine arrives, what already points at it may only now reach me: a Buzz like
     * with no `p` tag, or a public-chat reply, which reach me through the note they answer. Those
     * are its reactions, boosts and zaps, and the replies down a chat chain as deep as
     * [NotificationFeedFilter.isNotifiablePublicChatReply] looks.
     */
    private fun childrenOfMyNote(
        note: Note,
        me: String,
    ): Collection<Note> {
        val author = (note as? AddressableNote)?.address?.pubKeyHex ?: note.author?.pubkeyHex
        if (author != me) return chatRepliesBelow(note)

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

    /**
     * A chat message that is not mine can still be the missing link between my message and a reply
     * further down ([NotificationFeedFilter.isNotifiablePublicChatReply] walks up through it): when
     * it loads after the replies below it, they are checked again. Usually it has none yet.
     */
    private fun chatRepliesBelow(note: Note): Collection<Note> {
        if (note.event !is ChannelMessageEvent || note.replies.isEmpty()) return emptyList()

        val found = ArrayList<Note>()
        val seen = HashSet<Note>()
        val queue = ArrayDeque<Pair<Note, Int>>()
        queue.add(note to 0)
        while (queue.isNotEmpty() && found.size < MAX_DEPENDENTS) {
            val (current, depth) = queue.removeFirst()
            if (depth >= CHAT_CHAIN_DEPTH) continue
            current.replies.forEach { reply ->
                if (reply.event is ChannelMessageEvent && seen.add(reply)) {
                    found.add(reply)
                    queue.add(reply to depth + 1)
                }
            }
        }
        return found
    }

    private const val CHAT_CHAIN_DEPTH = 30

    // Walked on the cache's insert path, for every note of mine that arrives: a bound on the work.
    // Collecting them is cheap; the set skips the filter for those it already has.
    private const val MAX_DEPENDENTS = 2_000
}
