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
package com.vitorpamplona.quartz.nip05DnsIdentifiers

import androidx.compose.runtime.Stable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.utils.Hex
import kotlinx.coroutines.CancellationException

@Stable
class Nip05Client(
    val fetcher: Nip05Fetcher,
    val namecoinResolverBuilder: (() -> NamecoinNameResolver)? = null,
) : INip05Client {
    val parser = Nip05Parser()

    override suspend fun verify(
        nip05: Nip05Id,
        hexKey: HexKey,
    ): Boolean {
        // Namecoin: route .bit domains to blockchain verification
        if (namecoinResolverBuilder != null && NamecoinNameResolver.isNamecoinIdentifier(nip05.toValue())) {
            val result = namecoinResolverBuilder().resolve(nip05.toValue())
            return result?.pubkey == hexKey
        }

        val json = fetchNip05Data(nip05)

        val key =
            try {
                parser.parseHexKey(nip05, json)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                throw IllegalStateException("Error Parsing JSON from NIP-05 address $nip05", e)
            }

        return if (key == null) {
            false
        } else {
            key == hexKey
        }
    }

    override suspend fun get(nip05: Nip05Id): Nip05KeyInfo? {
        // Namecoin: route .bit domains to blockchain resolution
        if (namecoinResolverBuilder != null && NamecoinNameResolver.isNamecoinIdentifier(nip05.toValue())) {
            val result = namecoinResolverBuilder().resolve(nip05.toValue()) ?: return null
            return Nip05KeyInfo(result.pubkey, result.relays)
        }

        return parser.parseHexKeyAndRelays(nip05, fetchNip05Data(nip05))
    }

    override suspend fun load(nip05: Nip05Id) = parser.parse(fetchNip05Data(nip05))

    override suspend fun list(domain: String) = parser.parse(fetchNip05Data(domain))

    override suspend fun loadClinkOffer(nip05: Nip05Id): String? =
        try {
            parser.parseClinkOffer(nip05, fetchNip05Data(nip05))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }

    /**
     * The NIP-46 signer [nip05]'s domain advertises. The legacy shape first, from the address's own
     * document: it names the user's key as the signer. Then the spec's, from the domain's `_`
     * document. Null for a `.bit` name, an unknown address, or a domain that advertises no signer.
     */
    override suspend fun loadRemoteSigner(nip05: Nip05Id): Nip05RemoteSigner? {
        if (namecoinResolverBuilder != null && NamecoinNameResolver.isNamecoinIdentifier(nip05.toValue())) return null

        val userJson = fetchNip05Data(nip05)
        val user = runCatching { parser.parseHexKey(nip05, userJson) }.getOrNull()?.lowercase() ?: return null
        if (!Hex.isHex(user) || user.length != 64) return null

        parser.parseLegacyNip46Relays(userJson, user)?.let { urls ->
            val relays = normalize(urls)
            if (relays.isNotEmpty()) return Nip05RemoteSigner(userPubKey = user, signerPubKey = user, relays = relays)
        }

        val announcementJson =
            try {
                fetchNip05Data(Nip05Id("_", nip05.domain))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                return null
            }
        val announcement = parser.parseNip46Announcement(announcementJson) ?: return null
        val signer = announcement.signerPubKey.lowercase()
        if (!Hex.isHex(signer) || signer.length != 64) return null
        val relays = normalize(announcement.relays)
        if (relays.isEmpty()) return null
        return Nip05RemoteSigner(
            userPubKey = user,
            signerPubKey = signer,
            relays = relays,
            nostrConnectUrl = announcement.nostrConnectUrl?.takeIf { NostrConnectURI.isNostrConnectUrlTemplate(it) },
        )
    }

    /** Normalized, deduplicated and capped: the document is the domain's to write. */
    private fun normalize(urls: List<String>): Set<NormalizedRelayUrl> =
        urls
            .mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }
            .distinct()
            .take(MAX_SIGNER_RELAYS)
            .toSet()

    suspend fun fetchNip05Data(nip05: Nip05Id): String {
        val url = nip05.toUserUrl()

        return try {
            fetcher.fetch(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Error Fetching JSON from NIP-05 address $nip05 at $url", e)
        }
    }

    suspend fun fetchNip05Data(domain: String): String {
        val url = Nip05Id.domainUrl(domain)

        return try {
            fetcher.fetch(url)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw IllegalStateException("Error Fetching JSON from the entire NIP-05 domain $domain at $url", e)
        }
    }

    private companion object {
        /** More relays than a signer needs; a longer list is cut rather than dialed in full. */
        const val MAX_SIGNER_RELAYS = 20
    }
}
