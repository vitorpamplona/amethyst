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
package com.vitorpamplona.amethyst.commons.marmot

import com.vitorpamplona.quartz.marmot.mip05PushNotifications.InMemoryPushStateStore
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.MarmotPushStateStore
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.NotificationRequestEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushBase64
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushGossip
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushOwnerProof
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushPlatform
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushRecordKind
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushRecordStore
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushRemovalEntry
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushSignedRecord
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushStateCodec
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.PushTokenEntry
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenEncryption
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenListEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRemovalEvent
import com.vitorpamplona.quartz.marmot.mip05PushNotifications.TokenRequestEvent
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip59Giftwrap.rumors.RumorAssembler
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Push token gossip for the groups this client is in
 * (`features/push-notifications.md`).
 *
 * ## What it owns, and what it deliberately does not
 *
 * It produces and consumes kinds `447`/`448`/`449` and assembles the kind `446`
 * trigger rumor. It does NOT publish anything: the trigger's NIP-59 seal, its
 * recipient addressing and its publish targets belong to the Nostr binding, and
 * the gossip events are ordinary group messages the caller sends like any
 * other.
 *
 * It also does not decide whether push is enabled. A device token and a
 * notification server public key both come from the application — a server can
 * only wake the app whose platform credentials it holds, so there is no
 * protocol-level discovery to do here.
 *
 * ## Nothing here can affect a group
 *
 * Every failure in this file is advisory. A malformed entry, an unverifiable
 * signature, a removal matching nothing, a stale list — all drop the datum and
 * continue. None of it may reject a group message, mutate MLS state, or change
 * which commit wins, and the code is shaped so it cannot: the coordinator never
 * throws at its callers on bad input, it returns "nothing changed".
 */
