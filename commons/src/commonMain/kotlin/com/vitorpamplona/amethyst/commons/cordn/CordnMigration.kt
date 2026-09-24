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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnCarriedKeyPackage
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnDeviceTip
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnDocumentException
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnDocumentSeal
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnGroupDocument
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnHandoffCode
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnLastResortKeyPackage
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnMetaDocument
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnTipEntry
import com.vitorpamplona.quartz.cordn.appMultiDevice.CordnTipInventory
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchFirst
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Moving one account's cordn state from the phone it is on to a new one.
 *
 * ## Why this is safe when continuous sync is not
 *
 * `multi-device.md` §10 leaves one case unresolved: two devices of one
 * identity committing inside a single delivery round-trip both reach epoch N+1
 * with different states, and §15 concedes that *equal-epoch MLS states have no
 * merge function*. That race needs two live writers. A handoff has one — the
 * old phone writes a snapshot and then stops (see [CordnMigrationSnapshot] and
 * the handoff lock) — so the race is out of reach by construction rather than
 * by mitigation.
 *
 * Everything here is therefore §9 seeding plus the §6/§7 transport. There are
 * no `prev` chains (§8.5), no sibling-Commit convergence (§10) and no
 * publish-on-every-Commit (§10.5): each of those exists to keep two *live*
 * devices in step.
 *
 * ## The honest cost
 *
 * The sealed documents leave the device. They are NIP-44 v2 ciphertext under a
 * DEK reachable only through a seal to the owner's own `npub`, so a storage
 * server learns nothing but size and timing — but the group state, including
 * leaf private keys, is on someone else's disk until the blobs are deleted.
 * That is the trade the tip transport makes, and a UI should say so rather
 * than let a user discover it.
 */
