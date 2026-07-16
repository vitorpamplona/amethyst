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
package com.vitorpamplona.amethyst.model.nip46Signer

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectCoordinator
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentCoordinator
import com.vitorpamplona.amethyst.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.napplet.label
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestSign
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Bridges the (KMP, headless) [com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer]
 * to the interactive consent UI. It reuses the SAME dialogs the napplet/browser signer path uses —
 * [SignerConnectCoordinator] (first-connect trust picker) and [SignerConsentCoordinator]
 * (per-operation allow/deny) — so a NIP-46 remote app prompts through one consistent surface, and
 * the user's "remember" choices land in the same [com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerPermissionLedger].
 *
 * Runs only in the main process (the signer never runs in `:napplet`), so [Amethyst.instance] is set;
 * the coordinators launch their Activity from the application context.
 */
object Nip46ConsentBridge {
    /**
     * Upper bound on how long a consent prompt may block the signer's single-consumer loop. A user who
     * ignores the dialog eventually fails the request closed (deny / declined) instead of wedging the
     * signer for every other client whose requests queue behind that one blocked prompt.
     */
    private const val CONSENT_TIMEOUT_MS = 120_000L

    /** First-connect consent: show the app's self-declared identity and let the user pick a trust level. */
    suspend fun requestConnect(
        coordinate: String,
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): AppConnectResult {
        val context = Amethyst.instance.appContext
        val meta = request.clientMetadata
        val title = meta?.name?.ifBlank { null } ?: context.getString(R.string.nip46_signer_remote_app)
        val domain = meta?.url?.ifBlank { null } ?: (clientPubKey.take(12) + "…")
        val info = SignerConnectInfo(appletTitle = title, coordinate = coordinate, domain = domain, iconUrl = meta?.image)
        // Fail closed (declined) if the prompt is never answered, so a stuck first-connect dialog can't
        // hold the single-consumer loop hostage against every other client.
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConnectCoordinator.requestConnect(context, info)
        } ?: AppConnectResult.Cancelled
    }

    /** Per-operation consent: describe the request (op + event preview) and await the user's grant. */
    suspend fun requestOp(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
    ): SignerOpGrant {
        val context = Amethyst.instance.appContext
        val info = runCatching { Amethyst.instance.nip46ClientStore.load(coordinate) }.getOrNull()
        val title = info?.name?.ifBlank { null } ?: context.getString(R.string.nip46_signer_remote_app)
        val preview =
            if (request is BunkerRequestSign) {
                request.event.content
                    .take(160)
                    .trim()
            } else {
                ""
            }
        val rawData = if (request is BunkerRequestSign) JacksonMapper.toJsonPretty(request.event) else ""
        val consentInfo =
            SignerConsentInfo(
                appletTitle = title,
                coordinate = coordinate,
                op = op,
                operationSummary = op.label(context),
                contentPreview = preview,
                rawData = rawData,
                iconUrl = info?.image,
            )
        // Fail closed if the prompt is never answered so a stuck dialog can't hold the signer hostage.
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) {
            SignerConsentCoordinator.requestConsent(context, consentInfo)
        } ?: SignerOpGrant.DenyOnce
    }
}
