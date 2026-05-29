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
package com.vitorpamplona.amethyst.model.nip60Cashu

import com.vitorpamplona.amethyst.service.cashu.v4.V4Encoder
import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.tags.aTag.aTag
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip60Cashu.history.TokenReference
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DleqProofDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintProtocolException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.ProofState
import com.vitorpamplona.quartz.nip60Cashu.mintApi.RandomSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.splitAmountIntoDenominations
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.nip87Ecash.cashu.CashuMintEvent
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.Log
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Wallet-level operations that combine the [CashuMintOperations] HTTP layer
 * with Nostr event publishing (kind 7375 / 7376 / 7374 / 17375 / 10019 /
 * deletion-5).
 *
 * Stateless w.r.t. wallet contents — each call receives the current state
 * from `CashuWalletState`. Signing and broadcast are abstracted behind the
 * [signer] + [publish] callbacks so the ops layer can be unit-tested without
 * a full [com.vitorpamplona.amethyst.model.Account] graph.
 *
 * Per-mint [CashuMintOperations] instances are cached so repeated calls
 * against the same mint reuse the same `MintHttpClient` and avoid re-
 * instantiating the JSON serializer per request.
 */
class CashuWalletOps(
    private val signer: NostrSigner,
    private val publish: suspend (Event) -> Unit,
    private val okHttpClient: (String) -> OkHttpClient,
    /**
     * NUT-13 secret strategy. Defaults to random for backwards
     * compatibility with tests that don't carry a seed. The wallet state
     * supplies a [DeterministicSecretFactory] in production so kind:7375
     * loss is recoverable via NUT-09 /v1/restore.
     */
    private val secretFactory: SecretFactory = RandomSecretFactory,
    /**
     * Suspend callback that ensures the NUT-13 seed is materialised in
     * the caller's cache before any blinding op runs. The factory above
     * reads that cache synchronously — without warming first, a fresh
     * wallet falls back to random secrets for its very first mint.
     * Default is a no-op for tests / random-only callers.
     */
    private val seedWarmer: suspend () -> Unit = {},
    /**
     * Returns the wallet's NUT-13 seed bytes, or null if the wallet
     * isn't ready (kind:17375 not decrypted yet). Used by the
     * "outputs already signed" recovery path in [completeMintFromLightning]
     * to re-derive the blinded outputs the mint has already signed.
     * Default returns null — recovery is then skipped and the original
     * mint error propagates.
     */
    private val seedForRestore: suspend () -> ByteArray? = { null },
    /**
     * Inspect the next NUT-13 counter for a keyset without advancing
     * it. Used to rewind the restore window in [completeMintFromLightning]
     * recovery. Default is 0 (no persistent counter store).
     */
    private val peekCashuCounter: (keysetId: String) -> Long = { 0L },
    /**
     * Atomically reserve [count] consecutive NUT-13 counters and
     * return the first reserved index. Used by the recovery path to
     * advance past slots the mint confirmed in use. Default is a
     * no-op for tests / random-only callers.
     */
    private val reserveCashuCounters: (keysetId: String, count: Int) -> Long = { _, _ -> 0L },
) {
    private val opsCache = ConcurrentHashMap<String, CashuMintOperations>()

    private fun ops(mintUrl: String): CashuMintOperations =
        opsCache.getOrPut(mintUrl.trimEnd('/')) {
            CashuMintOperations(MintHttpClient(mintUrl, okHttpClient), secretFactory)
        }

    /**
     * Publish kind:17375 + kind:10019 in one go.
     *
     * The same `mints` list and P2PK key feed both events so that any other
     * client receiving a nutzap to this user will know which mints they
     * accept and which P2PK pubkey to lock against. If [p2pkPrivkeyHex] is
     * null, a fresh key is generated.
     *
     * Returns the generated/used P2PK private key so the caller can persist it
     * across the wallet save flow.
     */
    suspend fun publishWalletEvents(
        mints: List<String>,
        p2pkPrivkeyHex: String?,
        nutzapRelays: List<NormalizedRelayUrl> = emptyList(),
    ): CreatedWallet {
        require(mints.isNotEmpty()) { "Wallet must have at least one mint" }

        val priv = (p2pkPrivkeyHex?.takeIf { it.isNotBlank() } ?: Bdhke.randomScalar().toHexKey())
        val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(priv.hexToByteArray())).toHexKey()

        val walletTemplate = CashuWalletEvent.build(mints, priv, signer)
        val walletEvent = signer.sign(walletTemplate)
        publish(walletEvent)

        // Populate `relay` tags so senders know where to publish nutzaps —
        // without these, they fall back to NIP-65 outbox and may miss our
        // subscription scope on relays we don't read from.
        val nutzapInfoTemplate =
            NutzapInfoEvent.build(
                mints = mints.map { NutzapMintTag(it, listOf("sat")) },
                relays = nutzapRelays,
                p2pkPubkey = pubKeyHex,
            )
        val nutzapInfoEvent = signer.sign(nutzapInfoTemplate)
        publish(nutzapInfoEvent)

        return CreatedWallet(
            walletEvent = walletEvent,
            nutzapInfo = nutzapInfoEvent,
            p2pkPrivkeyHex = priv,
            p2pkPubkeyHex = pubKeyHex,
        )
    }

    /**
     * Mint phase 1: ask the mint for a bolt11 invoice and persist the quote
     * id as a kind:7374 event so we can resume on next launch.
     */
    suspend fun startMintFromLightning(
        mintUrl: String,
        amountSats: Long,
    ): MintQuoteStarted {
        // NUT-20: generate a fresh per-quote keypair, bind the mint quote
        // to it, and persist the privkey inside the encrypted kind:7374
        // so the resume-on-next-launch path can sign the matching mint
        // request later. Mints that don't support NUT-20 ignore the
        // pubkey field (per spec), so always-on is safe.
        val signingPriv = Bdhke.randomScalar()
        val signingPub = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(signingPriv)).toHexKey()
        val response = ops(mintUrl).requestMintQuote(amountSats, signingPubkey = signingPub)
        val quoteTemplate =
            CashuMintQuoteEvent.build(
                quoteId = response.quote,
                mintUrl = mintUrl,
                signer = signer,
                signingPrivkey = signingPriv.toHexKey(),
            )
        val quoteEvent = signer.sign(quoteTemplate)
        publish(quoteEvent)
        return MintQuoteStarted(
            quoteEvent = quoteEvent,
            mintQuote = response,
            invoice = response.request,
        )
    }

    /** Poll the mint to check whether a quote has been paid. */
    suspend fun checkMintQuote(
        mintUrl: String,
        quoteId: String,
    ): MintQuoteBolt11ResponseDto = ops(mintUrl).mintQuoteStatus(quoteId)

    /**
     * User-initiated abandonment of a pending mint quote.
     *
     * NIP-09 deletes the kind:7374 so the quote no longer shows in the
     * pending banner. We do NOT call the mint to cancel — bolt11 invoices
     * just expire on their own, and a fresh quote is cheap if the user
     * changes their mind.
     *
     * No-op if the invoice was already paid: by the time the mint issues
     * proofs and completeMintFromLightning runs, the same NIP-09 delete is
     * fired anyway; running it twice on a missing event is harmless.
     */
    suspend fun cancelMintQuote(quoteEvent: CashuMintQuoteEvent) {
        val delTemplate = DeletionEvent.build(listOf(quoteEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)
    }

    /**
     * Mint phase 2: after the user paid the invoice, exchange the quote for
     * proofs, publish them as kind:7375, log kind:7376, and delete kind:7374.
     *
     * When the mint reports "outputs already signed" / "quote already
     * issued", it means a prior attempt's `/v1/mint/bolt11` succeeded at
     * the mint but we failed to publish the resulting kind:7375 locally
     * (signer dialog dismissed, transient network error, app killed).
     * The mint considers the quote consumed and won't re-issue; new
     * blinded outputs would be rejected too because the quote-budget is
     * spent. NUT-09 restore is the documented recovery path: re-derive
     * the deterministic blinded outputs from the seed, ask the mint
     * which it has signed, unblind those into proofs. The end state
     * matches what we'd have published if the first attempt hadn't
     * crashed.
     */
    suspend fun completeMintFromLightning(
        mintUrl: String,
        quoteEvent: CashuMintQuoteEvent,
        amountSats: Long,
    ): MintCompleted {
        seedWarmer()
        // NUT-20: pull both the quote id and the persisted signing privkey
        // out of the kind:7374 in one decrypt. Legacy quote events stored
        // only the quote id (string), so signingPrivkey is null in that
        // case and we skip the signature — mints that need it will
        // reject, but old quotes opened pre-NUT-20 wouldn't have had a
        // pubkey on them anyway, so the mint should accept.
        val decryptedQuote = quoteEvent.decrypt(signer)
        val tokenContent =
            try {
                ops(mintUrl)
                    .mintProofs(
                        quote = decryptedQuote.quoteId,
                        amountSats = amountSats,
                        signingPrivkey = decryptedQuote.p2pkPriv,
                    ).toTokenContent(mintUrl)
            } catch (e: MintHttpException) {
                if (isOutputsAlreadySignedError(e)) {
                    // Mint already issued for this quote on a prior crashed
                    // attempt. Recover via NUT-09 instead of bailing.
                    recoverPreviouslyIssuedProofs(mintUrl, amountSats)
                        ?: throw MintProtocolException(
                            "Mint already issued for this quote, but seed-based restore found no recoverable proofs",
                        )
                } else {
                    throw e
                }
            }

        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = amountSats,
                tokenReferences =
                    listOf(
                        TokenReference(
                            eventId = tokenEvent.id,
                            relay = null,
                            marker = TokenReference.MARKER_CREATED,
                        ),
                    ),
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        // NIP-09 delete the now-fulfilled quote event.
        val delTemplate = DeletionEvent.build(listOf(quoteEvent))
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)

        return MintCompleted(
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            mintedAmount = amountSats,
        )
    }

    /**
     * NUT-09 fallback for the "outputs already signed" / "quote already
     * issued" mint response — re-derives the deterministic blinded
     * outputs from the seed and asks the mint which it has signed.
     *
     * Scope is intentionally narrow: the prior `/v1/mint/bolt11` call
     * reserved exactly `splitAmounts(amountSats).size` counters and
     * signed one output per slot, so we restrict the restore to
     *   - those amount denominations (skips the ~63-denom fan-out that
     *     would push request size past the mint's 1000-item cap), and
     *   - one batch starting at `peekCashuCounter - DEFAULT_RESTORE_SCAN_BACK`
     *     (the wallet reserved the counters before the failed call, so
     *     the relevant slots sit just below the current high-water mark).
     *
     * Returns null when there's no seed yet (kind:17375 not decrypted)
     * or when the restore turns up no unspent proofs — caller surfaces
     * the failure to the user.
     */
    private suspend fun recoverPreviouslyIssuedProofs(
        mintUrl: String,
        amountSats: Long,
    ): TokenContent? {
        val seed = seedForRestore() ?: return null
        val mintOps = ops(mintUrl)
        val keysetId = mintOps.activeKeyset().id
        val counterBefore = peekCashuCounter(keysetId)
        val startCounter = (counterBefore - DEFAULT_RESTORE_SCAN_BACK).coerceAtLeast(0L)
        // splitAmountIntoDenominations is what the mint flow itself used
        // to decide which amounts to ask /v1/mint/bolt11 for, so the
        // signatures the mint has correspond to exactly these denoms.
        val expectedDenoms = splitAmountIntoDenominations(amountSats).distinct()
        // One batch of DEFAULT_RESTORE_SCAN_BACK counters across just the
        // expected denoms is enough — the proofs we're looking for sit in
        // a contiguous window of `expectedDenoms.size` counters at the top
        // of [startCounter, counterBefore).
        val restoreResult =
            mintOps.restore(
                seed = seed,
                keysetId = keysetId,
                startCounter = startCounter,
                batchSize = DEFAULT_RESTORE_SCAN_BACK.toInt(),
                emptyBatchesToStop = 1,
                amounts = expectedDenoms,
            )
        if (restoreResult.proofs.isEmpty()) return null
        val states = mintOps.checkStates(restoreResult.proofs.map { it.proof })
        val unspent =
            restoreResult.proofs
                .filter { states[it.proof.secret] == ProofState.UNSPENT }
                .map { it.proof }
        if (unspent.isEmpty()) return null
        // Bump the persisted counter past the highest slot we just confirmed
        // in use so the next mint/swap doesn't reuse one of these secrets.
        val current = peekCashuCounter(keysetId)
        val delta = (restoreResult.nextCounterAfterScan - current).coerceAtLeast(0L)
        if (delta > 0) reserveCashuCounters(keysetId, delta.toInt())
        Log.i("CashuWallet") {
            "Recovered ${unspent.size} proof(s) (${unspent.sumOf { it.amount }} sat) via NUT-09 after mint reported quote already issued for $mintUrl"
        }
        return TokenContent(mint = mintUrl, proofs = unspent)
    }

    private fun isOutputsAlreadySignedError(e: MintHttpException): Boolean {
        if (e.code == 10002) return true
        val detail = e.detail?.lowercase().orEmpty()
        return "already signed" in detail ||
            "already issued" in detail ||
            "already minted" in detail
    }

    /**
     * Phase 1 of melt — ask the mint how much an invoice will cost and what
     * its fee_reserve is. Pure read; no state mutation. UI shows the quote to
     * the user; they confirm; the wallet calls [meltToLightning] with the
     * same quote to actually pay.
     */
    suspend fun requestMeltQuote(
        mintUrl: String,
        invoice: String,
    ): MeltQuoteBolt11ResponseDto = ops(mintUrl).requestMeltQuote(invoice)

    /**
     * Phase 2 of melt — pay the invoice using the agreed-upon [quote].
     * Spends [available] token events: picks the smallest subset whose total
     * covers `amount + fee_reserve` (greedy by total amount descending). Any
     * leftover change comes back as proofs in a fresh kind:7375.
     */
    suspend fun meltToLightning(
        mintUrl: String,
        quote: MeltQuoteBolt11ResponseDto,
        available: List<TokenEntry>,
    ): MeltCompleted {
        seedWarmer()
        if (available.isEmpty()) throw IllegalStateException("No proofs available to spend")

        val ops = ops(mintUrl)
        val required = quote.amount + quote.feeReserve

        val (selected, _) = selectProofsCovering(available, required)
        val spendingProofs = selected.flatMap { it.content.proofs }
        val total = spendingProofs.sumOf { it.amount }

        // If the selected proofs overshoot, swap them down to (required) first.
        val (inputs, prePaidChangeEvent) =
            if (total > required) {
                val swap = ops.swap(spendingProofs, targetSplit = required)
                // The keep-side from the pre-swap is the "extra change" we
                // didn't burn into the melt — publish it now so a crashed
                // melt doesn't lose those proofs.
                val keepEvent =
                    if (swap.keep.isNotEmpty()) {
                        val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                        val tokenTemplate = CashuTokenEvent.build(content, signer)
                        val signed = signer.sign(tokenTemplate)
                        publish(signed)
                        signed
                    } else {
                        null
                    }
                swap.send to keepEvent
            } else {
                spendingProofs to null
            }

        val meltResult = ops.meltProofs(quote, inputs)

        // Publish the change proofs (mint may have returned fewer than we
        // requested if it consumed some fees).
        val finalChangeEvent =
            if (meltResult.changeProofs.isNotEmpty()) {
                val delIds =
                    if (prePaidChangeEvent != null) {
                        listOf(prePaidChangeEvent.id)
                    } else {
                        selected.map { it.event.id }
                    }
                val content = TokenContent(mint = mintUrl, proofs = meltResult.changeProofs, del = delIds)
                val tokenTemplate = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(tokenTemplate)
                publish(signed)
                signed
            } else {
                null
            }

        // NIP-09 delete the source token events. Per spec, also need k=7375 tag,
        // which DeletionEvent.build adds automatically from the source event kind.
        val deleteEvent =
            run {
                val toDelete = selected.map { it.event } + listOfNotNull(prePaidChangeEvent.takeIf { finalChangeEvent != null })
                val delTemplate = DeletionEvent.build(toDelete)
                signer.sign(delTemplate).also { publish(it) }
            }

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = quote.amount,
                tokenReferences =
                    buildList {
                        selected.forEach {
                            add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED))
                        }
                        finalChangeEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return MeltCompleted(
            preimage = meltResult.preimage,
            paidAmount = quote.amount,
            fees = total - meltResult.changeProofs.sumOf { it.amount } - quote.amount,
            historyEvent = historyEvent,
            deleteEvent = deleteEvent,
            newTokenEvent = finalChangeEvent,
        )
    }

    /**
     * Mint a token of [amountSats] using a swap of [available] proofs, encode
     * the resulting "send" proofs as a `cashuB` string, and roll over any
     * change into a new kind:7375.
     */
    suspend fun sendAsToken(
        mintUrl: String,
        amountSats: Long,
        available: List<TokenEntry>,
        memo: String? = null,
    ): SendTokenCompleted {
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        val (selected, totalSelected) = selectProofsCovering(available, amountSats)
        if (totalSelected < amountSats) throw IllegalStateException("Insufficient balance for $mintUrl")

        val ops = ops(mintUrl)
        val swap = ops.swap(selected.flatMap { it.content.proofs }, targetSplit = amountSats)

        val tokenString = V4Encoder.encode(mintUrl, swap.send, memo = memo)

        val newKeepEvent =
            if (swap.keep.isNotEmpty()) {
                val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                val template = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(template)
                publish(signed)
                signed
            } else {
                null
            }

        val deleteEvent =
            run {
                val template = DeletionEvent.build(selected.map { it.event })
                signer.sign(template).also { publish(it) }
            }

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = amountSats,
                tokenReferences =
                    buildList {
                        selected.forEach { add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED)) }
                        newKeepEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return SendTokenCompleted(
            cashuToken = tokenString,
            amount = amountSats,
            deleteEvent = deleteEvent,
            keepEvent = newKeepEvent,
            historyEvent = historyEvent,
        )
    }

    /**
     * Swap proofs from an incoming `cashuB` token into our wallet by minting
     * fresh proofs at the same mint. The original token is consumed: re-using
     * its proofs would double-spend.
     *
     * Throws if the token's mint is not configured in our kind:17375 wallet.
     */
    suspend fun redeemToken(
        cashuToken: String,
        proofs: List<CashuProof>,
        mintUrl: String,
        nutzapEventId: String? = null,
    ): RedeemCompleted {
        if (proofs.isEmpty()) throw IllegalArgumentException("Token has no proofs")
        val swap = ops(mintUrl).swap(proofs, targetSplit = null)
        val total = swap.keep.sumOf { it.amount }

        // All output goes to "keep" since targetSplit was null.
        val content = TokenContent(mint = mintUrl, proofs = swap.keep)
        val tokenTemplate = CashuTokenEvent.build(content, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    buildList {
                        add(TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED))
                        nutzapEventId?.let { add(TokenReference(it, null, TokenReference.MARKER_REDEEMED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RedeemCompleted(
            amount = total,
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            rawToken = cashuToken,
        )
    }

    /**
     * Send a NIP-61 nutzap.
     *
     * Spends [available] proofs at [mintUrl] to produce P2PK-locked outputs
     * worth [amountSats] for [recipientPubKey]'s [recipientP2pkPubkeyHex].
     * Publishes a kind:9321 with the locked proofs + message, rolls leftover
     * change into a new kind:7375, NIP-09-deletes the source token events,
     * and records the spend in kind:7376.
     *
     * Throws if `available` doesn't sum to at least [amountSats].
     */
    suspend fun sendNutzap(
        mintUrl: String,
        amountSats: Long,
        recipientPubKey: HexKey,
        recipientP2pkPubkeyHex: String,
        zappedEvent: EventHintBundle<out Event>,
        message: String,
        available: List<TokenEntry>,
        onProgress: ((Float) -> Unit)? = null,
    ): NutzapSent {
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        seedWarmer()
        val (selected, totalSelected) = selectProofsCovering(available, amountSats)
        if (totalSelected < amountSats) throw IllegalStateException("Insufficient balance for $mintUrl")

        val swap =
            ops(mintUrl).swapToLocked(
                proofs = selected.flatMap { it.content.proofs },
                recipientP2pkPubkeyHex = recipientP2pkPubkeyHex,
                targetSplit = amountSats,
            )
        // Mint round-trip done — biggest chunk of the wall clock. Surface
        // visible motion here so the user knows the click registered even
        // before the recipient sees the kind:9321.
        onProgress?.invoke(0.55f)

        // Build the kind:9321 first so we have its id to reference from history.
        val proofJsons = swap.send.map { nutzapProofJson.encodeToString(NutzapProofJson.serializer(), it.toNutzapJson()) }
        val nutzapTemplate =
            NutzapEvent.build(
                message = message,
                proofs = proofJsons,
                mintUrl = mintUrl,
                unit = "sat",
                zappedEvent = zappedEvent,
                recipientPubKey = recipientPubKey,
            )
        val nutzapEvent = signer.sign(nutzapTemplate)
        publish(nutzapEvent)
        // kind:9321 is out — recipient's auto-redeem can fire from here.
        onProgress?.invoke(0.80f)

        // Roll over change locally if any.
        val keepEvent =
            if (swap.keep.isNotEmpty()) {
                val content = TokenContent(mint = mintUrl, proofs = swap.keep, del = selected.map { it.event.id })
                val template = CashuTokenEvent.build(content, signer)
                val signed = signer.sign(template)
                publish(signed)
                signed
            } else {
                null
            }

        // NIP-09 delete the source token events.
        val deleteEvent =
            run {
                val template = DeletionEvent.build(selected.map { it.event })
                signer.sign(template).also {
                    publish(it)
                }
            }
        onProgress?.invoke(0.95f)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.OUT,
                amount = amountSats,
                tokenReferences =
                    buildList {
                        selected.forEach { add(TokenReference(it.event.id, null, TokenReference.MARKER_DESTROYED)) }
                        keepEvent?.let { add(TokenReference(it.id, null, TokenReference.MARKER_CREATED)) }
                    },
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)
        onProgress?.invoke(1.0f)

        return NutzapSent(
            nutzapEvent = nutzapEvent,
            keepEvent = keepEvent,
            deleteEvent = deleteEvent,
            historyEvent = historyEvent,
            amount = amountSats,
        )
    }

    private fun CashuProof.toNutzapJson() =
        NutzapProofJson(
            id = id,
            amount = amount,
            secret = secret,
            c = c,
            witness = witness,
            // Forward the NUT-12 DLEQ tuple if we have it. Lets the
            // recipient Carol-verify the proofs against the mint's
            // keyset key offline — without it they're stuck with
            // spend-time validation.
            dleq =
                dleq?.let { src ->
                    NutzapDleqJson(e = src.e, s = src.s, r = src.r)
                },
        )

    /**
     * Redeem an inbound NIP-61 nutzap.
     *
     * Steps:
     *   1. Pull `proof` tags (JSON-encoded NUT-11 P2PK-locked proofs) and the
     *      mint URL from the nutzap event.
     *   2. Reject the redemption if the mint isn't one we have configured in
     *      our wallet (the sender should have respected our kind:10019).
     *   3. Sign the unlock witness for each proof with our wallet's P2PK
     *      private key, then submit a swap to the mint to get fresh,
     *      unlocked proofs.
     *   4. Publish a new kind:7375 holding the swapped proofs and a kind:7376
     *      with the `e=<nutzap-id>,marker=redeemed` tag left unencrypted per
     *      the spec.
     *
     * Returns [RedeemCompleted] on success; throws on any failure.
     */
    suspend fun redeemNutzap(
        nutzap: NutzapEvent,
        walletPrivkeyHex: String,
        walletP2pkPubkeyHex: String,
    ): RedeemCompleted {
        seedWarmer()
        val mintUrl =
            nutzap.mintUrl()
                ?: throw IllegalArgumentException("Nutzap has no mint tag")
        val proofJsons = nutzap.proofs()
        if (proofJsons.isEmpty()) throw IllegalArgumentException("Nutzap has no proofs")

        val parsedProofs =
            proofJsons.map { js ->
                val dto = nutzapProofJson.decodeFromString<NutzapProofJson>(js)
                CashuProof(
                    id = dto.id,
                    amount = dto.amount,
                    secret = dto.secret,
                    c = dto.c,
                    dleq =
                        dto.dleq?.let { d ->
                            DleqProofDto(e = d.e, s = d.s, r = d.r)
                        },
                )
            }

        // Verify the lock points at us BEFORE any mint round-trip. The
        // pubkey in the P2PK secret can be 64-char x-only or 66-char
        // compressed; normalize to x-only for the comparison since BIP-340
        // verification (which the mint uses) is parity-agnostic. Doing
        // this first means a nutzap locked to a stale wallet key (sender
        // saw an old kind:10019 and locked to that pubkey, we've since
        // rotated) is rejected without burning a /v1/keys fetch — and
        // without the auto-redeem loop hammering the mint on every
        // triggerAutoRedeem.
        val ourXOnly = walletP2pkPubkeyHex.lastHex64()
        parsedProofs.forEach { proof ->
            val parsed =
                P2PK.parseSecret(proof.secret)
                    ?: throw IllegalArgumentException("Nutzap proof is not P2PK-locked")
            if (parsed.pubKeyHex.lastHex64() != ourXOnly) {
                throw IllegalArgumentException(
                    "Nutzap proof is locked to a different pubkey (${parsed.pubKeyHex.take(16)}…)",
                )
            }
        }

        // NUT-12 §3 Carol verification on the incoming proofs. When the
        // sender includes the dleq tuple we can check the proofs against
        // the mint's keyset key BEFORE swapping — defends against a
        // malicious sender handing us proofs the mint won't honour at
        // spend time. Verification is best-effort: legacy nutzaps
        // without dleq skip the check (returns true) and we fall back
        // to spend-time validation.
        val dleqOk = ops(mintUrl).verifyTokenDleq(parsedProofs)
        if (!dleqOk) {
            throw MintProtocolException(
                "NUT-12 Carol verification failed on inbound nutzap — proofs don't match mint's published keys",
            )
        }

        val swap = ops(mintUrl).redeemNutzap(parsedProofs, walletPrivkeyHex)
        val total = swap.keep.sumOf { it.amount }

        val tokenContent = TokenContent(mint = mintUrl, proofs = swap.keep)
        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    listOf(
                        TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED),
                        TokenReference(nutzap.id, null, TokenReference.MARKER_REDEEMED),
                    ),
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RedeemCompleted(
            amount = total,
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            rawToken = "nutzap:${nutzap.id}",
        )
    }

    /**
     * Greedy proof selection: sort by amount descending, take entries until
     * the running total covers [target]. Returns the picked entries + their
     * total.
     */
    private fun selectProofsCovering(
        available: List<TokenEntry>,
        target: Long,
    ): Pair<List<TokenEntry>, Long> {
        val sorted = available.sortedByDescending { it.content.totalAmount() }
        var running = 0L
        val picked = mutableListOf<TokenEntry>()
        for (entry in sorted) {
            if (running >= target) break
            picked += entry
            running += entry.content.totalAmount()
        }
        return picked to running
    }

    /**
     * Ping `/v1/info` on [mintUrl] and return the mint's display name on
     * success. Used by the Add-Mint UI to give immediate feedback when a URL
     * is typo'd, points at a non-Cashu host, or is otherwise unreachable.
     *
     * Throws on failure so the UI can surface the underlying reason.
     */
    suspend fun pingMint(mintUrl: String): String? = MintHttpClient(mintUrl, okHttpClient).info().name

    /**
     * Fetch the currently-active keyset id for [mintUrl]. Cheap wrapper
     * over [CashuMintOperations.activeKeyset] used by the migration
     * driver to detect proofs minted under rotated-out keysets — see
     * [CashuWalletState.migrateStaleKeysets].
     */
    suspend fun fetchActiveKeysetId(mintUrl: String): String = ops(mintUrl).activeKeyset().id

    /**
     * NUT-07 batched proof-state query. Returns a map keyed by each
     * proof's secret to the mint-side state. Used by the wallet's
     * stale-proof sweep to detect kind:7375 events whose proofs are
     * already spent at the mint (so the parent event can be NIP-09
     * deleted before the next send picks them and hits HTTP 400
     * "proofs already spent").
     */
    suspend fun checkProofStates(
        mintUrl: String,
        proofs: List<CashuProof>,
    ): Map<String, ProofState> = ops(mintUrl).checkStates(proofs)

    /**
     * Swap every proof inside [entries] (which the caller has already
     * filtered to "contains at least one stale-keyset proof") onto the
     * mint's current active keyset, publish the result as a single
     * fresh kind:7375 event, and NIP-09 the originals. Mirrors the
     * change-rollover pattern used by [sendNutzap].
     *
     * No-op when [entries] is empty. The [activeKeysetId] is passed in
     * by the caller so we don't double-fetch /v1/keys between
     * [fetchActiveKeysetId] and this method.
     */
    suspend fun migrateToActiveKeyset(
        mintUrl: String,
        entries: List<TokenEntry>,
        activeKeysetId: String,
    ): MigrationResult {
        if (entries.isEmpty()) return MigrationResult(amountMigrated = 0L, proofsMigrated = 0)
        seedWarmer()
        val allProofs = entries.flatMap { it.content.proofs }
        // Defensive: skip if every input is already on the active
        // keyset. Caller is supposed to pre-filter but a race against
        // mint rotation could land us here with nothing to do.
        if (allProofs.all { it.id == activeKeysetId }) {
            return MigrationResult(amountMigrated = 0L, proofsMigrated = 0)
        }
        val swap = ops(mintUrl).swap(allProofs, targetSplit = null)
        val migratedTotal = swap.keep.sumOf { it.amount }

        // Publish the consolidated proofs as one kind:7375 and delete
        // the originals.
        val tokenContent =
            TokenContent(
                mint = mintUrl,
                proofs = swap.keep,
                del = entries.map { it.event.id },
            )
        val template = CashuTokenEvent.build(tokenContent, signer)
        val signed = signer.sign(template)
        publish(signed)

        val delTemplate = DeletionEvent.build(entries.map { it.event })
        val delEvent = signer.sign(delTemplate)
        publish(delEvent)

        return MigrationResult(
            amountMigrated = migratedTotal,
            proofsMigrated = swap.keep.size,
        )
    }

    /**
     * Publish a NIP-87 kind:38000 recommendation for [mintUrl].
     *
     * The recommendation is keyed by the announcement's d-tag (mint pubkey)
     * when one is known — that makes it discoverable via `#a` queries that
     * scope by mint identity. We always emit a `u` tag with the raw URL too
     * so older clients that index by URL still pick it up. [review] is an
     * optional free-text body (kind:38000 content).
     */
    suspend fun recommendMint(
        mintUrl: String,
        mintAnnouncementDTag: String? = null,
        review: String = "",
    ): MintRecommendationEvent {
        val template =
            MintRecommendationEvent.build(
                mintIdentifier = mintAnnouncementDTag ?: mintUrl,
                mintKind = CashuMintEvent.KIND,
                review = review,
                mintUrls = listOf(mintUrl),
            )
        val event = signer.sign(template)
        publish(event)
        return event
    }

    /**
     * NIP-09 retract a previously-published mint recommendation.
     *
     * kind:38000 is parameterized-replaceable — a delete with an `a` tag
     * pointing at the address coordinate (kind:pubkey:dTag) drops all
     * versions on compliant relays. We also include the original event id
     * via DeletionEvent.build so relays that only track by event id still
     * remove it. MintRecommendationEvent doesn't extend AddressableEvent
     * today, so we compute and add the `a` tag ourselves.
     */
    suspend fun deleteRecommendation(event: MintRecommendationEvent) {
        // Add the `a` tag when we have a d-tag — kind:38000 is parameterized-
        // replaceable, so the address coordinate lets compliant relays drop
        // all versions, not just the specific id. Recommendations without a
        // d-tag still get a NIP-09 `e`-only delete (the default build path).
        val dTag = event.dTag()
        val template =
            DeletionEvent.build(listOf(event)) {
                if (dTag != null) aTag(Address(event.kind, event.pubKey, dTag))
            }
        val delEvent = signer.sign(template)
        publish(delEvent)
    }

    /**
     * NUT-09 wallet restore — recover proofs the wallet previously
     * minted at [mintUrl] but whose kind:7375 token events have been lost
     * (rogue relay, NIP-09 from a compromised key, fresh device with no
     * cache backup). Uses the wallet's NUT-13 seed to re-derive every
     * past secret/r pair and asks the mint which it has signed.
     *
     * Process:
     *  1. Fetch the mint's active keyset.
     *  2. Drive [CashuMintOperations.restore] — scans counters in batches
     *     until [emptyBatchesToStop] empty batches in a row signal
     *     "this keyset has no more proofs further down".
     *  3. Filter the returned proofs through /v1/checkstate to keep
     *     only UNSPENT ones — NUT-09 alone returns even spent proofs.
     *  4. Publish surviving proofs as one kind:7375.
     *  5. Bump the persisted counter past the highest seen so future
     *     mints don't reuse a slot we just confirmed in use.
     *
     * Returns the number of unspent sats recovered. The caller's UI
     * surfaces this to the user.
     *
     * [existingSecrets] is the set of NUT-00 `secret` strings the wallet
     * already holds locally (across all kind:7375 events). Recovered
     * proofs whose secret is already in this set are skipped — they're
     * not "newly recovered", just re-derived copies of proofs we already
     * accounted for. Without this filter every Resync click would
     * publish a fresh kind:7375 with the same proofs and inflate the
     * balance, since the balance sums proofs across every kind:7375.
     *
     * Idempotent — re-running after a successful restore returns 0
     * (every counter still produces the same proofs the mint already
     * marked spent in the previous round of publishing + redemption,
     * AND every UNSPENT slot is now in [existingSecrets]).
     */
    suspend fun restoreFromMint(
        mintUrl: String,
        seed: ByteArray,
        startCounter: Long = 0L,
        existingSecrets: Set<String> = emptySet(),
    ): RestoreOutcome {
        seedWarmer()
        val mintOps = ops(mintUrl)
        val activeKeyset = mintOps.activeKeyset()
        val result =
            mintOps.restore(
                seed = seed,
                keysetId = activeKeyset.id,
                startCounter = startCounter,
            )
        if (result.proofs.isEmpty()) {
            return RestoreOutcome(
                keysetId = result.keysetId,
                amountRecoveredSats = 0L,
                proofsRecovered = 0,
                nextCounterAfterScan = result.nextCounterAfterScan,
                tokenEvent = null,
            )
        }

        // /v1/checkstate filters out proofs that were minted but already
        // melted or sent. Without this, recovered "balance" would include
        // already-spent proofs that the mint would reject at next swap.
        val stateMap = mintOps.checkStates(result.proofs.map { it.proof })
        val unspent =
            result.proofs.filter { recovered ->
                stateMap[recovered.proof.secret] == ProofState.UNSPENT &&
                    recovered.proof.secret !in existingSecrets
            }
        if (unspent.isEmpty()) {
            return RestoreOutcome(
                keysetId = result.keysetId,
                amountRecoveredSats = 0L,
                proofsRecovered = 0,
                nextCounterAfterScan = result.nextCounterAfterScan,
                tokenEvent = null,
            )
        }

        val totalSats = unspent.sumOf { it.proof.amount }
        val tokenContent =
            TokenContent(
                mint = mintUrl,
                proofs = unspent.map { it.proof },
            )
        val tokenTemplate = CashuTokenEvent.build(tokenContent, signer)
        val tokenEvent = signer.sign(tokenTemplate)
        publish(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = totalSats,
                tokenReferences =
                    listOf(
                        TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED),
                    ),
                signer = signer,
            )
        val historyEvent = signer.sign(historyTemplate)
        publish(historyEvent)

        return RestoreOutcome(
            keysetId = result.keysetId,
            amountRecoveredSats = totalSats,
            proofsRecovered = unspent.size,
            nextCounterAfterScan = result.nextCounterAfterScan,
            tokenEvent = tokenEvent,
        )
    }

    companion object {
        /**
         * How far to rewind the persisted counter when looking for proofs
         * the mint already signed but we never published. A previous mint
         * call reserves contiguous slots ending at the current counter,
         * and a typical receive splits into <= 16 power-of-two outputs;
         * 32 is a comfortable upper bound that still keeps the restore
         * cheap (one /v1/restore batch).
         */
        private const val DEFAULT_RESTORE_SCAN_BACK: Long = 32L
    }
}

