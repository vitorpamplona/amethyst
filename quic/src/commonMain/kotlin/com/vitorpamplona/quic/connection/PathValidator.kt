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
package com.vitorpamplona.quic.connection

import com.vitorpamplona.quartz.utils.RandomInstance

/**
 * Single connection ID the peer (server) has issued to us via a
 * `NEW_CONNECTION_ID` frame (RFC 9000 §19.15) and we have NOT yet
 * retired. The initial DCID negotiated during the handshake is
 * implicitly sequence number 0 (RFC 9000 §5.1.1).
 *
 * `connectionId` is the bytes the writer would stamp into outbound
 * short-header packets; `statelessResetToken` is the 16-byte token
 * we'd compare against an incoming stateless reset.
 */
data class PeerConnectionIdEntry(
    val sequenceNumber: Long,
    val connectionId: ByteArray,
    val statelessResetToken: ByteArray,
) {
    init {
        require(connectionId.isNotEmpty()) { "connection id must not be empty" }
        require(statelessResetToken.size == 16) { "stateless reset token must be 16 bytes" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PeerConnectionIdEntry) return false
        return sequenceNumber == other.sequenceNumber &&
            connectionId.contentEquals(other.connectionId) &&
            statelessResetToken.contentEquals(other.statelessResetToken)
    }

    override fun hashCode(): Int {
        var h = sequenceNumber.hashCode()
        h = 31 * h + connectionId.contentHashCode()
        h = 31 * h + statelessResetToken.contentHashCode()
        return h
    }
}

/**
 * Where the path validation state machine currently sits (RFC 9000
 * §8.2 / §9). Most installations spend their entire life in [Idle];
 * the transition to [Validating] happens when the driver detects the
 * current path looks dead (consecutive PTOs without ACKs) and asks
 * the connection to migrate to a fresh DCID.
 *
 *  - [Idle]: nothing in flight; the writer is happily using the
 *    current DCID.
 *  - [Validating]: a `PATH_CHALLENGE` is on the wire (or queued for
 *    the next outbound) and we're waiting for a matching
 *    `PATH_RESPONSE` with the SAME 8-byte payload.
 *  - [Succeeded]: the peer echoed the payload; the writer has
 *    switched to the new DCID and a `RETIRE_CONNECTION_ID` for the
 *    old sequence number is queued.
 *  - [Failed]: validation didn't complete inside the 3*PTO window
 *    (RFC 9000 §8.2.4) — caller may retry with a different CID, or
 *    surface the failure as a connection close.
 */
sealed class PathValidationState {
    object Idle : PathValidationState()

    data class Validating(
        val challengeData: ByteArray,
        val newCidSequence: Long,
        val newConnectionId: ByteArray,
        val priorCidSequence: Long,
        val startedAtMillis: Long,
        val priorPtoMillis: Long,
    ) : PathValidationState() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Validating) return false
            return challengeData.contentEquals(other.challengeData) &&
                newCidSequence == other.newCidSequence &&
                newConnectionId.contentEquals(other.newConnectionId) &&
                priorCidSequence == other.priorCidSequence &&
                startedAtMillis == other.startedAtMillis &&
                priorPtoMillis == other.priorPtoMillis
        }

        override fun hashCode(): Int {
            var h = challengeData.contentHashCode()
            h = 31 * h + newCidSequence.hashCode()
            h = 31 * h + newConnectionId.contentHashCode()
            h = 31 * h + priorCidSequence.hashCode()
            h = 31 * h + startedAtMillis.hashCode()
            h = 31 * h + priorPtoMillis.hashCode()
            return h
        }
    }

    object Succeeded : PathValidationState()

    object Failed : PathValidationState()
}

/**
 * Outcome of [PathValidator.tryStartValidation], used by the caller
 * (driver / test) to know whether a probe was actually issued.
 */
enum class PathMigrationResult {
    /** A `PATH_CHALLENGE` was queued; state is [PathValidationState.Validating]. */
    Started,

    /**
     * Already in [PathValidationState.Validating] — the previous
     * attempt hasn't resolved yet. Caller should not pile on; the
     * existing challenge will resolve via PATH_RESPONSE or fail
     * via [PathValidator.checkValidationTimeout].
     */
    AlreadyInProgress,

    /**
     * No spare CID in the pool. The peer hasn't yet issued a
     * NEW_CONNECTION_ID we can use, or we've already drained the
     * pool. Caller should wait for fresh peer offers.
     */
    NoSpareCid,

