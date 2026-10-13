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
package com.vitorpamplona.quartz.nip01Core.relay.server.policies

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.CountCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.EventCmd
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import kotlin.concurrent.Volatile

/**
 * Refuses a client's EVENTs and REQs (COUNTs, NEG-OPENs) past their rates with a NIP-01
 * `rate-limited:` refusal that states its back-off: `["OK", id, false, "rate-limited: …", "1500"]`,
 * `["CLOSED", sub, "rate-limited: …", "1500"]`. The back-off is exactly how long the client's
 * bucket needs for its next token, so a client that waits it out is let through.
 *
 * Built per connection, around limiters shared by all of them ([RateLimiter] is per client,
 * not per socket). The client is [RequestContext.clientAddress] when the transport knows it —
 * an IPv6 address by its /64, since one host routinely holds a whole /64 and could otherwise
 * take a fresh allowance per address — else the connection. A null limiter leaves that command type unlimited. AUTH and CLOSE are
 * never limited: refusing them only makes a client hold subscriptions or lose its identity.
 *
 * Stack it first ([IRelayPolicy.plus]) so a flood is turned away before signature checks.
 */
class RateLimitPolicy(
    private val events: RateLimiter?,
    private val reqs: RateLimiter?,
) : PassThroughPolicy() {
    @Volatile
    private var client: String = "connection:?"

    override fun onConnect(
        scope: RequestContext,
        send: (Message) -> Unit,
    ) {
        client = scope.clientAddress?.let(::clientKeyOf) ?: "connection:${scope.connectionId}"
    }

    override fun accept(cmd: EventCmd): PolicyResult<EventCmd> = check(events, EVENTS_REFUSED) ?: PolicyResult.Accepted(cmd)

    override fun accept(cmd: ReqCmd): PolicyResult<ReqCmd> = check(reqs, REQS_REFUSED) ?: PolicyResult.Accepted(cmd)

    override fun accept(cmd: CountCmd): PolicyResult<CountCmd> = check(reqs, REQS_REFUSED) ?: PolicyResult.Accepted(cmd)

    private fun <T : Command> check(
        limiter: RateLimiter?,
        message: String,
    ): PolicyResult<T>? {
        val wait = limiter?.acquire(client) ?: return null
        return if (wait > 0) PolicyResult.Rejected.rateLimited(message, wait) else null
    }

    companion object {
        const val EVENTS_REFUSED = "too many events, slow down"
        const val REQS_REFUSED = "too many subscriptions opened, slow down"

        /**
         * Who [address] counts as: an IPv6 address by its /64 (`2001:db8:0:1::/64`), anything
         * else (IPv4, an IPv4-mapped IPv6, a name) as itself. Brackets and a `%zone` are dropped.
         */
        fun clientKeyOf(address: String): String {
            val bare = address.removePrefix("[").substringBefore(']').substringBefore('%')
            if (':' !in bare) return bare
            // ::ffff:203.0.113.7 is that IPv4 client.
            if ('.' in bare) return bare.substringAfterLast(':')
            val groups = ipv6Groups(bare) ?: return bare
            return groups.take(4).joinToString(":") { it.toString(16) } + "::/64"
        }

        /** The eight 16-bit groups of [address], expanding one `::`; null when it is not IPv6. */
        private fun ipv6Groups(address: String): List<Int>? {
            val halves = address.split("::")
            if (halves.size > 2) return null
            val head = hexGroups(halves[0]) ?: return null
            val tail = if (halves.size == 2) hexGroups(halves[1]) ?: return null else emptyList()
            val missing = 8 - head.size - tail.size
            if (halves.size == 1 && missing != 0) return null
            if (halves.size == 2 && missing < 1) return null
            return head + List(missing) { 0 } + tail
        }

        /** `2001:db8:1` as its groups; null when any is not 1-4 hex digits. */
        private fun hexGroups(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            return part.split(':').map { group ->
                if (group.length !in 1..4) return null
                group.toIntOrNull(16) ?: return null
            }
        }
    }
}
