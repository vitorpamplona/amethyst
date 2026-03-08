/**
 * FollowListImporter.kt
 *
 * Fetches another user's follow list (kind 3 / ContactListEvent) from Nostr
 * relays. Supports all identifier types:
 *
 *   - npub1...           (NIP-19 bech32)
 *   - 64-char hex pubkey
 *   - alice@example.com  (NIP-05 → HTTP lookup)
 *   - alice@example.bit  (Namecoin d/ namespace → blockchain lookup)
 *   - example.bit        (Namecoin d/ namespace, root identity)
 *   - d/example          (Namecoin d/ namespace, direct)
 *   - id/alice           (Namecoin id/ namespace)
 *
 * The Namecoin path is tried FIRST for any identifier that
 * NamecoinNameResolver.isNamecoinIdentifier() recognises. If it doesn't
 * match, we fall through to npub → hex → NIP-05 (HTTP) in order.
 *
 * SPDX-License-Identifier: MIT
 */
package com.vitorpamplona.amethyst.service.followimport

import com.vitorpamplona.amethyst.service.namecoin.NamecoinNameService
import com.vitorpamplona.quartz.nip05.namecoin.NamecoinNameResolver
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
    data class InvalidIdentifier(val reason: String) : FollowListResult()
    data class Error(val message: String) : FollowListResult()
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
 */
class FollowListImporter {

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
        if (NamecoinNameResolver.isNamecoinIdentifier(trimmed)) {
            val pubkey = NamecoinNameService.getInstance().resolvePubkey(trimmed)
            if (pubkey != null) {
                return ResolvedIdentifier(pubkey, namecoinSource = trimmed)
            }
            // If Namecoin resolution fails, don't fall through — the user
            // clearly intended a Namecoin lookup. Return null so the caller
            // can show a specific error.
            return null
        }

        // ── 2. Direct hex pubkey ───────────────────────────────────────
        if (HEX_PUBKEY_REGEX.matches(trimmed)) {
            return ResolvedIdentifier(trimmed.lowercase())
        }

        // ── 3. NIP-19 npub ────────────────────────────────────────────
        if (trimmed.startsWith(NPUB_PREFIX, ignoreCase = true)) {
            val hex = Bech32Util.decodeNpub(trimmed)
            if (hex != null) return ResolvedIdentifier(hex)
            return null
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
    ): FollowListResult = withContext(Dispatchers.IO) {

        // 1. Resolve identifier
        val resolved = resolveIdentifier(identifier, resolveNip05)
        if (resolved == null) {
            // Give a Namecoin-aware error message
            val msg = if (NamecoinNameResolver.isNamecoinIdentifier(identifier)) {
                "Could not resolve Namecoin name \"$identifier\". " +
                "Make sure the name exists and has a \"nostr\" field in its value."
            } else {
                "Could not resolve \"$identifier\" to a public key. " +
                "Enter an npub, hex pubkey, NIP-05, or Namecoin name (.bit / d/ / id/)."
            }
            return@withContext FollowListResult.InvalidIdentifier(msg)
        }

        // 2. Fetch kind 3
        val deferred = CompletableDeferred<Kind3EventData?>()
        val sub = try {
            fetchEvent(KIND_CONTACT_LIST, resolved.pubkeyHex, 1) { event ->
                if (!deferred.isCompleted) deferred.complete(event)
            }
        } catch (e: Exception) {
            return@withContext FollowListResult.Error("Failed to connect to relays: ${e.message}")
        }

        val event = withTimeoutOrNull(timeoutMs) { deferred.await() }
        try { sub?.close() } catch (_: Exception) {}

        if (event == null) return@withContext FollowListResult.NoFollowList

        // 3. Parse p-tags
        val follows = event.pTags.mapNotNull { tag ->
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

/**
 * Minimal Bech32 utility for npub decoding.
 * In production, replace with Quartz's bechToBytes()/toHexKey().
 */
internal object Bech32Util {
    private const val CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

    fun decodeNpub(npub: String): String? {
        if (!npub.startsWith("npub1", ignoreCase = true)) return null
        val lower = npub.lowercase()
        val hrpEnd = lower.lastIndexOf('1')
        if (hrpEnd < 1) return null
        val data = lower.substring(hrpEnd + 1).map { CHARSET.indexOf(it) }
        if (data.any { it == -1 }) return null
        val payload = data.dropLast(6)
        if (payload.isEmpty()) return null
        val bytes = convertBits(payload, 5, 8, false) ?: return null
        if (bytes.size != 32) return null
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun convertBits(data: List<Int>, from: Int, to: Int, pad: Boolean): List<Int>? {
        var acc = 0; var bits = 0; val result = mutableListOf<Int>(); val maxV = (1 shl to) - 1
        for (v in data) {
            if (v < 0 || v shr from != 0) return null
            acc = (acc shl from) or v; bits += from
            while (bits >= to) { bits -= to; result.add((acc shr bits) and maxV) }
        }
        if (pad) { if (bits > 0) result.add((acc shl (to - bits)) and maxV) }
        else if (bits >= from || (acc shl (to - bits)) and maxV != 0) return null
        return result
    }
}
