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
import com.vitorpamplona.amethyst.commons.napplet.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.napplet.signers.SignerOpGrant
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Everything the per-operation consent dialog needs to render. */
data class NappletSignerConsentInfo(
    val appletTitle: String,
    val coordinate: String,
    val op: NostrSignerOp,
    val operationSummary: String,
    val contentPreview: String,
)

/**
 * Bridges the broker to the per-operation signer consent UI.
 * A dismissed dialog resolves to [SignerOpGrant.DenyOnce] — fails closed.
 */
object NappletSignerConsentCoordinator {
    private class Pending(
        val info: NappletSignerConsentInfo,
        val deferred: CompletableDeferred<SignerOpGrant>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun requestConsent(
        context: Context,
        info: NappletSignerConsentInfo,
    ): SignerOpGrant {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<SignerOpGrant>()
        pending[token] = Pending(info, deferred)

        context.startActivity(
            Intent(context, NappletSignerConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_TOKEN, token),
        )

        return try {
            deferred.await()
        } finally {
            pending.remove(token)
        }
    }

    fun infoFor(token: String): NappletSignerConsentInfo? = pending[token]?.info

    fun complete(
        token: String,
        grant: SignerOpGrant,
    ) {
        pending[token]?.deferred?.complete(grant)
    }

    fun cancel(token: String) {
        pending[token]?.deferred?.complete(SignerOpGrant.DenyOnce)
    }

    const val EXTRA_TOKEN = "napplet_signer_consent_token"
}
