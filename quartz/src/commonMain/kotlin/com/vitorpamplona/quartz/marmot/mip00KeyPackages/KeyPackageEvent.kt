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
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.AppComponentsTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.EncodingTag
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.tags.MlsProposalsTag
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentsList
import com.vitorpamplona.quartz.marmot.mls.messages.MlsKeyPackage
import com.vitorpamplona.quartz.nip01Core.core.BaseAddressableEvent
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.TagArrayBuilder
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.eventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    /** Relays where this KeyPackage is published */
    fun relays() = tags.keyPackageRelays()

    /** Optional client name */
    fun clientName() = tags.clientName()

    /** Whether content encoding is valid (must be base64) */
    fun hasValidEncoding() = encoding() == EncodingTag.BASE64

    companion object {
        const val KIND = 30443

        /** `app_data_dictionary` — the current profile's only required MLS extension. */
        const val CURRENT_PROFILE_EXTENSION = "0x0006"

        /** `app_data_update` — required alongside `self_remove`. */
        const val APP_DATA_UPDATE_PROPOSAL = "0x0008"

        /**
         * Build a CURRENT-PROFILE kind:30443 event.
         *
         * Differences from [build], all of them wire-visible:
         *  - `mls_extensions` names `app_data_dictionary` (`0x0006`), not
         *    MIP-01's `marmot_group_data`; last resort is a KeyPackage
         *    component now, not extension `0x000a`;
         *  - `mls_proposals` adds `app_data_update` (`0x0008`);
         *  - an `app_components` tag is required and must include `0x8009`;
         *  - NO `encoding` tag — the current profile forbids it;
         *  - NO `relays` tag — discovery uses the author's NIP-65 write set,
         *    and the spec removed the dedicated KeyPackage relay list.
         *
         * [dTagSlot] is a stable random 32-byte publication slot id. Replacing
         * the KeyPackage in that logical slot MUST reuse the same value; a
         * fresh one creates a second concurrently discoverable slot.
         */
        fun buildCurrentProfile(
            keyPackageBase64: String,
            dTagSlot: String,
            keyPackageRef: HexKey,
            appComponentIds: List<String>,
            ciphersuite: String = "0x0001",
            mlsExtensionIds: List<String> = listOf(CURRENT_PROFILE_EXTENSION),
            mlsProposalIds: List<String> = listOf(APP_DATA_UPDATE_PROPOSAL, MlsProposalsTag.SELF_REMOVE),
            clientName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<KeyPackageEvent>.() -> Unit = {},
        ) = eventTemplate(KIND, keyPackageBase64, createdAt) {
            dTag(dTagSlot)
            mlsProtocolVersion()
            mlsCiphersuite(ciphersuite)
            mlsExtensions(mlsExtensionIds.distinct().sorted())
            mlsProposals(mlsProposalIds.distinct().sorted())
            appComponents(
                (appComponentIds + AppComponentsTag.ACCOUNT_IDENTITY_PROOF_V2).distinct().sorted(),
            )
            keyPackageRef(keyPackageRef)
            clientName?.let { client(it) }
            initializer()
        }

        /**
         * Build the event from the KeyPackage itself, deriving every id-list
         * tag from the bytes it advertises.
         *
         * The tags duplicate metadata that is already inside the KeyPackage, so
         * writing them by hand is writing a second source of truth — and MDK
         * rejects a KeyPackage whose `mls_extensions` tag "does not exactly
         * match decoded KeyPackage metadata". Adding one leaf capability and
         * forgetting the tag is enough to make every one of our KeyPackages
         * unusable, which is exactly what happened.
         *
         * `app_components` lists the Marmot registry ids only. The
         * `app_components` component itself (`0x0001`) and the other upstream
         * MLS-extensions component ids live below `0x8000` and are not app
         * components being advertised.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun buildCurrentProfileFrom(
            keyPackage: MlsKeyPackage,
            dTagSlot: String,
            clientName: String? = null,
            createdAt: Long = TimeUtils.now(),
            initializer: TagArrayBuilder<KeyPackageEvent>.() -> Unit = {},
        ) = buildCurrentProfile(
            keyPackageBase64 = Base64.encode(KeyPackageUtils.frameKeyPackage(keyPackage)),
            dTagSlot = dTagSlot,
            keyPackageRef = keyPackage.reference().toHexKey(),
            appComponentIds = advertisedAppComponents(keyPackage).map(::idHex),
            ciphersuite = idHex(keyPackage.cipherSuite),
            mlsExtensionIds =
                keyPackage.leafNode.capabilities.extensions
                    .map(::idHex),
            mlsProposalIds =
                keyPackage.leafNode.capabilities.proposals
                    .map(::idHex),
            clientName = clientName,
            createdAt = createdAt,
            initializer = initializer,
        )

        /** `0x`-prefixed lowercase hex of a 16-bit id, zero-padded to four digits. */
        fun idHex(id: Int): String {
            val hex = id.toString(16)
            return "0x" + "0".repeat(4 - hex.length) + hex
        }

        /** Marmot app-component ids the leaf advertises, from its `app_components` component. */
        private fun advertisedAppComponents(keyPackage: MlsKeyPackage): List<Int> =
            try {
                val dictionary = AppDataDictionary.fromExtensionsOrEmpty(keyPackage.leafNode.extensions)
                val list = dictionary[ComponentsList.APP_COMPONENTS_ID] ?: return emptyList()
                ComponentsList.decode(list).filter { it >= MARMOT_COMPONENT_RANGE_START }
            } catch (_: Exception) {
                emptyList()
            }

        /** Marmot's own component registry starts here; lower ids are upstream MLS-extensions ones. */
        private const val MARMOT_COMPONENT_RANGE_START = 0x8000

        /** MIP-era builder, kept for legacy groups already on disk. */
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
