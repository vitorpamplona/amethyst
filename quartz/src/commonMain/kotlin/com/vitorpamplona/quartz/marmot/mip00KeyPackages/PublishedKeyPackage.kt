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

import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.AppComponentsTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.IEvent
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.TagArray

/**
 * A MIP-00 KeyPackage as published on Nostr, in either of its two kinds:
 *
 *  - [KeyPackageEvent], kind `30443`: the addressable form, rotated in place under its `d` slot;
 *  - [LegacyKeyPackageEvent], kind `443`: the regular form it replaced, which has no `d` tag.
 *
 * MIP-00's migration section gives both the same tags and the same content ("The legacy
 * `kind:443` event SHOULD carry the same KeyPackage content and the same applicable tags as
 * the `kind:30443` event, except for the `d` tag"), so the accessors live here once and the
 * invite path ([KeyPackageFetcher.fetchKeyPackageForInvite], [KeyPackageUtils.selectForInvite])
 * can hand either kind to the MLS layer.
 *
 * The properties are the `Event` ones; both implementations inherit them from there.
 */
sealed interface PublishedKeyPackage : IEvent {
    val id: HexKey
    val pubKey: HexKey
    val createdAt: Long
    val kind: Kind
    val tags: TagArray
    val content: String

    /**
     * Base64 of the TLS-serialized KeyPackage: the framed `MLSMessage`, or a bare
     * `KeyPackage` from older publishers. [KeyPackageUtils.decodeKeyPackage] reads both.
     */
    fun keyPackageBase64() = content

    /** MLS protocol version (e.g., "1.0") */
    fun mlsProtocolVersion() = tags.mlsProtocolVersion()

    /** MLS ciphersuite ID (e.g., "0x0001") */
    fun mlsCiphersuite() = tags.mlsCiphersuite()

    /** Supported non-default MLS extension IDs */
    fun mlsExtensions() = tags.mlsExtensions()

    /** Supported non-default MLS proposal type IDs */
    fun mlsProposals() = tags.mlsProposals()

    /** Content encoding format — MIP-era only; forbidden in the current profile. */
    fun encoding() = tags.encoding()

    /** Marmot app-component ids this KeyPackage advertises (current profile). */
    fun appComponents() = tags.appComponents()

    /**
     * True when this event advertises the current profile: it carries an
     * `app_components` tag naming `0x8009`. A MIP-era event has no such tag.
     */
    fun isCurrentProfile() = appComponents()?.any { it.equals(AppComponentsTag.ACCOUNT_IDENTITY_PROOF_V2, true) } == true

    /** Hex-encoded KeyPackageRef for efficient relay queries */
    fun keyPackageRef() = tags.keyPackageRef()

    /** Relays where this KeyPackage is published (MIP-era only; the current profile omits the tag). */
    fun relays() = tags.keyPackageRelays()

    /** Optional client name */
    fun clientName() = tags.clientName()

    /** Whether content encoding is valid (must be base64) */
    fun hasValidEncoding() = encoding() == EncodingTag.BASE64
}
