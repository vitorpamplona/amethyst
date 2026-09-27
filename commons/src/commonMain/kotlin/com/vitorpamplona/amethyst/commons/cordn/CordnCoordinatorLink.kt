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
package com.vitorpamplona.amethyst.commons.cordn

import com.vitorpamplona.quartz.contextvm.cep04Encryption.CvmGiftWrap
import com.vitorpamplona.quartz.contextvm.mcp.CvmMcpClient
import com.vitorpamplona.quartz.contextvm.transport.CvmTransport
import com.vitorpamplona.quartz.contextvm.transport.DualSigner
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorClient
import com.vitorpamplona.quartz.cordn.spec00Coordinator.CoordinatorServerInfo
import com.vitorpamplona.quartz.cordn.spec00Coordinator.ICoordinator
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal

/** A live connection to one coordinator, and the way to close it. */
interface CordnCoordinatorLink {
    val coordinator: ICoordinator

    /**
     * What the coordinator says about itself, or null.
     *
     * Deliberately **not** on `ICoordinator`. That interface is "the eleven
     * coordinator tools, as a contract" and its KDoc says it adds nothing to
     * them; `initialize` is the MCP handshake, not a twelfth tool. It belongs
     * to whoever owns the transport, which is this.
     *
     * The default is null so a substitute link — a fixture, a test double —
     * does not have to invent a handshake it never performed.
     */
    suspend fun serverInfo(): CoordinatorServerInfo? = null

    suspend fun close()
}

/**
 * Opens the ContextVM transport to a coordinator.
 *
 * The one piece a scope factory cannot supply on its own: a `CvmTransport`
 * needs a relay pool and the account's signers, which belong to whichever
 * front end is running. Everything else about a scope — where the bytes go
 * and how they are encrypted — is the same on every platform.
 */
fun interface CordnCoordinatorLinkFactory {
    suspend fun connect(
        accountPubKey: HexKey,
        config: CoordinatorConfig,
    ): CordnCoordinatorLink
}

/**
 * The production [CordnCoordinatorLinkFactory]: a real ContextVM transport per
 * coordinator, over whatever relay client the caller is already running.
 *
 * Shared rather than per-front-end on purpose. Everything this assembles is a
 * protocol decision — which signer signs which call, that gift wrapping stays
 * REQUIRED, that `initialize` is not performed at open — and a second copy in
 * a second front end is a second place those decisions can drift. Android, the
 * desktop app and `amy` all take this one.
 *
 * ## The ephemeral signer is created here, once, and never persisted
 *
 * `spec/00.md` §8 splits the identity a coordinator sees: the account key
 * signs what must be attributable (publishing a KeyPackage, posting to a
 * group), and a throwaway key signs everything else, so the coordinator cannot
 * link a session's reads to an account. Which key signs which call is fixed by
 * `CoordinatorMethod` and not a choice made here; what IS decided here is that
 * the throwaway key lives as long as this factory and no longer. Persisting it
 * would quietly undo the split — a "session" key reused across launches is
 * just a second account key with worse ergonomics.
 *
 * One consequence worth naming: a front end that builds a factory per run —
 * `amy` does, since every invocation is its own process — gets a fresh
 * throwaway key each time, which is the strong end of the split rather than a
 * degradation of it.
 */
object CordnLinks {
    fun over(
        accountSigner: NostrSigner,
        client: INostrClient,
    ): CordnCoordinatorLinkFactory {
        val ephemeralSigner = NostrSignerInternal(KeyPair())

        return CordnCoordinatorLinkFactory { _, config ->
            val transport =
                CvmTransport(
                    relays = NostrClientCvmRelayPool(client, config.relays.toSet()),
                    signers = DualSigner(accountSigner, ephemeralSigner),
                    serverPubKey = config.pubKey,
                    // Left at its default, which is REQUIRED (§8.6). Passing
                    // anything else here is the one line that would silently
                    // downgrade every coordinator call to plaintext.
                    crypto = CvmGiftWrap(),
                )

            object : CordnCoordinatorLink {
                override val coordinator = CoordinatorClient(CvmMcpClient(transport))

                // Handshaken on demand, not at open: `initialize` is a call
                // like any other (§8), and a coordinator that only ever hears
                // from us when we have something to say tells it less than one
                // that is greeted at every launch.
                override suspend fun serverInfo() = coordinator.serverInfo()

                override suspend fun close() {
                    // Nothing to release. CvmTransport opens a subscription
                    // per request and closes it in the same call — kind 25910
                    // is ephemeral, so there is no long-lived stream to tear
                    // down and no connection of its own. The relays it used
                    // belong to the shared client, which outlives this link.
                }
            }
        }
    }
}
