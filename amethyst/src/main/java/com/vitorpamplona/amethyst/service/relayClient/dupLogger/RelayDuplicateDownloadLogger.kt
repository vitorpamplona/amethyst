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
package com.vitorpamplona.amethyst.service.relayClient.dupLogger

import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.EventMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.displayUrl
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.bytesUsedInMemory
import com.vitorpamplona.quartz.utils.cache.LargeCache

/**
 * Debug-only logger that measures how good our filter design is at NOT downloading
 * the same event twice from the same relay.
 *
 * A relay normally returns each matching event only once per subscription. So a
 * second arrival of the same (relay, eventId) means one of our filters is doing
 * redundant work:
 *  - if it arrives again on the SAME subscription id, that subscription re-REQ'd
 *    without a tight enough `since` (an EOSE/since refinement opportunity);
 *  - if it arrives on a DIFFERENT subscription id, two assemblers have overlapping
 *    filters against the same relay (a filter-merging/narrowing opportunity).
 *
 * For every duplicate it prints an immediate warning that includes the subscription
 * ids involved — those ids can be matched to the exact filter that was sent by
 * cross-referencing the REQ frames printed by [RelayLogger]. It also keeps a rolling
 * [DuplicateDownloadStats] digest grouped by the offending interaction, relay and kind.
 *
 * Memory: it remembers every (relay, eventId) seen for the lifetime of the session,
 * which is why it must only be enabled on debug builds.
 *
 * Wire it up the same way as [RelaySpeedLogger]:
 * ```
 * val dupLogger = if (isDebug) RelayDuplicateDownloadLogger(client) else null
 * ```
 */
class RelayDuplicateDownloadLogger(
    val client: INostrClient,
) {
    companion object {
        val TAG: String = RelayDuplicateDownloadLogger::class.java.simpleName
    }

    // every (relay, eventId) we have downloaded, plus the subs that delivered it.
    private val seen = LargeCache<Int, DownloadRecord>()

    private val window = DuplicateDownloadStats()

    private fun key(
        relayHash: Int,
        eventIdHash: Int,
    ): Int = 31 * relayHash + eventIdHash

    private val clientListener =
        object : RelayConnectionListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is EventMessage) {
                    record(relay, msg, msgStr.bytesUsedInMemory())
                }
            }
        }

    private fun record(
        relay: IRelayClient,
        msg: EventMessage,
        memory: Int,
    ) {
        val event = msg.event
        val record =
            seen.getOrCreate(key(relay.url.hashCode(), event.id.hashCode())) {
                DownloadRecord(relay.url, event.id, event.kind, event.pubKey)
            }

        val dup = record.register(msg.subId, memory) ?: return

        window.add(dup)

        Log.w(TAG) {
            "DUP x${dup.downloads} [${dup.category}] ${relay.url.displayUrl()}: " +
                "kind ${dup.kind} ev ${dup.eventId.take(8)} by ${dup.author.take(8)}; " +
                "subs ${dup.subscriptions}; +${dup.lastBytes}b (total ${dup.wastedBytesTotal}b wasted)"
        }
    }

    init {
        Log.d(TAG, "Init, Subscribe")
        client.addConnectionListener(clientListener)
    }

    fun destroy() {
        Log.d(TAG, "Destroy, Unsubscribe")
        client.removeConnectionListener(clientListener)
    }
}
