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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.isLocalHost
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip31Alts.AltTag
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
    fun relays(): List<NormalizedRelayUrl> = tags.mapNotNull(RelayTag::parse)

    companion object {
        const val KIND = 10051
        const val ALT_DESCRIPTION = "MLS KeyPackage relay list"

        fun createAddress(pubKey: HexKey): Address = Address(KIND, pubKey, FIXED_D_TAG)

        fun createAddressTag(pubKey: HexKey): String = Address.assemble(KIND, pubKey, FIXED_D_TAG)

        private fun createTagArray(relays: List<NormalizedRelayUrl>): Array<Array<String>> =
            relays
                .map { RelayTag.assemble(it) }
                .plusElement(AltTag.assemble(ALT_DESCRIPTION))
                .toTypedArray()

        suspend fun create(
            relays: List<NormalizedRelayUrl>,
            signer: NostrSigner,
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

    fun parse(tag: Array<String>): NormalizedRelayUrl? {
        if (tag.size < 2 || tag[0] != TAG_NAME || tag[1].isEmpty()) return null
        val relay =
            com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
                .normalizeOrNull(tag[1])
        return relay?.takeUnless { it.isLocalHost() }
    }

    fun assemble(relay: NormalizedRelayUrl) = arrayOf(TAG_NAME, relay.url)
}
