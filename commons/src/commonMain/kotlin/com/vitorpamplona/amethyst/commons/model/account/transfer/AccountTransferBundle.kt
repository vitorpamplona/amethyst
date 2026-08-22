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

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

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
     * The account's secret key. Always carried for an account that holds one:
     * the file exists to move an account, and an account without its key is not
     * moved — the user would land with their settings and no way to post.
     *
     * Null means the account has no key HERE to carry, i.e. it signs through an
     * external app (NIP-55). Those import fine and reconnect on the new phone;
     * see [externalSignerPackageName].
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
    /**
     * Marmot (MLS) conversation history, group id -> decrypted inner event JSON
     * in append order.
     *
     * The ARCHIVE only. The group's MLS state — ratchet, retained epoch secrets,
     * key package bundles — deliberately stays on the old device: cloning a
     * ratchet onto a second device is what MLS's forward secrecy is built to
     * prevent, and two devices sharing one leaf can reuse a key at the same
     * epoch. So the new phone rejoins as a new member for future messages,
     * which by design cannot decrypt anything sent before it joined — this
     * archive is the only way the past conversation survives the move.
     *
     * TRUSTED EXACTLY AS MUCH AS THE FILE. These are MIP-03 inner events, which
     * are unsigned rumors; their authenticity came from the MLS credential check
     * at decrypt time and cannot be re-established afterwards. Restoring them is
     * therefore a guarded action — a bundle can otherwise fabricate a whole
     * conversation attributed to anyone.
     */
    val marmotMessages: Map<String, List<String>> = emptyMap(),
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
 * True when [npub] is a canonical npub this device is willing to create an
 * account record for.
 *
 * A bundle is untrusted input, and its npub is used unchecked as part of a
 * preference FILE NAME and as the identity of a saved account. Round-tripping
 * (decode, re-encode, compare) is the check rather than a prefix test, because
 * only that rejects every non-canonical string — including the empty one, which
 * is the dangerous case: account deletion matches preference files by
 * `name.contains(npub)`, so an account saved under "" would match every file on
 * disk and take every other account's data with it when removed.
 */
fun isWellFormedNpub(npub: String): Boolean =
    try {
        val hex = (Nip19Parser.uriToRoute(npub)?.entity as? NPub)?.hex
        hex != null && hex.hexToByteArray().toNpub() == npub
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }

/**
 * True when [AccountTransferEntry.privKeyHex] actually belongs to
 * [AccountTransferEntry.npub].
 *
 * An entry pairing one account's npub with a different account's key is not
 * something a real export produces, and honouring it is destructive: the key is
 * stored under that npub's file, and `KeyPair` derives the pubkey FROM the
 * private key, so the account silently becomes whoever owns the key while still
 * listed under the original npub. Since npubs are public, a bundle can name any
 * victim.
 */
fun AccountTransferEntry.keyMatchesNpub(): Boolean {
    val hex = privKeyHex ?: return true

    return try {
        Nip01Crypto.pubKeyCreate(hex.hexToByteArray()).toNpub() == npub
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }
}

/**
 * The largest NUT-13 counter an import may carry.
 *
 * Counters become BIP32 indexes, which are only valid below 2^31, and
 * [mergeCounters] never moves one backwards — so a bundle naming an absurd value
 * would permanently disable that keyset on this device with no way back through
 * the UI. Rejecting is better than clamping: clamping to the ceiling bricks it
 * just as thoroughly.
 */
const val MAX_IMPORTABLE_CASHU_COUNTER = 1L shl 31

/**
 * Per-group ceiling on archived Marmot messages, both directions.
 *
 * Bounds the file for a real export and, more to the point, bounds what an
 * imported bundle can push into the cache in one go.
 */
const val MAX_ARCHIVED_MESSAGES_PER_GROUP = 20_000

/** Drops counters no legitimate wallet would have reached. See [MAX_IMPORTABLE_CASHU_COUNTER]. */
fun sanitizeCounters(counters: Map<String, Long>) = counters.filterValues { it in 0 until MAX_IMPORTABLE_CASHU_COUNTER }

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
