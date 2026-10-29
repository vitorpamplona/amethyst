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
package com.vitorpamplona.amethyst.napplet.gateways

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger
import com.vitorpamplona.amethyst.commons.model.Account
import com.vitorpamplona.amethyst.commons.napplet.NappletBroker
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.NappletIdentity
import com.vitorpamplona.amethyst.commons.napplet.NappletNotification
import com.vitorpamplona.amethyst.commons.napplet.NappletNotificationStore
import com.vitorpamplona.amethyst.commons.napplet.NappletNotifyGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletPrompts
import com.vitorpamplona.amethyst.commons.napplet.NappletStorage
import com.vitorpamplona.amethyst.commons.napplet.NappletThemeGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadGateway
import com.vitorpamplona.amethyst.commons.napplet.NappletUploadResult
import com.vitorpamplona.amethyst.commons.napplet.buildAccountNappletBroker
import com.vitorpamplona.amethyst.commons.napplet.permissions.NappletPermissionLedger
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.napplet_cap_notify
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectCoordinator
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentCoordinator
import com.vitorpamplona.amethyst.napplet.NappletConsentCoordinator
import com.vitorpamplona.amethyst.napplet.NappletNetworkRegistry
import com.vitorpamplona.amethyst.napplet.currentNappletThemeColors
import com.vitorpamplona.amethyst.service.uploads.blossom.BlossomUploader
import com.vitorpamplona.crypto.sha2.sha256
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.runCatchingCancellable
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream

/**
 * The account adapter: turns a signed-in [Account] into a configured [NappletBroker] through the
 * shared [buildAccountNappletBroker], adding what is Android's: the consent dialogs (each an
 * Activity, through its coordinator), the system notifications behind NAP `notify`, and Blossom
 * uploads. The broker itself owns the trust boundary. Built once per account and cached by the caller.
 */
class AccountNappletGateways(
    private val account: Account,
    private val context: Context,
    private val ledger: NappletPermissionLedger,
    private val storage: NappletStorage,
    private val httpClient: (useProxy: Boolean) -> OkHttpClient,
    private val signerLedger: NostrSignerPermissionLedger? = null,
) {
    private val prompts =
        object : NappletPrompts {
            override suspend fun capability(
                app: NappletIdentity,
                info: NappletConsentInfo,
            ) = NappletConsentCoordinator.requestConsent(context, info)

            override suspend fun connect(
                app: NappletIdentity,
                info: SignerConnectInfo,
            ) = SignerConnectCoordinator.requestConnect(context, info)

            override suspend fun signerOp(
                app: NappletIdentity,
                info: SignerConsentInfo,
            ) = SignerConsentCoordinator.requestConsent(context, info)
        }

    private val notify =
        object : NappletNotifyGateway {
            override suspend fun create(
                coordinate: String,
                title: String,
                body: String,
            ): String {
                val id = NappletNotificationStore.create(coordinate, title, body)
                runCatching { postSystemNotification(id, title, body) }
                return id
            }

            override suspend fun list(coordinate: String): List<NappletNotification> = NappletNotificationStore.list(coordinate)

            override suspend fun dismiss(
                coordinate: String,
                id: String,
            ) {
                NappletNotificationStore.dismiss(coordinate, id)
                runCatching { NotificationManagerCompat.from(context).cancel(id.hashCode()) }
            }
        }

    fun broker(): NappletBroker =
        buildAccountNappletBroker(
            account = account,
            browserIcons = Amethyst.instance.browserIcons,
            ledger = ledger,
            signerLedger = signerLedger,
            storage = storage,
            // Reuse the app-wide HTTP client so napplet blob fetches inherit the same Tor
            // routing, Onion-Location discovery/rewriting, Blossom cache and pool as the
            // rest of the app, instead of a private client that has to re-wire all of it.
            httpClient = httpClient,
            useTor = { coordinate ->
                NappletNetworkRegistry.awaitReady()
                NappletNetworkRegistry.useTor(coordinate)
            },
            prompts = prompts,
            theme = NappletThemeGateway { currentNappletThemeColors(context) },
            upload = NappletUploadGateway { bytes, contentType, filename -> uploadBlob(bytes, contentType, filename) },
            notify = notify,
        )

    /**
     * Posts a napplet's notification to the system tray (best-effort: a missing POST_NOTIFICATIONS
     * grant just no-ops). The in-app registry in [NappletNotificationStore] is the source of truth for
     * the napplet's own `notify.list`/`dismiss`; this is the user-visible surface.
     */
    private suspend fun postSystemNotification(
        id: String,
        title: String,
        body: String,
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val channel =
            NotificationChannelCompat
                .Builder(NOTIFY_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(loadStringRes(Res.string.napplet_cap_notify))
                .build()
        manager.createNotificationChannel(channel)
        val notification =
            NotificationCompat
                .Builder(context, NOTIFY_CHANNEL_ID)
                .setSmallIcon(R.drawable.amethyst)
                .setContentTitle(title.ifBlank { loadStringRes(Res.string.napplet_cap_notify) })
                .setContentText(body)
                .setAutoCancel(true)
                .build()
        try {
            manager.notify(id.hashCode(), notification)
        } catch (_: SecurityException) {
            // Best effort: if the user revoked permission between the check and here, just no-op.
        }
    }

    /**
     * Uploads [bytes] to the user's first Blossom server (kind:10063) with a signed authorization
     * event, via the app's existing [BlossomUploader]. Returns null when there's no server or the
     * upload fails. Consent is enforced by the broker before this runs.
     */
    private suspend fun uploadBlob(
        bytes: ByteArray,
        contentType: String,
        filename: String?,
    ): NappletUploadResult? {
        val server =
            account.blossomServers
                .getBlossomServersList()
                ?.servers()
                ?.firstOrNull() ?: return null
        val hash = sha256(bytes).toHexKey()
        val result =
            runCatchingCancellable {
                BlossomUploader().upload(
                    inputStream = ByteArrayInputStream(bytes),
                    hash = hash,
                    length = bytes.size.toLong(),
                    baseFileName = filename,
                    contentType = contentType,
                    alt = null,
                    sensitiveContent = null,
                    serverBaseUrl = server,
                    okHttpClient = Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForUploads,
                    httpAuth = { h, size, alt -> account.createBlossomUploadAuth(h, size, alt) },
                    context = context,
                )
            }.getOrNull() ?: return null
        val url = result.url ?: return null
        return NappletUploadResult(url, result.sha256, result.size, result.type)
    }

    companion object {
        /** Notification channel for napplet-originated notifications (NAP `notify`). */
        private const val NOTIFY_CHANNEL_ID = "napplet_notifications"
    }
}
