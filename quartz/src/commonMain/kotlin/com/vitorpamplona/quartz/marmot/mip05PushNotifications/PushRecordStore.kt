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
package com.vitorpamplona.quartz.marmot.mip05PushNotifications

import com.vitorpamplona.quartz.nip01Core.core.HexKey

/**
 * One group's push token records: which token is active per record key, and
 * which keys carry a tombstone (`features/push-notifications.md`, "Record
 * state").
 *
 * ## What it is not
 *
 * Never group state. Nothing here can reject, delay or reorder a group message,
 * and no MLS decision may read it. It is local push routing that two members'
 * clients happen to converge on.
 *
 * ## Why a tombstone has to be durable
 *
 * Owner authentication makes a record relay-portable: any current member can
 * re-emit another member's still-valid signed record inside a fresh kind `448`
 * at any later epoch. So a stale record's carrying epoch is unbounded, and the
 * retained app-payload window cannot bound it. The per-key stamp and tombstone
 * are the ONLY durable high-water marks that stop a revoked token from being
 * resurrected by a relay, which is why they are cleared on exactly two events —
 * a strictly-greater-stamped entry, or the owning leaf leaving the group — and
 * never on a wall clock, an `owner_ts`, or an epoch count.
 */
class PushRecordStore(
    val groupIdHex: HexKey,
    /**
     * Whether this group requires `0x8009`. It selects which owner-proof forms
     * are acceptable, so it must come from the group's GroupContext rather than
     * from anything a sender says.
     */
    val currentProfileGroup: Boolean,
) {
    private val records = mutableMapOf<PushRecordKey, PushTokenEntry>()
    private val stamps = mutableMapOf<PushRecordKey, PushRecordStamp>()
    private val tombstones = mutableSetOf<PushRecordKey>()

    /** Every active token record, in no particular order. */
    fun active(): List<PushTokenEntry> = records.values.toList()

    fun activeFor(key: PushRecordKey): PushTokenEntry? = records[key]

    /** The high-water mark for a key, whether it currently holds a record or a tombstone. */
    fun stampFor(key: PushRecordKey): PushRecordStamp? = stamps[key]

    fun isTombstoned(key: PushRecordKey): Boolean = key in tombstones

    /** Restore a persisted store. Stamps and tombstones survive restarts or the guarantee is gone. */
    fun restore(
        activeRecords: Collection<PushTokenEntry>,
        persistedStamps: Map<PushRecordKey, PushRecordStamp>,
        persistedTombstones: Collection<PushRecordKey>,
    ) {
        records.clear()
        stamps.clear()
        tombstones.clear()
        activeRecords.forEach { records[it.key] = it }
        stamps.putAll(persistedStamps)
        tombstones.addAll(persistedTombstones)
    }

    fun snapshotStamps(): Map<PushRecordKey, PushRecordStamp> = stamps.toMap()

    fun snapshotTombstones(): Set<PushRecordKey> = tombstones.toSet()

    /**
     * Apply the entries of one kind `447`/`448` event.
     *
     * [isCurrentMember] is asked per member id because a record's authority
     * comes from `owner_sig` plus current membership — never from who carried
     * it. A verified entry is applied even when the sender is not its owner,
     * and an entry naming a non-member is dropped even when it verifies.
     *
     * @return the keys whose stored record changed.
     */
    fun applyTokens(
        entries: List<PushTokenEntry>,
        nowMillis: Long,
        isCurrentMember: (HexKey) -> Boolean,
    ): Set<PushRecordKey> {
        val changed = mutableSetOf<PushRecordKey>()
        entries.forEach { entry ->
            if (entry.ownerTsMillis > nowMillis + PushGossip.OWNER_TS_MAX_FUTURE_MILLIS) return@forEach
            if (!isCurrentMember(entry.memberIdHex)) return@forEach
            if (!entry.verifyOwner(groupIdHex, currentProfileGroup)) return@forEach

            val key = entry.key
            val stamp = entry.stamp(groupIdHex)
            // Strictly greater, so re-applying the same signed record is a
            // no-op whether it arrives fresh or relayed. Array position is not a
            // tie-breaker: the highest stamp wins wherever it sits.
            if (!stamp.wins(stamps[key])) return@forEach

            records[key] = entry
            stamps[key] = stamp
            tombstones.remove(key)
            changed.add(key)
        }
        return changed
    }

    /**
     * Apply the entries of one kind `449` event.
     *
     * A removal that wins its key does two things: it deletes the record AND
     * writes a tombstone at the removal's own stamp, so a token list assembled
     * before the removal cannot bring the revoked token back.
     *
     * @return the keys whose stored record changed.
     */
    fun applyRemovals(
        entries: List<PushRemovalEntry>,
        nowMillis: Long,
        isCurrentMember: (HexKey) -> Boolean,
    ): Set<PushRecordKey> {
        val changed = mutableSetOf<PushRecordKey>()
        entries.forEach { entry ->
            if (entry.ownerTsMillis > nowMillis + PushGossip.OWNER_TS_MAX_FUTURE_MILLIS) return@forEach
            if (!isCurrentMember(entry.memberIdHex)) return@forEach
            if (!entry.verifyOwner(groupIdHex, currentProfileGroup)) return@forEach

            val key = entry.key
            val stamp = entry.stamp(groupIdHex)
            if (!stamp.wins(stamps[key])) return@forEach

            records.remove(key)
            stamps[key] = stamp
            tombstones.add(key)
            changed.add(key)
        }
        return changed
    }

    /**
     * Forget a leaf an accepted Commit removed.
     *
     * The whole key — record, stamp and tombstone — goes, because the leaf can
     * no longer be a current member and nothing it signed can be applied again.
     * A sibling leaf of the same account keeps its own records: they are
     * different keys and the account is still in the group.
     */
    fun forgetLeaf(
        memberIdHex: HexKey,
        leafIndex: Int,
    ) {
        val doomed = (records.keys + stamps.keys + tombstones).filter { it.memberIdHex == memberIdHex && it.leafIndex == leafIndex }
        doomed.forEach {
            records.remove(it)
            stamps.remove(it)
            tombstones.remove(it)
        }
    }

    private fun PushRecordStamp.wins(previous: PushRecordStamp?): Boolean = previous == null || this > previous
}
