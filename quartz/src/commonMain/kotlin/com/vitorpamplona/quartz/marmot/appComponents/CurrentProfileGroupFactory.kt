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
package com.vitorpamplona.quartz.marmot.appComponents

import com.vitorpamplona.quartz.marmot.appComponents.accountIdentityProof.AccountIdentityProofV2
import com.vitorpamplona.quartz.marmot.mip01Groups.MlsCiphersuite
import com.vitorpamplona.quartz.marmot.mls.components.AppDataDictionary
import com.vitorpamplona.quartz.marmot.mls.components.ComponentData
import com.vitorpamplona.quartz.marmot.mls.components.ComponentsList
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
import com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519KeyPair
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.messages.KeyPackageBundle
import com.vitorpamplona.quartz.marmot.mls.tree.Extension
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner

/**
 * Builds current-profile Marmot leaves, KeyPackages and groups.
 *
 * ## Why this exists as a factory
 *
 * A current-profile leaf must carry `marmot.member.account-identity-proof.v2`
 * over its OWN MLS signature key, and only the account key can produce that
 * proof — through a signer that may be a remote bunker or an external app. MLS
 * leaf construction is synchronous and normally generates its signature keypair
 * internally, so the proof cannot be added afterwards by code that only sees a
 * finished leaf.
 *
 * The order is therefore fixed: generate the leaf keypair, ask the account
 * signer to authorize its public half, then build the leaf around both. Every
 * function here follows it.
 */
object CurrentProfileGroupFactory {
    /**
     * The component ids this client supports, advertised in every leaf.
     *
     * `0x0001` is in the list because a client advertising `app_data_dictionary`
     * must understand and advertise `app_components` itself.
     */
    val SUPPORTED_COMPONENTS: List<Int> =
        listOf(
            ComponentsList.APP_COMPONENTS_ID,
            AppComponentIds.GROUP_PROFILE_V1,
            AppComponentIds.GROUP_BLOSSOM_IMAGE_V1,
            AppComponentIds.ADMIN_POLICY_V1,
            AppComponentIds.NOSTR_ROUTING_V1,
            AppComponentIds.MESSAGE_RETENTION_V1,
            AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2,
            AppComponentIds.GROUP_LIFECYCLE_V1,
        )

    /** A leaf keypair plus the account proof authorizing it. */
    class LeafIdentity(
        val signatureKeyPair: Ed25519KeyPair,
        val leafExtensions: List<Extension>,
    )

    /**
     * Generate a leaf signature keypair and have [signer] authorize it.
     *
     * The returned leaf dictionary carries the supported-component list, an
     * explicit EMPTY `safe_aad` list (understood, nothing contributed — required
     * of anyone advertising `app_data_dictionary`), and the 104-byte proof.
     */
    suspend fun newLeafIdentity(
        signer: NostrSigner,
        ciphersuite: MlsCiphersuite = MlsCiphersuite.DEFAULT,
        supportedComponents: List<Int> = SUPPORTED_COMPONENTS,
    ): LeafIdentity {
        val keyPair = Ed25519.generateKeyPair()
        val proof = AccountIdentityProofV2.create(signer, ciphersuite, keyPair.publicKey)

        val dictionary =
            AppDataDictionary(
                listOf(
                    ComponentData(ComponentsList.APP_COMPONENTS_ID, ComponentsList.encode(supportedComponents)),
                    ComponentData(ComponentsList.SAFE_AAD_ID, ComponentsList.encode(emptyList())),
                    ComponentData(AppComponentIds.ACCOUNT_IDENTITY_PROOF_V2, proof.encode()),
                ),
            )
        return LeafIdentity(keyPair, listOf(dictionary.toExtension()))
    }

    /**
     * Publish-ready current-profile KeyPackage for [signer]'s account.
     *
     * [lastResort] adds the empty-data `last_resort_key_package` component to
     * the KEYPACKAGE-level dictionary — a separate dictionary from the embedded
     * LeafNode's, and a component rather than the extension type the MIP-era
     * profile used.
     */
    suspend fun createKeyPackage(
        signer: NostrSigner,
        lastResort: Boolean = true,
        ciphersuite: MlsCiphersuite = MlsCiphersuite.DEFAULT,
    ): KeyPackageBundle {
        val identity = signer.pubKey.hexToByteArray()
        val leaf = newLeafIdentity(signer, ciphersuite)

        val keyPackageExtensions =
            if (lastResort) {
                listOf(
                    AppDataDictionary(
                        listOf(ComponentData(AppComponentIds.LAST_RESORT_KEY_PACKAGE, ByteArray(0))),
                    ).toExtension(),
                )
            } else {
                emptyList()
            }

        // A throwaway group only as a factory for the bundle; nothing about it
        // survives the call.
        return MlsGroup
            .create(identity)
            .createKeyPackage(
                identity = identity,
                signingKey = leaf.signatureKeyPair.privateKey,
                leafSignatureKeyPair = leaf.signatureKeyPair,
                leafExtensions = leaf.leafExtensions,
                capabilities = MlsGroup.currentProfileLeafCapabilities(),
                keyPackageExtensions = keyPackageExtensions,
            )
    }

    /**
     * Create a current-profile group with [signer]'s account as its sole
     * initial admin.
     *
     * Epoch 0 carries `required_capabilities` of extension `0x0006` and
     * proposal `0x0008`, and a GroupContext dictionary with the required
     * component list plus admin policy, routing, and the rest of the initial
     * state. The creator's leaf carries its own dictionary with the identity
     * proof.
     */
    suspend fun createGroup(
        signer: NostrSigner,
        nostrGroupId: ByteArray,
        relays: List<String>,
        profile: GroupProfileV1? = null,
        additionalAdmins: List<ByteArray> = emptyList(),
        retention: MessageRetentionV1? = null,
        ciphersuite: MlsCiphersuite = MlsCiphersuite.DEFAULT,
    ): MlsGroup {
        val identity = signer.pubKey.hexToByteArray()
        val leaf = newLeafIdentity(signer, ciphersuite)

        val dictionary =
            MarmotGroupState.buildDictionary(
                adminPolicy = AdminPolicyV1.of(listOf(identity) + additionalAdmins),
                routing = NostrRoutingV1.of(nostrGroupId, relays),
                profile = profile,
                retention = retention,
                lifecycle = GroupLifecycleV1.ACTIVE,
            )

        return MlsGroup.create(
            identity = identity,
            signingKey = leaf.signatureKeyPair.privateKey,
            initialExtensions = listOf(dictionary.toExtension()),
            leafExtensions = leaf.leafExtensions,
            capabilities = MlsGroup.currentProfileLeafCapabilities(),
            requiredCapabilities = MlsGroup.buildCurrentProfileRequiredCapabilitiesExtension(),
        )
    }
}
