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
package com.vitorpamplona.quartz.nip01Core.core

import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.delay

/**
 * The NIP-01 replaceable/addressable supersession rule: the winner of an
 * address is the highest `created_at`, with ties broken by the LEXICALLY
 * SMALLEST id. True when THIS event beats [existing] — equal events (same id)
 * do not supersede themselves. One rule, shared by every store; the SQLite
 * triggers encode the same comparison in SQL.
 */
fun Event.supersedes(existing: Event): Boolean =
    when {
        createdAt > existing.createdAt -> true
        createdAt < existing.createdAt -> false
        else -> id < existing.id
    }

/**
 * The `created_at` a new version needs in order to supersede [newestKnown]: one second past it, or
 * [now] when the clock has already moved beyond it.
 *
 * `created_at` has one-second resolution, so republishing an address twice inside the same second
 * stamps both versions identically and leaves [supersedes] to settle it on the id tie-break — which
 * signing makes effectively random. Every store applies that rule, so the loser is dropped silently:
 * locally it never reaches the observers watching the address, and relays may keep a different
 * version than the one this client kept. Any address republished from user input (a settings toggle,
 * a room status change) hits this routinely.
 *
 * [now] is a parameter rather than read here so the rule stays pure and testable.
 */
fun nextCreatedAtToSupersede(
    newestKnown: Long,
    now: Long,
): Long = (newestKnown + 1).coerceAtLeast(now)

/**
 * How far ahead of this device's clock [awaitCreatedAtToSupersede] is still willing to wait. A
 * version further ahead than this came from another device's skewed clock rather than from this
 * client's own burst, and waiting it out could mean sleeping for hours.
 */
const val MAX_SUPERSEDE_WAIT_SECONDS = 5L

/**
 * The `created_at` for the next version of an address, waiting for the clock rather than running
 * ahead of it: if [newestKnown] already claims the current second, this suspends until that second
 * has passed and then stamps the real time.
 *
 * Prefer this to [nextCreatedAtToSupersede] wherever the caller can suspend. Both make the new
 * version win, but this one never puts a `created_at` in the future — one second of second-resolution
 * `created_at` is genuinely the floor on how often an address can be replaced, so a client that
 * replaces one faster has to wait, not invent a timestamp. Repeatedly out-stamping instead would
 * drift a second further ahead per republish, and relays reject events too far in the future.
 *
 * The wait is bounded by [MAX_SUPERSEDE_WAIT_SECONDS]; past that the only way to supersede is still
 * to out-stamp, so it falls back to [nextCreatedAtToSupersede].
 *
 * [now] is injectable so the rule can be tested against a virtual clock.
 */
suspend fun awaitCreatedAtToSupersede(
    newestKnown: Long,
    now: () -> Long = TimeUtils::now,
): Long {
    val startedAt = now()
    if (newestKnown < startedAt) return startedAt

    val secondsToWait = newestKnown - startedAt + 1
    if (secondsToWait > MAX_SUPERSEDE_WAIT_SECONDS) return nextCreatedAtToSupersede(newestKnown, startedAt)

    delay(secondsToWait * 1000)
    return maxOf(now(), newestKnown + 1)
}
