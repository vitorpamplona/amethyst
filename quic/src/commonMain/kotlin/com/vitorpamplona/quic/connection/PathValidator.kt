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
 * Outcome of attempting to start a client-initiated path
 * migration. Produced by [PathValidator.tryStartValidation] (which
 * never returns [NotConnected]) and the connection-level wrapper
 * [QuicConnection.triggerPathMigrationLocked] (which adds the
 * pre-handshake gate). Used by the driver / application code that
 * fires the trigger to decide whether to retry later.
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
     * Stateless-reset-token lookup for an unused-pool entry. Returns
     * null if no entry exists for [sequenceNumber] (e.g. it was
     * retired between [unusedSequences] returning and this call).
     * Used by [QuicConnection.isStatelessReset] for token matching
     * against arriving look-like-noise datagrams per RFC 9000 §10.3.
     */
    fun unusedTokenForSequence(sequenceNumber: Long): ByteArray? = unusedCids[sequenceNumber]?.statelessResetToken

    /**
     * RFC 9000 §10.3 stateless-reset support, audio-rooms WiFi-handoff
     * extension. Every stateless-reset token the peer has ever issued
     * us via NEW_CONNECTION_ID — whether the corresponding CID is
     * still in [unusedCids], currently active (rotated to via
     * [tryStartValidation] / [forceRotateToHigherSequence]), or
     * RETIRE_CONNECTION_ID'd.
     *
     * Why retain after rotation/retire: when the user roams between
     * WiFi and cellular, we migrate the connection to a fresh DCID
     * via [tryStartValidation]. If the peer (relay) loses connection
     * state during the handoff (crash, restart, NAT rebinding), it
     * sends a stateless reset using whichever of OUR-issued source
     * CIDs it last had cached — which corresponds to whichever
     * stateless-reset token it remembers issuing for that CID. We
     * MUST be able to match the reset against the right token. If we
     * only kept tokens for CIDs in the unused pool (the pre-fix
     * shape), the rotated-to active CID's token would be lost the
     * moment migration started — a stateless reset on the new path
     * would look like noise and we'd hang until idle timeout.
     *
     * §10.3.1 explicitly allows endpoints to keep tokens after
     * retirement: "An endpoint MAY drop a Stateless Reset Token
     * after retiring the corresponding connection ID; this saves
     * storage at the cost of being more vulnerable to spoofing if
     * the same token is reused by an attacker." For audio-rooms the
     * extra ~16 bytes per peer-issued CID is trivial.
     *
     * Returned in insertion order (oldest first); caller treats it
     * as an unordered set for matching.
     */
    fun allKnownStatelessResetTokens(): List<ByteArray> = knownStatelessResetTokens

    /**
     * Lifetime store of every stateless-reset token the peer has
     * ever advertised via NEW_CONNECTION_ID. Append-only — entries
     * are NOT removed when the corresponding CID is rotated out of
     * [unusedCids] or RETIRE_CONNECTION_ID'd. See
     * [allKnownStatelessResetTokens] for the rationale.
     */
    private val knownStatelessResetTokens: ArrayList<ByteArray> = ArrayList()

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

        val tokenCopy = statelessResetToken.copyOf()
        unusedCids[sequenceNumber] =
            PeerConnectionIdEntry(
                sequenceNumber = sequenceNumber,
                connectionId = connectionId.copyOf(),
                statelessResetToken = tokenCopy,
            )
        // Append to the lifetime store so the token is still
        // matchable after a future [tryStartValidation] /
        // [forceRotateToHigherSequence] removes the entry from
        // [unusedCids]. See [allKnownStatelessResetTokens] for
        // rationale (WiFi-handoff stateless-reset detection).
        knownStatelessResetTokens.add(tokenCopy)
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
     * CID is removed from the unused pool. The prior CID's
     * sequence is queued for RETIRE_CONNECTION_ID immediately —
     * we're abandoning that path and the peer can drop the
     * routing entry now (RFC 9000 §5.1.2). [activeCidSequence]
     * is bumped to the new sequence so it tracks what the writer
     * is putting on the wire.
     *
     * On success the writer just promotes [PathValidationState] to
     * [PathValidationState.Succeeded]; on timeout the failed CID
     * is also queued for retire (see [checkValidationTimeout]).
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
        // Pick the SMALLEST sequence number, not insertion order. The
        // peer is allowed (RFC 9000 §19.15) to issue NEW_CONNECTION_ID
        // out of sequence — e.g. retransmits arriving after newer
        // higher-seq offers. LinkedHashMap iteration is by insertion,
        // so the prior `firstOrNull` would pick whichever offer landed
        // first, not the lowest seq. Picking the smallest preserves
        // RFC's expected ordering (lower seq retired first) and lines
        // up with what other clients (quicly, neqo) do.
        val (seq, entry) = unusedCids.entries.minByOrNull { it.key } ?: return PathMigrationResult.NoSpareCid
        unusedCids.remove(seq)
        val payload =
            challengePayloadFactory().also {
                require(it.size == 8) { "challenge payload supplier must return 8 bytes" }
            }
        pendingChallenges.addLast(payload)
        val priorSeq = activeCidSequence
        // Bug-B fix: retire the prior sequence at trigger time.
        // Abrupt migration means we won't use the old DCID again,
        // so the peer's routing entry for it is dead state. Without
        // this, two consecutive failed validations leave the
        // initial seq=0 unretired indefinitely.
        if (priorSeq != seq) queueRetireSequence(priorSeq)
        // [activeCidSequence] tracks "the seq the writer is currently
        // stamping on the wire", so it advances at trigger time
        // alongside `destinationConnectionId`. The
        // [PathValidationState.Validating.priorCidSequence] field
        // preserves the value for diagnostic / qlog purposes.
        activeCidSequence = seq
        state =
            PathValidationState.Validating(
                challengeData = payload,
                newCidSequence = seq,
                newConnectionId = entry.connectionId,
                priorCidSequence = priorSeq,
                startedAtMillis = nowMillis,
                priorPtoMillis = currentPtoMillis,
            )
        return PathMigrationResult.Started
    }

    /**
     * Process an inbound `PATH_RESPONSE` payload. Confirms the
     * outstanding challenge: state transitions to
     * [PathValidationState.Succeeded] and a [ValidationOutcome.Validated]
     * is returned with the new sequence + connection-id bytes for
     * the connection-level wrapper to surface in qlog.
     *
     * Note that [activeCidSequence] and the retire-queue entry for
     * the prior sequence were already mutated at challenge-issue
     * time (see [tryStartValidation]) — under the abrupt-migration
     * model the prior CID is abandoned the moment we rotate, not
     * when the response arrives. So this method has no further
     * cleanup work beyond marking the state as Succeeded.
     *
     * Returns [ValidationOutcome.NotValidating] if no challenge
     * is outstanding, or [ValidationOutcome.PayloadMismatch] if
     * the bytes don't match. Both are silently dropped at the
     * caller per RFC 9000 §8.2.2.
     */
    fun applyPathResponse(payload: ByteArray): ValidationOutcome {
        val current = state as? PathValidationState.Validating ?: return ValidationOutcome.NotValidating
        if (!payload.contentEquals(current.challengeData)) return ValidationOutcome.PayloadMismatch
        state = PathValidationState.Succeeded
        successfulValidations += 1
        return ValidationOutcome.Validated(
            newSequence = current.newCidSequence,
            connectionId = current.newConnectionId,
            retiredSequence = current.priorCidSequence,
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
     * response.
     *
     * On abandonment we probed with the new CID — even though the
     * peer never echoed our challenge, our challenge packet may
     * have reached them and they've stamped routing state against
     * the CID. So we'd like to retire it (RFC 9000 §5.1.2: a
     * connection ID that was used and then abandoned MUST be
     * retired). BUT — under the abrupt-migration model, the
     * writer's outbound DCID is currently STAMPING the failed
     * CID. If we queue a RETIRE_CONNECTION_ID for it without
     * rotating the writer to a different CID, the next outbound
     * packet carries both the RETIRE frame AND the now-retired
     * CID as its destination — quic-go / picoquic / msquic /
     * mvfst correctly read that as PROTOCOL_VIOLATION (see
     * `quic/plans/2026-05-08-rebind-port-bug.md`).
     *
     * So on timeout we have two cases:
     *
     *  - **Spare available** ([TimeoutOutcome.RecoveredOnSpare]):
     *    pick the lowest-sequence unused CID, rotate
     *    [activeCidSequence] to it, and queue
     *    `RETIRE_CONNECTION_ID` for the failed sequence. The
     *    caller MUST swap the connection's
     *    `destinationConnectionId` to
     *    [RecoveredOnSpare.newConnectionId] atomically with the
     *    state change so the next outbound stamps the rotated
     *    CID, not the retired one.
     *
     *  - **No spare** ([TimeoutOutcome.StuckOnFailedCid]): keep
     *    [activeCidSequence] on the failed sequence and do NOT
     *    queue retire. The validation didn't succeed but the CID
     *    itself is still "ours" until we explicitly retire it —
     *    the peer hasn't been told to drop it. The writer keeps
     *    using it; if the path actually still works, traffic
     *    resumes; if it really is dead, the next idle-timeout or
     *    application-layer close handles the connection. Once a
     *    spare arrives via NEW_CONNECTION_ID we'll rotate on the
     *    next trigger.
     *
     * Returns [TimeoutOutcome.NotTimedOut] if no validation is in
     * progress or the budget hasn't elapsed yet.
     */
    fun checkValidationTimeout(nowMillis: Long): TimeoutOutcome {
        val current = state as? PathValidationState.Validating ?: return TimeoutOutcome.NotTimedOut
        val elapsed = nowMillis - current.startedAtMillis
        val budget = (current.priorPtoMillis * VALIDATION_PTO_BUDGET_MULTIPLIER).coerceAtLeast(MIN_VALIDATION_BUDGET_MS)
        if (elapsed < budget) return TimeoutOutcome.NotTimedOut
        // Drop any PATH_CHALLENGE we hadn't yet drained for this
        // attempt — the writer mustn't emit them onto an abandoned
        // validation cycle.
        pendingChallenges.removeAll { it.contentEquals(current.challengeData) }
        state = PathValidationState.Failed
        failedValidations += 1
        val sparePair = unusedCids.entries.firstOrNull()
        if (sparePair == null) {
            // No spare to rotate into. KEEP the failed seq active —
            // queuing a retire here would have us stamping a retired
            // CID on every subsequent packet (the very bug we're
            // guarding against). The CID is still valid from the
            // peer's perspective until we explicitly retire it.
            return TimeoutOutcome.StuckOnFailedCid(abandoned = current)
        }
        val (spareSeq, spareEntry) = sparePair
        unusedCids.remove(spareSeq)
        // Atomic with the rotation: queue retire AND bump active to
        // the spare. The caller (connection wrapper) MUST also
        // update its `destinationConnectionId` to the new bytes
        // before releasing the lock.
        queueRetireSequence(current.newCidSequence)
        activeCidSequence = spareSeq
        return TimeoutOutcome.RecoveredOnSpare(
            abandoned = current,
            newSequence = spareSeq,
            newConnectionId = spareEntry.connectionId,
        )
    }

    sealed class TimeoutOutcome {
        /** No validation in progress, or budget hasn't elapsed yet. */
        object NotTimedOut : TimeoutOutcome()

        /**
         * Validation timed out and we rotated [activeCidSequence]
         * to a fresh spare. The caller MUST update the
         * connection's `destinationConnectionId` to
         * [newConnectionId] atomically with this transition.
         * `RETIRE_CONNECTION_ID` for the abandoned seq has been
         * queued for the next outbound.
         */
        data class RecoveredOnSpare(
            val abandoned: PathValidationState.Validating,
            val newSequence: Long,
            val newConnectionId: ByteArray,
        ) : TimeoutOutcome() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is RecoveredOnSpare) return false
                return abandoned == other.abandoned &&
                    newSequence == other.newSequence &&
                    newConnectionId.contentEquals(other.newConnectionId)
            }

            override fun hashCode(): Int {
                var h = abandoned.hashCode()
                h = 31 * h + newSequence.hashCode()
                h = 31 * h + newConnectionId.contentHashCode()
                return h
            }
        }

        /**
         * Validation timed out but no spare CID is available. The
         * failed sequence is KEPT as active (no retire queued) so
         * the writer doesn't stamp a CID it has already retired —
         * see [checkValidationTimeout] kdoc for the rationale.
         */
        data class StuckOnFailedCid(
            val abandoned: PathValidationState.Validating,
        ) : TimeoutOutcome()
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

    /**
     * RFC 9000 §5.1.2: "If the active connection ID's sequence
     * number is less than the Retire Prior To value, the endpoint
     * MUST retire the active connection ID and adopt one with a
     * higher sequence number."
     *
     * Server-forced retirement does NOT require path validation —
     * it's the same 4-tuple, just a different CID. Pick the
     * lowest-sequence entry from the pool, swap it in, queue the
     * old active sequence for RETIRE_CONNECTION_ID. If no spare
     * is available, returns null and the caller should close the
     * connection (we have no CID to use that satisfies the peer's
     * watermark).
     *
     * If a path validation happens to be in progress when this
     * fires, the in-flight challenge's CID may itself fall under
     * the watermark — abandon the validation and queue the failed
     * sequence for retire alongside the swap.
     */
    fun forceRotateToHigherSequence(): ForcedRotationResult? {
        if (activeCidSequence >= retirePriorToWatermark) return null
        // Pick the smallest seq above the watermark — see
        // [tryStartValidation]'s rationale. LinkedHashMap is insertion-
        // ordered, not seq-ordered, so a peer that retransmits an old
        // offer can shift the "first" entry away from the actual
        // smallest.
        val (seq, entry) =
            unusedCids.entries
                .filter { it.key >= retirePriorToWatermark }
                .minByOrNull { it.key }
                ?: return ForcedRotationResult.NoSpareCid
        unusedCids.remove(seq)
        val priorSeq = activeCidSequence
        queueRetireSequence(priorSeq)
        activeCidSequence = seq

        // If we were mid-validation, the challenge we issued is
        // for a CID that may now be retired (priorSeq could be
        // the seq we were trying to validate). Abandon it.
        val s = state
        if (s is PathValidationState.Validating) {
            queueRetireSequence(s.newCidSequence)
            pendingChallenges.removeAll { it.contentEquals(s.challengeData) }
            state = PathValidationState.Failed
            failedValidations += 1
        }
        return ForcedRotationResult.Rotated(newSequence = seq, connectionId = entry.connectionId, retiredSequence = priorSeq)
    }

    sealed class ForcedRotationResult {
        /**
         * Watermark moved past active CID but the pool was empty —
         * the caller cannot satisfy the spec MUST. RFC 9000 §5.1.2
         * leaves the recovery to the caller; closing the connection
         * with CONNECTION_ID_LIMIT_ERROR is the safe default.
         */
        object NoSpareCid : ForcedRotationResult()

        data class Rotated(
            val newSequence: Long,
            val connectionId: ByteArray,
            val retiredSequence: Long,
        ) : ForcedRotationResult() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Rotated) return false
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
