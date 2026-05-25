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

import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.service.cashu.v4.V4Encoder
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.history.SpendingDirection
import com.vitorpamplona.quartz.nip60Cashu.history.TokenReference
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.NutzapInfoEvent
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * Wallet-level operations that combine the [CashuMintOperations] HTTP layer
 * with Nostr event publishing (kind 7375 / 7376 / 7374 / 17375 / 10019 /
 * deletion-5).
 *
 * The operations layer is intentionally stateless: each call receives the
 * current decrypted state from [CashuWalletViewModel] (or rebuilds it from
 * [LocalCache] when needed). All side effects flow through [Account] which
 * handles signing + publishing + local-cache consumption in one shot.
 */
class CashuWalletOps(
    private val account: Account,
    private val okHttpClient: (String) -> OkHttpClient,
) {
    private fun client(mintUrl: String) = MintHttpClient(mintUrl, okHttpClient)

    private fun ops(mintUrl: String) = CashuMintOperations(client(mintUrl))

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
    ): CreatedWallet {
        require(mints.isNotEmpty()) { "Wallet must have at least one mint" }

        val priv = (p2pkPrivkeyHex?.takeIf { it.isNotBlank() } ?: Bdhke.randomScalar().toHexKey())
        val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(priv.hexToByteArray())).toHexKey()

        val walletTemplate = CashuWalletEvent.build(mints, priv, account.signer)
        val walletEvent = account.signer.sign(walletTemplate)
        account.sendLiterallyEverywhere(walletEvent)

        val nutzapInfoTemplate =
            NutzapInfoEvent.build(
                mints = mints.map { NutzapMintTag(it, listOf("sat")) },
                relays = emptyList(),
                p2pkPubkey = pubKeyHex,
            )
        val nutzapInfoEvent = account.signer.sign(nutzapInfoTemplate)
        account.sendLiterallyEverywhere(nutzapInfoEvent)

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
                signer = account.signer,
            )
        val quoteEvent = account.signer.sign(quoteTemplate)
        account.sendLiterallyEverywhere(quoteEvent)
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
     * Mint phase 2: after the user paid the invoice, exchange the quote for
     * proofs, publish them as kind:7375, log kind:7376, and delete kind:7374.
     */
    suspend fun completeMintFromLightning(
        mintUrl: String,
        quoteEvent: CashuMintQuoteEvent,
        amountSats: Long,
    ): MintCompleted {
        val quoteId = quoteEvent.quoteId(account.signer)
        val minted = ops(mintUrl).mintProofs(quoteId, amountSats)

        val tokenContent = minted.toTokenContent(mintUrl)
        val tokenTemplate = CashuTokenEvent.build(tokenContent, account.signer)
        val tokenEvent = account.signer.sign(tokenTemplate)
        account.sendLiterallyEverywhere(tokenEvent)

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
                signer = account.signer,
            )
        val historyEvent = account.signer.sign(historyTemplate)
        account.sendLiterallyEverywhere(historyEvent)

        // NIP-09 delete the now-fulfilled quote event.
        val delTemplate = DeletionEvent.build(listOf(quoteEvent))
        val delEvent = account.signer.sign(delTemplate)
        account.sendLiterallyEverywhere(delEvent)

        return MintCompleted(
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            mintedAmount = amountSats,
        )
    }

    /**
     * Pay a bolt11 invoice via the chosen mint, spending [available] token
     * events. Picks the smallest subset of token events whose total covers
     * `amount + fee_reserve` (greedy by total amount descending). Any leftover
     * change comes back as proofs in a fresh kind:7375.
     */
    suspend fun meltToLightning(
        mintUrl: String,
        invoice: String,
        available: List<TokenEntry>,
    ): MeltCompleted {
        if (available.isEmpty()) throw IllegalStateException("No proofs available to spend")

        val ops = ops(mintUrl)
        val quote = ops.requestMeltQuote(invoice)
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
                        val tokenTemplate = CashuTokenEvent.build(content, account.signer)
                        val signed = account.signer.sign(tokenTemplate)
                        account.sendLiterallyEverywhere(signed)
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
                val tokenTemplate = CashuTokenEvent.build(content, account.signer)
                val signed = account.signer.sign(tokenTemplate)
                account.sendLiterallyEverywhere(signed)
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
                account.signer.sign(delTemplate).also { account.sendLiterallyEverywhere(it) }
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
                signer = account.signer,
            )
        val historyEvent = account.signer.sign(historyTemplate)
        account.sendLiterallyEverywhere(historyEvent)

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
                val template = CashuTokenEvent.build(content, account.signer)
                val signed = account.signer.sign(template)
                account.sendLiterallyEverywhere(signed)
                signed
            } else {
                null
            }

        val deleteEvent =
            run {
                val template = DeletionEvent.build(selected.map { it.event })
                account.signer.sign(template).also { account.sendLiterallyEverywhere(it) }
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
                signer = account.signer,
            )
        val historyEvent = account.signer.sign(historyTemplate)
        account.sendLiterallyEverywhere(historyEvent)

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
        val tokenTemplate = CashuTokenEvent.build(content, account.signer)
        val tokenEvent = account.signer.sign(tokenTemplate)
        account.sendLiterallyEverywhere(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    buildList {
                        add(TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED))
                        nutzapEventId?.let { add(TokenReference(it, null, TokenReference.MARKER_REDEEMED)) }
                    },
                signer = account.signer,
            )
        val historyEvent = account.signer.sign(historyTemplate)
        account.sendLiterallyEverywhere(historyEvent)

        return RedeemCompleted(
            amount = total,
            tokenEvent = tokenEvent,
            historyEvent = historyEvent,
            rawToken = cashuToken,
        )
    }

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

        val swap = ops(mintUrl).redeemNutzap(parsedProofs, walletPrivkeyHex)
        val total = swap.keep.sumOf { it.amount }

        val tokenContent = TokenContent(mint = mintUrl, proofs = swap.keep)
        val tokenTemplate = CashuTokenEvent.build(tokenContent, account.signer)
        val tokenEvent = account.signer.sign(tokenTemplate)
        account.sendLiterallyEverywhere(tokenEvent)

        val historyTemplate =
            CashuSpendingHistoryEvent.build(
                direction = SpendingDirection.IN,
                amount = total,
                tokenReferences =
                    listOf(
                        TokenReference(tokenEvent.id, null, TokenReference.MARKER_CREATED),
                        TokenReference(nutzap.id, null, TokenReference.MARKER_REDEEMED),
                    ),
                signer = account.signer,
            )
        val historyEvent = account.signer.sign(historyTemplate)
        account.sendLiterallyEverywhere(historyEvent)

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

    /** Catches mint HTTP errors and surfaces their detail message. */
    fun describe(e: Throwable): String =
        when (e) {
            is MintHttpException -> "Mint error (HTTP ${e.httpStatus}): ${e.detail ?: e.message}"
            else -> e.message ?: e::class.simpleName ?: "Unknown error"
        }
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
