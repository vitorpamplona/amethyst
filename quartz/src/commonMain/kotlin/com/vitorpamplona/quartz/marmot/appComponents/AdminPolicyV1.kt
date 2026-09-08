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

import com.vitorpamplona.quartz.marmot.mls.codec.TlsReader
import com.vitorpamplona.quartz.marmot.mls.codec.TlsWriter
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey

/**
 * `marmot.group.admin-policy.v1`, component `0x8003` — who may govern the group.
 *
 * ```text
 * struct { opaque xonly_pubkey[32]; } MarmotAdminKeyV1;
 * struct { MarmotAdminKeyV1 admins<V>; } MarmotAdminPolicyV1;
 * ```
 *
 * On the wire that is one var-bytes vector whose payload is `32 * n`
 * concatenated keys — the keys are fixed-size, so they carry no inner length
 * prefix of their own.
 *
 * Each entry is a Marmot ACCOUNT identity: the same raw 32-byte x-only key a
 * member carries as its MLS `BasicCredential` identity, not a separate
 * authorization key. That is what makes a multi-device account share a single
 * admin entry across all of its leaves.
 *
 * ## Active admins
 *
 * Listing a key is not sufficient. An account is an *active* admin when its key
 * is here AND it has at least one current member leaf, which is why
 * [isActiveAdmin] takes the group's member accounts rather than answering from
 * the component alone. Valid group state never lists an account with no leaf:
 * a commit that removes an account's last leaf must drop its key in the same
 * commit.
 *
 * ## What this component cannot do
 *
 * There is no succession mechanism. If every active admin loses its keys, the
 * group stays cryptographically valid and messages keep flowing, but every
 * admin-gated change is frozen permanently. Local policy MUST NOT elevate
 * another account to fill the gap — recovery means creating a new group.
 */
data class AdminPolicyV1(
    /** Sorted, unique, non-empty 32-byte x-only account keys. */
    val admins: List<ByteArray>,
) {
    init {
        require(admins.isNotEmpty()) { "admin policy must list at least one admin" }
        admins.forEach {
            require(it.size == KEY_SIZE) { "admin key must be $KEY_SIZE bytes, was ${it.size}" }
        }
        for (i in 1 until admins.size) {
            val order = compareKeys(admins[i - 1], admins[i])
            require(order != 0) { "admin policy contains a duplicate key" }
            require(order < 0) { "admin policy must be sorted lexicographically by key bytes" }
        }
    }

    val adminHexKeys: List<HexKey> get() = admins.map { it.toHexKey() }

    fun contains(accountIdentity: ByteArray): Boolean = admins.any { it.contentEquals(accountIdentity) }

    /**
     * True when [accountIdentity] is listed AND holds a current member leaf.
     *
     * [memberAccounts] is the set of MLS-authenticated account identities with
     * at least one leaf in the state being evaluated.
     */
    fun isActiveAdmin(
        accountIdentity: ByteArray,
        memberAccounts: Collection<ByteArray>,
    ): Boolean = contains(accountIdentity) && memberAccounts.any { it.contentEquals(accountIdentity) }

    fun encode(): ByteArray {
        val flat = ByteArray(admins.size * KEY_SIZE)
        admins.forEachIndexed { index, key -> key.copyInto(flat, index * KEY_SIZE) }
        val writer = TlsWriter()
        writer.putOpaqueVarInt(flat)
        return writer.toByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AdminPolicyV1) return false
        if (admins.size != other.admins.size) return false
        return admins.indices.all { admins[it].contentEquals(other.admins[it]) }
    }

    override fun hashCode(): Int = admins.fold(7) { acc, key -> 31 * acc + key.contentHashCode() }

    companion object {
        const val COMPONENT_ID = AppComponentIds.ADMIN_POLICY_V1
        const val KEY_SIZE = 32

        /** Build from arbitrary keys, sorting and de-duplicating. */
        fun of(keys: Collection<ByteArray>): AdminPolicyV1 {
            val unique = mutableListOf<ByteArray>()
            for (key in keys.sortedWith(::compareKeys)) {
                if (unique.lastOrNull()?.contentEquals(key) != true) unique.add(key)
            }
            return AdminPolicyV1(unique)
        }

        fun decode(bytes: ByteArray): AdminPolicyV1 {
            val reader = TlsReader(bytes)
            val flat = reader.readOpaqueVarInt()
            require(!reader.hasRemaining) { "admin policy component has trailing bytes" }
            require(flat.size % KEY_SIZE == 0) {
                "admin policy payload must be a whole number of $KEY_SIZE-byte keys, was ${flat.size} bytes"
            }
            val keys = (0 until flat.size / KEY_SIZE).map { flat.copyOfRange(it * KEY_SIZE, (it + 1) * KEY_SIZE) }
            // The constructor enforces sorted/unique/non-empty rather than
            // repairing them: unsorted bytes are invalid group state, not a
            // presentation detail to normalize away.
            return AdminPolicyV1(keys)
        }

        private fun compareKeys(
            a: ByteArray,
            b: ByteArray,
        ): Int {
            val common = minOf(a.size, b.size)
            for (i in 0 until common) {
                val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
                if (diff != 0) return diff
            }
            return a.size - b.size
        }
    }
}
