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
package com.vitorpamplona.amethyst.commons.relays

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

typealias SincePerRelayMap = MutableMap<NormalizedRelayUrl, MutableTime>

/**
 * Tracks EOSE (End Of Stored Events) timestamps for relay subscriptions.
 * Used by UserCardsCache and similar classes to manage relay subscription state.
 */
class EOSERelayList {
    var relayList: SincePerRelayMap = mutableMapOf()

    /**
     * Writers are serialized because they are not all on one thread: [addOrUpdate] runs on each
     * relay's own socket-reader thread as EOSE frames land, so a client holding a few hundred relays
     * has that many potential writers to one plain map. [EOSEAccountFast] wraps its lists in a lock
     * for the same reason; a bare list handed to [SingleSubEoseManager] had none.
     *
     * Reads still go through the map returned by [since] — deliberately live rather than a snapshot,
     * since callers clear a relay and then re-read it inside one assembly pass.
     */
    private val lock = KmpLock()

    fun addOrUpdate(
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) = lock.withLock {
        val eose = relayList[relayUrl]
        if (eose == null) {
            relayList[relayUrl] = MutableTime(time)
        } else {
            eose.updateIfNewer(time)
        }
    }

    fun clear() =
        lock.withLock {
            relayList = mutableMapOf()
        }

    /**
     * Forgets one relay's cursor, so the next filter built for it asks from scratch.
     *
     * Needed when a subscription's *scope* widens rather than its contents changing — a merged filter
     * that starts covering another account has already-EOSE'd relays whose `since` would silence
     * exactly the history the new account still needs.
     */
    fun remove(relayUrl: NormalizedRelayUrl) =
        lock.withLock {
            relayList.remove(relayUrl)
        }

    fun since() = relayList

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(relay, time)
}