class MarmotPushCoordinator(
    private val manager: MarmotManager,
    /**
     * Durable per-group state. The default forgets tombstones on restart,
     * which lets a relayed but revoked record win exactly once — acceptable
     * for a CLI, not for a phone.
     */
    private val stateStore: MarmotPushStateStore = InMemoryPushStateStore(),
) {
    private val mutex = Mutex()
    private val stores = mutableMapOf<HexKey, PushRecordStore>()

    /** The active records this client believes in for a group. */
    suspend fun activeRecords(nostrGroupId: HexKey): List<PushTokenEntry> = mutex.withLock { storeFor(nostrGroupId)?.active().orEmpty() }

    // ------------------------------------------------------------- producing

    /**
     * Encrypt this device's token to [serverPubKeyHex], sign the owner proof,
     * and build the kind `447` self-update that announces it.
     *
     * The record is applied locally first so a later kind `448` of ours carries
     * it, and so a stale relay of an older record of ours loses on arrival.
     *
     * @return the inner event to send into the group, or null when this client
     *   is not a member of the group or holds no leaf in it.
     */
    suspend fun buildSelfUpdate(
        nostrGroupId: HexKey,
        platform: PushPlatform,
        deviceToken: ByteArray,
        serverPubKeyHex: HexKey,
        relayHint: String = "",
        ownerTsMillis: Long = TimeUtils.nowMillis(),
    ): Event? {
        val entry =
            signOwnRecord(nostrGroupId, platform, deviceToken, serverPubKeyHex, relayHint, ownerTsMillis)
                ?: return null

        mutex.withLock {
            val store = storeFor(nostrGroupId) ?: return null
            store.applyTokens(listOf(entry), TimeUtils.nowMillis(), memberCheck(nostrGroupId))
            persist(nostrGroupId, store)
        }

        return rumor(TokenRequestEvent.build(listOf(entry)))
    }

    /** The empty kind `447`: "share the records you hold with me." */
    fun buildTokenRequest(): Event = rumor(TokenRequestEvent.buildRequest())

    /**
     * Build the kind `448` answer to a request: every active record we hold,
     * including other members' records, with their signatures untouched.
     *
     * Null when we hold nothing to say — an empty list response is noise.
     * Records beyond the 32-entry cap are dropped rather than split, because a
     * responder is a convenience path and the owners will re-announce.
     */
    suspend fun buildTokenList(nostrGroupId: HexKey): Event? {
        val records = mutex.withLock { storeFor(nostrGroupId)?.active().orEmpty() }
        if (records.isEmpty()) return null
        return rumor(TokenListEvent.build(records.take(PushGossip.MAX_ENTRIES)))
    }

    /**
     * Sign and build the kind `449` that revokes this device's record on
     * [serverPubKeyHex].
     *
     * [deviceToken] is needed even though the token is not in the removal: the
     * fingerprint is, and it is what states which token instance the owner
     * meant to revoke.
     */
    suspend fun buildRemoval(
        nostrGroupId: HexKey,
        platform: PushPlatform,
        deviceToken: ByteArray,
        serverPubKeyHex: HexKey,
        ownerTsMillis: Long = TimeUtils.nowMillis(),
    ): Event? {
        val groupIdHex = manager.mlsGroupIdHex(nostrGroupId) ?: return null
        val leafIndex = manager.leafIndexOf(nostrGroupId, manager.signer.pubKey) ?: return null
        val fingerprint = PushSignedRecord.fingerprintOf(platform, deviceToken)

        val ownerSig =
            proof(PushRecordKind.REMOVAL, groupIdHex, leafIndex, platform, serverPubKeyHex, fingerprint, ownerTsMillis)
                ?: return null

        val entry =
            PushRemovalEntry(
                memberIdHex = manager.signer.pubKey,
                leafIndex = leafIndex,
                platform = platform,
                tokenFingerprint = fingerprint,
                serverPubKeyHex = serverPubKeyHex,
                ownerTsMillis = ownerTsMillis,
                ownerSig = ownerSig,
            )

        mutex.withLock {
            val store = storeFor(nostrGroupId) ?: return null
            store.applyRemovals(listOf(entry), TimeUtils.nowMillis(), memberCheck(nostrGroupId))
            persist(nostrGroupId, store)
        }

        return rumor(TokenRemovalEvent.build(listOf(entry)))
    }

    /**
     * The kind `446` trigger rumor for a group's active records, or null when
     * there is nothing to wake.
     *
     * [padding] chunks of uniform random bytes are appended to obscure the real
     * recipient count from anyone watching the gift wrap's length. They are
     * indistinguishable from tokens to an observer and merely fail to decrypt
     * at the server — which is why a real token must never be used as padding:
     * it would fire a wake with no content behind it.
     *
     * The caller seals and wraps this to the notification server; nothing here
     * publishes.
     */
    suspend fun buildTrigger(
        nostrGroupId: HexKey,
        serverPubKeyHex: HexKey,
        padding: Int = 0,
    ): Event? {
        val chunks =
            mutex
                .withLock { storeFor(nostrGroupId)?.active().orEmpty() }
                .filter { it.serverPubKeyHex == serverPubKeyHex }
                .map { it.encryptedToken }
        if (chunks.isEmpty()) return null

        val padded =
            (chunks + List(padding) { RandomInstance.bytes(PushSignedRecord.ENCRYPTED_TOKEN_BYTES) })
                .take(NotificationRequestEvent.MAX_CHUNKS)
                .shuffled()

        // A fresh ephemeral key per trigger, so the server cannot link two
        // triggers to one sender — and cannot dedup on the outer event id
        // either, which is why the spec keys dedup on the content hash.
        val ephemeral = RandomInstance.bytes(32)
        val ephemeralPubKey = Nip01Crypto.pubKeyCreate(ephemeral).toHexKey()
        return RumorAssembler.assembleRumor(ephemeralPubKey, NotificationRequestEvent.build(padded))
    }

    // ------------------------------------------------------------- consuming

    /**
     * Feed one decrypted inner app event to the push state.
     *
     * Returns true when a stored record changed, so a caller can decide whether
     * to answer a request or re-persist. A non-push kind, an unreadable
     * payload, and an entry that lost its ordering race all return false and
     * are indistinguishable on purpose — none of them is an error.
     */
    suspend fun apply(
        nostrGroupId: HexKey,
        innerEvent: Event,
    ): Boolean =
        try {
            when (innerEvent.kind) {
                TokenRequestEvent.KIND, TokenListEvent.KIND ->
                    applyChange(nostrGroupId) { store, now, isMember ->
                        store.applyTokens(PushGossip.decodeTokens(innerEvent.content), now, isMember)
                    }

                TokenRemovalEvent.KIND ->
                    applyChange(nostrGroupId) { store, now, isMember ->
                        store.applyRemovals(PushGossip.decodeRemovals(innerEvent.content), now, isMember)
                    }

                else -> false
            }
        } catch (e: Exception) {
            // Push is advisory end to end: a surprise here must never reach the
            // ingest path that decides whether the carrying group message was
            // valid.
            Log.w("MarmotPushCoordinator", "dropping unreadable push payload in $nostrGroupId", e)
            false
        }

    /** True when [innerEvent] is a kind `447` asking others to share their records. */
    fun isTokenRequest(innerEvent: Event): Boolean = innerEvent.kind == TokenRequestEvent.KIND && PushGossip.decodeTokens(innerEvent.content).isEmpty()

    /**
     * Forget a leaf an accepted Commit removed — record, stamp and tombstone.
     *
     * Nothing that leaf signed can be applied again, so the durable high-water
     * mark has no work left to do. A sibling leaf of the same account keeps
     * its own records: different key, still a member.
     */
    suspend fun forgetLeaf(
        nostrGroupId: HexKey,
        memberIdHex: HexKey,
        leafIndex: Int,
    ) {
        mutex.withLock {
            val store = storeFor(nostrGroupId) ?: return
            store.forgetLeaf(memberIdHex, leafIndex)
            persist(nostrGroupId, store)
        }
    }

    suspend fun forgetGroup(nostrGroupId: HexKey) {
        mutex.withLock {
            stores.remove(nostrGroupId)
            stateStore.clear(nostrGroupId)
        }
    }

    // ------------------------------------------------------------- internals

    private suspend fun applyChange(
        nostrGroupId: HexKey,
        change: (PushRecordStore, Long, (HexKey) -> Boolean) -> Set<*>,
    ): Boolean =
        mutex.withLock {
            val store = storeFor(nostrGroupId) ?: return false
            val changed = change(store, TimeUtils.nowMillis(), memberCheck(nostrGroupId))
            if (changed.isNotEmpty()) persist(nostrGroupId, store)
            changed.isNotEmpty()
        }

    /**
     * Membership is read from the MLS tree, never from the carrying event's
     * sender: a verified entry applies whoever relayed it, and an entry naming
     * a non-member is dropped however it arrived.
     */
    private fun memberCheck(nostrGroupId: HexKey): (HexKey) -> Boolean {
        val members = manager.memberPubkeys(nostrGroupId).map { it.pubkey }.toSet()
        return { it in members }
    }

    private suspend fun storeFor(nostrGroupId: HexKey): PushRecordStore? {
        stores[nostrGroupId]?.let { return it }
        val groupIdHex = manager.mlsGroupIdHex(nostrGroupId) ?: return null
        val store =
            PushRecordStore(
                groupIdHex = groupIdHex,
                // From the GroupContext, never from anything a sender claims:
                // it decides which owner-proof forms are acceptable at all.
                currentProfileGroup = manager.groupState(nostrGroupId)?.isCurrentProfile == true,
            )
        stateStore.load(nostrGroupId)?.let { PushStateCodec.decodeInto(store, it) }
        stores[nostrGroupId] = store
        return store
    }

    private suspend fun persist(
        nostrGroupId: HexKey,
        store: PushRecordStore,
    ) {
        try {
            stateStore.save(nostrGroupId, PushStateCodec.encode(store))
        } catch (e: Exception) {
            Log.w("MarmotPushCoordinator", "could not persist push state for $nostrGroupId", e)
        }
    }

    private suspend fun signOwnRecord(
        nostrGroupId: HexKey,
        platform: PushPlatform,
        deviceToken: ByteArray,
        serverPubKeyHex: HexKey,
        relayHint: String,
        ownerTsMillis: Long,
    ): PushTokenEntry? {
        val groupIdHex = manager.mlsGroupIdHex(nostrGroupId) ?: return null
        val leafIndex = manager.leafIndexOf(nostrGroupId, manager.signer.pubKey) ?: return null
        val fingerprint = PushSignedRecord.fingerprintOf(platform, deviceToken)
        val encryptedTokenBase64 =
            try {
                TokenEncryption.encrypt(platform, deviceToken, serverPubKeyHex.hexToByteArray())
            } catch (e: Exception) {
                Log.w("MarmotPushCoordinator", "could not encrypt the device token", e)
                return null
            }
        val hint = PushSignedRecord.normalizeRelayHint(relayHint)

        val ownerSig =
            proof(
                record = PushRecordKind.TOKEN,
                groupIdHex = groupIdHex,
                leafIndex = leafIndex,
                platform = platform,
                serverPubKeyHex = serverPubKeyHex,
                fingerprint = fingerprint,
                ownerTsMillis = ownerTsMillis,
                relayHint = hint,
                encryptedTokenBase64 = encryptedTokenBase64,
            ) ?: return null

        return PushTokenEntry(
            memberIdHex = manager.signer.pubKey,
            leafIndex = leafIndex,
            platform = platform,
            tokenFingerprint = fingerprint,
            serverPubKeyHex = serverPubKeyHex,
            relayHint = hint,
            encryptedToken = requireNotNull(PushBase64.decodeOrNull(encryptedTokenBase64)),
            ownerTsMillis = ownerTsMillis,
            ownerSig = ownerSig,
        )
    }

    /**
     * Ask the account signer for the unpublished kind `451` proof.
     *
     * [PushOwnerProof.create] re-validates whatever the signer returns before
     * copying the signature out, which matters for an external signer: a
     * substituted group id or server pubkey would otherwise become a proof that
     * silently authorizes the wrong destination.
     */
    private suspend fun proof(
        record: PushRecordKind,
        groupIdHex: HexKey,
        leafIndex: Int,
        platform: PushPlatform,
        serverPubKeyHex: HexKey,
        fingerprint: String,
        ownerTsMillis: Long,
        relayHint: String = "",
        encryptedTokenBase64: String = "",
    ): ByteArray? =
        try {
            PushOwnerProof.create(
                signer = manager.signer,
                record = record,
                groupIdHex = groupIdHex,
                leafIndex = leafIndex,
                platform = platform.wireName,
                serverPubKeyHex = serverPubKeyHex,
                tokenFingerprint = fingerprint,
                ownerTsMillis = ownerTsMillis,
                relayHint = relayHint,
                encryptedTokenBase64 = encryptedTokenBase64,
            )
        } catch (e: Exception) {
            Log.w("MarmotPushCoordinator", "the signer did not produce a usable push owner proof", e)
            null
        }

    /**
     * The unsigned inner rumor a caller sends like any other group message.
     *
     * The coordinator deliberately stops here rather than building the kind:445
     * itself: encrypting one advances the group's ratchet, and a message the
     * caller then decides not to publish would burn a generation for nothing.
     */
    private fun rumor(template: EventTemplate<out Event>): Event {
        @Suppress("UNCHECKED_CAST")
        return RumorAssembler.assembleRumor(manager.signer.pubKey, template as EventTemplate<Event>)
    }
}
