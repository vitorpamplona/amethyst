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
package com.vitorpamplona.quartz.concord.cord04Roles

import com.vitorpamplona.quartz.concord.crypto.ConcordKeyDerivation
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/** The opened `control_wrap` payload: the `control_root` delivered for [epoch]. */
class DeliveredControlRoot(
    val epoch: Long,
    val controlRoot: ByteArray,
)

/**
 * The staff write-key delivery riding a Grant (CORD-04 §3): a staff-making Grant
 * carries the current `control_root` in [GrantEntity.controlWrap], NIP-44-encrypted
 * under the granter↔member pairwise conversation key — one ECDH either side can
 * compute, so a NIP-46 bunker account opens it with a single `nip44Decrypt`.
 *
 * The plaintext is fixed-width, the rekey-blob discipline (CORD-06 §1):
 * `epoch_be[8] ‖ control_root[32]`, 40 bytes. The epoch rides *inside* the
 * ciphertext because staleness is structural — compaction re-wraps a Grant head
 * verbatim across Refoundings, so a folded head can carry a wrap minted for a
 * prior epoch's key. Harmless: the recipient adopts the secret only if it derives
 * to exactly the `control_pk` they hold for the named epoch ([derivesTo]), and any
 * mismatch is dropped, never adopted.
 */
object ControlRootWrap {
    /** `epoch_be[8] ‖ control_root[32]` */
    const val SIZE = 40

    fun encodePlaintext(
        epoch: Long,
        controlRoot: ByteArray,
    ): ByteArray {
        require(controlRoot.size == 32) { "controlRoot must be 32 bytes" }
        val out = ByteArray(SIZE)
        ConcordKeyDerivation.writeBe64(out, 0, epoch)
        controlRoot.copyInto(out, 8)
        return out
    }

    fun decodePlaintext(bytes: ByteArray): DeliveredControlRoot? {
        if (bytes.size != SIZE) return null
        var epoch = 0L
        for (i in 0 until 8) epoch = (epoch shl 8) or (bytes[i].toLong() and 0xFF)
        return DeliveredControlRoot(epoch, bytes.copyOfRange(8, SIZE))
    }

    /**
     * Builds the `control_wrap` value a staff-making Grant carries: the 40-byte
     * plaintext, base64'd, then NIP-44-encrypted by [granterSigner] to
     * [memberPubKey]. A staff-making edition MUST carry a wrap fresh for the
     * current [epoch].
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun build(
        granterSigner: NostrSigner,
        memberPubKey: HexKey,
        epoch: Long,
        controlRoot: ByteArray,
    ): String = granterSigner.nip44Encrypt(Base64.Default.encode(encodePlaintext(epoch, controlRoot)), memberPubKey)

    /**
     * Opens a received `control_wrap` with the member's own [memberSigner] against
     * the Grant edition's author ([granterPubKey]). Null on any failure — a garbage
     * wrap is attributable griefing, nothing worse. The caller MUST still gate
     * adoption on [derivesTo] against the `control_pk` it holds for the returned
     * epoch.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun openOrNull(
        controlWrap: String,
        memberSigner: NostrSigner,
        granterPubKey: HexKey,
    ): DeliveredControlRoot? =
        try {
            decodePlaintext(Base64.Default.decode(memberSigner.nip44Decrypt(controlWrap, granterPubKey)))
        } catch (_: Exception) {
            null
        }

    /**
     * The adoption check (CORD-04 §3): true when [controlRoot] derives to exactly
     * the [heldControlPk] this member holds for [epoch] (CORD-02 §5). A mismatch is
     * dropped, never adopted — the check fails closed.
     */
    fun derivesTo(
        controlRoot: ByteArray,
        communityId: ByteArray,
        epoch: Long,
        heldControlPk: HexKey,
    ): Boolean = ConcordKeyDerivation.controlSignerKey(controlRoot, communityId, epoch).publicKeyHex == heldControlPk.lowercase()
}
