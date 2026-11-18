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
package com.vitorpamplona.amethyst.desktop.napplet

import androidx.compose.runtime.Composable
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentInfoBuilder
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentStrings
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerFace
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.napplet.NappletPromptQueues
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_allow_always_for
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_decrypt_failed
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.ui.connectedApps.consent.label
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * The desktop's answer to a NIP-46 app asking the bunker: each question waits in [queues] and
 * [Nip46ConsentWindows] shows the oldest in a window of its own, with the same cards the napplet
 * windows use. The desktop's counterpart of Android's `Nip46ConsentBridge`.
 *
 * Every question fails closed: one left unanswered for [CONSENT_TIMEOUT_MS], or whose window is
 * closed, declines the connect or denies the operation.
 */
class DesktopNip46Consent(
    private val clientStore: () -> Nip46ClientStore,
) : Nip46ConsentPrompter {
    /** Keyed by the app's ledger coordinate (`nip46:<account>:<app>`). */
    val queues = NappletPromptQueues()

    /** The name each asking app goes by, for its question's window title. */
    private val titles = ConcurrentHashMap<String, String>()

    fun titleOf(coordinate: String?): String? = coordinate?.let { titles[it] }

    override suspend fun requestConnect(
        coordinate: String,
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): AppConnectResult {
        val meta = request.clientMetadata
        val title = meta?.name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)
        val face = accountFace(coordinate)
        val info =
            SignerConnectInfo(
                appletTitle = title,
                coordinate = coordinate,
                domain = meta?.url?.ifBlank { null } ?: (clientPubKey.take(12) + "…"),
                iconUrl = meta?.image,
                accountName = face.name,
                accountPicture = face.picture,
                accountPubKey = face.pubKey,
            )
        titles[coordinate] = title
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) { queues.connects.ask(info, coordinate) } ?: AppConnectResult.Cancelled
    }

    override suspend fun requestNostrConnectConsent(
        coordinate: String,
        name: String?,
        url: String?,
        image: String?,
        requestedOps: List<NostrSignerOp>,
    ): AppConnectResult {
        val title = name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)
        val face = accountFace(coordinate)
        val info =
            SignerConnectInfo(
                appletTitle = title,
                coordinate = coordinate,
                domain = url?.ifBlank { null } ?: (Nip46PermissionAuthorizer.clientPubKeyOf(coordinate)?.take(12)?.plus("…") ?: ""),
                iconUrl = image,
                accountName = face.name,
                accountPicture = face.picture,
                accountPubKey = face.pubKey,
                requestedPermissions = requestedOps.map { it.label() },
            )
        titles[coordinate] = title
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) { queues.connects.ask(info, coordinate) } ?: AppConnectResult.Cancelled
    }

    /**
     * One operation. A decrypt is decrypted first, locally with the account's own [signer], so the
     * window shows what the app would read; nothing leaves the device unless the user allows it.
     */
    override suspend fun requestOp(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
        signer: NostrSigner,
    ): SignerOpGrant {
        val stored = runCatching { clientStore().load(coordinate) }.getOrNull()
        val title = stored?.name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)
        val info =
            Nip46ConsentInfoBuilder.build(
                coordinate = coordinate,
                title = title,
                iconUrl = stored?.image,
                op = op,
                request = request,
                account = accountFace(coordinate),
                faceOf = ::userFace,
                strings =
                    Nip46ConsentStrings(
                        opLabel = { it.label() },
                        allowAlwaysFor = { loadStringRes(Res.string.nip46_signer_allow_always_for, it) },
                        decryptFailed = loadStringRes(Res.string.nip46_signer_decrypt_failed),
                    ),
                decrypt = { decryptWithAccountSigner(signer, it) },
            )
        titles[coordinate] = title
        return withTimeoutOrNull(CONSENT_TIMEOUT_MS) { queues.signerOps.ask(info, coordinate) } ?: SignerOpGrant.DenyOnce
    }

    private suspend fun decryptWithAccountSigner(
        signer: NostrSigner,
        request: BunkerRequest,
    ): String? =
        when (request) {
            is BunkerRequestNip04Decrypt -> signer.nip04Decrypt(request.ciphertext, request.pubKey)
            is BunkerRequestNip44Decrypt -> signer.nip44Decrypt(request.ciphertext, request.pubKey)
            else -> null
        }

    /** The account being signed for, from the coordinate's signer pubkey. */
    private fun accountFace(coordinate: String): SignerFace = userFace(Nip46PermissionAuthorizer.signerPubKeyOf(coordinate))

    private fun userFace(pubKey: HexKey?): SignerFace {
        val user = pubKey?.let { LocalCache.getUserIfExists(it) }
        return SignerFace(name = user?.toBestDisplayName(), picture = user?.profilePicture(), pubKey = pubKey)
    }

    companion object {
        /**
         * How long a question may wait. An app gives up on a signer after a minute or two, and an
         * unanswered question must not hold the signer for every other app; same bound as Android.
         */
        const val CONSENT_TIMEOUT_MS = 120_000L
    }
}

/** Every NIP-46 question waiting on the user, each in a window of its own over the app. */
@Composable
fun Nip46ConsentWindows(consent: DesktopNip46Consent) {
    ConsentWindows(
        apps = { true },
        titleOf = { app -> consent.titleOf(app) ?: "" },
        queues = consent.queues,
    )
}
