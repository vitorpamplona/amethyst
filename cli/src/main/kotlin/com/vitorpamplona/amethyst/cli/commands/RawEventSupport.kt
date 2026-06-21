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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/**
 * Shared helpers for the nak-style raw-event verbs (`event`, `publish`,
 * `fetch`, `subscribe`, `count`). Kept tiny — parsing/normalisation only,
 * no protocol logic.
 */
object RawEventSupport {
    /**
     * Read a blob from the first positional argument, or from stdin when the
     * argument is omitted or `-`. Used by verbs that take event/filter JSON.
     */
    fun readArgOrStdin(args: Args): String {
        val arg = args.positionalOrNull(0)
        return if (arg == null || arg == "-") {
            System.`in`
                .readBytes()
                .decodeToString()
                .trim()
        } else {
            arg.trim()
        }
    }

    /** Parse a `--relay a,b,c` flag into normalized relay URLs (silently drops un-normalizable entries). */
    fun relayFlag(args: Args): Set<NormalizedRelayUrl> =
        args
            .flag("relay")
            ?.split(',')
            ?.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it.trim()) }
            ?.toSet()
            .orEmpty()

    /**
     * Resolve where to publish: the explicit `--relay` set when given,
     * otherwise the account's NIP-65 outbox. Empty only when neither is
     * available (caller turns that into a `no_relays` error).
     */
    suspend fun publishTargets(
        ctx: Context,
        args: Args,
    ): Set<NormalizedRelayUrl> = relayFlag(args).ifEmpty { ctx.outboxRelays() }
}
