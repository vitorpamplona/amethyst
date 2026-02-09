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

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

typealias SincePerRelayMap = MutableMap<NormalizedRelayUrl, MutableTime>

/**
 * Tracks EOSE (End Of Stored Events) timestamps for relay subscriptions.
 * Used by UserCardsCache and similar classes to manage relay subscription state.
 */
class EOSERelayList {
    var relayList: SincePerRelayMap = mutableMapOf()

    fun addOrUpdate(
        relayUrl: NormalizedRelayUrl,
        time: Long,
    ) {
        val eose = relayList[relayUrl]
        if (eose == null) {
            relayList[relayUrl] = MutableTime(time)
        } else {
            eose.updateIfNewer(time)
        }
    }

    fun clear() {
        relayList = mutableMapOf()
    }

    fun since() = relayList

    fun newEose(
        relay: NormalizedRelayUrl,
        time: Long,
    ) = addOrUpdate(relay, time)
}
