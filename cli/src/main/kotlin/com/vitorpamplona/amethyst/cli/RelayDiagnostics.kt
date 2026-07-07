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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.AuthMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.NoticeMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Client-wide tally of the relay feedback the crawl would otherwise never see:
 * `NOTICE` frames, `CLOSED` reasons (`auth-required` / `rate-limited` /
 * `restricted` / …), and NIP-42 `AUTH` challenges. Registered as a
 * [RelayConnectionListener] on the shared client, so every incoming message
 * during a run is counted and a REQ failure can be explained instead of
 * guessed at.
 *
 * Callbacks fire on the per-relay socket threads, so all state is concurrent.
 */
class RelayDiagnostics : RelayConnectionListener {
    private val closedByReason = ConcurrentHashMap<String, AtomicLong>()
    private val noticeSamples = ConcurrentHashMap<String, AtomicLong>()
    private val authChallenges = AtomicLong()

    override fun onIncomingMessage(
        relay: IRelayClient,
        msgStr: String,
        msg: Message,
    ) {
        when (msg) {
            // CLOSED reasons follow the NIP-01 machine-readable "word: text"
            // convention, so the prefix categorises the failure.
            is ClosedMessage -> bump(closedByReason, prefix(msg.message))
            // NOTICE is free-form; keep the (truncated) text so recurring
            // relay complaints ("too many concurrent REQs", …) are visible.
            is NoticeMessage -> if (noticeSamples.size < MAX_DISTINCT_NOTICES) bump(noticeSamples, msg.message.trim().take(80))
            is AuthMessage -> authChallenges.incrementAndGet()
            else -> Unit
        }
    }

    private fun bump(
        map: ConcurrentHashMap<String, AtomicLong>,
        key: String,
    ) {
        map.getOrPut(key) { AtomicLong() }.incrementAndGet()
    }

    /** The NIP-01 machine-readable prefix (`word` before `:`), or `other`. */
    private fun prefix(message: String): String {
        val head = message.substringBefore(':').trim().lowercase()
        return head.ifEmpty { "other" }.take(24)
    }

    fun hadFeedback(): Boolean = authChallenges.get() > 0 || closedByReason.isNotEmpty() || noticeSamples.isNotEmpty()

    /** JSON-friendly summary for the command output. */
    fun snapshot(): Map<String, Any?> =
        mapOf(
            "auth_challenges" to authChallenges.get(),
            "closed_by_reason" to closedByReason.entries.associate { it.key to it.value.get() }.toSortedMap(),
            "notices" to noticeSamples.values.sumOf { it.get() },
            "notice_top" to
                noticeSamples.entries
                    .sortedByDescending { it.value.get() }
                    .take(TOP_NOTICES)
                    .map { "${it.key} (${it.value.get()})" },
        )

    companion object {
        private const val MAX_DISTINCT_NOTICES = 500
        private const val TOP_NOTICES = 8
    }
}
