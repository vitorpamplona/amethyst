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
package com.vitorpamplona.quartz.nip54Wiki

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArray
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.hints.AddressHintProvider
import com.vitorpamplona.quartz.nip01Core.hints.types.AddressHint
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.aTag.ATag
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * A wiki redirect (kind 30819) — NIP-54.
 *
 * "If one thinks `Shell structure` should redirect to `Thin-shell structure` they can issue one
 * of these events instead of replicating the content." Clients use them to follow redirects
 * automatically and to build crowdsourced disambiguation pages.
 *
 * The spec section carries `[INSERT EVENT EXAMPLE]` and never gives the shape, so the only
 * reference is what the publishing clients emit: `d` is the normalized slug being redirected
 * *from*, and `a` is the article being redirected *to*.
 */
@Immutable
class WikiRedirectEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: TagArray,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig),
    AddressHintProvider {
    override fun addressHints(): List<AddressHint> = tags.mapNotNull(ATag::parseAsHint)

    override fun linkedAddressIds(): List<String> = tags.mapNotNull(ATag::parseAddressId)

    /** The slug being redirected from — this event's own `d`. */
    fun fromSlug(): String = dTag()

    /** The article being redirected to. */
    fun target(): Address? = tags.firstNotNullOfOrNull(ATag::parseAddress)

    /** A redirect that names no destination cannot be followed. */
    fun hasTarget() = target() != null

    companion object {
        const val KIND = 30819

        /**
         * The wiki slug rule, matched to what the publishing clients compute.
         *
         * Separators (whitespace, `-`, `.`, `_`) become a dash, letters and digits are kept, and
         * **everything else is dropped** — folding other punctuation to a dash instead would give
         * `C++ Programming` the slug `c--programming` where the rest of the network says
         * `c-programming`, and a redirect that disagrees on the slug points at nothing.
         *
         * Letters and digits are Unicode-wide, not ASCII, so non-Latin titles keep their words.
         *
         * One documented divergence: the reference implementation NFC-normalizes first, which
         * commonMain has no portable way to do. It only matters for input that arrives already
         * decomposed.
         */
        fun normalizeSlug(name: String): String {
            val out = StringBuilder(name.length)
            for (ch in name.lowercase()) {
                when {
                    ch.isWhitespace() || ch == '-' || ch == '.' || ch == '_' -> out.append('-')
                    ch.isLetterOrDigit() -> out.append(ch)
                    // Other punctuation and symbols are dropped, not folded.
                }
            }
            return out
                .toString()
                .replace(DASH_RUN, "-")
                .trim('-')
        }

        private val DASH_RUN = Regex("-+")

        fun build(
            fromSlug: String,
            target: Address,
            relay: NormalizedRelayUrl? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<WikiRedirectEvent>.() -> Unit = {},
        ): EventTemplate<WikiRedirectEvent> =
            eventTemplate(KIND, "", createdAt) {
                dTag(normalizeSlug(fromSlug))
                add(ATag.assemble(target, relay))

                initializer()
            }
    }
}
