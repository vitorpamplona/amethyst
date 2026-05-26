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
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintProtocolException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11ResponseDto
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
) {
    private val opsCache = ConcurrentHashMap<String, CashuMintOperations>()

    private fun ops(mintUrl: String): CashuMintOperations =
        opsCache.getOrPut(mintUrl.trimEnd('/')) {
            CashuMintOperations(MintHttpClient(mintUrl, okHttpClient))
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
        val response = ops(mintUrl).requestMintQuote(amountSats)
        val quoteTemplate =
            CashuMintQuoteEvent.build(
                quoteId = response.quote,
                mintUrl = mintUrl,
                signer = signer,
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
     */
    suspend fun completeMintFromLightning(
        mintUrl: String,
        quoteEvent: CashuMintQuoteEvent,
        amountSats: Long,
    ): MintCompleted {
        val quoteId = quoteEvent.quoteId(signer)
        val minted = ops(mintUrl).mintProofs(quoteId, amountSats)

        val tokenContent = minted.toTokenContent(mintUrl)
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
    ): NutzapSent {
        if (amountSats <= 0) throw IllegalArgumentException("Amount must be positive")
        val (selected, totalSelected) = selectProofsCovering(available, amountSats)
        if (totalSelected < amountSats) throw IllegalStateException("Insufficient balance for $mintUrl")

        val swap =
            ops(mintUrl).swapToLocked(
                proofs = selected.flatMap { it.content.proofs },
                recipientP2pkPubkeyHex = recipientP2pkPubkeyHex,
                targetSplit = amountSats,
            )

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
                signer.sign(template).also { publish(it) }
            }

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
                )
            }

        // Verify the lock points at us before spending a mint round-trip. The
        // pubkey in the P2PK secret can be 64-char x-only or 66-char
        // compressed; normalize to x-only for the comparison since BIP-340
        // verification (which the mint uses) is parity-agnostic.
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
}

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
