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
import com.vitorpamplona.amethyst.commons.connectedApps.consent.ConsentQueue
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState

/**
 * Bridges the main-process broker to the consent UI. The broker suspends in [requestConsent], which
 * queues the question and launches [NappletConsentActivity] with its token; the activity reports the
 * user's choice back here.
 *
 * A dismissed dialog resolves to [GrantState.ASK] — i.e. "not authorized, ask again next
 * time" — so closing the prompt never silently grants nor permanently blocks.
 */
object NappletConsentCoordinator {
    private val queue = ConsentQueue<NappletConsentInfo, GrantState>(unanswered = GrantState.ASK)

    suspend fun requestConsent(
        context: Context,
        info: NappletConsentInfo,
    ): GrantState =
        queue.ask(info, onQueued = { request ->
            context.startActivity(
                Intent(context, NappletConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TOKEN, request.token),
            )
        })

    /** Called by [NappletConsentActivity] to render the prompt. */
    fun infoFor(token: String): NappletConsentInfo? = queue.info(token)

    /** Called by [NappletConsentActivity] with the user's decision. */
    fun complete(
        token: String,
        grant: GrantState,
    ) = queue.answer(token, grant)

    /** Called when the dialog is dismissed without a choice — fails closed (no grant). */
    fun cancel(token: String) = queue.dismiss(token)

    const val EXTRA_TOKEN = "napplet_consent_token"
}