    /**
     * Connection is not yet in CONNECTED state — RFC 9000 §9.1
     * forbids initiating migration before the handshake is
     * confirmed. Caller should drop the trigger; a fresh handshake
     * doesn't need migration.
     */
    NotConnected,
}

/**
 * Per-connection state for client-initiated path validation + DCID
 * rotation (RFC 9000 §9). Owned by [QuicConnection]; the parser and
 * writer call into it under `streamsLock`.
 *
 * Spec mapping:
 *   - **§5.1.1** initial DCID is sequence number 0.
 *   - **§5.1.2** at most `active_connection_id_limit` CIDs
 *     simultaneously usable; the limit is the PEER's transport
 *     parameter telling us how many of OUR source CIDs they'll
 *     hold, but reciprocally we cap the peer's pool the same way
 *     so a buggy server can't pin arbitrary memory by spamming
 *     NEW_CONNECTION_ID frames.
 *   - **§5.1.2** `retire_prior_to` in a NEW_CONNECTION_ID frame
 *     forces us to retire every previously-issued CID with a
 *     smaller sequence number (and queue RETIRE_CONNECTION_ID for
 *     each). The `retire_prior_to` value MUST NOT decrease across
 *     successive frames; we treat a regression as a peer protocol
 *     violation and abort the connection upstream.
 *   - **§8.2 / §9** path validation: pick a spare CID, issue
 *     PATH_CHALLENGE with a cryptographically random 8-byte
 *     payload, await a PATH_RESPONSE that echoes the payload byte
 *     for byte. RFC 9000 §8.2.4: validation MUST be abandoned after
 *     `3 * PTO` of inactivity.
 *   - **§19.16** RETIRE_CONNECTION_ID is reliable; the dispatcher
 *     re-queues on loss until ACK'd.
 *
 * Thread safety: every method here assumes the caller holds the
 * connection's `streamsLock`. The pool / outstanding-challenge maps
 * are plain mutable collections.
 */
