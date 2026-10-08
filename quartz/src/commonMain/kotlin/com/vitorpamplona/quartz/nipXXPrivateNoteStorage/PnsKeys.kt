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
package com.vitorpamplona.quartz.nipXXPrivateNoteStorage

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip44Encryption.Nip44
import com.vitorpamplona.quartz.nip44Encryption.crypto.Hkdf

/**
 * The key material of NIP-PNS (Private Note Storage, draft nostr-protocol/nips#1893) for one
 * device key:
 *
 * ```
 * pns_key       = hkdf_extract(ikm = device_key, salt = "nip-pns")
 * pns_keypair   = derive_secp256k1_keypair(pns_key)
 * pns_nip44_key = hkdf_extract(ikm = pns_key, salt = "nip44-v2")
 * ```
 *
 * `hkdf_extract` is RFC 5869 HKDF-Extract with SHA-256, i.e. `HMAC-SHA256(key = salt, msg = ikm)`
 * — the same reading nostrdb (`ndb_ingester_add_pns_key`), nostrdb-rs (`nostrdb_net::pns`) and
 * Damus (`PNS.key(for:)`) use. [nip44Key] is a NIP-44 v2 *conversation key*: it replaces the
 * ECDH + extract step of NIP-44 and goes straight into its per-message HKDF-Expand, ChaCha20 and
 * HMAC (all reused from [Nip44]; nothing here re-implements them).
 *
 * **Needs the raw device secret.** Every step is an HMAC over the private key itself, which no
 * remote signer exposes: NIP-46 bunkers and NIP-55 external signers (Amber) only sign and
 * encrypt to a *peer pubkey*, and neither protocol has a "derive a symmetric key from my secret"
 * method today. So PNS works only with a local key ([NostrSignerInternal], [KeyPair] with a
 * private key, or raw bytes); [fromSignerOrNull] returns null for every other signer. Supporting
 * remote signers needs a new signer-protocol method and is out of scope here.
 *
 * All three values are secrets: whoever holds [nip44Key] reads every note, whoever holds
 * [keyPair] can publish (and delete) them. Don't log or persist instances.
 */
class PnsKeys private constructor(
    /** The pubkey of the device key these keys were derived from — the author every rumor inside must claim. */
    val devicePubKey: HexKey,
    /** `pns_keypair`: signs every kind-1080 event. Its pubkey is unlinkable to [devicePubKey]. */
    val keyPair: KeyPair,
    /** `pns_nip44_key`: the NIP-44 v2 conversation key every PNS payload is encrypted under. */
    val nip44Key: ByteArray,
) {
    /** `pns_keypair.pubkey`: the author of every kind-1080 event of this device. Subscribe with `authors = [pubKey]`. */
    val pubKey: HexKey = keyPair.pubKey.toHexKey()

    /** Signs kind-1080 events as [pubKey]. */
    val signer = NostrSignerSync(keyPair)

    /** NIP-44 v2 encrypts [plaintext] under [nip44Key] with a random nonce; returns the base64 payload. */
    fun encrypt(plaintext: String): String = Nip44.v2.encrypt(plaintext, nip44Key).encodePayload()

    /** NIP-44 v2 decrypts a base64 [payload] under [nip44Key]. Throws if the MAC, version or padding is wrong. */
    fun decrypt(payload: String): String = Nip44.v2.decrypt(payload, nip44Key)

    override fun toString(): String = "PnsKeys(device=$devicePubKey, pns=$pubKey, secrets=<redacted>)"

    companion object {
        /** Salt of the `device_key -> pns_key` extract. */
        const val PNS_SALT = "nip-pns"

        /** Salt of the `pns_key -> pns_nip44_key` extract: the same salt NIP-44 uses after ECDH. */
        const val NIP44_SALT = "nip44-v2"

        private val pnsSalt = PNS_SALT.encodeToByteArray()
        private val nip44Salt = NIP44_SALT.encodeToByteArray()

        /**
         * Derives the PNS keys of the 32-byte secp256k1 secret [deviceKey].
         *
         * An HKDF output that is not a valid secp256k1 scalar (probability ~2^-128) makes the
         * keypair derivation throw.
         */
        fun derive(deviceKey: ByteArray): PnsKeys {
            require(deviceKey.size == 32) { "PNS device key must be 32 bytes, got ${deviceKey.size}" }
            val hkdf = Hkdf()
            val pnsKey = hkdf.extract(deviceKey, pnsSalt)
            return PnsKeys(
                devicePubKey = Nip01Crypto.pubKeyCreate(deviceKey).toHexKey(),
                keyPair = KeyPair(privKey = pnsKey),
                nip44Key = hkdf.extract(pnsKey, nip44Salt),
            )
        }

        /** Derives the PNS keys of [device], or null for a read-only key pair (no private key to derive from). */
        fun deriveOrNull(device: KeyPair): PnsKeys? = device.privKey?.let(::derive)

        /**
         * Derives the PNS keys behind [signer] when it holds the raw private key
         * ([NostrSignerInternal]). Null for NIP-46 / NIP-55 remote signers and read-only accounts:
         * they cannot run the HKDF over the secret (see the class KDoc).
         */
        fun fromSignerOrNull(signer: NostrSigner): PnsKeys? = (signer as? NostrSignerInternal)?.keyPair?.let(::deriveOrNull)
    }
}
