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
package com.vitorpamplona.amethyst.napplet

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Everything the consent dialog needs to describe what an applet is asking permission to do. */
data class NappletConsentInfo(
    val appletTitle: String,
    val coordinate: String,
    val capabilityLabel: String,
    val operationSummary: String,
    /** Whether a persistent "Always allow" choice may be offered (false for per-use caps like payments). */
    val allowAlways: Boolean,
)

/**
 * Bridges the main-process broker to the consent UI. The broker suspends in
 * [requestConsent]; this launches [NappletConsentActivity], holds the pending decision keyed
 * by a one-time token, and resolves it when the activity reports the user's choice.
 *
 * A dismissed dialog resolves to [GrantState.ASK] — i.e. "not authorized, ask again next
 * time" — so closing the prompt never silently grants nor permanently blocks.
 */
object NappletConsentCoordinator {
    private class Pending(
        val info: NappletConsentInfo,
        val deferred: CompletableDeferred<GrantState>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun requestConsent(
        context: Context,
        info: NappletConsentInfo,
    ): GrantState {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<GrantState>()
        pending[token] = Pending(info, deferred)

        val intent =
            Intent(context, NappletConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TOKEN, token)
        context.startActivity(intent)

        return try {
            deferred.await()
        } finally {
            pending.remove(token)
        }
    }

    /** Called by [NappletConsentActivity] to render the prompt. */
    fun infoFor(token: String): NappletConsentInfo? = pending[token]?.info

    /** Called by [NappletConsentActivity] with the user's decision. */
    fun complete(
        token: String,
        grant: GrantState,
    ) {
        pending[token]?.deferred?.complete(grant)
    }

    /** Called when the dialog is dismissed without a choice — fails closed (no grant). */
    fun cancel(token: String) {
        pending[token]?.deferred?.complete(GrantState.ASK)
    }

    const val EXTRA_TOKEN = "napplet_consent_token"
}
