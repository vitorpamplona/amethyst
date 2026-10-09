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
package com.vitorpamplona.amethyst.cli.commands.trust

import com.vitorpamplona.amethyst.cli.Args
import com.vitorpamplona.amethyst.cli.Context
import com.vitorpamplona.amethyst.cli.DataDir
import com.vitorpamplona.amethyst.cli.Output
import com.vitorpamplona.amethyst.cli.commands.RawEventSupport
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.rankProvider
import com.vitorpamplona.amethyst.commons.model.trustedAssertions.withProviderRows
import com.vitorpamplona.amethyst.commons.wot.onboarding.KnownTrustProviders
import com.vitorpamplona.amethyst.commons.wot.onboarding.OkHttpTrustProviderHttp
import com.vitorpamplona.amethyst.commons.wot.onboarding.TrustProviderException

/**
 * `amy trust setup PROVIDER [--private] [--timeout SECS]`
 *
 * Runs a provider's guided sign-up (today: `brainstorm`) and publishes the kind 10040 rows the
 * provider serves the account with (`rows` in the output: `30382:rank`, `30382:followers`, …),
 * replacing rows of the same names and any previous score provider, keeping every other entry.
 * `--private` puts the rows in the encrypted content. Then `amy trust sync` downloads the network.
 *
 * Any other NIP-85 provider needs no sign-up code: `amy graperank register PROVIDER --relay URL`.
 */
object TrustSetup {
    suspend fun run(
        dataDir: DataDir,
        rest: Array<String>,
    ): Int {
        val args = Args(rest)
        val providerId = args.positional(0, "provider")
        val isPrivate = args.bool("private")
        val timeoutMs = args.timeoutMs(8)
        args.rejectUnknown()

        Context.open(dataDir).use { ctx ->
            val onboarding =
                KnownTrustProviders.all(OkHttpTrustProviderHttp { ctx.okhttp }).firstOrNull { it.id == providerId }
                    ?: return Output.error(
                        "bad_args",
                        "unknown provider '$providerId' (guided: ${KnownTrustProviders.all(OkHttpTrustProviderHttp { ctx.okhttp }).joinToString { it.id }}); " +
                            "for any other NIP-85 provider use `amy graperank register PROVIDER --relay URL`",
                    )
            ctx.prepare()

            val registration =
                try {
                    onboarding.register(ctx.signer) { step -> System.err.println("[amy] ${onboarding.name}: ${step.name.lowercase().replace('_', ' ')}") }
                } catch (e: TrustProviderException) {
                    return Output.error("provider_" + e.reason.name.lowercase(), e.message)
                }

            val latest = (readOwnProviderList(ctx, timeoutMs) ?: return ownListUnreachable()).event
            // Read before the rewrite: each decrypt is a signer round trip (a bunker, a prompt).
            val replaced = latest?.rankProvider(ctx.signer)?.pubkey?.takeIf { it != registration.serviceKey }
            val event = withProviderRows(latest, registration.rows, isPrivate, ctx.signer)
            val ack = ctx.publish(event, ctx.outboxRelays())
            RawEventSupport.publishGuard(ack, event.id)?.let { return it }

            Output.emit(
                mapOf(
                    // Like every trust command, the key whose cards make the network.
                    "provider" to registration.serviceKey,
                    "onboarding" to onboarding.id,
                    "service_key" to registration.serviceKey,
                    "relay" to registration.relay.url,
                    "rows" to registration.rows.map { it.toTagArray().toList() },
                    "scores_ready" to registration.scoresReady,
                    "private" to isPrivate,
                    "replaced" to replaced,
                    "event_id" to event.id,
                ) + RawEventSupport.ackFields(ack),
            )
            return 0
        }
    }
}
