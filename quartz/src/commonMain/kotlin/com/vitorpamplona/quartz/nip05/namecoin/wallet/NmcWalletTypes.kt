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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import kotlinx.serialization.Serializable

@Serializable
data class NmcBalance(
    val confirmed: Long = 0L,
    val unconfirmed: Long = 0L,
) {
    val totalSatoshis: Long get() = confirmed + unconfirmed
    val totalNmc: Double get() = totalSatoshis / 100_000_000.0
    val confirmedNmc: Double get() = confirmed / 100_000_000.0
    val unconfirmedNmc: Double get() = unconfirmed / 100_000_000.0
    val isEmpty: Boolean get() = confirmed == 0L && unconfirmed == 0L
}

@Serializable
data class NmcUtxo(
    val txHash: String,
    val txPos: Int,
    val height: Int,
    val value: Long,
) {
    val valueNmc: Double get() = value / 100_000_000.0
}

@Serializable
data class NmcHistoryEntry(
    val txHash: String,
    val height: Int,
    val fee: Long = 0L,
)

@Serializable
data class OwnedName(
    val name: String,
    val value: String,
    val txid: String,
    val height: Int,
    val expiresIn: Int,
) {
    val namespace: String get() = name.substringBefore("/")
    val shortName: String get() = name.substringAfter("/")
    val daysRemaining: Int get() = expiresIn / 144
    val isExpiringSoon: Boolean get() = expiresIn in 1..4320
    val isExpired: Boolean get() = expiresIn <= 0
    val displayDomain: String
        get() =
            when {
                name.startsWith("d/") -> "$shortName.bit"
                name.startsWith("id/") -> "$shortName.nmc"
                else -> name
            }
}

sealed class NameAvailability {
    data object Available : NameAvailability()

    data class Taken(
        val currentValue: String,
        val expiresIn: Int,
    ) : NameAvailability()

    data class Expired(
        val lastValue: String,
    ) : NameAvailability()

    data class Error(
        val message: String,
    ) : NameAvailability()
}

/**
 * A derived receive address at a BIP44 index.
 */
@Serializable
data class DerivedAddress(
    val index: Int,
    val address: String,
    val pubKeyHex: String,
    val isPrimary: Boolean = false,
)

/**
 * Full details of a registered name, used for update/transfer operations.
 */
@Serializable
data class NameDetails(
    val name: String,
    val value: String,
    val txid: String,
    val vout: Int,
    val height: Int,
    val expiresIn: Int,
) {
    val daysRemaining: Int get() = expiresIn / 144
    val isExpiringSoon: Boolean get() = expiresIn in 1..4320
}

/**
 * Wallet settings persisted across sessions.
 */
@Serializable
data class NmcWalletSettings(
    val defaultAddressType: String = "P2WPKH",
    val multisigConfigs: List<String> = emptyList(),
    val cosignerPaths: List<String> = emptyList(),
)

/**
 * Pending name registration: the user has broadcast NAME_NEW and
 * is waiting 12+ blocks before broadcasting NAME_FIRSTUPDATE.
 */
@Serializable
data class PendingNameRegistration(
    val name: String,
    val saltHex: String,
    val nameNewTxid: String,
    val nameNewHeight: Int,
    val proposedValue: String,
    val timestampSecs: Long,
) {
    /** Blocks elapsed since NAME_NEW (caller provides current height). */
    fun blocksElapsed(currentHeight: Int): Int = currentHeight - nameNewHeight

    /** Whether enough blocks have passed for NAME_FIRSTUPDATE (need ≥12). */
    fun isReadyForFirstUpdate(currentHeight: Int): Boolean = blocksElapsed(currentHeight) >= 12
}
