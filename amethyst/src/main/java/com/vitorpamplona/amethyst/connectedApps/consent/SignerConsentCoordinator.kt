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
package com.vitorpamplona.amethyst.connectedApps.consent

import android.content.Context
import android.content.Intent
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Everything the per-operation consent dialog needs to render. */
data class SignerConsentInfo(
    val appletTitle: String,
    val coordinate: String,
    val op: NostrSignerOp,
    val operationSummary: String,
    /** Short excerpt shown in the dialog body (≤ 160 chars). */
    val contentPreview: String,
    /**
     * Full raw content for the "See more" toggle — event JSON for sign/encrypt operations,
     * decrypted plaintext for decrypt (Amethyst decrypts first, then asks permission to expose).
     */
    val rawData: String = "",
    val iconUrl: String? = null,
    /**
     * The account that would sign/encrypt/decrypt, shown as an avatar + name so it's clear which
     * logged-in identity is acting. Null on paths that don't resolve it; [accountPubKey] seeds the
     * robohash avatar fallback when there's no picture.
     */
    val accountName: String? = null,
    val accountPicture: String? = null,
    val accountPubKey: String? = null,
    /**
     * The unsigned event a `sign_event`/publish request would sign, so the dialog can render it as a
     * note preview (what it will look like) in addition to the raw JSON. Null for encrypt/decrypt and
     * non-event ops.
     */
    val previewTemplate: EventTemplate<Event>? = null,
)

/**
 * Bridges the broker to the per-operation signer consent UI.
 * A dismissed dialog resolves to [SignerOpGrant.DenyOnce] — fails closed.
 */
object SignerConsentCoordinator {
    private class Pending(
        val info: SignerConsentInfo,
        val deferred: CompletableDeferred<SignerOpGrant>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun requestConsent(
        context: Context,
        info: SignerConsentInfo,
    ): SignerOpGrant {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<SignerOpGrant>()
        pending[token] = Pending(info, deferred)

        // Fast path when Amethyst already owns the foreground: open the dialog directly. When the app
        // is backgrounded this is silently dropped by Android 12+ BAL, so the full-screen-intent
        // notification below is what actually surfaces the prompt. Wrapped because a blocked launch
        // can throw on some OEMs rather than no-op.
        runCatching {
            context.startActivity(
                Intent(context, SignerConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TOKEN, token),
            )
        }

        val notificationId =
            SignerConsentNotifier.show(
                context = context,
                activityClass = SignerConsentActivity::class.java,
                extraKey = EXTRA_TOKEN,
                token = token,
                titleRes = R.string.nip46_signer_notif_sign_title,
            )

        return try {
            deferred.await()
        } finally {
            pending.remove(token)
            SignerConsentNotifier.cancel(context, notificationId)
        }
    }

    fun infoFor(token: String): SignerConsentInfo? = pending[token]?.info

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
