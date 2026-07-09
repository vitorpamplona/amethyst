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
package com.vitorpamplona.quartz.concord.crypto

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf
import com.vitorpamplona.quartz.utils.RandomInstance
import com.vitorpamplona.quartz.utils.Secp256k1Instance
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * Concord key derivation (CORD-02 Appendix A, CORD-03, CORD-06, CORD-07).
 *
 * Everything a Concord community needs is derived deterministically from a small
 * number of secrets so that "only holders of the secret can derive a plane's
 * address, and only members can produce events at it." All of this is pinned to
 * the Concord v2 reference client (Armada) for wire interoperability — see
 * [ConcordLabels] for the frozen label strings.
 *
 * The core primitive is [groupKey]. Its info layout is
 * `utf8(label) ‖ 0x00 ‖ id[32]? ‖ epoch_be8?` fed into `HKDF-SHA256(ikm=secret,
 * salt=zeros(32), info, L=32)`, with a counter-byte retry ([deriveSecretKey]) on
 * the astronomically rare chance the 32-byte output is not a valid secp256k1
 * scalar.
 */
object ConcordKeyDerivation {
    private val hkdf = Hkdf()

    /** RFC 5869 "salt not provided" — HashLen (32) zero bytes. */
    private val ZERO_SALT = ByteArray(32)

    /**
     * Builds the HKDF `info` for a plane/channel derivation:
     * `utf8(label) ‖ 0x00 ‖ id[32]? ‖ epoch_be8?`.
     *
     * [id] is appended verbatim when present (32 bytes for community/channel ids,
     * or `sha256(identity)` for voice-sender keys). [epoch] is appended as a
     * big-endian unsigned 64-bit integer when present, and omitted otherwise.
     */
    fun buildInfo(
        label: String,
        id: ByteArray? = null,
        epoch: Long? = null,
    ): ByteArray {
        val labelBytes = label.encodeToByteArray()
        val idLen = id?.size ?: 0
        val epochLen = if (epoch != null) 8 else 0
        val out = ByteArray(labelBytes.size + 1 + idLen + epochLen)
        var pos = 0
        labelBytes.copyInto(out, pos)
        pos += labelBytes.size
        out[pos] = 0x00
        pos += 1
        if (id != null) {
            id.copyInto(out, pos)
            pos += id.size
        }
        if (epoch != null) {
            writeBe64(out, pos, epoch)
        }
        return out
    }

    /** `HKDF-SHA256(ikm=secret, salt=zeros(32), info, L=32)` — a raw 32-byte output. */
    fun hkdf32(
        secret: ByteArray,
        info: ByteArray,
    ): ByteArray {
        val prk = hkdf.extract(secret, ZERO_SALT)
        return hkdf.expand(prk, info, 32)
    }

    /**
     * Derives a valid secp256k1 secret key from [secret] and [info].
     *
     * Runs [hkdf32]; if the result is not a valid scalar (0 or ≥ curve order —
     * probability ≈ 2⁻¹²⁸), appends an incrementing counter byte (0…255) to the
     * info and retries, matching the reference client's deterministic fallback.
     */
    fun deriveSecretKey(
        secret: ByteArray,
        info: ByteArray,
    ): ByteArray {
        val first = hkdf32(secret, info)
        if (Secp256k1Instance.isPrivateKeyValid(first)) return first

        val extended = ByteArray(info.size + 1)
        info.copyInto(extended)
        var counter = 0
        while (counter <= 255) {
            extended[info.size] = counter.toByte()
            val candidate = hkdf32(secret, extended)
            if (Secp256k1Instance.isPrivateKeyValid(candidate)) return candidate
            counter++
        }
        throw IllegalStateException("Unable to derive a valid secp256k1 scalar for the given secret/info")
    }

