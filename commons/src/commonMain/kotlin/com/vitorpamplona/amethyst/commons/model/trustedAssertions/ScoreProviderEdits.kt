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
package com.vitorpamplona.amethyst.commons.model.trustedAssertions

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag

/** The kind 10040 entries that name a user-score provider (what Brainstorm registers). */
val SCORE_SERVICES = setOf(ProviderTypes.rank, ProviderTypes.followerCount)

/**
 * The provider whose `30382:rank` cards decide who is in the user's network: the first rank
 * entry, public ones before private ones (decrypted with [signer] when it is the list's
 * author). The same choice the apps make through `TrustProviderListState.liveUserRankProvider`.
 */
suspend fun TrustProviderListEvent.rankProvider(signer: NostrSigner?): ServiceProviderTag? {
    serviceProviders().firstOrNull { it.service == ProviderTypes.rank }?.let { return it }
    if (signer == null || content.isBlank()) return null
    return privateTags(signer)?.serviceProviders()?.firstOrNull { it.service == ProviderTypes.rank }
}

/**
 * A new kind 10040 built on [existing] in which the user-score entries (`30382:rank`,
 * `30382:followers`) name [providerKey] on [relay], replacing whichever provider they named
 * before. Every other entry, public or private, is kept. [isPrivate] puts the new entries in
 * the NIP-44 encrypted content instead of the public tags.
 */
suspend fun withScoreProvider(
    existing: TrustProviderListEvent?,
    providerKey: HexKey,
    relay: NormalizedRelayUrl,
    isPrivate: Boolean,
    signer: NostrSigner,
): TrustProviderListEvent {
    val entries = SCORE_SERVICES.map { ServiceProviderTag(it, providerKey, relay).toTagArray() }
    return rewriteScoreEntries(
        existing,
        publicEntries = if (isPrivate) emptyList() else entries,
        privateEntries = if (isPrivate) entries else emptyList(),
        signer = signer,
    )
}

/** A new kind 10040 built on [existing] without any user-score entry, or null when it has none. */
suspend fun withoutScoreProvider(
    existing: TrustProviderListEvent?,
    signer: NostrSigner,
): TrustProviderListEvent? {
    if (existing == null) return null
    val private = if (existing.content.isBlank()) emptyList() else existing.privateTags(signer)?.serviceProviders().orEmpty()
    if ((existing.serviceProviders() + private).none { it.service in SCORE_SERVICES }) return null
    return rewriteScoreEntries(existing, emptyList(), emptyList(), signer)
}

private suspend fun rewriteScoreEntries(
    existing: TrustProviderListEvent?,
    publicEntries: List<Array<String>>,
    privateEntries: List<Array<String>>,
    signer: NostrSigner,
): TrustProviderListEvent {
    val isScoreEntry = { tag: Array<String> -> ServiceProviderTag.parse(tag)?.service in SCORE_SERVICES }

    val publicTags = existing?.tags?.filterNot(isScoreEntry).orEmpty() + publicEntries
    val oldPrivate =
        if (existing == null || existing.content.isBlank()) {
            emptyArray()
        } else {
            // Never drop entries we cannot read: refuse instead.
            existing.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
        }
    val privateTags = oldPrivate.filterNot(isScoreEntry) + privateEntries

    return if (privateTags.isEmpty()) {
        TrustProviderListEvent.resign(content = "", tags = publicTags.toTypedArray(), signer = signer)
    } else {
        TrustProviderListEvent.resign(tags = publicTags.toTypedArray(), privateTags = privateTags.toTypedArray(), signer = signer)
    }
}
