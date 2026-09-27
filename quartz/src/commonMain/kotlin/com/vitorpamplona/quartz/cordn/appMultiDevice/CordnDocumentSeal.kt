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
package com.vitorpamplona.quartz.cordn.appMultiDevice

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import com.vitorpamplona.quartz.utils.sha256.sha256

/**
 * §7 — sealing a document to the per-identity document encryption key.
 *
 * ## Why a DEK rather than sealing to the owner
 *
 * Sealing each document straight to the owner `npub` would bind every document
 * decrypt to the owner `nsec` — one signer round-trip per document, which on a
 * remote NIP-46 signer over a bad connection is the difference between a
 * migration that finishes and one that does not. The DEK is a throwaway Nostr
 * keypair; its private half rides inside the tip's owner-sealed inner event, so
 * one NIP-44 decrypt of the tip yields a key every later document decrypt uses
 * locally.
 *
 * ## What the seal does and does not give you
 *
 * Confidentiality only. It is a self-seal — sender and recipient are both the
 * DEK — so anyone holding the DEK can encrypt as easily as decrypt. Documents
 * carry no signature. **Authenticity comes from the tip**, whose inner event is
 * signed by the owner, and integrity of a particular blob comes from the
 * address: [address] is `sha256` of the sealed bytes, and §6 requires a reader
 * to check it against what the tip advertised before trusting the content.
 *
 * Nothing here is a substitute for that check. A blob that decrypts is not a
 * blob that anybody authorised.
 */
object CordnDocumentSeal {
    private val nip44 = Nip44v2()

    /** A fresh DEK. One per identity, reused across every publish (§7). */
    fun newKey(): KeyPair = KeyPair()

    /**
     * Seals [document] to [dek].
     *
     * NIP-44 v2 uses a random nonce, so the same document seals to different
     * bytes — and therefore a different [address] — every time. §5 says that
     * explicitly: no canonical form is required, because the address is over
     * ciphertext and could not enable dedup anyway.
     */
    fun seal(
        document: CordnDeviceDoc,
        dek: KeyPair,
    ): ByteArray = nip44.encrypt(CordnDeviceDocument.encode(document), dek.privKey!!, dek.pubKey).encodePayload().encodeToByteArray()

    /**
     * Opens a sealed blob.
     *
     * @throws CordnDocumentException when the blob does not decrypt, or decrypts
     * to something that is not a readable document. Both are the same class of
     * fault to a caller that has already matched the address.
     */
    fun open(
        blob: ByteArray,
        dek: KeyPair,
    ): CordnDeviceDoc {
        val plaintext =
            try {
                nip44.decrypt(blob.decodeToString(), dek.privKey!!, dek.pubKey)
            } catch (e: Exception) {
                throw CordnDocumentException("sealed document did not decrypt: ${e.message}")
            }
        return CordnDeviceDocument.decode(plaintext)
    }

    /**
     * §6 — a document's address is `sha256` of its **sealed** bytes, lowercase
     * hex, and doubles as the content-addressed store key.
     */
    fun address(blob: ByteArray): String = sha256(blob).toHexKey()

    /**
     * The §6 check a reader MUST perform before trusting a fetched blob.
     *
     * Separate from [open] on purpose: the order matters. Verifying the address
     * first means a blob that fails was never decrypted, so a store that serves
     * the wrong bytes cannot get its plaintext in front of the parser at all.
     */
    fun verifyAddress(
        blob: ByteArray,
        expected: String,
    ): Boolean = address(blob).equals(expected, ignoreCase = true)
}
