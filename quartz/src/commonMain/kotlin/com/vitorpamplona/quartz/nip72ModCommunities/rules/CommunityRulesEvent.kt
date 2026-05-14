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

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.KindRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.MaxEventSizeTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.MinRulesCreatedAtTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.PubkeyRuleTag
import com.vitorpamplona.quartz.nip72ModCommunities.rules.tags.WotTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * NIP-9B Verifiable Community Rules.
 *
 * Addressable replaceable event (`kind:34551`) carrying a machine-readable, signed
 * rules document for a community defined by NIP-72 (`kind:34550`). Clients fetch
 * the latest rules event before publishing into a community and reject drafts
 * locally if they would violate any rule, surfacing the violation to the user
 * before send.
 *
 * Strictly additive to NIP-72: the freeform `rules` tag on `kind:34550` continues
 * to carry human-readable text. This event carries the machine-readable companion.
 *
 * See `9A.md` in nostr-protocol/nips for the full specification.
 */
@Immutable
class CommunityRulesEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints() = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds() = tags.mapNotNull(ATag::parseAddressId)

    /** All `k` rules. May contain duplicates; callers usually want [allowedKinds]. */
    fun kindRules(): List<KindRuleTag> = tags.mapNotNull(KindRuleTag::parse)

    /** Distinct kinds whitelisted by this rules document. */
    fun allowedKinds(): Set<Int> = kindRules().map { it.kind }.toSet()

    /** Returns the rule for [kind], or null if the kind is not whitelisted. */
    fun ruleForKind(kind: Int): KindRuleTag? = kindRules().firstOrNull { it.kind == kind }

    fun pubkeyRules(): List<PubkeyRuleTag> = tags.mapNotNull(PubkeyRuleTag::parse)

    /**
     * Returns the policy declared for [pubkey], or null if no `p` rule applies.
     * `deny` overrides `allow`; if both exist for the same pubkey, deny wins.
     */
    fun policyFor(pubkey: HexKey): PubkeyRuleTag.Policy? {
        var allow: PubkeyRuleTag.Policy? = null
        for (rule in pubkeyRules()) {
            if (rule.pubkey != pubkey) continue
            if (rule.policy == PubkeyRuleTag.Policy.DENY) return PubkeyRuleTag.Policy.DENY
            allow = PubkeyRuleTag.Policy.ALLOW
        }
        return allow
    }

    fun wotGates(): List<WotTag> = tags.mapNotNull(WotTag::parse)

    fun maxEventSize(): Int? = tags.firstNotNullOfOrNull(MaxEventSizeTag::parse)

    fun minRulesCreatedAt(): Long? = tags.firstNotNullOfOrNull(MinRulesCreatedAtTag::parse)

    /** Address (`a` tag) of the community this rules document governs. */
    fun communityAddress(): String? = tags.firstNotNullOfOrNull(ATag::parseAddressId)

    companion object {
        const val KIND = 34551
        const val KIND_STR = "34551"
        const val ALT_DESCRIPTION = "Community rules"

        fun build(
            dTag: String,
            communityAddress: ATag,
            kindRules: List<KindRuleTag>,
            pubkeyRules: List<PubkeyRuleTag> = emptyList(),
            wotGates: List<WotTag> = emptyList(),
            maxEventSize: Int? = null,
            minRulesCreatedAt: Long? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<CommunityRulesEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, "", createdAt) {
            alt(ALT_DESCRIPTION)

            dTag(dTag)
            add(communityAddress.toATagArray())

            kindRules.forEach { add(it.toTagArray()) }
            pubkeyRules.forEach { add(it.toTagArray()) }
            wotGates.forEach { add(it.toTagArray()) }
            maxEventSize?.let { add(MaxEventSizeTag.assemble(it)) }
            minRulesCreatedAt?.let { add(MinRulesCreatedAtTag.assemble(it)) }

            initializer()
        }
    }
}
