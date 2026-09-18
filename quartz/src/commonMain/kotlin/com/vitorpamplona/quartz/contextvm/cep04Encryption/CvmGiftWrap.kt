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
package com.vitorpamplona.quartz.contextvm.cep04Encryption

import com.vitorpamplona.quartz.contextvm.core.CvmKinds
import com.vitorpamplona.quartz.contextvm.core.CvmTags
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.Kind
import com.vitorpamplona.quartz.nip01Core.core.OptimizedJsonMapper
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.tags.people.PTag
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * Whether ContextVM messages must be encrypted.
 *
 * Defaults to [REQUIRED] rather than mirroring the reference SDK's permissive
 * default. Under `OPTIONAL` a coordinator that simply does not advertise
 * encryption receives plaintext JSON-RPC on public relays, exposing every tool
 * argument to any relay operator. Failing closed is the only safe default for a
 * messaging client.
 */
enum class EncryptionMode {
    /** Encrypt always; refuse to talk to a peer that cannot. */
    REQUIRED,

    /** Encrypt when the peer advertises support. Opt in deliberately. */
    OPTIONAL,

    /** Never encrypt. For test fixtures and diagnostics only. */
    DISABLED,
}

/** Which gift wrap kind to use (CEP-19). */
enum class GiftWrapMode {
    /** Kind 21059 — ephemeral, so relays do not retain the envelope. */
    EPHEMERAL,

    /** Kind 1059 — persistent, for deliberately retained delivery. */
    PERSISTENT,
}

/** Thrown when a gift wrap cannot be produced or trusted. */
class CvmEncryptionException(
    message: String,
) : IllegalStateException(message)

/**
 * CEP-4 and CEP-19 message encryption.
 *
 * The scheme is a simplified NIP-59: the inner kind-25910 event is **signed
 * first**, then the whole signed event JSON is NIP-44 encrypted to the
 * recipient and placed in a gift wrap. There is no separate seal and no unsigned
 * rumor, so a receiver verifies the inner signature and correlates on the
 * **inner** event id.
 *
 * What this hides and what it does not: the sender, the inner kind and the real
 * timestamp are concealed, but the recipient's pubkey is a `p` tag on the wrap
 * and is therefore visible to relays. CEP-4 states that limitation outright; it
 * is inherent to the addressing model, not a defect here.
 */
class CvmGiftWrap(
    private val encryptionMode: EncryptionMode = EncryptionMode.REQUIRED,
    private val giftWrapMode: GiftWrapMode = GiftWrapMode.EPHEMERAL,
) {
    /** The wrap kind this client emits. */
    fun wrapKind(): Kind =
        when (giftWrapMode) {
            GiftWrapMode.EPHEMERAL -> CvmKinds.EPHEMERAL_GIFT_WRAP
            GiftWrapMode.PERSISTENT -> CvmKinds.GIFT_WRAP
        }

    /**
     * The kind to actually use against a peer.
     *
     * CEP-19 requires falling back to the persistent wrap for a peer that does
     * not advertise ephemeral support, so preferring 21059 must never mean
     * refusing to talk to a 1059-only server.
     */
    fun negotiatedWrapKind(peerSupportsEphemeral: Boolean): Kind =
        if (giftWrapMode == GiftWrapMode.EPHEMERAL && peerSupportsEphemeral) {
            CvmKinds.EPHEMERAL_GIFT_WRAP
        } else {
            CvmKinds.GIFT_WRAP
        }

    /**
     * Whether a message to this peer must be encrypted.
     *
     * @throws CvmEncryptionException under [EncryptionMode.REQUIRED] when the
     *   peer cannot encrypt — failing loudly rather than downgrading.
     */
    fun shouldEncrypt(peerSupportsEncryption: Boolean): Boolean =
        when (encryptionMode) {
            EncryptionMode.DISABLED -> false
            EncryptionMode.OPTIONAL -> peerSupportsEncryption
            EncryptionMode.REQUIRED ->
                if (peerSupportsEncryption) {
                    true
                } else {
                    throw CvmEncryptionException(
                        "peer does not advertise encryption and this client requires it",
                    )
                }
        }

    /**
     * Wraps an already-signed inner event for [recipient].
     *
     * The wrap is signed by a fresh throwaway key, so nothing links two wraps
     * from the same sender. Its `created_at` is randomized per NIP-59 and must
     * never be used for ordering.
     */
    suspend fun wrap(
        inner: Event,
        recipient: HexKey,
        kind: Kind = wrapKind(),
    ): Event {
        if (!CvmKinds.isGiftWrap(kind)) {
            throw CvmEncryptionException("$kind is not a gift wrap kind")
        }

        val wrapSigner = NostrSignerInternal(KeyPair())
        val ciphertext = wrapSigner.nip44Encrypt(OptimizedJsonMapper.toJson(inner), recipient)

        return wrapSigner.sign(
            createdAt = randomizedTimestamp(),
            kind = kind,
            tags = arrayOf(PTag.assemble(recipient, relayHint = null)),
            content = ciphertext,
        )
    }

    /**
     * Unwraps [giftWrap] and returns the signed inner event.
     *
     * The inner signature is verified here: without that check anyone able to
     * encrypt to us could impersonate any pubkey, since the wrap's own signature
     * only proves the throwaway key signed it.
     */
    suspend fun unwrap(
        giftWrap: Event,
        signer: NostrSigner,
    ): Event {
        if (!CvmKinds.isGiftWrap(giftWrap.kind)) {
            throw CvmEncryptionException("${giftWrap.kind} is not a gift wrap kind")
        }

        val json =
            try {
                signer.nip44Decrypt(giftWrap.content, giftWrap.pubKey)
            } catch (e: Exception) {
                throw CvmEncryptionException("could not decrypt gift wrap: ${e.message}")
            }

        val inner =
            try {
                OptimizedJsonMapper.fromJson(json)
            } catch (e: Exception) {
                throw CvmEncryptionException("gift wrap did not contain an event: ${e.message}")
            }

        if (!inner.verify()) {
            throw CvmEncryptionException("inner event signature is invalid")
        }

        return inner
    }

    /** Reads whether a peer advertised encryption support (CEP-4). */
    fun peerSupportsEncryption(tags: Array<Array<String>>) = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION)

    /** Reads whether a peer advertised ephemeral wrap support (CEP-19). */
    fun peerSupportsEphemeral(tags: Array<Array<String>>) = CvmTags.hasFlag(tags, CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL)

    /** The capability flags this client advertises to peers. */
    fun capabilityTags(): List<Array<String>> =
        buildList {
            if (encryptionMode != EncryptionMode.DISABLED) {
                add(CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION))
                if (giftWrapMode == GiftWrapMode.EPHEMERAL) {
                    add(CvmTags.flag(CvmTags.SUPPORT_ENCRYPTION_EPHEMERAL))
                }
            }
        }

    /**
     * A timestamp shifted randomly into the recent past, per NIP-59.
     *
     * Consumers must not order on it — it is deliberately not the real send
     * time.
     */
    private fun randomizedTimestamp(): Long {
        val now = TimeUtils.now()
        return now - (0..TWO_DAYS_SECONDS).random()
    }

    companion object {
        private const val TWO_DAYS_SECONDS = 2 * 24 * 60 * 60L
    }
}
