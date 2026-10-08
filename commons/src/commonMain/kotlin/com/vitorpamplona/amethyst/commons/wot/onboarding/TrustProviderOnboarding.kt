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
package com.vitorpamplona.amethyst.commons.wot.onboarding

import com.vitorpamplona.amethyst.commons.wot.onboarding.brainstorm.BrainstormOnboarding
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag

/**
 * A NIP-85 trust provider whose sign-up Amethyst knows how to run.
 *
 * NIP-85 says how a provider publishes scores and how a user points to it (kind 10040), but not
 * how a user becomes a provider's customer or learns the per-user key it signs with. Each
 * provider has its own API for that, so each one is an implementation of this interface.
 * Providers without one can still be used: the user enters the service key and relay by hand.
 */
interface TrustProviderOnboarding {
    /** Stable id, for saving which guided provider the user picked. */
    val id: String

    /** The provider's name, as shown to the user. */
    val name: String

    /** Where to read about the provider. */
    val homepage: String

    /** The relay the provider publishes its kind 30382 cards to. */
    val relay: NormalizedRelayUrl

    /** Whether the 10040 entry [provider] points at this provider (to mark it "in use"). */
    fun serves(provider: ServiceProviderTag): Boolean

    /**
     * Signs in as [signer]'s user, asks the provider to compute their scores and returns the
     * key it signs them with. Calls [onStep] as it goes. Throws [TrustProviderException].
     */
    suspend fun register(
        signer: NostrSigner,
        onStep: (TrustProviderOnboardingStep) -> Unit = {},
    ): TrustProviderRegistration
}

enum class TrustProviderOnboardingStep {
    SIGNING_IN,
    REQUESTING_SCORES,
    FETCHING_SERVICE_KEY,
}

/** What a sign-up returns: the per-user key the provider signs this user's cards with. */
class TrustProviderRegistration(
    val serviceKey: HexKey,
    val relay: NormalizedRelayUrl,
    /** False while the provider has not finished computing the first scores. */
    val scoresReady: Boolean,
)

class TrustProviderException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        /** The provider could not be reached, or answered with a server error. */
        UNREACHABLE,

        /** The provider rejected the sign-in (signature refused, challenge expired). */
        SIGN_IN_REJECTED,

        /** The user's signer declined to sign. */
        SIGNER_DECLINED,

        /** The provider answered something we do not understand. */
        UNEXPECTED_RESPONSE,
    }
}

/** The providers with guided sign-up, in the order the settings screen offers them. */
object KnownTrustProviders {
    fun all(http: TrustProviderHttp): List<TrustProviderOnboarding> = listOf(BrainstormOnboarding(http))
}
