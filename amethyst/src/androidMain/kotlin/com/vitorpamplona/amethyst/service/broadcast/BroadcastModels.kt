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
package com.vitorpamplona.amethyst.service.broadcast

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

/**
 * Result of a relay's response to an event publish.
 */
@Immutable
sealed class RelayResult {
    /** Relay accepted the event (OK message with success=true) */
    data object Success : RelayResult()

    /** Relay rejected the event (OK message with success=false) */
    data class Error(
        val message: String,
    ) : RelayResult()

    /** Relay did not respond within timeout */
    data object Timeout : RelayResult()

    /** Waiting for relay response */
    data object Pending : RelayResult()

    /** Retry in progress for this relay */
    data object Retrying : RelayResult()
}

/**
 * Overall status of a broadcast operation.
 */
enum class BroadcastStatus {
    /** Currently waiting for relay responses */
    IN_PROGRESS,

    /** All relays accepted the event */
    SUCCESS,

    /** Some relays accepted, some failed */
    PARTIAL,

    /** No relays accepted the event */
    FAILED,
}

/**
 * Tracks a single event broadcast to multiple relays.
 */
@Immutable
data class BroadcastEvent(
    val id: String,
    val event: Event,
    val targetRelays: List<NormalizedRelayUrl>,
    val startedAt: Long = System.currentTimeMillis(),
    val results: Map<NormalizedRelayUrl, RelayResult> = emptyMap(),
    val status: BroadcastStatus = BroadcastStatus.IN_PROGRESS,
) {
    /** Number of relays that accepted the event */
    val successCount: Int
        get() = results.count { it.value is RelayResult.Success }

    /** Number of relays that rejected or timed out */
    val failureCount: Int
        get() = results.count { it.value is RelayResult.Error || it.value is RelayResult.Timeout }

    /** Number of relays still pending response */
    val pendingCount: Int
        get() = targetRelays.size - results.size

    /** Total number of target relays */
    val totalRelays: Int
        get() = targetRelays.size

    /** Progress as a fraction (0.0 to 1.0) */
    val progress: Float
        get() = if (totalRelays == 0) 0f else results.size.toFloat() / totalRelays

    /** Whether all relays have responded */
    val isComplete: Boolean
        get() = results.size >= targetRelays.size

    /** List of relays that failed and are not currently retrying */
    val failedRelays: List<NormalizedRelayUrl>
        get() =
            results
                .filter {
                    (it.value is RelayResult.Error || it.value is RelayResult.Timeout) &&
                        it.value !is RelayResult.Retrying
                }.keys
                .toList()

    /** List of relays currently being retried */
    val retryingRelays: List<NormalizedRelayUrl>
        get() = results.filter { it.value is RelayResult.Retrying }.keys.toList()

    /** Creates a copy with an updated relay result */
    fun withResult(
        relay: NormalizedRelayUrl,
        result: RelayResult,
    ): BroadcastEvent {
        val newResults = results + (relay to result)
        val newStatus =
            when {
                newResults.size < targetRelays.size -> BroadcastStatus.IN_PROGRESS
                newResults.all { it.value is RelayResult.Success } -> BroadcastStatus.SUCCESS
                newResults.none { it.value is RelayResult.Success } -> BroadcastStatus.FAILED
                else -> BroadcastStatus.PARTIAL
            }
        return copy(results = newResults, status = newStatus)
    }
}
