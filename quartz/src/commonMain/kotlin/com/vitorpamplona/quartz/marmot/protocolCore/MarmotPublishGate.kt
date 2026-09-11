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
package com.vitorpamplona.quartz.marmot.protocolCore

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupManager
import com.vitorpamplona.quartz.marmot.mls.group.MlsGroupState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.utils.sha256.sha256
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One unresolved publish obligation
 * (`protocol-core/publish-lifecycle.md`, "Publish obligation").
 *
 * The four protocol-relevant parts are the outbound bytes, their recipient
 * scope, the prior canonical state they were generated from, and the pending
 * state they would make canonical. All four are here because all four are
 * needed after a restart: without the bytes a retry would have to generate a
 * REPLACEMENT commit (which peers would see as a second, competing one), and
 * without the pending state a confirmed-but-unapplied obligation could not be
 * completed at all.
 */
class MarmotPublishObligation(
    val groupId: HexKey,
    /** `SHA-256` of the outbound bytes. Stable across restart and retry. */
    val obligationId: HexKey,
    /** The exact bytes to publish. A safe retry republishes THESE, byte for byte. */
    val outboundBytes: ByteArray,
    /** Endpoints an acknowledged accept may come from. */
    val recipientScope: List<String>,
    val priorState: MlsGroupState,
    val pendingState: MlsGroupState,
) {
    fun encodeTls(): ByteArray {
        val writer = TlsWriter()
        writer.putOpaqueVarInt(groupId.encodeToByteArray())
        writer.putOpaqueVarInt(outboundBytes)
        writer.putUint16(recipientScope.size)
        recipientScope.forEach { writer.putOpaqueVarInt(it.encodeToByteArray()) }
        writer.putOpaqueVarInt(priorState.encodeTls())
        writer.putOpaqueVarInt(pendingState.encodeTls())
        return writer.toByteArray()
    }

    companion object {
        fun idFor(outboundBytes: ByteArray): HexKey = sha256(outboundBytes).toHexKey()

        fun decodeTls(bytes: ByteArray): MarmotPublishObligation {
            val reader = TlsReader(bytes)
            val groupId = reader.readOpaqueVarInt().decodeToString()
            val outbound = reader.readOpaqueVarInt()
            val scopeCount = reader.readUint16()
            val scope = (0 until scopeCount).map { reader.readOpaqueVarInt().decodeToString() }
            val prior = MlsGroupState.decodeTls(reader.readOpaqueVarInt())
            val pending = MlsGroupState.decodeTls(reader.readOpaqueVarInt())
            return MarmotPublishObligation(
                groupId = groupId,
                obligationId = idFor(outbound),
                outboundBytes = outbound,
                recipientScope = scope,
                priorState = prior,
                pendingState = pending,
            )
        }
    }
}

/** Durable storage for unresolved publish obligations and outbound gates. */
interface MarmotPublishObligationStore {
    suspend fun save(
        obligationId: HexKey,
        bytes: ByteArray,
    )

    suspend fun delete(obligationId: HexKey)

    suspend fun loadAll(): List<ByteArray>

    /**
     * Persist an outbound gate for one group.
     *
     * Gates are durable by definition — `Disbanding` "survives publication
     * failure, restart, and a losing branch", and `Leaving` and `Removed` are
     * one-way until the protocol event that clears them. An implementation
     * that does not override these keeps them in memory only, which loses the
     * request on restart; that is the pre-existing behaviour, not a new one,
     * so it defaults rather than breaking every store.
     */
    suspend fun saveGate(
        groupId: HexKey,
        gate: String,
    ) {
    }

    suspend fun deleteGate(groupId: HexKey) {
    }

    /** Group id to gate name, as written by [saveGate]. */
    suspend fun loadGates(): Map<HexKey, String> = emptyMap()
}

/** Non-durable default. A client that uses this loses publish-before-apply across restart. */
class InMemoryPublishObligationStore : MarmotPublishObligationStore {
    private val entries = LinkedHashMap<HexKey, ByteArray>()
    private val gateEntries = LinkedHashMap<HexKey, String>()

