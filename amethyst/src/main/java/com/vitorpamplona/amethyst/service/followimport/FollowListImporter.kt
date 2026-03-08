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
package com.vitorpamplona.amethyst.service.followimport

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinNameResolver
import com.vitorpamplona.quartz.nip05DnsIdentifiers.namecoin.NamecoinResolveOutcome
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A single entry from a follow list.
 */
data class FollowEntry(
    val pubkeyHex: String,
    val relayHint: String? = null,
    val petname: String? = null,
)

/**
 * Result of fetching a user's follow list.
 */
sealed class FollowListResult {
    data class Success(
        val sourcePubkeyHex: String,
        val follows: List<FollowEntry>,
        val createdAt: Long,
        /** If the identifier was resolved via Namecoin, this holds the .bit name */
        val resolvedViaNamecoin: String? = null,
    ) : FollowListResult()

    data object NoFollowList : FollowListResult()

    data class InvalidIdentifier(
        val reason: String,
    ) : FollowListResult()

    data class Error(
        val message: String,
    ) : FollowListResult()
}

/**
 * Minimal representation of a kind 3 event received from a relay callback.
 * Avoids coupling to any specific Event class.
 */
data class Kind3EventData(
    val pTags: List<List<String>>,
    val createdAt: Long,
)

/**
 * Fetches another user's follow list from Nostr relays.
 *
 * Resolution order for identifiers:
 *   1. Namecoin (.bit / d/ / id/) → ElectrumX blockchain query
 *   2. Hex pubkey (64 hex chars)
 *   3. npub1... (NIP-19 bech32)
 *   4. NIP-05 (user@domain) → HTTP /.well-known/nostr.json
 *
 * @param resolveNamecoin Optional Namecoin resolver. When provided, .bit/d//id/ identifiers
 *   will be resolved via ElectrumX. Pass `namecoinNameResolver::resolveDetailed`.
 */
