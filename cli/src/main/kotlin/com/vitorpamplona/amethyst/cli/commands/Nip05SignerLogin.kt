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
package com.vitorpamplona.amethyst.cli.commands

import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Identity
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.commons.domain.nip46.Nip05SignerLoginUseCase
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.BasicOkHttpWebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.okhttp.TcpNoDelaySocketFactory
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip05DnsIdentifiers.Nip05RemoteSigner
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import okhttp3.OkHttpClient
import kotlin.coroutines.cancellation.CancellationException

/**
 * `amy login name@domain` when the domain advertises a NIP-46 signer for the address: log in
 * through it (commons [Nip05SignerLoginUseCase]) and persist a bunker account for the address's
 * key. Pages the signer needs the user to open are printed to stderr.
 */
object Nip05SignerLogin {
    suspend fun login(
        dataDir: DataDir,
        address: String,
        signer: Nip05RemoteSigner,
        timeoutMs: Long,
    ): Int {
        val transport = KeyPair()
        val okhttp = OkHttpClient.Builder().socketFactory(TcpNoDelaySocketFactory).build()
        val client = NostrClient(websocketBuilder = BasicOkHttpWebSocket.Builder { okhttp })
        System.err.println("[nip46] $address has a remote signer; asking it to log you in (up to ${timeoutMs / 1000}s)")
        try {
            client.connect()
            val login =
                try {
                    Nip05SignerLoginUseCase.execute(
                        signer = signer,
                        transport = transport,
                        client = client,
                        onAuthUrl = { url -> System.err.println("[nip46] approve in your signer, then the login continues:\n  $url") },
                        connectTimeoutMs = timeoutMs,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Nip05SignerLoginUseCase.SignerMismatchException) {
                    return Output.error("signer_error", "the signer for $address signs for ${e.actual}, not the address's ${e.expected}; nothing was saved")
                } catch (e: SignerExceptions) {
                    return Output.error("signer_error", "the signer for $address did not complete the login: ${e.message}")
                } catch (e: Exception) {
                    return Output.error("timeout", "the signer for $address did not answer: ${e.message}")
                }
            login.result.signer.closeSubscription()

            val relays = NostrConnectURI.parseBunker(login.bunkerUri)?.relays ?: signer.relays
            val identity =
                Identity.bunkerIdentity(
                    signerPubkey = login.result.signer.remotePubkey,
                    relays = relays.map { it.url },
                    connectSecret = null,
                    clientPrivKeyHex = transport.privKey!!.toHexKey(),
                    userPubkey = login.result.pubKeyHex,
                )
            dataDir.saveIdentity(identity)
            Output.emit(
                mapOf(
                    "npub" to identity.npub,
                    "hex" to identity.pubKeyHex,
                    "read_only" to false,
                    "signer" to "bunker",
                    "nip05" to address,
                    "bunker_relays" to identity.bunker?.relays,
                    "data_dir" to dataDir.root.absolutePath,
                ),
            )
            return 0
        } finally {
            client.close()
        }
    }
}
