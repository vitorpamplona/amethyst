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
package com.vitorpamplona.quartz.concord.envelope

import com.vitorpamplona.quartz.concord.crypto.ControlPlaneKeys
import com.vitorpamplona.quartz.concord.crypto.GroupKey
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.crypto.verifyId
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * The Concord stream envelope (CORD-01): a three-layer wrap → seal → rumor that
 * carries every plane's traffic on Nostr.
 *
 * This is a deliberate **inversion** of NIP-59: the outer wrap is signed by the
 * shared *stream key* (a plane's [GroupKey]) and carries an ephemeral `["p", …]`
 * tag, rather than being signed by a random key and addressed to a fixed
 * recipient. Because the true author's rumor is only ever visible after
 * decrypting under the stream conversation key, a relay can never retain or
 * display the plaintext as a public event.
 *
 * ```
 * kind 1059/21059 wrap          signed by stream key, content = NIP-44(seal, streamConvKey)
 *   └─ kind 20013/20014 seal    signed by the real author
 *        └─ rumor               unsigned author event (kind 9, 3308, 3306, …)
 * ```
 *
 * Two seal flavors (CORD-01 §Encryption):
 *  - **Plaintext seal (20014)** — `content` is the rumor JSON verbatim. Required
 *    by the Control Plane so an author's signature survives re-encryption across
 *    epochs (the exact bytes must be preserved).
 *  - **Encrypted seal (20013)** — `content` is the rumor JSON NIP-44-encrypted
 *    under the same stream conversation key, hiding it twice over. Used by every
 *    plane that never crosses an epoch or re-seeds with fresh attestations.
 *
 * All of this is pinned to the Concord v2 reference client for wire interop.
 */
object ConcordStreamEnvelope {
    const val KIND_WRAP = 1059
    const val KIND_WRAP_EPHEMERAL = 21059
    const val KIND_SEAL_ENCRYPTED = 20013
    const val KIND_SEAL_PLAINTEXT = 20014

    /**
     * Seals [rumor] for the [stream] plane, signed by [authorSigner] (the real
     * author's key). [encrypted] selects a 20013 encrypted seal; otherwise a
     * 20014 plaintext seal. The seal inherits the rumor's `created_at`.
     */
    suspend fun seal(
        rumor: Event,
        stream: GroupKey,
        authorSigner: NostrSigner,
        encrypted: Boolean,
    ): Event {
        val content =
            if (encrypted) {
                encryptChecked(rumor.toJson(), stream.conversationKey)
            } else {
                rumor.toJson()
            }
        val kind = if (encrypted) KIND_SEAL_ENCRYPTED else KIND_SEAL_PLAINTEXT
        return authorSigner.sign(rumor.createdAt, kind, EMPTY_TAGS, content)
    }

    /**
     * Wraps an already-built [seal] into a stream event at the [stream] plane's
     * address, signed by the stream key and encrypted under its conversation key.
     * Adds a fresh ephemeral `["p", …]` tag. Use [KIND_WRAP_EPHEMERAL] via
     * [ephemeral] for transient traffic (typing, voice presence).
     *
     * [outerTags] are appended after the `p` tag. The only sanctioned one is the CORD-08 §2
     * `["expiration", …]` that a disappearing Chat rumor's wrap repeats for NIP-40 relays
     * ([com.vitorpamplona.quartz.concord.cord03Channels.ConcordDisappearing.wrapTagsFor]).
     */
    fun wrapSeal(
        seal: Event,
        stream: GroupKey,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
        outerTags: Array<Array<String>> = EMPTY_TAGS,
    ): Event = wrapSeal(seal, stream, stream.conversationKey, ephemeral, createdAt, outerTags)

    /**
     * Write-restricted variant (CORD-01, Write-Restricted Streams): the wrap is
     * signed by [signerKey] (its pk the stream address, its sk held by the writers
     * alone) while the content is encrypted under [readConversationKey], the second
     * shared key the full readership holds. Concord's Control Plane wraps this way
     * on a split epoch (CORD-02 §5).
     */
    fun wrapSeal(
        seal: Event,
        signerKey: GroupKey,
        readConversationKey: ByteArray,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
        outerTags: Array<Array<String>> = EMPTY_TAGS,
    ): Event {
        val streamSigner = NostrSignerSync(KeyPair(privKey = signerKey.secretKey))
        val content = encryptChecked(seal.toJson(), readConversationKey)
        val ephemeralP = KeyPair().pubKey.toHexKey()
        val kind = if (ephemeral) KIND_WRAP_EPHEMERAL else KIND_WRAP
        return streamSigner.signNormal(createdAt, kind, arrayOf(arrayOf("p", ephemeralP)) + outerTags, content)
    }

    /**
     * Wraps [seal] onto the Control Plane described by [keys]: signed by its signer
     * (which the holder must have — throws when [ControlPlaneKeys.canWrite] is
     * false), encrypted under its read key. On a legacy epoch signer == read key
     * and this is the classic single-key wrap.
     */
    fun wrapSeal(
        seal: Event,
        keys: ControlPlaneKeys,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
    ): Event {
        val signer = requireNotNull(keys.signer) { "This account cannot write to the Control Plane: control_root not held (CORD-02 §2)" }
        return wrapSeal(seal, signer, keys.readKey.conversationKey, ephemeral, createdAt)
    }

    /** Convenience: [seal] then [wrapSeal] in one call. [outerTags] ride the wrap after its `p` tag. */
    suspend fun wrap(
        rumor: Event,
        stream: GroupKey,
        authorSigner: NostrSigner,
        encrypted: Boolean,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
        outerTags: Array<Array<String>> = EMPTY_TAGS,
    ): Event = wrapSeal(seal(rumor, stream, authorSigner, encrypted), stream, ephemeral, createdAt, outerTags)

    /**
     * Convenience for the Control Plane: seals under [keys]' read key (an encrypted
     * seal's rumor must decrypt for every reader, not only writers) and wraps with
     * its signer. Throws when the account cannot write (see [wrapSeal]).
     */
    suspend fun wrap(
        rumor: Event,
        keys: ControlPlaneKeys,
        authorSigner: NostrSigner,
        encrypted: Boolean,
        ephemeral: Boolean = false,
        createdAt: Long = TimeUtils.now(),
    ): Event = wrapSeal(seal(rumor, keys.readKey, authorSigner, encrypted), keys, ephemeral, createdAt)

    /**
     * Opens a stream [wrap] for the [stream] plane and returns the verified author
     * rumor, or throws if any layer fails to validate:
     *  1. `wrap.pubkey` must equal the stream address, and the wrap must be signed
     *     by the stream key.
     *  2. `wrap.content` decrypts under the stream conversation key into a seal
     *     whose own signature must verify against `seal.pubkey`.
     *  3. For a 20013 seal the rumor decrypts under the same conversation key; a
     *     20014 seal carries it verbatim.
     *  4. The rumor's author must equal the seal's author (no impersonation) and
     *     its `id` must be the correct NIP-01 event hash.
     */
    fun open(
        wrap: Event,
        stream: GroupKey,
    ): OpenedStreamEvent = open(wrap, stream.publicKeyHex, stream.conversationKey)

    /**
     * Write-restricted variant (CORD-01, Write-Restricted Streams): opening takes
     * only the stream [address] (the writers' pubkey, held by every reader) and the
     * [readConversationKey] — never the signer's secret. `wrap.verify()` checks the
     * signature against `wrap.pubkey`, which the address equality pins to the
     * writers' key, so a wrap minted by anyone else fails here. A verifying wrap
     * proves only that *a* writer published it; the seal's actor stays the sole
     * authority (CORD-04).
     */
    fun open(
        wrap: Event,
        address: HexKey,
        readConversationKey: ByteArray,
    ): OpenedStreamEvent {
        require(wrap.kind == KIND_WRAP || wrap.kind == KIND_WRAP_EPHEMERAL) {
            "Not a Concord stream wrap: kind ${wrap.kind}"
        }
        require(wrap.pubKey == address) {
            "Wrap author ${wrap.pubKey} is not the stream address $address"
        }
        require(wrap.verify()) { "Wrap signature/id is invalid" }

        val seal = Event.fromJson(decryptChecked(wrap.content, readConversationKey))
        require(seal.kind == KIND_SEAL_ENCRYPTED || seal.kind == KIND_SEAL_PLAINTEXT) {
            "Not a Concord seal: kind ${seal.kind}"
        }
        require(seal.verify()) { "Seal signature/id is invalid" }

        val rumorJson =
            if (seal.kind == KIND_SEAL_ENCRYPTED) {
                decryptChecked(seal.content, readConversationKey)
            } else {
                seal.content
            }

        val rumor = Event.fromJson(rumorJson)
        require(rumor.pubKey == seal.pubKey) {
            "Rumor author ${rumor.pubKey} does not match seal author ${seal.pubKey}"
        }
        require(rumor.verifyId()) { "Rumor id ${rumor.id} is not its NIP-01 hash" }

        return OpenedStreamEvent(rumor, seal.kind, seal.pubKey, seal)
    }

    /**
     * Opens a Control Plane wrap with [keys] (split or legacy): verified against the
     * plane's address, decrypted under its read key. Needs no write key, so a regular
     * member reads exactly as staff does (CORD-02 §5).
     */
    fun open(
        wrap: Event,
        keys: ControlPlaneKeys,
    ): OpenedStreamEvent = open(wrap, keys.address, keys.readKey.conversationKey)

    /** Like [open] but returns null instead of throwing on any validation failure. */
    fun openOrNull(
        wrap: Event,
        stream: GroupKey,
    ): OpenedStreamEvent? = openOrNull(wrap, stream.publicKeyHex, stream.conversationKey)

    /** Like the write-restricted [open] but returns null instead of throwing. */
    fun openOrNull(
        wrap: Event,
        address: HexKey,
        readConversationKey: ByteArray,
    ): OpenedStreamEvent? =
        try {
            open(wrap, address, readConversationKey)
        } catch (_: Exception) {
            null
        }

    /** Opens a Control Plane wrap with [keys] (split or legacy), or null on failure. */
    fun openOrNull(
        wrap: Event,
        keys: ControlPlaneKeys,
    ): OpenedStreamEvent? = openOrNull(wrap, keys.address, keys.readKey.conversationKey)

    private val EMPTY_TAGS = emptyArray<Array<String>>()

    /**
     * NIP-44's hard plaintext cap (CORD-02 Appendix B). Every layer of a Concord event is a NIP-44
     * plaintext, and the spec makes enforcing the cap each implementation's job: quartz's NIP-44
     * silently switches to its extended (u32-prefixed) format past it, which strict readers —
     * the reference client among them — cannot decrypt.
     */
    const val NIP44_MAX_PLAINTEXT = 65_535

    /** The largest standard-format NIP-44 v2 ciphertext: the u16 prefix plus the 64 KiB pad bucket. */
    private const val MAX_STANDARD_CIPHERTEXT = 2 + 65_536

    /** base64 of version (1) + nonce (32) + [MAX_STANDARD_CIPHERTEXT] + mac (32): anything longer is not standard NIP-44. */
    private const val MAX_STANDARD_PAYLOAD = 87_472

    /**
     * NIP-44 v2 encrypt that refuses a plaintext over [NIP44_MAX_PLAINTEXT] UTF-8 bytes instead of
     * minting an extended-format payload (the reference client's `encryptChecked`).
     */
    private fun encryptChecked(
        plaintext: String,
        conversationKey: ByteArray,
    ): String {
        val size = plaintext.encodeToByteArray().size
        require(size <= NIP44_MAX_PLAINTEXT) { "Concord plaintext is $size bytes, over the NIP-44 cap of $NIP44_MAX_PLAINTEXT (CORD-02 Appendix B)" }
        return Nip44.v2.encrypt(plaintext, conversationKey).encodePayload()
    }

    /**
     * NIP-44 v2 decrypt that only accepts the standard format: a payload or ciphertext too large
     * for the u16 length prefix is the extended format, which no strict Concord client can read
     * and none of ours ever writes, so it is refused before any decryption work.
     */
    private fun decryptChecked(
        payload: String,
        conversationKey: ByteArray,
    ): String {
        val info = Nip44v2.EncryptedInfo.decodePayload(payload, MAX_STANDARD_PAYLOAD)
        require(info.ciphertext.size <= MAX_STANDARD_CIPHERTEXT) { "Extended-format NIP-44 payload refused (CORD-02 Appendix B)" }
        return Nip44.v2.decrypt(info, conversationKey)
    }
}

/**
 * The verified result of opening a stream wrap: the author [rumor], the
 * [sealKind] it arrived under (20013/20014), the true [author] pubkey (equal to
 * `rumor.pubKey`, surfaced for convenience), and the verified inner [seal] event
 * itself. The [seal] carries the original author's signature, so a Refounding can
 * re-wrap a plaintext control seal under a fresh root without re-signing it
 * (CORD-06 §3 compaction).
 */
class OpenedStreamEvent(
    val rumor: Event,
    val sealKind: Int,
    val author: String,
    val seal: Event,
)
