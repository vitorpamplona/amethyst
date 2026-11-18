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
package com.vitorpamplona.amethyst.commons.ui.connectedApps.consent

import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentInfoBuilder
import com.vitorpamplona.amethyst.commons.connectedApps.consent.Nip46ConsentStrings
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerFace
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46ClientStore
import com.vitorpamplona.amethyst.commons.connectedApps.nip46.Nip46PermissionAuthorizer
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.NostrSignerOp
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.model.cache.LocalCache
import com.vitorpamplona.amethyst.commons.model.nip46Signer.Nip46ConsentPrompter
import com.vitorpamplona.amethyst.commons.resources.Res
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_allow_always_for
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_decrypt_failed
import com.vitorpamplona.amethyst.commons.resources.nip46_signer_remote_app
import com.vitorpamplona.amethyst.commons.ui.loadStringRes
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequest
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestConnect
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip04Decrypt
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerRequestNip44Decrypt
import com.vitorpamplona.quartz.utils.runCatchingCancellable

/**
 * The questions a NIP-46 bunker asks its user, worded once for every platform: the first-connect
 * trust-level picker (`bunker://` and `nostrconnect://`) and the per-operation prompt. Each platform
 * only shows them: [showConnect] and [showOp] put the worded question in front of the user and
 * suspend until they answer, failing closed ([AppConnectResult.Cancelled], [SignerOpGrant.DenyOnce])
 * when they never do. How long a question may wait is theirs to decide, since only they know when it
 * is on screen.
 */
class Nip46ConsentPrompts(
    private val clientStore: () -> Nip46ClientStore,
    private val showConnect: suspend (SignerConnectInfo) -> AppConnectResult,
    private val showOp: suspend (SignerConsentInfo) -> SignerOpGrant,
    /** An account's or counterparty's name and picture, for the prompt. */
    private val faceOf: (HexKey) -> SignerFace = ::cachedFace,
) : Nip46ConsentPrompter {
    /** First connect over `bunker://`: the app's self-declared identity, and a trust level to pick. */
    override suspend fun requestConnect(
        coordinate: String,
        clientPubKey: HexKey,
        request: BunkerRequestConnect,
    ): AppConnectResult {
        val meta = request.clientMetadata
        return showConnect(
            connectInfo(
                coordinate = coordinate,
                name = meta?.name,
                domain = meta?.url?.ifBlank { null } ?: (clientPubKey.take(12) + "…"),
                image = meta?.image,
            ),
        )
    }

    /**
     * First connect over `nostrconnect://`: like [requestConnect], built from the pasted or scanned
     * offer, and listing the [requestedOps] the app would be pre-granted, so the user knows before
     * agreeing.
     */
    override suspend fun requestNostrConnectConsent(
        coordinate: String,
        name: String?,
        url: String?,
        image: String?,
        requestedOps: List<NostrSignerOp>,
    ): AppConnectResult =
        showConnect(
            connectInfo(
                coordinate = coordinate,
                name = name,
                domain = url?.ifBlank { null } ?: (Nip46PermissionAuthorizer.clientPubKeyOf(coordinate)?.take(12)?.plus("…") ?: ""),
                image = image,
                requestedPermissions = requestedOps.map { it.label() },
            ),
        )

    /**
     * One operation. A decrypt is decrypted first, locally with the account's own [signer], so the
     * prompt shows what the app would read instead of only "wants to read your private messages";
     * nothing leaves the device unless the user allows it. The decryption is bounded by
     * [Nip46ConsentInfoBuilder], so a slow signer degrades to an explanation, not a hang.
     */
    override suspend fun requestOp(
        coordinate: String,
        clientPubKey: HexKey,
        op: NostrSignerOp,
        request: BunkerRequest,
        signer: NostrSigner,
    ): SignerOpGrant {
        val stored = runCatchingCancellable { clientStore().load(coordinate) }.getOrNull()
        return showOp(
            Nip46ConsentInfoBuilder.build(
                coordinate = coordinate,
                title = appTitle(stored?.name),
                iconUrl = stored?.image,
                op = op,
                request = request,
                account = accountFace(coordinate),
                faceOf = faceOf,
                strings =
                    Nip46ConsentStrings(
                        opLabel = { it.label() },
                        allowAlwaysFor = { loadStringRes(Res.string.nip46_signer_allow_always_for, it) },
                        decryptFailed = loadStringRes(Res.string.nip46_signer_decrypt_failed),
                    ),
                decrypt = { decryptWithAccountSigner(signer, it) },
            ),
        )
    }

    private suspend fun connectInfo(
        coordinate: String,
        name: String?,
        domain: String,
        image: String?,
        requestedPermissions: List<String> = emptyList(),
    ): SignerConnectInfo {
        val account = accountFace(coordinate)
        return SignerConnectInfo(
            appletTitle = appTitle(name),
            coordinate = coordinate,
            domain = domain,
            iconUrl = image,
            accountName = account.name,
            accountPicture = account.picture,
            accountPubKey = account.pubKey,
            requestedPermissions = requestedPermissions,
        )
    }

    /** The name the app gave itself, or "a remote app" when it gave none. */
    private suspend fun appTitle(name: String?): String = name?.ifBlank { null } ?: loadStringRes(Res.string.nip46_signer_remote_app)

    /** The account being signed for, from the coordinate's signer pubkey. */
    private fun accountFace(coordinate: String): SignerFace = Nip46PermissionAuthorizer.signerPubKeyOf(coordinate)?.let(faceOf) ?: SignerFace(name = null, picture = null, pubKey = null)

    private suspend fun decryptWithAccountSigner(
        signer: NostrSigner,
        request: BunkerRequest,
    ): String? =
        when (request) {
            is BunkerRequestNip04Decrypt -> signer.nip04Decrypt(request.ciphertext, request.pubKey)
            is BunkerRequestNip44Decrypt -> signer.nip44Decrypt(request.ciphertext, request.pubKey)
            else -> null
        }

    companion object {
        /** The cached profile's name and picture; the prompt falls back to a shortened npub. */
        fun cachedFace(pubKey: HexKey): SignerFace {
            val user = LocalCache.getUserIfExists(pubKey)
            return SignerFace(name = user?.toBestDisplayName(), picture = user?.profilePicture(), pubKey = pubKey)
        }
    }
}