class CordnMigration(
    private val client: INostrClient,
    private val signer: NostrSigner,
    private val blobs: CordnBlobStore,
) {
    /**
     * Seals, uploads and advertises [snapshot]; returns the code to scan.
     *
     * The ephemeral keypair and the `d` value are both minted here and both
     * random. §6 forbids deriving the signing key from the owner identity: a
     * public derivation would let anyone compute the tip's author from an
     * `npub` and go looking for it, which is the linkage the opaque tip exists
     * to prevent.
     *
     * @param relays where to publish the tip.
     * @throws CordnMigrationException when nothing could be stored or published.
     */
    suspend fun publish(
        snapshot: CordnMigrationSnapshot,
        relays: Set<NormalizedRelayUrl>,
    ): CordnHandoffCode {
        require(relays.isNotEmpty()) { "a migration needs at least one relay to publish the tip on" }

        val dek = CordnDocumentSeal.newKey()
        val issuedAt = TimeUtils.now() * MILLIS_PER_SECOND

        val hosts = mutableSetOf<String>()

        val groupEntries =
            snapshot.groups.map { group ->
                val document =
                    CordnGroupDocument(
                        gid = group.gid,
                        coordinator = group.coordinatorPubKey,
                        clientState = group.clientStateBase64,
                        cursor = group.cursor,
                        issuedAt = issuedAt,
                        roomState = group.roomStateBase64,
                        echoState = group.echoStateBase64,
                        joinedViaRequest = group.joinedViaRequest,
                        messages = group.messages.takeIf { it.isNotEmpty() },
                        coordinatorRelays = group.coordinatorRelays,
                    )
                val blob = CordnDocumentSeal.seal(document, dek)
                val servers = blobs.put(blob)
                if (servers.isEmpty()) {
                    throw CordnMigrationException("no server accepted the document for group ${group.gid}")
                }
                hosts += servers
                CordnTipEntry(address = CordnDocumentSeal.address(blob), gid = group.gid)
            }

        val metaBlob =
            CordnDocumentSeal.seal(
                CordnMetaDocument(
                    lastResortKeyPackage = snapshot.lastResortKeyPackage,
                    keyPackages = snapshot.keyPackages,
                    issuedAt = issuedAt,
                ),
                dek,
            )
        val metaServers = blobs.put(metaBlob)
        if (metaServers.isEmpty()) throw CordnMigrationException("no server accepted the meta document")
        hosts += metaServers

        val inventory =
            CordnTipInventory(
                groups = groupEntries,
                meta = CordnDocumentSeal.address(metaBlob),
                dekPrivateKey = dek.privKey!!.toHexString(),
                servers = hosts.toList(),
            )

        val ephemeral = KeyPair()
        val dTag = RandomInstance.randomChars(D_TAG_LENGTH)

        val inner = signer.signInner(inventory)
        val sealedInner = signer.nip44Encrypt(JacksonMapper.toJson(inner), signer.pubKey)

        val outer =
            NostrSignerInternal(ephemeral).sign<Event>(
                createdAt = TimeUtils.now(),
                kind = CordnDeviceTip.OUTER_KIND,
                tags = arrayOf(arrayOf(CordnDeviceTip.TAG_D, dTag)),
                content = sealedInner,
            )

        if (!client.publishAndConfirm(outer, relays)) {
            throw CordnMigrationException("no relay accepted the tip")
        }

        return CordnHandoffCode(
            ephemeralPubKey = ephemeral.pubKey.toHexString(),
            dTag = dTag,
            relays = relays.map { it.url },
        )
    }

    /**
     * Reads back what [code] points at.
     *
     * The outer event is signed by a key anyone holding the code could hold, so
     * it establishes nothing. Confidentiality and authorship both come from the
     * seal: the content is NIP-44 to the owner's own pubkey, and only the owner
     * can compute that conversation key — so an outsider can neither read the
     * tip nor write one the owner will read.
     *
     * The inner signature check on top of that is defence in depth against a
     * writer-side bug rather than an outsider: adopting MLS state whose
     * credential names another account would leave the new phone making Commits
     * the rest of every group rejects. Each blob's address is checked too, and
     * before decryption, because that one IS an outsider's opening — the
     * storage server chooses the bytes.
     */
    suspend fun fetch(code: CordnHandoffCode): CordnMigrationSnapshot {
        val relays = code.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
        if (relays.isEmpty()) throw CordnMigrationException("the handoff code names no usable relay")

        val outer =
            client.fetchFirst(
                filters =
                    relays.associateWith {
                        listOf(
                            Filter(
                                kinds = listOf(code.kind),
                                authors = listOf(code.ephemeralPubKey),
                                tags = mapOf(CordnDeviceTip.TAG_D to listOf(code.dTag)),
                                limit = 1,
                            ),
                        )
                    },
            ) ?: throw CordnMigrationException("no relay had a tip for this code")

        val innerJson =
            try {
                signer.nip44Decrypt(outer.content, signer.pubKey)
            } catch (e: Exception) {
                throw CordnMigrationException("the tip did not decrypt — is this the same account?")
            }

        val inner =
            try {
                JacksonMapper.fromJson(innerJson)
            } catch (e: Exception) {
                throw CordnMigrationException("the tip's inner event is not readable: ${e.message}")
            }

        // The authenticity root. Without it, anyone holding the code could
        // repoint the tip at an inventory of their own choosing.
        if (inner.pubKey != signer.pubKey) {
            throw CordnMigrationException("the tip was signed by a different account")
        }
        if (!inner.verify()) {
            throw CordnMigrationException("the tip's inner signature does not verify")
        }

        val inventory = CordnDeviceTip.parse(inner)
        val dek = KeyPair(privKey = inventory.dekPrivateKey.hexToByteArray())

        val groups =
            inventory.groups.map { entry ->
                val document = fetchDocument(entry.address, inventory.servers, dek)
                if (document !is CordnGroupDocument) {
                    throw CordnMigrationException("the document for ${entry.gid} is not a group document")
                }
                if (!document.isReadableHere) {
                    throw CordnMigrationException(
                        "group ${entry.gid} was written by a different MLS engine " +
                            "(${document.clientStateFormat ?: "unmarked"}) and cannot be read here",
                    )
                }
                CordnMigrationGroup(
                    coordinatorPubKey = document.coordinator,
                    coordinatorRelays = document.coordinatorRelays,
                    gid = document.gid,
                    clientStateBase64 = document.clientState,
                    cursor = document.cursor,
                    roomStateBase64 = document.roomState,
                    echoStateBase64 = document.echoState,
                    joinedViaRequest = document.joinedViaRequest,
                    messages = document.messages.orEmpty(),
                )
            }

        val meta =
            inventory.meta?.let {
                fetchDocument(it, inventory.servers, dek) as? CordnMetaDocument
                    ?: throw CordnMigrationException("the meta address does not hold a meta document")
            }

        return CordnMigrationSnapshot(
            accountPubKey = signer.pubKey,
            groups = groups,
            lastResortKeyPackage = meta?.lastResortKeyPackage,
            keyPackages = meta?.keyPackages.orEmpty(),
        )
    }

    private suspend fun fetchDocument(
        address: String,
        servers: List<String>,
        dek: KeyPair,
    ) = run {
        val blob =
            blobs.get(address, servers)
                ?: throw CordnMigrationException("no server served the document at $address")

        // §6: check the address BEFORE decrypting, so a store serving the
        // wrong bytes never gets its plaintext in front of the parser.
        if (!CordnDocumentSeal.verifyAddress(blob, address)) {
            throw CordnMigrationException("the blob at $address does not hash to its address")
        }

        try {
            CordnDocumentSeal.open(blob, dek)
        } catch (e: CordnDocumentException) {
            throw CordnMigrationException("the document at $address is unreadable: ${e.message}")
        }
    }

    private suspend fun NostrSigner.signInner(inventory: CordnTipInventory): Event =
        sign(
            createdAt = TimeUtils.now(),
            kind = CordnDeviceTip.INNER_KIND,
            tags = CordnDeviceTip.tags(inventory),
            content = "",
        )

    companion object {
        private const val D_TAG_LENGTH = 16
        private const val MILLIS_PER_SECOND = 1000L
    }
}

/** A migration that could not be completed, with the step that stopped it. */
class CordnMigrationException(
    message: String,
) : Exception(message)

/**
 * Everything a replacement device needs, read from disk rather than memory.
 *
 * Reading from the stores is deliberate and copied from `exportArchive`: a
 * coordinator whose session failed to open today is still migrated, because a
 * handoff that silently omitted the groups the app could not reach would be
 * wrong precisely when it matters.
 */
data class CordnMigrationSnapshot(
    val accountPubKey: HexKey,
    val groups: List<CordnMigrationGroup>,
    val lastResortKeyPackage: CordnLastResortKeyPackage? = null,
    val keyPackages: List<CordnCarriedKeyPackage> = emptyList(),
)

/** One group in a migration. All blobs are base64 of their on-disk encoding. */
data class CordnMigrationGroup(
    val coordinatorPubKey: HexKey,
    val coordinatorRelays: List<String>,
    val gid: String,
    val clientStateBase64: String,
    val cursor: Long,
    val roomStateBase64: String? = null,
    val echoStateBase64: String? = null,
    val joinedViaRequest: Boolean = false,
    /** The conversation, as `CordnDeliveredMessageCodec` entries, oldest first. */
    val messages: List<String> = emptyList(),
)
