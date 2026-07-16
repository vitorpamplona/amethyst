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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/** One pending per-operation consent request, as the batched sheet renders it. */
data class PendingConsent(
    val token: String,
    val info: SignerConsentInfo,
)

/**
 * Bridges the broker to the per-operation signer consent UI. The signer services requests
 * concurrently (so their prompts can batch), so several requests can await consent at once: they all
 * land in [pending], one [SignerConsentActivity] observes that list and shows a single-request dialog
 * or a batched list, and each resolved token completes its own deferred. A dismissed/ignored request
 * resolves to [SignerOpGrant.DenyOnce] — fails closed.
 */
object SignerConsentCoordinator {
    private val deferreds = ConcurrentHashMap<String, CompletableDeferred<SignerOpGrant>>()
    private val _pending = MutableStateFlow<List<PendingConsent>>(emptyList())

    /** The live set of requests awaiting the user's decision; the Activity renders this. */
    val pending: StateFlow<List<PendingConsent>> = _pending

    // A stable notification id (one prompt notification for the whole batch, updated as requests
    // arrive) so concurrent requests don't each post their own.
    private val batchNotificationId = "nip46-signer-consent".hashCode()

    // Guards the surface (post/cancel of the one shared notification) against the pending set so a
    // concurrent arrival's post can't be clobbered by another request's teardown cancel. Without it,
    // request A could read "pending now empty" and then cancel AFTER request B posted a fresh
    // notification under the same id, leaving B with no UI while backgrounded (silent deny at timeout).
    private val surfaceLock = Mutex()

    suspend fun requestConsent(
        context: Context,
        info: SignerConsentInfo,
    ): SignerOpGrant {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<SignerOpGrant>()
        deferreds[token] = deferred

        surfaceLock.withLock {
            _pending.update { it + PendingConsent(token, info) }
            // Fast path when Amethyst already owns the foreground: open the dialog directly. When the app
            // is backgrounded this is silently dropped by Android 12+ BAL, so the full-screen-intent
            // notification is what surfaces the prompt. Both are idempotent — the Activity is singleTop and
            // observes [pending], and the notification uses a stable id, so concurrent requests just
            // refresh the one prompt. Wrapped because a BAL-blocked launch can throw rather than no-op.
            runCatching {
                context.startActivity(
                    Intent(context, SignerConsentActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
            }
            SignerConsentNotifier.show(
                context = context,
                activityClass = SignerConsentActivity::class.java,
                extraKey = EXTRA_TOKEN,
                token = "nip46-signer-consent",
                titleRes = R.string.nip46_signer_notif_sign_title,
            )
        }

        return try {
            deferred.await()
        } finally {
            deferreds.remove(token)
            surfaceLock.withLock {
                // Remove + emptiness check + cancel are one critical section vs. another request's
                // add + show, so a fresh notification is never cancelled out from under a live request.
                val stillPending = _pending.updateAndGet { list -> list.filterNot { it.token == token } }
                if (stillPending.isEmpty()) SignerConsentNotifier.cancel(context, batchNotificationId)
            }
        }
    }

    fun complete(
        token: String,
        grant: SignerOpGrant,
    ) {
        deferreds[token]?.complete(grant)
    }

    fun completeAll(
        tokens: Collection<String>,
        grant: SignerOpGrant,
    ) {
        tokens.forEach { complete(it, grant) }
    }

    /** Deny every still-open request — used when the user dismisses the whole sheet. Fails closed. */
    fun denyAllPending() {
        deferreds.values.forEach { it.complete(SignerOpGrant.DenyOnce) }
    }

    const val EXTRA_TOKEN = "napplet_signer_consent_token"
}