    override suspend fun save(
        obligationId: HexKey,
        bytes: ByteArray,
    ) {
        entries[obligationId] = bytes
    }

    override suspend fun delete(obligationId: HexKey) {
        entries.remove(obligationId)
    }

    override suspend fun loadAll(): List<ByteArray> = entries.values.toList()

    override suspend fun saveGate(
        groupId: HexKey,
        gate: String,
    ) {
        gateEntries[groupId] = gate
    }

    override suspend fun deleteGate(groupId: HexKey) {
        gateEntries.remove(groupId)
    }

    override suspend fun loadGates(): Map<HexKey, String> = gateEntries.toMap()
}

/** Why a publish attempt ended. */
enum class PublishOutcome {
    /** At least one endpoint in the recipient scope acknowledged an accept. */
    CONFIRMED,

    /**
     * The attempt did not confirm, and we cannot prove no endpoint took it.
     *
     * A timeout, a dropped connection, or an OK that never arrived all land
     * here, and none of them means "no peer has this commit". The obligation
     * stays durable and the group stays held, because minting a REPLACEMENT
     * commit for the same epoch would fork us against whoever did receive the
     * first one. This is the default for a publisher that answers with a
     * boolean: `false` is "unconfirmed", not "rejected".
     */
    UNKNOWN,

    /**
     * The attempt can never succeed and there is nothing to retry — an
     * unreadable record, or bytes no endpoint could ever accept.
     *
     * Discards the obligation. Use it only when retrying is impossible, never
     * as a synonym for "did not get an OK".
     */
    FAILED,
}

/**
 * Enforces publish-before-apply for locally generated group-state changes
 * (`protocol-core/publish-lifecycle.md`).
 *
 * ## Why staging rather than apply-and-roll-back
 *
 * Applying a local commit and undoing it if publication fails looks equivalent
 * and is not. Between the two there is a window in which this client's
 * canonical state is an epoch no peer has, and a crash inside that window makes
 * the fork permanent — the one outcome the rule exists to prevent. So the
 * commit is prepared on a clone, the live group never advances, and the pending
 * state becomes canonical only on a confirmed accept.
 *
 * ## What counts as confirmed
 *
 * At least one acknowledged accept from an endpoint in the obligation's
 * recipient scope. Queued, sent, or "no error yet" are explicitly NOT success:
 * the whole point is that some peer can now learn the new epoch. A transport
 * MAY require further fanout afterwards, but that fanout must not hold the
 * group in `PendingPublish`.
 *
 * ## Restart
 *
 * A process interruption does not resolve an obligation. An obligation whose
 * acknowledgement is unknown after restart is still `PendingPublish`, and a
 * safe retry republishes the byte-identical bytes rather than generating a
 * replacement Commit — a replacement would be a second commit at the same
 * epoch, i.e. a fork this client caused itself.
 */
