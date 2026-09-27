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
package com.vitorpamplona.quartz.nip98HttpAuth

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.utils.TimeUtils
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs

/**
 * Server-side counterpart to [HTTPAuthorizationEvent]. Verifies a
 * NIP-98 `Authorization: Nostr <base64-event>` header.
 *
 * NIP-98 reuses kind 27235 events with `u`, `method`, and (for bodies)
 * `payload` tags. Verification checks:
 *  1. Header is `Nostr <base64>`.
 *  2. Decoded body is a kind-27235 event with a valid Schnorr signature.
 *  3. The event's `created_at` is within ±[toleranceSeconds] of now.
 *  4. The `method` tag matches the HTTP method.
 *  5. The `u` tag matches the requested URL.
 *  6. If a body is present, the `payload` tag matches `sha256(body)` hex.
 *
 * Returns the verified pubkey on success; a [Result.Malformed] /
 * [Result.Missing] otherwise (the caller turns these into 401/403).
 */
class Nip98AuthVerifier(
    private val now: () -> Long = { TimeUtils.now() },
    /** Allowed clock skew in seconds. NIP-98 says 60. */
    private val toleranceSeconds: Long = 60,
) {
    /**
     * Recently-accepted event ids → expiry epoch second. Bounded to
     * [MAX_REPLAY_ENTRIES] (insertion-order eviction); each entry expires
     * after `2 × toleranceSeconds` (twice the accepted window so a token
     * can't be reused by an attacker who buffers across the boundary).
     *
     * Guarded by [seenLock] so the eviction sweep + insertion are
     * atomic. We use a coroutine [Mutex] so the type works in KMP
     * commonMain (no `synchronized` block).
     */
    private val seenEventIds: LinkedHashMap<String, Long> = LinkedHashMap()

    private val seenLock = Mutex()

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun verify(
        authorizationHeader: String?,
        method: String,
        url: String,
        body: ByteArray?,
    ): Result {
        if (authorizationHeader.isNullOrBlank()) return Result.Missing
        if (!authorizationHeader.startsWith(SCHEME)) return Result.Malformed("expected '$SCHEME <base64>' header")

        val token = authorizationHeader.substring(SCHEME.length).trim()
        val json =
            try {
                Base64.decode(token).decodeToString()
            } catch (_: IllegalArgumentException) {
                return Result.Malformed("token is not valid base64")
            }

        val event =
            try {
                OptimizedJsonMapper.fromJson(json)
            } catch (_: Exception) {
                return Result.Malformed("token does not decode to a Nostr event")
            }

        if (event.kind != HTTPAuthorizationEvent.KIND) {
            return Result.Malformed("event kind ${event.kind} != ${HTTPAuthorizationEvent.KIND}")
        }
        if (!event.verify()) return Result.Malformed("bad event signature or id")

        val nowSec = now()
        val skew = abs(event.createdAt - nowSec)
        if (skew > toleranceSeconds) {
            return Result.Malformed("created_at is ${skew}s away from now (max ${toleranceSeconds}s)")
        }

        // Re-wrap as the typed event so the tag accessors work.
        val auth =
            HTTPAuthorizationEvent(
                event.id,
                event.pubKey,
                event.createdAt,
                event.tags,
                event.content,
                event.sig,
            )

        if (!auth.method().equals(method, ignoreCase = true)) {
            return Result.Malformed("method mismatch: expected $method, got ${auth.method()}")
        }
        if (auth.url() != url) {
            return Result.Malformed("url mismatch: expected $url, got ${auth.url()}")
        }
        if (body != null && body.isNotEmpty()) {
            val expected = sha256(body).toHexKey()
            if (auth.payloadHash() != expected) {
                return Result.Malformed("payload hash mismatch")
            }
        }

        // Replay check — done LAST so we don't burn a one-shot id on a
        // request that would otherwise have failed signature/url/etc.
        val expiry = nowSec + 2 * toleranceSeconds
        seenLock.withLock {
            // Evict expired entries while we hold the lock. Insertion
            // order (LinkedHashMap default) tracks expiry order
            // because every entry's expiry = now + 2·tolerance, so
            // the first non-expired entry guarantees no later entry
            // is expired either.
            val it = seenEventIds.entries.iterator()
            while (it.hasNext()) {
                if (it.next().value <= nowSec) it.remove() else break
            }
            if (seenEventIds.put(event.id, expiry) != null) {
                return Result.Malformed("replay: this NIP-98 token has already been used")
            }
            // Cap entries: drop oldest by insertion order. Equivalent to
            // the JDK LinkedHashMap.removeEldestEntry hook we used before,
            // but works in KMP commonMain.
            while (seenEventIds.size > MAX_REPLAY_ENTRIES) {
                val eldest = seenEventIds.keys.iterator()
                if (!eldest.hasNext()) break
                eldest.next()
                eldest.remove()
            }
        }

        return Result.Verified(event.pubKey)
    }

    sealed interface Result {
        data class Verified(
            val pubkey: HexKey,
        ) : Result

        object Missing : Result

        data class Malformed(
            val reason: String,
        ) : Result
    }

    companion object {
        const val SCHEME = "Nostr "

        /**
         * Cap on the in-memory replay-cache size. With a 60s tolerance
         * an attacker would need to push >MAX/120 verified requests per
         * second (one new id per ~120 ms) to evict legitimate entries.
         * 1024 is generous for an admin endpoint.
         */
        const val MAX_REPLAY_ENTRIES = 1024
    }
}
