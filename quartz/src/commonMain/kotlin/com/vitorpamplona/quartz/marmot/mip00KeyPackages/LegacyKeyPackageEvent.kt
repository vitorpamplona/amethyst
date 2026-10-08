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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProposalsTag
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Legacy Marmot KeyPackage (MIP-00 before the addressable migration) — kind 443.
 *
 * A regular event with the same content and tags as a MIP-era [KeyPackageEvent]
 * minus the `d` slot: `mls_protocol_version`, `mls_ciphersuite`, `mls_extensions`,
 * `mls_proposals`, `relays`, `i` (KeyPackageRef), `encoding` = `base64` and an
 * optional `client`. The content is base64 of a TLS KeyPackage; White Noise's MDK
 * publishes the BARE `KeyPackage` (no `MLSMessage` envelope), which
 * [KeyPackageUtils.decodeKeyPackage] accepts.
 *
 * The Marmot spec moved KeyPackages to kind 30443 with a cutover on 2026-05-01, after
 * which "Legacy `kind:443` support becomes optional", and the current spec lists 443 as
 * a removed kind. MDK 0.7/0.8 still publishes it, though, and some of its users publish
 * nothing else, so an inviter that only reads 30443 cannot add them. MIP-00 lets an
 * inviter "fall back to the freshest valid `kind:443` event" when no valid 30443
 * exists, and that is the only thing this class is for: Amethyst reads it, it never
 * publishes one (see [KeyPackageUtils.selectForInvite]).
 *
 * Being regular, a 443 cannot be rotated in place: the same package stays on relays
 * after someone uses it, until its author deletes it with NIP-09. MDK marks these
 * KeyPackages `last_resort` (`0x000a`), so a reused package still opens; consuming one
 * needs nothing from the inviter beyond naming its event id in the Welcome's `e` tag.
 *
 * It references no other event, address or user, so it has no hint providers, and it
 * carries no human-authored text, so it is not searchable.
 */
@Immutable
class LegacyKeyPackageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : Event(id, pubKey, createdAt, KIND, tags, content, sig),
    PublishedKeyPackage {
    companion object {
        const val KIND = 443

        /**
         * Build a kind 443 KeyPackage, the MIP-era tag set without `d`.
         *
         * MIP-00 says clients SHOULD stop publishing 443 after the 2026-05-01 cutover;
         * this exists for tests and for interop tooling that has to stand in for a
         * legacy peer, not for Amethyst's own publication path.
         */
        fun build(
            keyPackageBase64: String,
            keyPackageRef: HexKey,
            relays: List<NormalizedRelayUrl>,
            ciphersuite: String = "0x0001",
            clientName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<LegacyKeyPackageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, keyPackageBase64, createdAt) {
            mlsProtocolVersion()
            mlsCiphersuite(ciphersuite)
            mlsExtensions(listOf("0xf2ee", "0x000a"))
            mlsProposals(listOf(MlsProposalsTag.SELF_REMOVE))
            encoding()
            keyPackageRef(keyPackageRef)
            keyPackageRelays(relays)
            clientName?.let { client(it) }
            initializer()
        }
    }
}