    /**
     * The core Concord derivation: turns a shared [secret] into a plane/channel
     * [GroupKey] (secret key, x-only public address, and self-ECDH NIP-44
     * conversation key) at the given [id] and [epoch].
     */
    fun groupKey(
        label: String,
        secret: ByteArray,
        id: ByteArray? = null,
        epoch: Long? = null,
    ): GroupKey {
        val sk = deriveSecretKey(secret, buildInfo(label, id, epoch))
        val keyPair = KeyPair(privKey = sk)
        val conversationKey = Nip44.v2.getConversationKey(sk, keyPair.pubKey)
        return GroupKey(sk, keyPair.pubKey, conversationKey)
    }

    /**
     * The permanent, self-certifying community id (CORD-02):
     * `sha256("concord/community" ‖ owner_xonly ‖ owner_salt)`.
     *
     * A plain SHA-256 commitment (not HKDF-shaped), so a bundle can never smuggle
     * a false owner or a fake key for a real community.
     */
    fun communityId(
        ownerXOnly: ByteArray,
        ownerSalt: ByteArray,
    ): ByteArray {
        val prefix = ConcordLabels.COMMUNITY.encodeToByteArray()
        val preimage = ByteArray(prefix.size + ownerXOnly.size + ownerSalt.size)
        prefix.copyInto(preimage, 0)
        ownerXOnly.copyInto(preimage, prefix.size)
        ownerSalt.copyInto(preimage, prefix.size + ownerXOnly.size)
        return sha256(preimage)
    }

    /** Fresh 32-byte owner salt, generated once at community creation (CORD-02). */
    fun newOwnerSalt(): ByteArray = RandomInstance.bytes(32)

    // ---- CORD-07 voice keys (all ride the Channel's epoch) --------------------

    /** Voice signer keypair; its x-only public key is the SFU room name (CORD-07 §1). */
    fun voiceSignerKey(
        channelSecret: ByteArray,
        channelId: ByteArray,
        epoch: Long,
    ): GroupKey = groupKey(ConcordLabels.VOICE_SIGNER, channelSecret, channelId, epoch)

    /** 32-byte voice media root; per-sender frame keys derive from it (CORD-07 §1). */
    fun voiceMediaKey(
        channelSecret: ByteArray,
        channelId: ByteArray,
        epoch: Long,
    ): ByteArray = hkdf32(channelSecret, buildInfo(ConcordLabels.VOICE_MEDIA, channelId, epoch))

    /**
     * Per-sender voice frame key (CORD-07 §3):
     * `hkdf32(voice_media_key, "concord/voice-sender" ‖ 0x00 ‖ sha256(utf8(identity)))`.
     * There is no epoch field — the media key already rides the epoch.
     */
    fun voiceSenderKey(
        voiceMediaKey: ByteArray,
        identity: String,
    ): ByteArray = hkdf32(voiceMediaKey, buildInfo(ConcordLabels.VOICE_SENDER, sha256(identity.encodeToByteArray())))

    // ---- CORD-06 rekey locator ------------------------------------------------

    /**
     * Recipient locator / pseudonym for a rekey blob (CORD-06 §2). Derived purely
     * from public inputs (`rotator_xonly ‖ recipient_xonly` as IKM), so bunker
     * accounts can locate their blob without touching raw keys.
     */
    fun recipientLocator(
        rotatorXOnly: ByteArray,
        recipientXOnly: ByteArray,
        scopeId: ByteArray,
        epoch: Long,
    ): ByteArray {
        val ikm = ByteArray(rotatorXOnly.size + recipientXOnly.size)
        rotatorXOnly.copyInto(ikm, 0)
        recipientXOnly.copyInto(ikm, rotatorXOnly.size)
        return hkdf32(ikm, buildInfo(ConcordLabels.RECIPIENT_PSEUDONYM, scopeId, epoch))
    }

    /** Writes [value] as a big-endian unsigned 64-bit integer into [out] at [offset]. */
    internal fun writeBe64(
        out: ByteArray,
        offset: Int,
        value: Long,
    ) {
        for (i in 0 until 8) {
            out[offset + i] = (value ushr (8 * (7 - i))).toByte()
        }
    }
}
