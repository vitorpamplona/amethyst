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
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import kotlinx.coroutines.CompletableDeferred
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Everything the "Connect to Nostr" dialog needs to render. */
data class SignerConnectInfo(
    val appletTitle: String,
    val coordinate: String,
    val domain: String,
    val iconUrl: String? = null,
    /**
     * The account the app is connecting to, shown as an avatar + name instead of a raw pubkey. When
     * [accountName] is null (e.g. napplet/browser paths that don't resolve it) the dialog falls back
     * to [domain]. [accountPubKey] seeds the robohash avatar fallback when there's no picture.
     */
    val accountName: String? = null,
    val accountPicture: String? = null,
    val accountPubKey: String? = null,
    /**
     * Human-readable permissions the app declared it needs (from a `nostrconnect://…?perms=` offer),
     * shown so the user gives INFORMED consent before those ops are pre-granted. Empty for flows that
     * carry no declaration (bunker connect), which just show the trust picker.
     */
    val requestedPermissions: List<String> = emptyList(),
)

/**
 * Bridges the broker to the "Connect to Nostr" first-connect UI. Suspends in [requestConnect];
 * the Activity resolves the deferred with the user's choice.
 * A dismissed dialog resolves to [AppConnectResult.Cancelled] — fails closed, no silent grant.
 */
object SignerConnectCoordinator {
    private class Pending(
        val info: SignerConnectInfo,
        val deferred: CompletableDeferred<AppConnectResult>,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun requestConnect(
        context: Context,
        info: SignerConnectInfo,
    ): AppConnectResult {
        val token = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<AppConnectResult>()
        pending[token] = Pending(info, deferred)

        // Fast path when Amethyst already owns the foreground; the full-screen-intent notification
        // below is what surfaces the dialog when a connect request arrives while backgrounded (see
        // SignerConsentNotifier). Wrapped because a BAL-blocked launch can throw on some OEMs.
        runCatching {
            context.startActivity(
                Intent(context, SignerConnectActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TOKEN, token),
            )
        }

        val notificationId =
            SignerConsentNotifier.show(
                context = context,
                activityClass = SignerConnectActivity::class.java,
                extraKey = EXTRA_TOKEN,
                token = token,
                titleRes = R.string.nip46_signer_notif_connect_title,
            )

        return try {
            deferred.await()
        } finally {
            pending.remove(token)
            SignerConsentNotifier.cancel(context, notificationId)
        }
    }

    fun infoFor(token: String): SignerConnectInfo? = pending[token]?.info

    fun complete(
        token: String,
        result: AppConnectResult,
    ) {
        pending[token]?.deferred?.complete(result)
    }

    fun cancel(token: String) {
        pending[token]?.deferred?.complete(AppConnectResult.Cancelled)
    }

    const val EXTRA_TOKEN = "napplet_connect_token"
}