class MarmotPublishGate(
    private val groupManager: MlsGroupManager,
    private val store: MarmotPublishObligationStore = InMemoryPublishObligationStore(),
) {
    private val mutex = Mutex()
    private val pending = LinkedHashMap<HexKey, MarmotPublishObligation>()
    private val gates = mutableMapOf<HexKey, LocalOutboundGate>()
    private val lifecycles = mutableMapOf<HexKey, GroupLifecycleState>()

    /**
     * An immutable copy of [gates], republished on every change.
     *
     * The map itself is mutable state guarded by [mutex], so it cannot be read
     * from a non-suspending caller without a data race. A front end needs the
     * gate on the synchronous path that refreshes a conversation's state — and
     * making that path suspend would push `suspend` up through every caller for
     * one flag — so the authority stays behind the lock and this is what
     * everyone else reads.
     */
    private val gateSnapshot = MutableStateFlow<Map<HexKey, LocalOutboundGate>>(emptyMap())

    /** Lock-free view of the outbound gates, for non-suspending readers. */
    val outboundGates: StateFlow<Map<HexKey, LocalOutboundGate>> get() = gateSnapshot

    /** The gate blocking [groupId] right now, read without suspending. */
    fun outboundGateNow(groupId: HexKey): LocalOutboundGate? = gateSnapshot.value[groupId]

    /** Reload unresolved obligations and outbound gates. Call once at startup, after group restore. */
    suspend fun restore() =
        mutex.withLock {
            store.loadGates().forEach { (groupId, name) ->
                // An unreadable gate name is dropped rather than guessed at:
                // inventing `Removed` for a group we are still in would hide it
                // forever, and inventing `Disbanding` would block a group whose
                // owner never asked to end it.
                LocalOutboundGate.entries.firstOrNull { it.name == name }?.let { gates[groupId] = it }
            }
            gateSnapshot.value = gates.toMap()
            store.loadAll().forEach { bytes ->
                try {
                    val obligation = MarmotPublishObligation.decodeTls(bytes)
                    pending[obligation.obligationId] = obligation
                    lifecycles[obligation.groupId] = GroupLifecycleState.PENDING_PUBLISH
                } catch (_: Exception) {
                    // A corrupt record is not a reason to lose the others. The
                    // group stays Stable and its commit is simply never
                    // confirmed, which is the safe direction: nothing local
                    // becomes canonical that peers never saw.
                }
            }
        }

    /** Lifecycle state for [groupId]. */
    suspend fun lifecycle(groupId: HexKey): GroupLifecycleState =
        mutex.withLock {
            lifecycles[groupId] ?: GroupLifecycleState.STABLE
        }

    /** The outbound gate blocking [groupId], if any. */
    suspend fun outboundGate(groupId: HexKey): LocalOutboundGate? = mutex.withLock { gates[groupId] }

    /**
     * Whether a new local commit may be prepared for [groupId].
     *
     * Only `Stable` may, and only with no outbound gate: `Leaving`,
     * `Disbanding` and a realized `Removed` each block all new outbound work.
     */
    suspend fun canPrepareLocalCommit(
        groupId: HexKey,
        ignoringGate: LocalOutboundGate? = null,
    ): Boolean =
        mutex.withLock {
            val state = lifecycles[groupId] ?: GroupLifecycleState.STABLE
            val gate = gates[groupId]
            // [ignoringGate] is for the work the gate itself exists to carry: a
            // `Disbanding` group must still be able to prepare — and regenerate
            // — the disband Commit, or raising the gate first would block the
            // very request that raised it.
            state.canPrepareLocalCommit && (gate == null || gate == ignoringGate)
        }

    /**
     * Raise an outbound gate — a sent SelfRemove, a disband request, a realized
     * removal — and make it durable before returning.
     *
     * Written before the caller acts on it, for the same reason a publish
     * obligation is: a disband request that is only in memory is lost by the
     * crash that happens between raising it and publishing the Commit, and the
     * next start would offer the group as ordinarily live.
     */
    suspend fun raiseGate(
        groupId: HexKey,
        gate: LocalOutboundGate,
    ) {
        store.saveGate(groupId, gate.name)
        mutex.withLock {
            gates[groupId] = gate
            gateSnapshot.value = gates.toMap()
        }
    }

    /** Clear an outbound gate. Only an authenticated re-join clears `REMOVED`. */
    suspend fun clearGate(groupId: HexKey) {
        store.deleteGate(groupId)
        mutex.withLock {
            gates.remove(groupId)
            gateSnapshot.value = gates.toMap()
        }
    }

    /** Unresolved obligations for [groupId], oldest first. */
    suspend fun pendingFor(groupId: HexKey): List<MarmotPublishObligation> =
        mutex.withLock {
            pending.values.filter { it.groupId == groupId }
        }

    /** Every unresolved obligation, oldest first. Retries republish these bytes verbatim. */
    suspend fun allPending(): List<MarmotPublishObligation> = mutex.withLock { pending.values.toList() }

    /**
     * Record a prepared commit and move the group to `PendingPublish`.
     *
     * The record is durable BEFORE the caller publishes anything. Publishing
     * first and recording after would leave a crash window in which peers have
     * accepted a commit this client has no memory of preparing — and on
     * restart it would generate a replacement, forking itself.
     */
    suspend fun prepare(
        groupId: HexKey,
        staged: MlsGroupManager.StagedCommit,
        outboundBytes: ByteArray,
        recipientScope: List<String>,
    ): MarmotPublishObligation {
        val obligation =
            MarmotPublishObligation(
                groupId = groupId,
                obligationId = MarmotPublishObligation.idFor(outboundBytes),
                outboundBytes = outboundBytes,
                recipientScope = recipientScope,
                priorState = staged.priorState,
                pendingState = staged.pendingState,
            )
        store.save(obligation.obligationId, obligation.encodeTls())
        mutex.withLock {
            pending[obligation.obligationId] = obligation
            lifecycles[groupId] = GroupLifecycleState.PENDING_PUBLISH
        }
        return obligation
    }

    /**
     * Resolve an obligation.
     *
     * On [PublishOutcome.CONFIRMED] the pending state becomes canonical; on
     * [PublishOutcome.FAILED] it is discarded and the live group — which never
     * advanced — is already correct, with its pending proposals still available
     * for another attempt.
     */
    suspend fun resolve(
        obligationId: HexKey,
        outcome: PublishOutcome,
    ): GroupLifecycleState {
        val obligation = mutex.withLock { pending[obligationId] } ?: return GroupLifecycleState.STABLE

        if (outcome == PublishOutcome.CONFIRMED) {
            mutex.withLock { lifecycles[obligation.groupId] = GroupLifecycleState.MERGING }
            // Applying happens outside the lock: installState persists, and a
            // partially applied merge must never be observable, so the state
            // swap is the single atomic step that ends it.
            groupManager.installState(obligation.groupId, obligation.pendingState)
        }

        if (outcome == PublishOutcome.UNKNOWN) {
            // Keep the record and keep the group held. The staged commit was
            // never applied, so nothing local is wrong — what is unknown is
            // whether a PEER took it, and preparing a fresh commit while that
            // is unknown is exactly the fork this gate exists to prevent.
            return mutex.withLock {
                lifecycles[obligation.groupId] = GroupLifecycleState.PENDING_PUBLISH
                GroupLifecycleState.PENDING_PUBLISH
            }
        }

        store.delete(obligationId)
        return mutex.withLock {
            pending.remove(obligationId)
            val stillPending = pending.values.any { it.groupId == obligation.groupId }
            val next =
                if (stillPending) GroupLifecycleState.PENDING_PUBLISH else GroupLifecycleState.STABLE
            lifecycles[obligation.groupId] = next
            next
        }
    }

    /**
     * Satisfy an obligation with no bytes and no recipients, per the group
     * creation exception.
     *
     * A one-member epoch-0 group has no peer that failure to publish could
     * fork, so the obligation is immediately satisfied and epoch 0 becomes
     * canonical with nothing sent. The exception is limited to creation and its
     * immediately following founding Add — every later commit takes the normal
     * path.
     */
    suspend fun satisfyEmptyObligation(groupId: HexKey) =
        mutex.withLock {
            lifecycles[groupId] = GroupLifecycleState.STABLE
            Unit
        }

    /** Record a lifecycle state decided elsewhere (convergence, terminalization). */
    suspend fun setLifecycle(
        groupId: HexKey,
        state: GroupLifecycleState,
    ) = mutex.withLock {
        lifecycles[groupId] = state
        Unit
    }

    /** Forget everything about [groupId]. */
    suspend fun forget(groupId: HexKey) {
        val ids = mutex.withLock { pending.values.filter { it.groupId == groupId }.map { it.obligationId } }
        ids.forEach { store.delete(it) }
        mutex.withLock {
            ids.forEach { pending.remove(it) }
            lifecycles.remove(groupId)
            gates.remove(groupId)
        }
    }
}
