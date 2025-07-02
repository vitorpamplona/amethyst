/**
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
package com.vitorpamplona.quartz.nip47WalletConnect

import androidx.core.net.toUri
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKey
import kotlinx.coroutines.CancellationException

// Rename to the corect nip number when ready.
class Nip47WalletConnect {
    companion object {
        fun parse(uri: String): Nip47URINorm {
            // nostrwalletconnect://b889ff5b1513b641e2a139f661a661364979c5beee91842f8f0ef42ab558e9d4?relay=wss%3A%2F%2Frelay.damus.io&metadata=%7B%22name%22%3A%22Example%22%7D

            val url = uri.toUri()

            if (url.scheme != "nostrwalletconnect" && url.scheme != "nostr+walletconnect") {
                throw IllegalArgumentException("Not a Wallet Connect QR Code")
            }

            val pubkey = url.host ?: throw IllegalArgumentException("Hostname cannot be null")

            val pubkeyHex =
                try {
                    decodePublicKey(pubkey).toHexKey()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw IllegalArgumentException("Hostname is not a valid Nostr Pubkey")
                }

            val relay = url.getQueryParameter("relay") ?: throw IllegalArgumentException("Relay cannot be null")
            val relayNorm = RelayUrlNormalizer.normalizeOrNull(relay) ?: throw IllegalArgumentException("Invalid relay Url")
            val secret = url.getQueryParameter("secret")

            return Nip47URINorm(pubkeyHex, relayNorm, secret)
        }
    }

    data class Nip47URI(
        val pubKeyHex: HexKey,
        val relayUri: String,
        val secret: HexKey?,
    ) {
        fun normalize(): Nip47WalletConnect.Nip47URINorm? =
            RelayUrlNormalizer.normalizeOrNull(relayUri)?.let {
                Nip47URINorm(
                    pubKeyHex,
                    it,
                    secret,
                )
            }
    }

    data class Nip47URINorm(
        val pubKeyHex: HexKey,
        val relayUri: NormalizedRelayUrl,
        val secret: HexKey?,
    )
}
