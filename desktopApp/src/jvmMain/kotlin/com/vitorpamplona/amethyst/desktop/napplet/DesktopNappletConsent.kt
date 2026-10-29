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

import com.vitorpamplona.amethyst.commons.connectedApps.consent.PendingConsent
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConnectInfo
import com.vitorpamplona.amethyst.commons.connectedApps.consent.SignerConsentInfo
import com.vitorpamplona.amethyst.commons.connectedApps.signers.AppConnectResult
import com.vitorpamplona.amethyst.commons.connectedApps.signers.SignerOpGrant
import com.vitorpamplona.amethyst.commons.napplet.NappletConsentInfo
import com.vitorpamplona.amethyst.commons.napplet.permissions.GrantState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * The questions open napplets and nSites are waiting on. The broker's prompts suspend here; the
 * window for that coordinate shows the oldest question and answers it. Every question fails closed:
 * a window that closes first, or a dialog closed without a choice, grants nothing.
 *
 * - [Capability]: may this app use a capability (NIP-5D, and NIP-07's identity and relays).
 * - [Connect]: a site's first NIP-07 call, asking how much to trust it.
 * - [SignerOp]: one signature, encryption or decryption. Several waiting at once are shown together.
 */
class DesktopNappletConsent {
    sealed class Request(
        val coordinate: String,
    ) {
        /** Answers it as "no" without the user, when its window goes away. */
        abstract fun cancel()
    }

    class Capability(
        coordinate: String,
        val info: NappletConsentInfo,
    ) : Request(coordinate) {
        internal val answer = CompletableDeferred<GrantState>()

        fun answer(decision: GrantState) {
            answer.complete(decision)
        }

        override fun cancel() = answer(GrantState.ASK)
    }

    class Connect(
        coordinate: String,
        val info: SignerConnectInfo,
    ) : Request(coordinate) {
        internal val answer = CompletableDeferred<AppConnectResult>()

        fun answer(result: AppConnectResult) {
            answer.complete(result)
        }

        override fun cancel() = answer(AppConnectResult.Cancelled)
    }

    class SignerOp(
        coordinate: String,
        val info: SignerConsentInfo,
    ) : Request(coordinate) {
        val token: String = UUID.randomUUID().toString()
        internal val answer = CompletableDeferred<SignerOpGrant>()

        fun answer(grant: SignerOpGrant) {
            answer.complete(grant)
        }

        override fun cancel() = answer(SignerOpGrant.DenyOnce)

        fun asPending() = PendingConsent(token, info)
    }

    private val mutablePending = MutableStateFlow<List<Request>>(emptyList())
    val pending: StateFlow<List<Request>> = mutablePending.asStateFlow()

    suspend fun request(
        coordinate: String,
        info: NappletConsentInfo,
    ): GrantState = ask(Capability(coordinate, info)) { it.answer.await() }

    suspend fun connect(
        coordinate: String,
        info: SignerConnectInfo,
    ): AppConnectResult = ask(Connect(coordinate, info)) { it.answer.await() }

    suspend fun signerOp(
        coordinate: String,
        info: SignerConsentInfo,
    ): SignerOpGrant = ask(SignerOp(coordinate, info)) { it.answer.await() }

    private suspend fun <R : Request, T> ask(
        request: R,
        await: suspend (R) -> T,
    ): T {
        mutablePending.update { it + request }
        try {
            return await(request)
        } finally {
            mutablePending.update { it - request }
        }
    }

    /** Answers "no" for every question a closed napplet or site left behind. */
    fun dismissAll(coordinate: String) {
        pending.value.filter { it.coordinate == coordinate }.forEach { it.cancel() }
    }
}