/** Result of a keyset migration. See [CashuWalletOps.migrateToActiveKeyset]. */
data class MigrationResult(
    val amountMigrated: Long,
    val proofsMigrated: Int,
)

/** Result of a NUT-09 wallet restore. See [CashuWalletOps.restoreFromMint]. */
data class RestoreOutcome(
    val keysetId: String,
    val amountRecoveredSats: Long,
    val proofsRecovered: Int,
    val nextCounterAfterScan: Long,
    val tokenEvent: CashuTokenEvent?,
)

/** Drop the leading parity byte if present so two pubkeys can be compared. */
private fun String.lastHex64(): String = if (length == 66) substring(2) else this

/** Catches mint HTTP / protocol errors and surfaces their detail message. */
fun describeMintError(e: Throwable): String =
    when (e) {
        is MintHttpException -> "Mint error (HTTP ${e.httpStatus}): ${e.detail ?: e.message}"
        is MintProtocolException -> "Mint refused: ${e.message}"
        else -> e.message ?: e::class.simpleName ?: "Unknown error"
    }

/** A decrypted, unspent token event ready to be spent. */
data class TokenEntry(
    val event: CashuTokenEvent,
    val content: TokenContent,
)

@Serializable
private data class NutzapProofJson(
    val id: String,
    val amount: Long,
    val secret: String,
    @SerialName("C") val c: String,
    val witness: String? = null,
    /**
     * NUT-12 DLEQ tuple — `(e, s, r)` where `r` is the original
     * blinding factor the sender's wallet used at mint time. Carried
     * along the nutzap so the recipient can verify the proof against
     * the mint's keyset key WITHOUT a mint round-trip (Carol
     * verification). Optional — older sender wallets omit it; we then
     * fall back to spend-time validation.
     */
    val dleq: NutzapDleqJson? = null,
)

