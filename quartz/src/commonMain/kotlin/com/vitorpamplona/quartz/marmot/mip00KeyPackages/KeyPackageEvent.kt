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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip31Alts.alt
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Marmot KeyPackage Event (MIP-00) — kind 30443.
 *
 * Addressable event that publishes an MLS KeyPackage for asynchronous group invitations.
 * Each KeyPackage is identified by its (kind, pubkey, d-tag) tuple, enabling native
 * rotation without NIP-09 deletion events.
 *
 * Content: base64-encoded TLS-serialized KeyPackageBundle from MLS library.
 *
 * Required tags: d, mls_protocol_version, mls_ciphersuite, mls_extensions,
 *                mls_proposals, encoding, i, relays
 * Optional tags: client
 */
@Immutable
class KeyPackageEvent(
    id: HexKey,
    pubKey: HexKey,
    createdAt: Long,
    tags: Array<Array<String>>,
    content: String,
    sig: HexKey,
) : BaseAddressableEvent(id, pubKey, createdAt, KIND, tags, content, sig) {
    /** Base64-encoded TLS-serialized KeyPackageBundle */
    fun keyPackageBase64() = content

    /** MLS protocol version (e.g., "1.0") */
    fun mlsProtocolVersion() = tags.mlsProtocolVersion()

    /** MLS ciphersuite ID (e.g., "0x0001") */
    fun mlsCiphersuite() = tags.mlsCiphersuite()

    /** Supported non-default MLS extension IDs */
    fun mlsExtensions() = tags.mlsExtensions()

    /** Supported non-default MLS proposal type IDs */
    fun mlsProposals() = tags.mlsProposals()

    /** Content encoding format (must be "base64") */
    fun encoding() = tags.encoding()

    /** Hex-encoded KeyPackageRef for efficient relay queries */
    fun keyPackageRef() = tags.keyPackageRef()

    /** Relays where this KeyPackage is published */
    fun relays() = tags.keyPackageRelays()

    /** Optional client name */
    fun clientName() = tags.clientName()

    /** Whether content encoding is valid (must be base64) */
    fun hasValidEncoding() = encoding() == EncodingTag.BASE64

    companion object {
        const val KIND = 30443
        const val ALT_DESCRIPTION = "MLS KeyPackage"

        fun build(
            keyPackageBase64: String,
            dTagSlot: String,
            keyPackageRef: HexKey,
            relays: List<NormalizedRelayUrl>,
            ciphersuite: String = "0x0001",
            clientName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<KeyPackageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, keyPackageBase64, createdAt) {
            alt(ALT_DESCRIPTION)
            dTag(dTagSlot)
            mlsProtocolVersion()
            mlsCiphersuite(ciphersuite)
            mlsExtensions(listOf("0xf2ee", "0x000a"))
            mlsProposals(listOf("0x000a"))
            encoding()
            keyPackageRef(keyPackageRef)
            keyPackageRelays(relays)
            clientName?.let { client(it) }
            initializer()
        }
    }
}
