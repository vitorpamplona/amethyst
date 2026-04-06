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
package com.vitorpamplona.amethyst.cli.engine

import com.vitorpamplona.quartz.marmot.MarmotSubscriptionManager
import com.vitorpamplona.quartz.marmot.mip00KeyPackages.KeyPackageEvent
import com.vitorpamplona.quartz.marmot.mip01Groups.MarmotGroupData
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEvent
import com.vitorpamplona.quartz.marmot.mip03GroupMessages.GroupEventEncryption
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroup
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64

class MarmotEngine(
    val account: AccountInfo,
    val signer: NostrSignerInternal,
    dataDir: File,
) {
    val stateStore = FileStateStore(File(dataDir, "accounts/${account.pubKeyHex}"))
    val groupManager = MlsGroupManager(stateStore)
    val subscriptionManager = MarmotSubscriptionManager(account.pubKeyHex)
    val relayPool = CliRelayPool()

    fun init(connectRelays: Boolean = false) {
        runBlocking { groupManager.restoreAll() }
        if (connectRelays) {
            relayPool.connect(account.relays)
        }
    }

    fun connectRelays() {
        relayPool.connect(account.relays)
    }

    fun shutdown() {
        relayPool.close()
    }

    fun createGroup(
        name: String,
        description: String,
        relays: List<String>,
    ): GroupInfo {
        val identity = signer.keyPair.pubKey
        val nostrGroupId = Nip01Crypto.privKeyCreate().toHexKey()

        val group =
            runBlocking {
                groupManager.createGroup(
                    nostrGroupId = nostrGroupId,
                    identity = identity,
                )
            }

        return GroupInfo(
            nostrGroupId = nostrGroupId,
            name = name,
            description = description,
            adminPubkeys = listOf(account.pubKeyHex),
            relays = relays.ifEmpty { account.relays },
            epoch = group.epoch,
            memberCount = group.memberCount,
        )
    }

    fun listGroups(): List<GroupInfo> {
        val groupIds = runBlocking { stateStore.listGroups() }
        return groupIds.mapNotNull { nostrGroupId ->
            val group = groupManager.getGroup(nostrGroupId) ?: return@mapNotNull null
            val marmotData = MarmotGroupData.fromExtensions(group.extensions)
            GroupInfo(
                nostrGroupId = nostrGroupId,
                name = marmotData?.name ?: "Unknown",
                description = marmotData?.description ?: "",
                adminPubkeys = marmotData?.adminPubkeys ?: emptyList(),
                relays = marmotData?.relays ?: emptyList(),
                epoch = group.epoch,
                memberCount = group.memberCount,
            )
        }
    }

    fun showGroup(nostrGroupId: HexKey): GroupInfo? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        val marmotData = MarmotGroupData.fromExtensions(group.extensions)
        return GroupInfo(
            nostrGroupId = nostrGroupId,
            name = marmotData?.name ?: "Unknown",
            description = marmotData?.description ?: "",
            adminPubkeys = marmotData?.adminPubkeys ?: emptyList(),
            relays = marmotData?.relays ?: emptyList(),
            epoch = group.epoch,
            memberCount = group.memberCount,
        )
    }

    fun groupMembers(nostrGroupId: HexKey): List<MemberInfo>? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        return group.members().map { (leafIndex, leafNode) ->
            MemberInfo(
                leafIndex = leafIndex,
                pubkey = leafNode.signatureKey.toHexKey(),
            )
        }
    }

    fun addMember(
        nostrGroupId: HexKey,
        keyPackageBase64: String,
    ): AddMemberResult {
        val commitResult = runBlocking { groupManager.addMember(nostrGroupId, Base64.getDecoder().decode(keyPackageBase64)) }
        return AddMemberResult(
            welcomeBytes = commitResult.welcomeBytes?.let { Base64.getEncoder().encodeToString(it) },
            commitBytes = Base64.getEncoder().encodeToString(commitResult.commitBytes),
        )
    }

    fun removeMember(
        nostrGroupId: HexKey,
        leafIndex: Int,
    ): String {
        val commitResult = runBlocking { groupManager.removeMember(nostrGroupId, leafIndex) }
        return Base64.getEncoder().encodeToString(commitResult.commitBytes)
    }

    fun leaveGroup(nostrGroupId: HexKey): String {
        val proposalBytes = runBlocking { groupManager.leaveGroup(nostrGroupId) }
        return Base64.getEncoder().encodeToString(proposalBytes)
    }

    fun generateKeyPackage(): KeyPackageInfo {
        val identity = signer.keyPair.pubKey

        // Let MlsGroup generate its own Ed25519 keypairs for MLS operations
        // (the Nostr secp256k1 key is used as the credential identity, not for MLS signing)
        val group = MlsGroup.create(identity)
        // createKeyPackage generates fresh Ed25519 keys internally;
        // the signingKey param is for the group's current signing key (unused for the KP itself)
        val tempSigningKey =
            com.vitorpamplona.quartz.marmot.mls.crypto.Ed25519
                .generateKeyPair()
        val bundle = group.createKeyPackage(identity, tempSigningKey.privateKey)
        val bundleBytes = bundle.keyPackage.toTlsBytes()
        val bundleBase64 = Base64.getEncoder().encodeToString(bundleBytes)
        val ref = bundle.keyPackage.reference().toHexKey()

        return KeyPackageInfo(
            base64 = bundleBase64,
            ref = ref,
            ciphersuite = "0x0001",
        )
    }

    fun buildKeyPackageEvent(kpInfo: KeyPackageInfo): Event =
        runBlocking {
            val template =
                KeyPackageEvent.build(
                    keyPackageBase64 = kpInfo.base64,
                    dTagSlot = "0",
                    keyPackageRef = kpInfo.ref,
                    relays = account.relays.map { NormalizedRelayUrl(it) },
                    ciphersuite = kpInfo.ciphersuite,
                    clientName = "amethyst-cli",
                )
            signer.sign(template)
        }

    fun sendMessage(
        nostrGroupId: HexKey,
        content: String,
    ): String? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        val exporterKey = group.exporterSecret(GroupEvent.EXPORTER_LABEL, GroupEvent.EXPORTER_CONTEXT.toByteArray(), GroupEvent.EXPORTER_KEY_LENGTH)

        val innerJson = """{"kind":9,"content":"$content","pubkey":"${account.pubKeyHex}","created_at":${System.currentTimeMillis() / 1000},"tags":[]}"""
        val mlsMessage = group.encrypt(innerJson.toByteArray())
        val encryptedContent = GroupEventEncryption.encrypt(mlsMessage, exporterKey)

        val template =
            GroupEvent.build(
                encryptedContentBase64 = encryptedContent,
                nostrGroupId = nostrGroupId,
            )

        val signed = runBlocking { signer.sign(template) }
        relayPool.send(signed)
        return signed.id
    }

    fun decryptGroupEvent(
        nostrGroupId: HexKey,
        encryptedContent: String,
    ): String? {
        val group = groupManager.getGroup(nostrGroupId) ?: return null
        val exporterKey = group.exporterSecret(GroupEvent.EXPORTER_LABEL, GroupEvent.EXPORTER_CONTEXT.toByteArray(), GroupEvent.EXPORTER_KEY_LENGTH)

        val mlsMessage = GroupEventEncryption.decrypt(encryptedContent, exporterKey)
        val decrypted = group.decrypt(mlsMessage)
        return String(decrypted.content)
    }

    fun publishEvent(event: Event) {
        relayPool.send(event)
    }

    fun subscribeToGroup(
        nostrGroupId: HexKey,
        listener: SubscriptionListener,
    ) {
        subscriptionManager.subscribeGroup(nostrGroupId)
        val filters = subscriptionManager.buildFilters()
        relayPool.subscribe(filters, listener)
    }
}

data class GroupInfo(
    val nostrGroupId: HexKey,
    val name: String,
    val description: String,
    val adminPubkeys: List<HexKey>,
    val relays: List<String>,
    val epoch: Long,
    val memberCount: Int,
)

data class MemberInfo(
    val leafIndex: Int,
    val pubkey: HexKey,
)

data class AddMemberResult(
    val welcomeBytes: String?,
    val commitBytes: String,
)

data class KeyPackageInfo(
    val base64: String,
    val ref: HexKey,
    val ciphersuite: String,
)
