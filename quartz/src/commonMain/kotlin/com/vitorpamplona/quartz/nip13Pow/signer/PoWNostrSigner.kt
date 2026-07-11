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
package com.vitorpamplona.quartz.nip13Pow.signer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip13Pow.miner.PoWMiner
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import com.vitorpamplona.quartz.nip57Zaps.LnZapPrivateEvent
import com.vitorpamplona.quartz.nip57Zaps.LnZapRequestEvent

/**
 * A [NostrSigner] decorator that mines a NIP-13 proof of work into the unsigned
 * template before delegating to the wrapped signer. Because mining happens on the
 * pre-signature template it composes with every signer kind (local key, NIP-55
 * external app, NIP-46 bunker).
 *
 * Only events whose kind is in [kindsToMine] are mined; anything else (e.g. the
 * seal and rumor of a gift-wrapped flow) passes through untouched. Templates that
 * already carry a nonce tag are not mined again.
 *
 * [workers] parallel searches race over disjoint nonce slices (see
 * [PoWMiner.mine]); 1 keeps the historical single-threaded behavior.
 */
class PoWNostrSigner(
    val signer: NostrSigner,
    val desiredPoW: Int,
    val kindsToMine: Set<Int>,
    val isActive: () -> Boolean = { true },
    val workers: Int = 1,
) : NostrSigner(signer.pubKey) {
    override fun isWriteable(): Boolean = signer.isWriteable()

    override suspend fun <T : Event> sign(
        createdAt: Long,
        kind: Int,
        tags: Array<Array<String>>,
        content: String,
    ): T =
        if (kind in kindsToMine && tags.none { PoWTag.hasTagWithContent(it) }) {
            val mined =
                PoWMiner.mine(
                    template = EventTemplate<T>(createdAt, kind, tags, content),
                    pubKey = pubKey,
                    desiredPoW = desiredPoW,
                    workers = workers,
                    isActive = isActive,
                )
            signer.sign(mined.createdAt, mined.kind, mined.tags, mined.content)
        } else {
            signer.sign(createdAt, kind, tags, content)
        }

    override suspend fun nip04Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = signer.nip04Encrypt(plaintext, toPublicKey)

    override suspend fun nip04Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = signer.nip04Decrypt(ciphertext, fromPublicKey)

    override suspend fun nip44Encrypt(
        plaintext: String,
        toPublicKey: HexKey,
    ): String = signer.nip44Encrypt(plaintext, toPublicKey)

    override suspend fun nip44Decrypt(
        ciphertext: String,
        fromPublicKey: HexKey,
    ): String = signer.nip44Decrypt(ciphertext, fromPublicKey)

    override suspend fun decryptZapEvent(event: LnZapRequestEvent): LnZapPrivateEvent = signer.decryptZapEvent(event)

    override suspend fun deriveKey(nonce: HexKey): HexKey = signer.deriveKey(nonce)

    override suspend fun signPsbt(psbtHex: String): String = signer.signPsbt(psbtHex)

    override fun hasForegroundSupport(): Boolean = signer.hasForegroundSupport()
}
