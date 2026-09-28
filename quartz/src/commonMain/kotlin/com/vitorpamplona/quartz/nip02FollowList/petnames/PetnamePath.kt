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
package com.vitorpamplona.quartz.nip02FollowList.petnames

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.fastFirstNotNullOfOrNull
import com.vitorpamplona.quartz.nip01Core.core.fastForEach
import com.vitorpamplona.quartz.nip02FollowList.tags.ContactTag
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.coroutines.CancellationException

/** Where a NIP-02 petname path starts. */
@Immutable
sealed interface PetnameRoot {
    /** `~/…`: the logged-in user's own follow list. */
    data object CurrentUser : PetnameRoot

    /** `~npub1…/…`: an absolute root by key. */
    data class PubKey(
        val pubKey: HexKey,
    ) : PetnameRoot

    /** `~name@domain/…`: an absolute root by NIP-05 identifier. */
    data class Nip05(
        val identifier: String,
    ) : PetnameRoot
}

/**
 * A NIP-02 petname path such as `~/erin/charlie`, `~npub1…/erin/charlie` or
 * `~carol@names.com/erin/charlie`.
 *
 * Each component is looked up in the follow list (kind 3) of the profile the previous
 * component resolved to: `~/erin/charlie` is whoever Erin calls `charlie`, where Erin is
 * whoever the current user calls `erin`. Only petnames made of ASCII letters, digits and
 * `_` take part in path resolution.
 */
@Immutable
data class PetnamePath(
    val root: PetnameRoot,
    val names: List<String>,
) {
    fun encode(): String {
        val prefix =
            when (root) {
                PetnameRoot.CurrentUser -> "$PREFIX"
                is PetnameRoot.PubKey -> PREFIX + NPub.create(root.pubKey)
                is PetnameRoot.Nip05 -> PREFIX + root.identifier
            }
        return if (names.isEmpty()) prefix else prefix + SEPARATOR + names.joinToString(SEPARATOR.toString())
    }

    companion object {
        const val PREFIX = '~'
        const val SEPARATOR = '/'

        /** NIP-02: only ASCII letters, numbers or `_` qualify for petname resolution. */
        fun isEligible(petname: String): Boolean =
            petname.isNotEmpty() &&
                petname.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

        /** How a contact of a contact is shown in the current user's context: `~/erin/charlie`. */
        fun display(names: List<String>): String = PREFIX + SEPARATOR.toString() + names.joinToString(SEPARATOR.toString())

        /** Parses [input] into a path, or null when it is not a well-formed petname path. */
        fun parse(input: String): PetnamePath? {
            val text = input.trim()
            if (text.length < 2 || text[0] != PREFIX) return null

            val parts = text.substring(1).split(SEPARATOR)
            val rootToken = parts[0]
            val names = parts.drop(1)
            if (!names.all(::isEligible)) return null

            val root =
                when {
                    rootToken.isEmpty() -> PetnameRoot.CurrentUser
                    rootToken.startsWith("npub1") || rootToken.startsWith("nprofile1") -> PetnameRoot.PubKey(decodeKey(rootToken) ?: return null)
                    looksLikeNip05(rootToken) -> PetnameRoot.Nip05(rootToken)
                    else -> return null
                }

            // `~/` alone names nobody; an absolute root on its own names the root.
            if (root == PetnameRoot.CurrentUser && names.isEmpty()) return null

            return PetnamePath(root, names)
        }

        private fun decodeKey(token: String): HexKey? =
            try {
                when (val entity = Nip19Parser.uriToRoute(token)?.entity) {
                    is NPub -> entity.hex
                    is NProfile -> entity.hex
                    else -> null
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }

        private fun looksLikeNip05(token: String): Boolean {
            val at = token.indexOf('@')
            if (at < 0 || at == token.length - 1) return false
            val domain = token.substring(at + 1)
            return domain.contains('.') && !domain.contains('@')
        }
    }
}

/**
 * Pure NIP-02 petname lookups over follow-list tags. Knows nothing about caches or relays:
 * callers hand in how to get a follow list and how to resolve a NIP-05 identifier.
 */
object PetnameResolver {
    /**
     * The petname [followList] gives [pubKey], if any. Any non-blank petname counts here:
     * the character restriction only applies to resolving paths.
     */
    fun petnameOf(
        followList: TagArray,
        pubKey: HexKey,
    ): String? =
        followList.fastFirstNotNullOfOrNull { tag ->
            if (tag.size > 3 && ContactTag.isTagged(tag, pubKey) && tag[3].isNotBlank()) tag[3] else null
        }

    /**
     * Every petname in [followList], by pubkey (the first one wins when a key repeats). Meant to be
     * built once per follow-list version and reused for every name rendered against it.
     */
    fun petnames(followList: TagArray): Map<HexKey, String> {
        val result = HashMap<HexKey, String>()
        followList.fastForEach { tag ->
            if (tag.size > 3 && ContactTag.isTagged(tag) && tag[3].isNotBlank() && tag[1] !in result) {
                result[tag[1]] = tag[3]
            }
        }
        return result
    }

    /** The pubkey [followList] calls [petname] (exact, case-sensitive match; eligible names only). */
    fun findByPetname(
        followList: TagArray,
        petname: String,
    ): HexKey? {
        if (!PetnamePath.isEligible(petname)) return null
        return followList.fastFirstNotNullOfOrNull { tag ->
            if (tag.size > 3 && tag[3] == petname && ContactTag.isTagged(tag)) tag[1] else null
        }
    }

    /**
     * Resolves [path] one component at a time.
     *
     * @param currentUser the logged-in user, required for `~/…` paths
     * @param followListOf the kind-3 tags of a user, or null when unknown
     * @param resolveNip05 resolves a `name@domain` root; the default resolves nothing
     * @return the pubkey the path points to, or null when any step cannot be resolved
     */
    suspend fun resolve(
        path: PetnamePath,
        currentUser: HexKey?,
        followListOf: suspend (HexKey) -> TagArray?,
        resolveNip05: suspend (String) -> HexKey? = { null },
    ): HexKey? {
        var current: HexKey =
            when (val root = path.root) {
                PetnameRoot.CurrentUser -> currentUser
                is PetnameRoot.PubKey -> root.pubKey
                is PetnameRoot.Nip05 -> resolveNip05(root.identifier)
            } ?: return null

        for (name in path.names) {
            val followList = followListOf(current) ?: return null
            current = findByPetname(followList, name) ?: return null
        }
        return current
    }

    /** Parses and resolves [input]; null when it is not a petname path or does not resolve. */
    suspend fun resolve(
        input: String,
        currentUser: HexKey?,
        followListOf: suspend (HexKey) -> TagArray?,
        resolveNip05: suspend (String) -> HexKey? = { null },
    ): HexKey? {
        val path = PetnamePath.parse(input) ?: return null
        return resolve(path, currentUser, followListOf, resolveNip05)
    }
}
