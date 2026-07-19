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
package com.vitorpamplona.amethyst.cli

import com.vitorpamplona.amethyst.cli.stores.FileCashuKeysetCounterStore
import com.vitorpamplona.amethyst.commons.cashu.CashuWalletReader
import com.vitorpamplona.amethyst.commons.cashu.ops.CashuWalletOps
import com.vitorpamplona.amethyst.commons.cashu.ops.RestoreOutcome
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DeterministicSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent

/**
 * Cashu (NIP-60 / NIP-61) wiring for the CLI, split out of [Context] — shared
 * wallet code from commons, driven by the CLI's file-backed NUT-13 counter
 * store and the run's [Context.store] snapshot projection. Instantiated
 * lazily by [Context.cashu], so a run that never touches the wallet pays
 * nothing.
 */
class CashuContext(
    private val ctx: Context,
) {
    /** Durable NUT-13 counter store at `<data-dir>/cashu.json`. */
    private val counters by lazy { FileCashuKeysetCounterStore(ctx.dataDir.cashuFile) }

    @Volatile private var cachedSeed: ByteArray? = null

    /**
     * Decrypt the wallet's NUT-13 seed once per run and cache it. The
     * [DeterministicSecretFactory] thunk reads this synchronously, so any
     * mint/swap op must warm it first (CashuWalletOps' seedWarmer does).
     */
    private suspend fun warmSeed() {
        if (cachedSeed != null) return
        val priv = snapshot().walletEvent?.let { runCatching { it.privkey(ctx.signer) }.getOrNull() } ?: return
        cachedSeed = CashuDeterministic.deriveWalletSeed(priv.hexToByteArray())
    }

    /**
     * Wallet operations driven by the exact same `commons` [CashuWalletOps]
     * the Android app uses. Wired to publish on the account's outbox relays,
     * the shared OkHttp instance for mint HTTP, and the file-backed NUT-13
     * counter store.
     */
    fun ops(): CashuWalletOps =
        CashuWalletOps(
            signer = ctx.signer,
            // Amethyst publishes cashu events via sendLiterallyEverywhere
            // (all the user's relays). anyRelays() — outbox + inbox +
            // keypackage — is the CLI's closest analog, so the wallet lands
            // on the same broad relay set the app would use, not just outbox.
            publish = { event -> ctx.publish(event, ctx.anyRelays()) },
            okHttpClient = { ctx.okhttp },
            secretFactory =
                DeterministicSecretFactory(
                    seedProvider = { cachedSeed },
                    reserveCounters = { keysetId, count -> counters.reserve(keysetId, count) },
                ),
            seedWarmer = { warmSeed() },
            seedForRestore = {
                warmSeed()
                cachedSeed
            },
            peekCashuCounter = { keysetId -> counters.peek(keysetId) },
            reserveCashuCounters = { keysetId, count -> counters.reserve(keysetId, count) },
        )

    /**
     * Project this account's locally-stored NIP-60/61/87 events into a wallet
     * snapshot via the shared [CashuWalletReader] — the same decrypt +
     * del-rollover + pending-quote logic the Android holder runs. Reads the
     * cache only; commands that need fresh state should [Context.drain] first.
     */
    suspend fun snapshot(): CashuWalletReader.WalletSnapshot {
        val pk = ctx.identity.pubKeyHex
        // Mirror commons' CashuWalletFilterAssembler exactly: authored wallet
        // kinds by authors=[pk], inbound nutzaps by #p — so amy projects the
        // same event set the Android app subscribes to.
        val authored =
            ctx.store.query<Event>(
                Filter(
                    authors = listOf(pk),
                    kinds =
                        listOf(
                            CashuWalletEvent.KIND,
                            CashuTokenEvent.KIND,
                            CashuSpendingHistoryEvent.KIND,
                            CashuMintQuoteEvent.KIND,
                            NutzapInfoEvent.KIND,
                            MintRecommendationEvent.KIND,
                        ),
                ),
            )
        val inboundNutzaps =
            ctx.store.query<Event>(
                Filter(kinds = listOf(NutzapEvent.KIND), tags = mapOf("p" to listOf(pk))),
            )
        return CashuWalletReader(ctx.signer).project(authored + inboundNutzaps)
    }

    /** Warm and return the wallet's NUT-13 seed, or null if no wallet. */
    suspend fun seed(): ByteArray? {
        warmSeed()
        return cachedSeed
    }

    /**
     * NUT-09 restore for one mint, mirroring Android's
     * `CashuWalletState.restoreFromMint`: re-derive proofs from the seed
     * (skipping secrets we already hold), then bump the persisted NUT-13
     * counter past every slot the scan confirmed in use so later mints can't
     * reuse one — the advance rule is the shared
     * [CashuWalletOps.restoreFromMintAdvancingCounter]. Returns null when the
     * wallet has no seed yet.
     */
    suspend fun restore(mintUrl: String): RestoreOutcome? {
        val seed = seed() ?: return null
        val existingSecrets =
            snapshot()
                .tokenEntries
                .flatMap { it.content.proofs }
                .mapTo(HashSet()) { it.secret }
        return ops().restoreFromMintAdvancingCounter(mintUrl = mintUrl, seed = seed, startCounter = 0L, existingSecrets = existingSecrets)
    }
}