class FollowListImporter(
    private val resolveNamecoin: (suspend (String) -> NamecoinResolveOutcome)? = null,
) {
    companion object {
        const val KIND_CONTACT_LIST = 3
        const val DEFAULT_TIMEOUT_MS = 15_000L
        private val HEX_PUBKEY_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private const val NPUB_PREFIX = "npub1"
    }

    /**
     * Resolve an identifier to a hex pubkey.
     *
     * Tries Namecoin first, then npub, hex, and finally NIP-05 (HTTP).
     */
    suspend fun resolveIdentifier(
        identifier: String,
        resolveNip05: (suspend (String) -> String?)? = null,
    ): ResolvedIdentifier? {
        val trimmed = identifier.trim()

        // ── 1. Namecoin ────────────────────────────────────────────────
        if (resolveNamecoin != null && NamecoinNameResolver.isNamecoinIdentifier(trimmed)) {
            val outcome = resolveNamecoin.invoke(trimmed)
            return when (outcome) {
                is NamecoinResolveOutcome.Success -> {
                    ResolvedIdentifier(outcome.result.pubkey, namecoinSource = trimmed)
                }

                else -> {
                    null
                }
            }
        }

        // ── 2. Direct hex pubkey ───────────────────────────────────────
        if (HEX_PUBKEY_REGEX.matches(trimmed)) {
            return ResolvedIdentifier(trimmed.lowercase())
        }

        // ── 3. NIP-19 npub ────────────────────────────────────────────
        if (trimmed.startsWith(NPUB_PREFIX, ignoreCase = true)) {
            return try {
                val bytes = trimmed.bechToBytes()
                if (bytes.size == 32) {
                    ResolvedIdentifier(bytes.toHexKey())
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        // ── 4. NIP-05 (HTTP) ──────────────────────────────────────────
        if (trimmed.contains("@") && resolveNip05 != null) {
            val pk = resolveNip05(trimmed)
            if (pk != null) return ResolvedIdentifier(pk)
        }

        // ── 5. Bare string — try as NIP-05 ────────────────────────────
        if (resolveNip05 != null && !trimmed.startsWith("nsec")) {
            val pk = resolveNip05(trimmed)
            if (pk != null) return ResolvedIdentifier(pk)
        }

        return null
    }

    /**
     * Fetch the follow list for a given identifier.
     */
    suspend fun fetchFollowList(
        identifier: String,
        relayUrls: List<String>,
        fetchEvent: suspend (kind: Int, author: String, limit: Int, onEvent: (Kind3EventData) -> Unit) -> AutoCloseable?,
        resolveNip05: (suspend (String) -> String?)? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): FollowListResult =
        withContext(Dispatchers.IO) {
            // 1. Resolve identifier
            val resolved = resolveIdentifier(identifier, resolveNip05)
            if (resolved == null) {
                val msg =
                    if (resolveNamecoin != null && NamecoinNameResolver.isNamecoinIdentifier(identifier)) {
                        // Get detailed outcome for specific error message
                        val outcome = resolveNamecoin.invoke(identifier)
                        when (outcome) {
                            is NamecoinResolveOutcome.NameNotFound -> {
                                "Namecoin name \"$identifier\" does not exist on the blockchain. " +
                                    "Check the spelling or register it with Electrum-NMC."
                            }

                            is NamecoinResolveOutcome.NoNostrField -> {
                                "Namecoin name \"$identifier\" exists but has no \"nostr\" field. " +
                                    "The owner needs to add a nostr pubkey to the name's value."
                            }

                            is NamecoinResolveOutcome.ServersUnreachable -> {
                                "All Namecoin ElectrumX servers are unreachable. " +
                                    "Check your internet connection and try again. (${outcome.message})"
                            }

                            is NamecoinResolveOutcome.Timeout -> {
                                "Namecoin lookup for \"$identifier\" timed out. " +
                                    "ElectrumX servers may be slow or unreachable — try again later."
                            }

                            is NamecoinResolveOutcome.InvalidIdentifier -> {
                                "\"$identifier\" is not a valid Namecoin identifier. " +
                                    "Use .bit domains, d/name, or id/name format."
                            }

                            is NamecoinResolveOutcome.Success -> {
                                "Unexpected error resolving \"$identifier\"."
                            }
                        }
                    } else {
                        "Could not resolve \"$identifier\" to a public key. " +
                            "Enter an npub, hex pubkey, NIP-05, or Namecoin name (.bit / d/ / id/)."
                    }
                return@withContext FollowListResult.InvalidIdentifier(msg)
            }

            // 2. Fetch kind 3
            val deferred = CompletableDeferred<Kind3EventData?>()
            val sub =
                try {
                    fetchEvent(KIND_CONTACT_LIST, resolved.pubkeyHex, 1) { event ->
                        if (!deferred.isCompleted) deferred.complete(event)
                    }
                } catch (e: Exception) {
                    return@withContext FollowListResult.Error("Failed to connect to relays: ${e.message}")
                }

            val event = withTimeoutOrNull(timeoutMs) { deferred.await() }
            try {
                sub?.close()
            } catch (_: Exception) {
            }

            if (event == null) return@withContext FollowListResult.NoFollowList

            // 3. Parse p-tags
            val follows =
                event.pTags
                    .mapNotNull { tag ->
                        if (tag.isEmpty()) return@mapNotNull null
                        val pk = tag[0]
                        if (!HEX_PUBKEY_REGEX.matches(pk)) return@mapNotNull null
                        FollowEntry(
                            pubkeyHex = pk.lowercase(),
                            relayHint = tag.getOrNull(1)?.takeIf { it.isNotBlank() },
                            petname = tag.getOrNull(2)?.takeIf { it.isNotBlank() },
                        )
                    }.distinctBy { it.pubkeyHex }

            FollowListResult.Success(
                sourcePubkeyHex = resolved.pubkeyHex,
                follows = follows,
                createdAt = event.createdAt,
                resolvedViaNamecoin = resolved.namecoinSource,
            )
        }
}

/**
 * Internal: result of resolving an identifier, tracking whether Namecoin was used.
 */
data class ResolvedIdentifier(
    val pubkeyHex: String,
    val namecoinSource: String? = null,
)
