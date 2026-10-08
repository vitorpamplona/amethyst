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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.TrustProviderListEvent
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.serviceProviders
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ProviderTypes
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag
import com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceType
import com.vitorpamplona.quartz.utils.Hex

/** The kind 10040 entries that name a user-score provider (the ones Amethyst reads). */
val SCORE_SERVICES = setOf(ProviderTypes.rank, ProviderTypes.followerCount)

/**
 * The 30382 tag names offered when the user writes the kind 10040 rows by hand: the ones
 * Amethyst reads ([SCORE_SERVICES]) and the others GrapeRank providers publish.
 */
val KNOWN_SCORE_TAGS: List<ServiceType> =
    listOf(ProviderTypes.rank, ProviderTypes.followerCount, ProviderTypes.hops, ProviderTypes.reporters, ProviderTypes.muters)

/**
 * One row of a kind 10040, `[name, key, relay]`: the provider [key] serves [name] (a
 * `<kind>:<metric>` like `30382:rank`, or a bare kind like `30392`) on [relay]. A provider's
 * rows are published as it hands them over, including ones Amethyst does not read.
 */
@Immutable
data class TrustProviderRow(
    val name: String,
    val key: HexKey,
    val relay: NormalizedRelayUrl,
) {
    fun toTagArray(): Array<String> = arrayOf(name, key, relay.url)

    companion object {
        /** A well-formed row, or null: a kind (or kind:metric) name, a 64-hex key and a relay. */
        fun parse(row: List<String>): TrustProviderRow? {
            if (row.size < 3 || !isRowName(row[0])) return null
            val key = row[1].lowercase()
            if (!Hex.isHex64(key)) return null
            val relay = RelayUrlNormalizer.normalizeOrNull(row[2]) ?: return null
            return TrustProviderRow(row[0], key, relay)
        }

        /** `30382:rank`, or a bare kind like `30392`. */
        fun isRowName(name: String): Boolean {
            val kind = name.substringBefore(':')
            if (kind.isEmpty() || kind.any { it !in '0'..'9' }) return false
            return ':' !in name || name.substringAfter(':').isNotEmpty()
        }
    }
}

/**
 * The well-formed public rows of this list, one per name. Copying another user's rows shows the
 * network from their point of view: their provider's cards are computed for them. Their
 * private rows are encrypted to them and cannot be read.
 */
fun TrustProviderListEvent.publicRows(): List<TrustProviderRow> = tags.mapNotNull { TrustProviderRow.parse(it.toList()) }.distinctBy { it.name }

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
 * A new kind 10040 built on [existing] with [rows] in it: every row of [existing] (public or
 * private) with the same name as one of [rows] is replaced, and so is any user-score entry
 * (`30382:rank`, `30382:followers`), so the previous score provider never lingers beside the
 * new one. Every other row is kept: other providers, Trusted Lists, `client`. [isPrivate] puts
 * [rows] in the NIP-44 encrypted content instead of the public tags.
 */
suspend fun withProviderRows(
    existing: TrustProviderListEvent?,
    rows: List<TrustProviderRow>,
    isPrivate: Boolean,
    signer: NostrSigner,
): TrustProviderListEvent {
    val names = rows.mapTo(HashSet()) { it.name } + SCORE_SERVICES.map { it.toValue() }
    val entries = rows.map { it.toTagArray() }
    return rewriteEntries(
        existing,
        drop = { tag -> tag.isNotEmpty() && tag[0] in names },
        publicEntries = if (isPrivate) emptyList() else entries,
        privateEntries = if (isPrivate) entries else emptyList(),
        signer = signer,
    )
}

/** [withProviderRows] with one key and relay serving the user scores (`30382:rank`, `30382:followers`). */
suspend fun withScoreProvider(
    existing: TrustProviderListEvent?,
    providerKey: HexKey,
    relay: NormalizedRelayUrl,
    isPrivate: Boolean,
    signer: NostrSigner,
): TrustProviderListEvent = withProviderRows(existing, SCORE_SERVICES.map { TrustProviderRow(it.toValue(), providerKey, relay) }, isPrivate, signer)

/**
 * A new kind 10040 built on [existing] without the score provider: every row its rank key
 * serves (whatever the provider set up with it, Trusted Lists included) and any user-score
 * entry. Other providers' rows are kept. Null when there is nothing to remove.
 */
suspend fun withoutScoreProvider(
    existing: TrustProviderListEvent?,
    signer: NostrSigner,
): TrustProviderListEvent? {
    if (existing == null) return null
    val private = if (existing.content.isBlank()) emptyList() else existing.privateTags(signer)?.serviceProviders().orEmpty()
    val all = existing.serviceProviders() + private
    if (all.none { it.service in SCORE_SERVICES }) return null
    val providerKey = existing.rankProvider(signer)?.pubkey
    val scoreNames = SCORE_SERVICES.mapTo(HashSet()) { it.toValue() }
    return rewriteEntries(
        existing,
        drop = { tag -> tag.isNotEmpty() && (tag[0] in scoreNames || (providerKey != null && tag.size > 1 && tag[1] == providerKey)) },
        publicEntries = emptyList(),
        privateEntries = emptyList(),
        signer = signer,
    )
}

private suspend fun rewriteEntries(
    existing: TrustProviderListEvent?,
    drop: (Array<String>) -> Boolean,
    publicEntries: List<Array<String>>,
    privateEntries: List<Array<String>>,
    signer: NostrSigner,
): TrustProviderListEvent {
    val publicTags = existing?.tags?.filterNot(drop).orEmpty() + publicEntries
    val oldPrivate =
        if (existing == null || existing.content.isBlank()) {
            emptyArray()
        } else {
            // Never drop entries we cannot read: refuse instead.
            existing.privateTags(signer) ?: throw SignerExceptions.UnauthorizedDecryptionException()
        }
    val privateTags = oldPrivate.filterNot(drop) + privateEntries

    return if (privateTags.isEmpty()) {
        TrustProviderListEvent.resign(content = "", tags = publicTags.toTypedArray(), signer = signer)
    } else {
        TrustProviderListEvent.resign(tags = publicTags.toTypedArray(), privateTags = privateTags.toTypedArray(), signer = signer)
    }
}
