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
package com.vitorpamplona.quartz.nip87Ecash.recommendation

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.fastAny
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip50Search.IndexableFieldVisitor
import com.vitorpamplona.quartz.nip50Search.SearchableEvent
import com.vitorpamplona.quartz.nip87Ecash.MintUrlTag
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent
import com.vitorpamplona.quartz.utils.TimeUtils

@Immutable
class MintRecommendationEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider,
    SearchableEvent {
    // The recommended mint's announcement (kind 38172/38173), with the relay it lives on.
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseValidAddress)

    override fun indexableContent() = content

    // The read path: the same fields indexableContent() joins, handed over without
    // building the joined string a scan would throw away.
    override fun forEachIndexableField(visitor: IndexableFieldVisitor) {
        visitor.visit(content)
    }

    fun mintUrls() = tags.mintUrls()

    fun mintEventKind() = tags.mintEventKind()

    fun mintEventAddresses() = tags.mintEventAddresses()

    fun isCashuRecommendation() = mintEventKind() == CashuMintEvent.KIND

    fun isFedimintRecommendation() = mintEventKind() == FedimintEvent.KIND

    companion object {
        const val KIND = 38000

        /**
         * True when a kind-38000 event is actually a NIP-87 mint recommendation.
         *
         * Kind 38000 is shared with unrelated apps (prediction markets, ballots, spam votes), so
         * the kind number alone does not say what an event is. A recommendation names the kind of
         * the mint it recommends in its `k` tag — 38172 (Cashu) or 38173 (Fedimint). Some early
         * recommendations carry no `k` at all; those are accepted when they point at a mint URL
         * (`u`) or at the mint's announcement (an `a` to a 38172/38173 address). A `k` naming
         * only other kinds is not a mint recommendation, whatever else it carries.
         */
        fun isMintRecommendation(tags: TagArray): Boolean {
            // Any `k` naming a mint's kind: a relay answering `#k=38172` may hand back an event
            // whose first `k` is something else.
            if (tags.fastAny { it.size > 1 && it[0] == "k" && it[1].toIntOrNull().isMintKind() }) return true
            if (tags.fastAny { it.size > 1 && it[0] == "k" && it[1].isNotBlank() }) return false
            return tags.fastAny {
                it.size > 1 &&
                    (
                        (it[0] == MintUrlTag.TAG_NAME && it[1].isNotBlank()) ||
                            // The mint's own announcement, by address: `38172:<pubkey>:<d>`.
                            (it[0] == "a" && (it[1].startsWith(CASHU_ADDRESS) || it[1].startsWith(FEDIMINT_ADDRESS)))
                    )
            }
        }

        private fun Int?.isMintKind() = this == CashuMintEvent.KIND || this == FedimintEvent.KIND

        private val CASHU_ADDRESS = "${CashuMintEvent.KIND}:"
        private val FEDIMINT_ADDRESS = "${FedimintEvent.KIND}:"

        /**
         * [mintKind] must be [CashuMintEvent.KIND] or [FedimintEvent.KIND]: the `k` tag it writes is
         * what [isMintRecommendation] reads back, so any other value signs into a plain kind-38000
         * event rather than a [MintRecommendationEvent].
         */
        fun build(
            mintIdentifier: String,
            mintKind: Int,
            review: String = "",
            mintUrls: List<String> = emptyList(),
            mintEventAddress: String? = null,
            mintEventRelay: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<MintRecommendationEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, review, createdAt) {
            dTag(mintIdentifier)
            kTag(mintKind)
            mintUrls.forEach { mintUrl(it) }
            if (mintEventAddress != null) {
                aTag(mintEventAddress, mintEventRelay)
            }
            initializer()
        }
    }
}
