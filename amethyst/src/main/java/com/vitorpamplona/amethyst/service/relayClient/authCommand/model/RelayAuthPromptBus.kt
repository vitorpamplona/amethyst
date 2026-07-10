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
package com.vitorpamplona.amethyst.service.relayClient.authCommand.model

import com.vitorpamplona.amethyst.commons.relayauth.AuthPurpose
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/** What the user chose when asked whether to authenticate with a relay. */
enum class UserAuthChoice {
    /** Authenticate this one time; keep asking next time. */
    ALLOW_ONCE,

    /** Authenticate now and remember ALLOW for this relay. */
    ALWAYS_ALLOW,

    /** Do not authenticate and remember DENY for this relay. */
    BLOCK,

    /** No decision (dismissed or timed out) — do not authenticate, don't remember. */
    DISMISS,
}

/**
 * A pending "should I authenticate with this relay?" question, surfaced to the UI. The relay
 * connection coroutine is suspended on [reply] until the user (or a timeout) answers.
 */
class RelayAuthPrompt(
    val relayUrl: NormalizedRelayUrl,
    val purposes: List<AuthPurpose>,
    private val reply: CompletableDeferred<UserAuthChoice>,
) {
    fun respond(choice: UserAuthChoice) {
        reply.complete(choice)
    }

    /** True once answered by the user or resolved by the bus (e.g. timed out). */
    val isResolved: Boolean get() = reply.isCompleted

    /** Runs [block] when this prompt is resolved by any path, so the UI can stop showing it. */
    fun onResolved(block: () -> Unit) {
        reply.invokeOnCompletion { block() }
    }
}

/**
 * Bridges the background NIP-42 auth path to the UI: when a challenge resolves to ASK, the auth
 * coroutine calls [requestDecision] and suspends; a Composable collects [prompts], shows a dialog,
 * and calls [RelayAuthPrompt.respond]. Concurrent challenges for the same relay share one prompt so
 * the user isn't asked twice, and an unanswered prompt resolves to [UserAuthChoice.DISMISS] after
 * [timeoutMs] so a connection never hangs forever waiting on a UI that may not be present.
 */
class RelayAuthPromptBus(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    // replay so a challenge raised *before* the UI host subscribes — cold start, an account switch,
    // any moment no RelayAuthPromptHost is collecting — isn't dropped (which would stall the auth
    // coroutine the full timeout and then silently DISMISS). The host filters out any already-
    // resolved prompt it replays, so re-delivering stale ones is harmless.
    private val mutablePrompts = MutableSharedFlow<RelayAuthPrompt>(replay = 32, extraBufferCapacity = 32)
    val prompts: SharedFlow<RelayAuthPrompt> = mutablePrompts

    private val inFlight = mutableMapOf<NormalizedRelayUrl, CompletableDeferred<UserAuthChoice>>()

    suspend fun requestDecision(
        relayUrl: NormalizedRelayUrl,
        purposes: List<AuthPurpose>,
    ): UserAuthChoice {
        // Suspension can't happen inside synchronized, so we only decide ownership under the lock
        // and await outside it. The owner is the challenge that first created the prompt; any
        // concurrent challenge for the same relay awaits the same answer.
        val (deferred, isOwner) =
            synchronized(inFlight) {
                inFlight[relayUrl]?.let { it to false }
                    ?: CompletableDeferred<UserAuthChoice>().also { inFlight[relayUrl] = it } to true
            }

        if (isOwner) mutablePrompts.emit(RelayAuthPrompt(relayUrl, purposes, deferred))
        return try {
            awaitOrTimeout(deferred)
        } finally {
            if (isOwner) synchronized(inFlight) { inFlight.remove(relayUrl) }
        }
    }

    private suspend fun awaitOrTimeout(deferred: CompletableDeferred<UserAuthChoice>): UserAuthChoice {
        withTimeoutOrNull(timeoutMs) { deferred.await() }?.let { return it }
        // Timed out: resolve the deferred so any UI still showing this prompt can drop it, and so a
        // concurrent waiter on the same deferred gets an answer too. complete() is a no-op if a late
        // user response already won the race.
        deferred.complete(UserAuthChoice.DISMISS)
        return deferred.await()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L
    }
}
