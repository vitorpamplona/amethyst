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
package com.vitorpamplona.quartz.nip89AppHandlers.definition.tags

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.graph.props.ReleaseProps
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.tags.aTag.AddressReferenceTag
import com.vitorpamplona.quartz.utils.arrayOfNotNull
import com.vitorpamplona.quartz.utils.ensure

/**
 * An app descriptor's release pointers to its nsite manifests, written like an `a`:
 * `["latest", "<kind>:<pubkey>:<d>", "<relay>"]` is the current manifest and `["next", …]` the
 * one intended for rollout. The tag name is the [release], as a platform link's name is its
 * platform.
 */
@Immutable
data class ManifestReleaseTag(
    val release: String,
    val address: Address,
    override val relayHint: NormalizedRelayUrl? = null,
) : AddressReferenceTag {
    override fun toAddressId() = address.toValue()

    /** Which release this manifest is, as the link's qualifier. */
    fun linkProps() = ReleaseProps(release)

    fun toTagArray() = assemble(release, address, relayHint)

    companion object {
        const val LATEST = "latest"
        const val NEXT = "next"

        fun match(tag: Array<String>) = tag.has(1) && (tag[0] == LATEST || tag[0] == NEXT)

        fun parse(tag: Array<String>): ManifestReleaseTag? {
            ensure(match(tag)) { return null }
            ensure(tag[1].isNotEmpty()) { return null }
            val address = Address.parse(tag[1]) ?: return null
            val hint = tag.getOrNull(2)?.let { RelayUrlNormalizer.normalizeOrNull(it) }
            return ManifestReleaseTag(tag[0], address, hint)
        }

        fun assemble(
            release: String,
            address: Address,
            relayHint: NormalizedRelayUrl? = null,
        ) = arrayOfNotNull(release, address.toValue(), relayHint?.url)
    }
}
