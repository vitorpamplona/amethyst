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
package com.vitorpamplona.amethyst.commons.model.account.transfer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The plaintext payload of a device-to-device account transfer file.
 *
 * # What this is for
 *
 * Most of an Amethyst account already follows the user to a new phone on its
 * own: the profile, follow list, relay lists, mute list and the NIP-78
 * `AmethystSettings` blob all live on relays and come back the moment the key
 * is entered. What does NOT come back is everything the app only ever wrote to
 * the device — wallet connections, NUT-13 cashu counters, Tor settings, read
 * markers, dismissal sets. This bundle is that remainder, so a user replacing a
 * phone can carry it across in one file instead of rebuilding it by hand.
 *
 * # Why preferences are carried verbatim
 *
 * [AccountTransferEntry.preferences] is a straight copy of the per-account
 * preference file rather than a hand-written field list. A field list would
 * silently omit every setting added after it was written — exactly the failure
 * this feature exists to prevent — and would need editing in lockstep with
 * `AccountSettings`. Copying the map keeps new settings covered for free; the
 * few keys that must NOT travel are named in `AccountTransferKeys` instead, a
 * list that only grows when something genuinely device-bound appears.
 *
 * # Compatibility
 *
 * Decoding ignores unknown keys, so a bundle written by a newer build still
 * imports into an older one (minus what it doesn't understand). [version] gates
 * the reverse case: a bundle whose version exceeds [CURRENT_VERSION] is
 * rejected outright rather than half-applied.
 */
@Serializable
data class AccountTransferBundle(
    val version: Int = CURRENT_VERSION,
    /** Unix seconds the bundle was written. Shown to the user before importing. */
    val createdAt: Long = 0,
    /** Human-readable app version that wrote it, for support/debugging. */
    val appVersion: String? = null,
    val accounts: List<AccountTransferEntry> = emptyList(),
    /**
     * The app-wide ENCRYPTED preference file, minus the account registry
     * ([AccountTransferKeys.EXCLUDED_GLOBAL_KEYS]). Read through its decrypting
     * accessor rather than copied as bytes: it is sealed with a Keystore master
     * key that never leaves the device, so the plaintext is what has to travel.
     */
    val globalPreferences: Map<String, TransferValue> = emptyMap(),
    /**
     * App-wide plain SharedPreferences files, keyed by file name (no `.xml`).
     * Same verbatim-copy reasoning as [AccountTransferEntry.preferences].
     */
    val sharedPreferences: Map<String, Map<String, TransferValue>> = emptyMap(),
    /**
     * Whole files copied verbatim, keyed by path relative to the app's files
     * dir, with Base64 content. This is where most of the app lives outside the
     * account files: UI settings, Tor, favorites, browser history, napplet
     * grants and storage, per-relay AUTH decisions, connected-app signer
     * permissions, scheduled posts.
     *
     * Copied as bytes rather than read through the DataStore API on purpose.
     * DataStore permits only one active instance per file per process and throws
     * if a second one appears, so an exporter that opened its own handle to a
     * store the app already had open would crash the export. Reading bytes is
     * also safe against a concurrent write: DataStore commits by writing a temp
     * file and renaming, so a reader sees either the whole old file or the whole
     * new one, never a torn one.
     */
    val files: Map<String, String> = emptyMap(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class AccountTransferEntry(
    val npub: String,
    /**
     * Present only when the user explicitly opted into including secret keys.
     * Null means the account still imports — settings and all — but the new
     * phone asks for the key (or re-pairs the external signer) on first login.
     */
    val privKeyHex: String? = null,
    /** NIP-55 external signer package, when the account signs through one. */
    val externalSignerPackageName: String? = null,
    /**
     * The account's preference file, verbatim, minus [AccountTransferKeys.EXCLUDED].
     */
    val preferences: Map<String, TransferValue> = emptyMap(),
    /**
     * NUT-13 deterministic-secret counters, keyset id -> next free counter.
     *
     * Carried separately because they live in their own synchronously-committed
     * store, and because they are the one entry here that is unsafe to lose:
     * a fresh phone restarting a keyset at zero re-derives blinded messages the
     * mint has already signed, which the mint rejects ("outputs already
     * signed"), stranding that keyset's balance. The importer merges by max —
     * see [mergeCounters].
     */
    val cashuKeysetCounters: Map<String, Long> = emptyMap(),
)

/**
 * A single preference value, in the shape the platform preference stores use.
 *
 * Modeled as a closed hierarchy rather than a raw JSON element so a value that
 * round-trips through the file lands back in the store with the SAME type it
 * left with. A `Long` read back as an `Int` (or a string set as a list) throws
 * a ClassCastException deep inside whichever feature reads it, long after the
 * import looked successful.
 */
@Serializable
sealed class TransferValue {
    @Serializable
    @SerialName("s")
    data class Str(
        val v: String,
    ) : TransferValue()

    @Serializable
    @SerialName("b")
    data class Bool(
        val v: Boolean,
    ) : TransferValue()

    @Serializable
    @SerialName("i")
    data class Int32(
        val v: Int,
    ) : TransferValue()

    @Serializable
    @SerialName("l")
    data class Int64(
        val v: Long,
    ) : TransferValue()

    @Serializable
    @SerialName("f")
    data class Flt(
        val v: Float,
    ) : TransferValue()

    /** DataStore preferences can hold a Double; SharedPreferences cannot. */
    @Serializable
    @SerialName("d")
    data class Dbl(
        val v: Double,
    ) : TransferValue()

    /**
     * A string set. Serialized as a list because JSON has no set type; the
     * importer converts back to a set, and the exporter sorts so the same
     * preferences always produce the same bytes.
     */
    @Serializable
    @SerialName("ss")
    data class StrSet(
        val v: List<String>,
    ) : TransferValue()
}

/**
 * Merge NUT-13 counters from an imported bundle into whatever this device
 * already has, keeping the HIGHER value per keyset.
 *
 * Never moves a counter backwards. A counter only ever means "the next index
 * the mint has not signed yet", so the larger of two observations is the safe
 * one: too high wastes a few unused indexes, too low re-derives an already
 * signed output and breaks the keyset.
 */
fun mergeCounters(
    current: Map<String, Long>,
    imported: Map<String, Long>,
): Map<String, Long> {
    if (imported.isEmpty()) return current
    val merged = current.toMutableMap()
    imported.forEach { (keysetId, counter) ->
        val existing = merged[keysetId]
        if (existing == null || counter > existing) {
            merged[keysetId] = counter
        }
    }
    return merged
}
