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
package com.vitorpamplona.quartz.nip72ModCommunities.rules

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag

/**
 * Validates draft events against a [CommunityRulesEvent] (NIP-9B).
 *
 * Pure logic; no I/O. Web-of-trust gates are deferred to the caller via
 * [WotResolver] because NIP-9B leaves the lookup mechanism unspecified
 * (NIP-02 follow lists, NIP-85 trusted assertions, etc).
 */
class CommunityRulesValidator(
    private val rules: CommunityRulesEvent,
) {
    /** A reason a draft event would be rejected. */
    sealed interface Violation {
        /** The rules document was signed before [minRulesCreatedAt] and must be ignored. */
        data class StaleRules(
            val rulesCreatedAt: Long,
            val minRulesCreatedAt: Long,
        ) : Violation

        /** Author is on the deny-list. */
        data class AuthorDenied(
            val author: HexKey,
        ) : Violation

        /** No `k` rule whitelists this kind. */
        data class KindNotAllowed(
            val kind: Int,
        ) : Violation

        /** Event exceeds the kind-specific size limit. */
        data class KindSizeExceeded(
            val kind: Int,
            val sizeBytes: Int,
            val maxBytes: Int,
        ) : Violation

        /** Event exceeds the global `max_event_size`. */
        data class MaxSizeExceeded(
            val sizeBytes: Int,
            val maxBytes: Int,
        ) : Violation

        /** Author exceeded the per-day quota for this kind. */
        data class QuotaExceeded(
            val kind: Int,
            val postsToday: Int,
            val maxPerDay: Int,
        ) : Violation

        /** Author failed every web-of-trust gate. */
        data class WotGateFailed(
            val gateCount: Int,
        ) : Violation
    }

    /** Looks up whether [author] is reachable from [root] in the follow graph within [depth] hops. */
    fun interface WotResolver {
        fun isReachable(
            author: HexKey,
            root: HexKey,
            depth: Int,
        ): Boolean
    }

    /**
     * Validates a draft event. Returns null on success, or the first violation found.
     *
     * @param author pubkey of the draft author
     * @param kind kind of the draft event
     * @param sizeBytes JSON-encoded event size in bytes
     * @param postsTodayByKind callback returning the count of events of [kind] this author has
     *   already published today, or null if unknown (in which case quota checks are skipped)
     * @param wot resolver for `wot` gates, or null to skip WoT checks
     */
    fun validate(
        author: HexKey,
        kind: Int,
        sizeBytes: Int,
        postsTodayByKind: ((Int) -> Int?)? = null,
        wot: WotResolver? = null,
    ): Violation? {
        // 1. Stale-rules ratchet: callers should pre-filter, but defend in depth.
        rules.minRulesCreatedAt()?.let { minAt ->
            if (rules.createdAt < minAt) {
                return Violation.StaleRules(rules.createdAt, minAt)
            }
        }

        // 2. Author deny-list overrides everything else.
        if (rules.policyFor(author) == PubkeyRuleTag.Policy.DENY) {
            return Violation.AuthorDenied(author)
        }

        // 3. Kind whitelist.
        val kindRule = rules.ruleForKind(kind) ?: return Violation.KindNotAllowed(kind)

        // 4. Per-kind size limit.
        kindRule.maxBytes?.let { maxBytes ->
            if (sizeBytes > maxBytes) {
                return Violation.KindSizeExceeded(kind, sizeBytes, maxBytes)
            }
        }

        // 5. Global size cap.
        rules.maxEventSize()?.let { maxBytes ->
            if (sizeBytes > maxBytes) {
                return Violation.MaxSizeExceeded(sizeBytes, maxBytes)
            }
        }

        // 6. Per-day quota.
        if (kindRule.maxPerAuthorPerDay != null && postsTodayByKind != null) {
            val today = postsTodayByKind(kind)
            if (today != null && today >= kindRule.maxPerAuthorPerDay) {
                return Violation.QuotaExceeded(kind, today, kindRule.maxPerAuthorPerDay)
            }
        }

        // 7. WoT gates: pass if any one gate accepts the author. Allow-listed pubkeys
        // bypass WoT (an explicit `allow` is a stronger signal than a graph traversal).
        if (rules.policyFor(author) != PubkeyRuleTag.Policy.ALLOW) {
            val gates = rules.wotGates()
            if (gates.isNotEmpty() && wot != null) {
                val anyPasses = gates.any { wot.isReachable(author, it.rootPubkey, it.depth) }
                if (!anyPasses) return Violation.WotGateFailed(gates.size)
            }
        }

        return null
    }
}
