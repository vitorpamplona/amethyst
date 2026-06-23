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
package com.vitorpamplona.amethyst.napplet

import android.os.Messenger
import com.vitorpamplona.amethyst.commons.napplet.protocol.NappletProtocolJson

/**
 * The NAP `inc` bus: a topic pub/sub that fans an `inc.emit` out to **other** napplet sessions
 * subscribed to that topic as an `inc.event` push (never echoing back to the sender). Lives in the
 * main-process broker service, which is the single point every sandbox session binds to, so it can
 * route between them.
 *
 * Subscribers are keyed by their reply [Messenger] (one per live napplet host). A napplet must have
 * declared the `inc` capability to subscribe or emit (enforced by the router before we get here).
 *
 * NOTE: Amethyst runs napplets **foreground-only, one at a time**, so in practice two sessions rarely
 * overlap and cross-napplet delivery is usually a no-op — but the bus is correct if they ever do.
 * The bus is app-wide (not scoped per author), so it deliberately allows different napplets to talk;
 * a future refinement could namespace topics by author if cross-napplet isolation is wanted.
 */
class NappletIncBus(
    private val deliver: (Messenger, String) -> Unit,
) {
    // topic -> the reply Messengers of the napplet sessions subscribed to it.
    private val subscribers = HashMap<String, MutableSet<Messenger>>()

    @Synchronized
    fun subscribe(
        who: Messenger,
        topic: String,
    ) {
        subscribers.getOrPut(topic) { mutableSetOf() }.add(who)
    }

    @Synchronized
    fun unsubscribe(
        who: Messenger,
        topic: String,
    ) {
        subscribers[topic]?.let { set ->
            set.remove(who)
            if (set.isEmpty()) subscribers.remove(topic)
        }
    }

    /** Delivers an `inc.event` for [topic] to every subscriber except [from] (no self-echo). */
    @Synchronized
    fun emit(
        from: Messenger,
        sender: String,
        topic: String,
        payloadRaw: String,
    ) {
        val set = subscribers[topic] ?: return
        if (set.isEmpty()) return
        val envelope = NappletProtocolJson.encodeIncEvent(topic, payloadRaw, sender)
        set.filter { it != from }.forEach { deliver(it, envelope) }
    }

    /** Drops [who] from every topic (e.g. when its napplet host goes away). */
    @Synchronized
    fun removeAll(who: Messenger) {
        val empties = mutableListOf<String>()
        for ((topic, set) in subscribers) {
            set.remove(who)
            if (set.isEmpty()) empties.add(topic)
        }
        empties.forEach { subscribers.remove(it) }
    }
}
