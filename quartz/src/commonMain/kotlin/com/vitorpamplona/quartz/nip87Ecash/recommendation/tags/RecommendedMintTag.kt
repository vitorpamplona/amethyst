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
package com.vitorpamplona.quartz.nip87Ecash.recommendation.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.links.props.PlatformProps
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.fedimint.FedimintEvent

/**
 * The `a` tag a NIP-87 recommendation (38000) names the recommended mint's announcement with: a
 * 38172 cashu mint or a 38173 fedimint. [platform] says which, from the announcement's kind; an
 * `a` to any other kind is not a mint.
 */
@Immutable
data class RecommendedMintTag(
    val address: ATag,
    val platform: String,
) : AddressReferenceTag {
    override fun toAddressId() = address.toAddressId()

    override val relayHint get() = address.relayHint

    /** The mint's platform, as the recommendation's props. */
    fun linkProps() = PlatformProps(platform)

    companion object {
        const val TAG_NAME = ATag.TAG_NAME
        const val CASHU = "cashu"
        const val FEDIMINT = "fedimint"

        fun parse(tag: Array<String>): RecommendedMintTag? {
            val address = ATag.parse(tag) ?: return null
            val platform =
                when (address.kind) {
                    CashuMintEvent.KIND -> CASHU
                    FedimintEvent.KIND -> FEDIMINT
                    else -> return null
                }
            return RecommendedMintTag(address, platform)
        }
    }
}
