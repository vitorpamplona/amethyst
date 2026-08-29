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
