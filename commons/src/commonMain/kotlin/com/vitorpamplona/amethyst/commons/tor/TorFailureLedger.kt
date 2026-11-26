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
package com.vitorpamplona.amethyst.commons.tor

import com.vitorpamplona.amethyst.commons.util.KmpLock
import com.vitorpamplona.amethyst.commons.util.withLock
import com.vitorpamplona.quartz.utils.TimeUtils

/** Why Arti could not open a stream to one host: its error kind and the SOCKS5 reply it sent. */
data class TorConnectFailure(
    val kind: String,
    val socksReply: Int?,
)

/**
 * The recent "Failed to connect through Tor to HOST:PORT" lines of Arti's log, by host and port.
 *
 * On Android and the desktop, OkHttp reaches Tor through Java's SOCKS client, which turns Arti's
 * SOCKS5 reply into a `SocketException` ("SOCKS: Host unreachable" for 0x04), and the relay client
 * reads that to tell a dead domain (parked for minutes) from a busy relay. iOS's NSURLSession drops
 * the reply and reports most of them as -1000 "bad URL", so the reason is taken from Arti's log
 * instead ([explain]).
 */
class TorFailureLedger(
    private val now: () -> Long = TimeUtils::nowMillis,
    private val keepMs: Long = KEEP_MS,
) {
    private val lock = KmpLock()
    private val failures = HashMap<String, Pair<Long, TorConnectFailure>>()

    /** Reads one Arti log line; anything but a stream failure is ignored. */
    fun record(line: String) {
        val match = FAILURE.find(line) ?: return
        val (host, port, kind) = match.destructured
        val reply =
            SOCKS_REPLY
                .find(line)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull(16)
        val key = key(host, port.toIntOrNull() ?: return)
        lock.withLock {
            failures[key] = now() to TorConnectFailure(kind, reply)
            if (failures.size > MAX_ENTRIES) {
                val cutoff = now() - keepMs
                failures.entries.removeAll { it.value.first < cutoff }
            }
        }
    }

    /** Arti's reason for the last failed stream to [host]:[port], if it was recent; read once. */
    fun explain(
        host: String,
        port: Int,
    ): TorConnectFailure? =
        lock.withLock {
            val entry = failures.remove(key(host, port)) ?: return@withLock null
            entry.second.takeIf { now() - entry.first <= keepMs }
        }

    private fun key(
        host: String,
        port: Int,
    ) = host.lowercase() + ":" + port

    companion object {
        private const val KEEP_MS = 30_000L
        private const val MAX_ENTRIES = 512
        private val FAILURE = Regex("""Failed to connect through Tor to ([^\s:]+):(\d+): kind=([A-Za-z]+)""")
        private val SOCKS_REPLY = Regex("""socks_reply=0x([0-9a-fA-F]{1,2})""")
    }
}

/**
 * The message Java's SOCKS client (`SocksSocketImpl`) gives a SOCKS5 [reply], which is what OkHttp
 * reports for a Tor-routed connection on Android and the desktop.
 */
fun socksReplyMessage(reply: Int?): String =
    when (reply) {
        1 -> "SOCKS server general failure"
        2 -> "SOCKS: Connection not allowed by ruleset"
        3 -> "SOCKS: Network unreachable"
        4 -> "SOCKS: Host unreachable"
        5 -> "SOCKS: Connection refused"
        6 -> "SOCKS: TTL expired"
        7 -> "SOCKS: Command not supported"
        8 -> "SOCKS: address type not supported"
        else -> "SOCKS: Reply error"
    }
