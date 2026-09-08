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
package com.vitorpamplona.quartz.marmot.mip00KeyPackages

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.BaseReplaceableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot KeyPackage Relay List Event (MIP-00) — kind 10051.
 *
 * Replaceable event that advertises which relays hold this user's MLS KeyPackages.
 * Other clients query this to discover where to fetch KeyPackages for group invitations.
 *
 * Uses the same "relay" tag format as NIP-17's ChatMessageRelayListEvent (kind 10050)
 * but serves a different purpose: KeyPackage discovery vs DM inbox routing.
 */
@Immutable
class KeyPackageRelayListEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseReplaceableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /**
     * Relays from this list, with local-network entries dropped.
     *
     * The drop is for lists that came from SOMEONE ELSE. A relay list is
     * attacker-supplied input, and an entry pointing at `127.0.0.1` or a
     * RFC 1918 address would aim our connection at our own machine or LAN. It
     * is also what exempts a relay from Tor, so an unfiltered entry could
     * quietly strip a relay's own onion routing.
     *
     * Use [allRelays] to read back a list this account published itself; our
     * own configuration is not attacker-supplied, and silently dropping it
     * makes a deliberately configured local relay look unconfigured.
     */
    fun relays(): List<NormalizedRelayUrl> = tags.mapNotNull(RelayTag::parse)

    /**
     * Every relay in this list, including local ones.
     *
     * For reading back OUR OWN published list. A user who configured a local
     * relay meant it, and [relays] would report their list as empty — which a
     * publisher then treats as "unconfigured" and answers with a default set
     * the user never chose. Sending a KeyPackage somewhere the user did not
     * pick is a worse outcome than the one the filter guards against.
     */
    fun allRelays(): List<NormalizedRelayUrl> = tags.mapNotNull(RelayTag::parseUnfiltered)

    companion object {
        const val KIND = 10051

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        private fun createTagArray(relays: List<NormalizedRelayUrl>): Array<Array<String>> =
            relays
                .map { RelayTag.assemble(it) }
                .toTypedArray()

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): KeyPackageRelayListEvent = signer.sign(createdAt, KIND, createTagArray(relays), "")

        fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSignerSync,
            createdAt: Long = TimeUtils.now(),
        ): KeyPackageRelayListEvent = signer.sign(createdAt, KIND, createTagArray(relays), "")

        suspend fun updateRelayList(
            earlierVersion: KeyPackageRelayListEvent,
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
            createdAt: Long = TimeUtils.now(),
        ): KeyPackageRelayListEvent {
            val tags =
                earlierVersion.tags
                    .filter { it.isEmpty() || it[0] != RelayTag.TAG_NAME }
                    .plus(relays.map { RelayTag.assemble(it) })
                    .toTypedArray()

            return signer.sign(createdAt, KIND, tags, earlierVersion.content)
        }
    }
}

/**
 * Relay tag parser for KeyPackage relay list events.
 * Uses the standard "relay" tag name.
 */
private object RelayTag {
    const val TAG_NAME = "relay"

    fun parse(tag: Array<String>): NormalizedRelayUrl? = parseUnfiltered(tag)?.takeUnless { it.isLocalHost() }

    fun parseUnfiltered(tag: Array<String>): NormalizedRelayUrl? {
        if (tag.size < 2 || tag[0] != TAG_NAME || tag[1].isEmpty()) return null
        return RelayUrlNormalizer.normalizeOrNull(tag[1])
    }

    fun assemble(relay: NormalizedRelayUrl) = arrayOf(TAG_NAME, relay.url)
}
