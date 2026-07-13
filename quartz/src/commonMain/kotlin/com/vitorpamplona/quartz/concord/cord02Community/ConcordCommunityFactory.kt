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
package com.vitorpamplona.quartz.concord.cord02Community

import com.vitorpamplona.quartz.concord.cord04Roles.ChannelEntity
import com.vitorpamplona.quartz.concord.cord04Roles.ConcordJson
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEdition
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEditionBuilder
import com.vitorpamplona.quartz.concord.cord04Roles.ControlEntityKind
import com.vitorpamplona.quartz.concord.cord04Roles.MetadataEntity
import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.concord.envelope.ConcordStreamEnvelope
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * A freshly created Concord community: its self-certifying identity and access
 * secrets, plus the genesis Control Plane wraps to publish and the equivalent
 * editions to fold locally.
 */
class NewConcordCommunity(
    val communityId: ByteArray,
    val ownerPubKey: String,
    val ownerSalt: ByteArray,
    val communityRoot: ByteArray,
    val rootEpoch: Long,
    val generalChannelId: ByteArray,
    val controlPlane: GroupKey,
    /** The kind-1059 control-plane wraps to publish (metadata + #general). */
    val genesisWraps: List<Event>,
    /** The same editions as parsed [ControlEdition]s, for immediate local folding. */
    val genesisEditions: List<ControlEdition>,
) {
    val communityIdHex: String get() = communityId.toHexKey()
    val generalChannelIdHex: String get() = generalChannelId.toHexKey()
}

/**
 * Creates new Concord communities (CORD-02 Genesis).
 *
 * `create` mints a random `owner_salt`, derives the self-certifying
 * `community_id = sha256("concord/community" ‖ owner ‖ salt)`, generates an
 * independent random `community_root` (so access can rotate while identity stays
 * fixed), and emits exactly two owner-signed genesis editions — the community
 * metadata and a public `#general` channel — as plaintext-seal wraps on the
 * Control Plane at epoch 0.
 */
object ConcordCommunityFactory {
    const val GENERAL_CHANNEL_NAME = "general"

    suspend fun create(
        ownerSigner: NostrSigner,
        name: String,
        createdAt: Long,
        description: String? = null,
        relays: List<String> = emptyList(),
        icon: ImagePointer? = null,
    ): NewConcordCommunity {
        val ownerXOnly = ownerSigner.pubKey.hexToByteArray()
        val ownerSalt = ConcordKeyDerivation.newOwnerSalt()
        val communityId = ConcordKeyDerivation.communityId(ownerXOnly, ownerSalt)
        val communityRoot = RandomInstance.bytes(32)
        val generalChannelId = RandomInstance.bytes(32)
        val rootEpoch = 0L
        val controlPlane = ConcordKeyDerivation.controlPlaneKey(communityRoot, communityId, rootEpoch)

        val metadataJson =
            ConcordJson.instance.encodeToString(
                MetadataEntity.serializer(),
                MetadataEntity(name = name, icon = icon, description = description, relays = relays),
            )
        val channelJson =
            ConcordJson.instance.encodeToString(
                ChannelEntity.serializer(),
                ChannelEntity(name = GENERAL_CHANNEL_NAME, private = false),
            )

        val metadataRumor =
            ControlEditionBuilder.rumor(
                authorPubKey = ownerSigner.pubKey,
                entityKind = ControlEntityKind.METADATA,
                entityId = communityId, // metadata eid == community id
                version = 0,
                prevHash = null,
                content = metadataJson,
                createdAt = createdAt,
            )
        val channelRumor =
            ControlEditionBuilder.rumor(
                authorPubKey = ownerSigner.pubKey,
                entityKind = ControlEntityKind.CHANNEL,
                entityId = generalChannelId, // channel eid == channel id
                version = 0,
                prevHash = null,
                content = channelJson,
                createdAt = createdAt,
            )

        // Control Plane uses plaintext (20014) seals so signatures survive re-encryption across epochs.
        val metadataWrap = ConcordStreamEnvelope.wrap(metadataRumor, controlPlane, ownerSigner, encrypted = false, createdAt = createdAt)
        val channelWrap = ConcordStreamEnvelope.wrap(channelRumor, controlPlane, ownerSigner, encrypted = false, createdAt = createdAt)

        return NewConcordCommunity(
            communityId = communityId,
            ownerPubKey = ownerSigner.pubKey,
            ownerSalt = ownerSalt,
            communityRoot = communityRoot,
            rootEpoch = rootEpoch,
            generalChannelId = generalChannelId,
            controlPlane = controlPlane,
            genesisWraps = listOf(metadataWrap, channelWrap),
            genesisEditions =
                listOfNotNull(
                    ControlEdition.fromRumor(metadataRumor),
                    ControlEdition.fromRumor(channelRumor),
                ),
        )
    }
}
