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
package com.vitorpamplona.quartz.experimental.trustedLists.treasureMap

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.experimental.trustedLists.addressables.AddressableTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.events.EventTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.externalIds.ExternalIdTrustedListEvent
import com.vitorpamplona.quartz.experimental.trustedLists.users.UserTrustedListEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Tag
import com.vitorpamplona.quartz.nip01Core.core.has
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.utils.ensure

/**
 * A Trusted List entry in a NIP-85 Treasure Map (kind 10040).
 *
 * The Map delegates each Trusted-Assertion kind+metric to a publisher with
 * `["30382:rank", <pubkey>, <relay>]`. Trusted Lists extend it with a
 * **generic bare-kind entry** (Tapestry ADR `tl-treasure-map/0001`):
 *
 * ```json
 * ["30392", "<publisher-pubkey>", "wss://nip85.brainstorm.world"]
 * ```
 *
 * One entry delegates *all* lists of that kind -- the lists computed under the
 * Map owner's point of view, discoverable at the relay hint. List names are
 * never enumerated, which is the whole point of the bare-kind form: the Map
 * stays a fixed size no matter how many lists the publisher computes.
 *
 * This deliberately lives outside `nip85TrustedAssertions` even though it
 * rides on that kind: the entry is a pre-NIP Tapestry extension, and
 * [com.vitorpamplona.quartz.nip85TrustedAssertions.list.tags.ServiceProviderTag]
 * models NIP-85's own `3038x:<metric>` delegation. Keeping them apart is what
 * lets a NIP-85 consumer stay unaware of this family -- and is why
 * `serviceProviders()` does not hand a `3039x` entry to code looking for a
 * rank provider.
 */
@Immutable
data class TrustedListProviderTag(
    /** The Trusted List kind delegated: one of [KINDS]. */
    val kind: Int,
    /**
     * The list name on a **named** entry, or null on the generic one.
     *
     * Named entries are *reserved*: once specified they will override the
     * generic entry for that one list. Until then they are parsed so a reader
     * can display them as Trusted List entries, and must drive no behavior --
     * which is why [isGeneric] is the guard every consumer should ask before
     * acting on an entry.
     */
    val name: String? = null,
    val pubkey: HexKey,
    /**
     * Where the publisher's lists of this kind can be found, or null when the
     * publisher had no relay configured. The spec keeps the entry at its
     * three-element shape with an empty string in that slot, so an absent hint
     * must not take the delegation down with it -- the pubkey is the part a
     * consumer cannot do without.
     */
    val relayUrl: NormalizedRelayUrl? = null,
) {
    /**
     * True for the bare-kind entry, the only form that currently drives
     * behavior. Named entries parse but stay inert until the spec defines them.
     */
    val isGeneric: Boolean get() = name == null

    fun toTagArray() = assemble(kind, name, pubkey, relayUrl)

    companion object {
        /** The Trusted List kinds a Map entry may delegate. */
        val KINDS =
            setOf(
                UserTrustedListEvent.KIND,
                EventTrustedListEvent.KIND,
                AddressableTrustedListEvent.KIND,
                ExternalIdTrustedListEvent.KIND,
            )

        /**
         * Splits the first element on `:`, per the ADR's parse rule: a single
         * all-digits segment is a generic entry, two segments are a named one.
         *
         * A 10040 carries foreign tags -- `["client", "nostria"]` and the like
         * -- so everything that is not a Trusted List entry must fall out as
         * null rather than throw. The kind is checked against [KINDS] for the
         * same reason NIP-85's own parser checks its range: `30382:rank` splits
         * into two segments too, and it is not ours.
         */
        fun parse(tag: Tag): TrustedListProviderTag? {
            ensure(tag.has(1)) { return null }
            ensure(tag[0].isNotEmpty()) { return null }
            ensure(tag[1].length == 64) { return null }

            val divider = tag[0].indexOf(':')

            val kind: Int
            val name: String?
            if (divider < 0) {
                kind = tag[0].toIntOrNull() ?: return null
                name = null
            } else {
                kind = tag[0].substring(0, divider).toIntOrNull() ?: return null
                // "30392:" is neither generic nor named -- a name was intended
                // and lost, so it is not something to display or act on
                name = tag[0].substring(divider + 1).takeIf { it.isNotEmpty() } ?: return null
            }

            ensure(kind in KINDS) { return null }

            return TrustedListProviderTag(kind, name, tag[1], relayHint(tag))
        }

        /** The generic entry alone, for callers that must not act on a reserved named one. */
        fun parseGeneric(tag: Tag): TrustedListProviderTag? = parse(tag)?.takeIf { it.isGeneric }

        private fun relayHint(tag: Tag): NormalizedRelayUrl? {
            val raw = tag.getOrNull(2)?.takeIf { it.isNotEmpty() } ?: return null
            return RelayUrlNormalizer.normalizeOrNull(raw)
        }

        fun assembleServiceType(
            kind: Int,
            name: String? = null,
        ) = if (name == null) kind.toString() else "$kind:$name"

        /**
         * Always three elements, with an empty relay slot when there is no hint:
         * the shape is what a reader indexes by, so it does not vary with what
         * the publisher happened to have configured.
         */
        fun assemble(
            kind: Int,
            name: String?,
            pubkey: HexKey,
            relayUrl: NormalizedRelayUrl?,
        ) = arrayOf(assembleServiceType(kind, name), pubkey, relayUrl?.url ?: "")

        fun assemble(provider: TrustedListProviderTag) = provider.toTagArray()
    }
}