@Serializable
private data class NutzapDleqJson(
    val e: String,
    val s: String,
    val r: String? = null,
)

private val nutzapProofJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

data class CreatedWallet(
    val walletEvent: CashuWalletEvent,
    val nutzapInfo: NutzapInfoEvent,
    val p2pkPrivkeyHex: String,
    val p2pkPubkeyHex: String,
)

data class MintQuoteStarted(
    val quoteEvent: CashuMintQuoteEvent,
    val mintQuote: MintQuoteBolt11ResponseDto,
    val invoice: String,
)

data class MintCompleted(
    val tokenEvent: CashuTokenEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val mintedAmount: Long,
)

data class MeltCompleted(
    val preimage: String?,
    val paidAmount: Long,
    val fees: Long,
    val historyEvent: CashuSpendingHistoryEvent,
    val deleteEvent: DeletionEvent,
    val newTokenEvent: CashuTokenEvent?,
)

data class SendTokenCompleted(
    val cashuToken: String,
    val amount: Long,
    val deleteEvent: DeletionEvent,
    val keepEvent: CashuTokenEvent?,
    val historyEvent: CashuSpendingHistoryEvent,
)

data class RedeemCompleted(
    val amount: Long,
    val tokenEvent: CashuTokenEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val rawToken: String,
)

data class NutzapSent(
    val nutzapEvent: NutzapEvent,
    val keepEvent: CashuTokenEvent?,
    val deleteEvent: DeletionEvent,
    val historyEvent: CashuSpendingHistoryEvent,
    val amount: Long,
)