class PathValidator(
    /**
     * Cap on the number of unused CIDs we'll buffer. Defaults to
     * the peer's `active_connection_id_limit` transport parameter,
     * but the connection caller may pass a tighter cap. Excess
     * NEW_CONNECTION_ID offers past this cap are silently dropped
     * — the peer is in violation of the limit it asked us to
     * advertise (or never advertised one) and we don't owe it
     * indefinite buffering.
     */
    private val maxUnusedCids: Int = DEFAULT_MAX_UNUSED_CIDS,
    /**
     * Cryptographically random 8-byte payload generator used for
     * outbound PATH_CHALLENGE. Default uses [RandomInstance]; tests
     * can substitute a deterministic supplier so assertions can
     * compare the on-wire payload against a known value.
     */
    private val challengePayloadFactory: () -> ByteArray = { RandomInstance.bytes(8) },
) {
    /**
     * Sequence number → entry. Holds CIDs the peer has issued and
     * we have NOT yet retired or activated. The active CID itself
     * is NOT in this map (it's held by the connection's
     * `destinationConnectionId` field); only the spare pool lives
     * here.
     *
     * LinkedHashMap so iteration order matches issuance order — when
     * we pick a CID for a fresh validation we prefer the lowest
     * sequence number for FIFO fairness.
     */
    private val unusedCids: LinkedHashMap<Long, PeerConnectionIdEntry> = LinkedHashMap()

    /**
     * Highest `retire_prior_to` value the peer has ever advertised.
     * Per RFC 9000 §5.1.2 this MUST NOT decrease; a decrease is a
     * peer protocol violation. Used to decide whether a
     * just-arrived NEW_CONNECTION_ID with a sequence number ≤
     * [retirePriorToWatermark] is stale (drop it) or whether a
     * previously-cached entry has now been forced into retirement.
     */
    var retirePriorToWatermark: Long = 0L
        private set

    /**
     * Sequence number of the CID the writer is currently stamping
     * into outbound short-header packets. Starts at 0 per RFC 9000
     * §5.1.1 — the DCID negotiated at handshake is implicitly
     * sequence 0. Bumped to the new sequence after a successful
     * [applyPathResponse]; the prior sequence is queued for
     * RETIRE_CONNECTION_ID via [pendingRetireSequences].
     */
    var activeCidSequence: Long = 0L
        private set

    /**
     * Sequence numbers of CIDs we owe the peer a
     * `RETIRE_CONNECTION_ID` for. Drained by the writer on the
     * next outbound application packet. Re-populated by the loss
     * dispatcher if our RETIRE_CONNECTION_ID was lost.
     */
    internal val pendingRetireSequences: ArrayDeque<Long> = ArrayDeque()

    /**
     * Outbound `PATH_CHALLENGE` payloads the writer should drain on
     * the next application-level packet. Populated by
     * [tryStartValidation]; emptied by the writer after encoding
     * (the writer also records a [com.vitorpamplona.quic.connection.recovery.RecoveryToken.PathChallenge]
     * so the loss dispatcher can decide to retransmit or abandon).
     */
    internal val pendingChallenges: ArrayDeque<ByteArray> = ArrayDeque()

    var state: PathValidationState = PathValidationState.Idle
        private set

    /**
     * Total number of validations that have completed successfully
     * over the lifetime of this connection. Diagnostic only — used
     * by tests and qlog to assert "exactly one rotation happened".
     */
    var successfulValidations: Long = 0L
        private set

    /**
     * Total number of validations that timed out / were abandoned.
     * Counter-part of [successfulValidations].
     */
    var failedValidations: Long = 0L
        private set

    /**
     * Number of unused CIDs currently in the pool. Used by tests
     * (and the connection's diagnostic surface) to assert the peer
     * actually offered new CIDs we can rotate to.
     */
    fun unusedCount(): Int = unusedCids.size

    fun unusedSequences(): List<Long> = unusedCids.keys.toList()

    /**
     * Record a peer-issued `NEW_CONNECTION_ID` (RFC 9000 §19.15).
     * Returns the result code so the caller (parser) can decide
     * whether to close the connection on a peer protocol violation.
     *
     * The semantics:
     *  - If [retirePriorTo] is greater than [sequenceNumber], that's
     *    a §19.15 frame-encoding error (the peer can't tell us to
     *    retire its own brand-new CID).
     *  - If [retirePriorTo] is smaller than the current
     *    [retirePriorToWatermark], it's clamped to the watermark
     *    per §19.15 ("MUST be treated as the largest one it has
     *    seen") — common with reordered NEW_CONNECTION_ID arrivals,
     *    not a peer error.
     *  - If the new entry would push the pool past [maxUnusedCids],
     *    return [RecordResult.PoolFull] — the peer over-issued.
     *  - If [sequenceNumber] is below the current
     *    [retirePriorToWatermark] (after clamp), the offer is
     *    already-retired and we drop it after queuing a
     *    RETIRE_CONNECTION_ID for it (§5.1.2).
     *  - Duplicate sequence number with matching CID/token is
     *    idempotent ([RecordResult.Duplicate]); a duplicate sequence
     *    with DIFFERENT bytes is a §19.15 violation.
     *
     * Side effect on success: any cached entry whose sequence
     * number is now below the new [retirePriorToWatermark] is moved
     * into [pendingRetireSequences] and dropped from [unusedCids].
     * Note that this routine does NOT force-retire the active CID
     * if the watermark moves past [activeCidSequence] — that's the
     * caller's responsibility (it requires picking a replacement
     * from the pool and rotating the writer's DCID). See
     * [QuicConnection.applyPeerNewConnectionIdLocked].
     */
    fun recordPeerNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ): RecordResult {
        if (retirePriorTo > sequenceNumber) return RecordResult.RetirePriorToExceedsSequence
        if (connectionId.isEmpty() || connectionId.size > 20) return RecordResult.InvalidCidLength
        if (statelessResetToken.size != 16) return RecordResult.InvalidStatelessResetToken

        // RFC 9000 §19.15: a smaller `retire_prior_to` than what we
        // previously saw "MUST be treated as the largest one it has
        // seen". This handles reordered NEW_CONNECTION_ID arrivals
        // — not a protocol violation, just clamp.
        val effectiveRetirePriorTo = if (retirePriorTo < retirePriorToWatermark) retirePriorToWatermark else retirePriorTo

        // §5.1.2: bump the watermark and force-retire any cached
        // entries that fall under it. This step happens BEFORE the
        // duplicate check so a sequence number that's just been
        // dropped doesn't survive on a stale entry.
        if (effectiveRetirePriorTo > retirePriorToWatermark) {
            retirePriorToWatermark = effectiveRetirePriorTo
            val it = unusedCids.entries.iterator()
            while (it.hasNext()) {
                val (seq, _) = it.next()
                if (seq < retirePriorToWatermark) {
                    queueRetireSequence(seq)
                    it.remove()
                }
            }
        }

        // Stale offer: the peer is pushing a sequence number we've
        // already retired. Per §5.1.2 we owe a RETIRE_CONNECTION_ID
        // back; queue it but don't add the entry to the pool.
        if (sequenceNumber < retirePriorToWatermark) {
            queueRetireSequence(sequenceNumber)
            return RecordResult.AlreadyRetired
        }

        // Duplicate handling.
        val existing = unusedCids[sequenceNumber]
        if (existing != null) {
            if (!existing.connectionId.contentEquals(connectionId) ||
                !existing.statelessResetToken.contentEquals(statelessResetToken)
            ) {
                return RecordResult.DuplicateSequenceMismatch
            }
            return RecordResult.Duplicate
        }

        if (unusedCids.size >= maxUnusedCids) return RecordResult.PoolFull

        unusedCids[sequenceNumber] =
            PeerConnectionIdEntry(
                sequenceNumber = sequenceNumber,
                connectionId = connectionId.copyOf(),
                statelessResetToken = statelessResetToken.copyOf(),
            )
        return RecordResult.Stored
    }

    /**
     * Result of [recordPeerNewConnectionId].
     */
    enum class RecordResult {
        Stored,
        Duplicate,
        DuplicateSequenceMismatch,
        AlreadyRetired,
        PoolFull,
        RetirePriorToExceedsSequence,
        InvalidCidLength,
        InvalidStatelessResetToken,
    }

    /**
     * Append [sequenceNumber] to [pendingRetireSequences] iff it
     * isn't already queued. Idempotent — a duplicate request from
     * the loss dispatcher (re-queue on loss) finds the entry
     * already there and is a no-op.
     */
    fun queueRetireSequence(sequenceNumber: Long) {
        if (!pendingRetireSequences.contains(sequenceNumber)) {
            pendingRetireSequences.addLast(sequenceNumber)
        }
    }

    /**
     * Begin a fresh path-validation attempt. Picks the lowest-
     * sequence unused CID, generates a random 8-byte challenge,
     * queues the challenge in [pendingChallenges], and transitions
     * [state] to [PathValidationState.Validating]. The selected
     * CID is removed from the unused pool; on validation success
     * [applyPathResponse] commits it (callers swap the writer's
     * DCID), and on timeout [checkValidationTimeout] queues it
     * for RETIRE_CONNECTION_ID so the peer can drop the routing
     * entry.
     *
     * Caller is responsible for two follow-ups when this returns
     * [PathMigrationResult.Started]:
     *  1. Rotate the connection's `destinationConnectionId` to
     *     [PathValidationState.Validating.newConnectionId] so the
     *     PATH_CHALLENGE packet (and subsequent traffic, per the
     *     abrupt-migration model) goes out on the new path.
     *  2. Wake the send loop so the challenge actually leaves.
     */
    fun tryStartValidation(
        nowMillis: Long,
        currentPtoMillis: Long,
    ): PathMigrationResult {
        if (state is PathValidationState.Validating) return PathMigrationResult.AlreadyInProgress
        val (seq, entry) = unusedCids.entries.firstOrNull() ?: return PathMigrationResult.NoSpareCid
        unusedCids.remove(seq)
        val payload =
            challengePayloadFactory().also {
                require(it.size == 8) { "challenge payload supplier must return 8 bytes" }
            }
        pendingChallenges.addLast(payload)
        state =
            PathValidationState.Validating(
                challengeData = payload,
                newCidSequence = seq,
                newConnectionId = entry.connectionId,
                priorCidSequence = activeCidSequence,
                startedAtMillis = nowMillis,
                priorPtoMillis = currentPtoMillis,
            )
        return PathMigrationResult.Started
    }

    /**
     * Process an inbound `PATH_RESPONSE` payload. Returns true when
     * it matched the outstanding challenge and the migration just
     * completed; false if there's no outstanding challenge or the
     * payload doesn't match (an attacker echoing random bytes).
     *
     * Side effects on success:
     *  - [state] transitions to [PathValidationState.Succeeded].
     *  - [activeCidSequence] is bumped to the validated sequence.
     *  - The prior sequence is queued for RETIRE_CONNECTION_ID.
     *  - Returns the new entry so the connection can swap its
     *    `destinationConnectionId` field.
     */
    fun applyPathResponse(payload: ByteArray): ValidationOutcome {
        val current = state as? PathValidationState.Validating ?: return ValidationOutcome.NotValidating
        if (!payload.contentEquals(current.challengeData)) return ValidationOutcome.PayloadMismatch
        val priorSeq = activeCidSequence
        activeCidSequence = current.newCidSequence
        if (priorSeq != current.newCidSequence) queueRetireSequence(priorSeq)
        state = PathValidationState.Succeeded
        successfulValidations += 1
        return ValidationOutcome.Validated(
            newSequence = current.newCidSequence,
            connectionId = current.newConnectionId,
            retiredSequence = priorSeq,
        )
    }

    sealed class ValidationOutcome {
        object NotValidating : ValidationOutcome()

        object PayloadMismatch : ValidationOutcome()

        data class Validated(
            val newSequence: Long,
            val connectionId: ByteArray,
            val retiredSequence: Long,
        ) : ValidationOutcome() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Validated) return false
                return newSequence == other.newSequence &&
                    connectionId.contentEquals(other.connectionId) &&
                    retiredSequence == other.retiredSequence
            }

            override fun hashCode(): Int {
                var h = newSequence.hashCode()
                h = 31 * h + connectionId.contentHashCode()
                h = 31 * h + retiredSequence.hashCode()
                return h
            }
        }
    }

    /**
     * RFC 9000 §8.2.4: abandon validation if more than 3 * PTO has
     * elapsed since the challenge went out without a matching
     * response. Returns the abandoned context (so the caller can
     * surface a qlog event / decide whether to attempt another
     * rotation), or null if no validation is in progress / the
     * timer hasn't fired yet.
     *
     * On abandonment the new CID's sequence is queued for
     * RETIRE_CONNECTION_ID. We probed with that CID — even though
     * the peer never echoed our challenge, our challenge packet
     * may have reached them and they've stamped routing state
     * against the CID. Retiring it tells them they can drop that
     * state. RFC 9000 §5.1.2: a connection ID that was used and
     * then abandoned MUST be retired.
     */
    fun checkValidationTimeout(nowMillis: Long): PathValidationState.Validating? {
        val current = state as? PathValidationState.Validating ?: return null
        val elapsed = nowMillis - current.startedAtMillis
        val budget = (current.priorPtoMillis * VALIDATION_PTO_BUDGET_MULTIPLIER).coerceAtLeast(MIN_VALIDATION_BUDGET_MS)
        if (elapsed < budget) return null
        queueRetireSequence(current.newCidSequence)
        state = PathValidationState.Failed
        failedValidations += 1
        return current
    }

    /**
     * Reset to [PathValidationState.Idle] after the caller has
     * surfaced a Succeeded / Failed transition. Allows the next
     * trigger to start a fresh attempt without leaking the
     * previous terminal state.
     */
    fun acknowledgeTerminal() {
        if (state is PathValidationState.Succeeded || state is PathValidationState.Failed) {
            state = PathValidationState.Idle
        }
    }

    companion object {
        /**
         * Default cap on the unused-CID pool. RFC 9000 §18.2 default
         * for `active_connection_id_limit` is 2, and the spec
         * lower-bounds it at 2. A peer that issues more than its
         * own advertised limit is misbehaving but harmless — we
         * just cap at 8 here so a buggy server can't pin memory.
         */
        const val DEFAULT_MAX_UNUSED_CIDS: Int = 8

        /**
         * RFC 9000 §8.2.4: validation MUST be abandoned after
         * 3 * PTO. We use 3.0 exactly; a slightly looser value
         * (e.g. 4) wouldn't change correctness but might mask peer
         * misbehavior in tests.
         */
        const val VALIDATION_PTO_BUDGET_MULTIPLIER: Long = 3L

        /**
         * Floor on the validation budget. PTO during the first RTT
         * sample is ~300 ms (see [com.vitorpamplona.quic.connection.recovery.QuicLossDetection.INITIAL_RTT_MS]),
         * so 3*PTO ≈ 900 ms. We add a generous floor so test paths
         * with an artificially-tiny RTT still get a real chance to
         * complete validation before the timer fires.
         */
        const val MIN_VALIDATION_BUDGET_MS: Long = 250L
    }
}
