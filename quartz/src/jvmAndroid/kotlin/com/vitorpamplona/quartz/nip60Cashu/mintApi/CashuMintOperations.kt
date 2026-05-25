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
package com.vitorpamplona.quartz.nip60Cashu.mintApi

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent

/**
 * High-level mint operations: mint-from-LN, swap, melt-to-LN, send-as-token,
 * redeem-token.
 *
 * Composes [MintHttpClient] + [Bdhke] + amount splitting. Pure logic — no
 * Nostr publishing, no UI. The amethyst-layer `CashuWalletOps` wraps this with
 * Account.sendLiterallyEverywhere(...) and the kind:7375/7376/7374 event
 * lifecycle the spec requires.
 */
class CashuMintOperations(
    private val client: MintHttpClient,
) {
    /**
     * Step 1 of mint-from-LN: ask the mint for a bolt11 invoice for `amount`
     * sats. Returns the quote, which contains the invoice the user must pay.
     *
     * The wallet should publish a kind:7374 quote event before returning to the
     * UI so that an interrupted flow can be recovered on next login.
     */
    suspend fun requestMintQuote(amountSats: Long): MintQuoteBolt11ResponseDto =
        client.mintQuoteBolt11(
            MintQuoteBolt11RequestDto(unit = "sat", amount = amountSats),
        )

    suspend fun mintQuoteStatus(quote: String): MintQuoteBolt11ResponseDto = client.mintQuoteBolt11Status(quote)

    /**
     * Step 2 of mint-from-LN: after the user pays the invoice, exchange the
     * paid quote for [amountSats] worth of proofs.
     *
     * Generates [amountSats] split into power-of-2 denominations, blinds each
     * output, posts to /v1/mint/bolt11, unblinds the responses, and returns
     * fresh proofs. The caller is responsible for publishing the proofs as
     * one kind:7375 event.
     */
    suspend fun mintProofs(
        quote: String,
        amountSats: Long,
    ): MintedProofs {
        val keyset = fetchKeyset()
        val outputs = createBlindedOutputs(amountSats, keyset)

        val response =
            client.mintBolt11(
                MintBolt11RequestDto(
                    quote = quote,
                    outputs = outputs.map { it.toDto() },
                ),
            )
        val proofs = unblindAll(outputs, response.signatures, keyset)
        return MintedProofs(proofs, keyset.id)
    }

    /**
     * Swap a set of proofs for new proofs.
     *
     * If [targetSplit] is supplied (positive Long), [targetSplit] sats are
     * blinded as one bucket and the remainder as change. Useful when sending:
     * the first bucket becomes the token to ship, the rest stays in the
     * wallet. If [targetSplit] is null, all amounts are reshuffled by power-of-2.
     */
    suspend fun swap(
        proofs: List<CashuProof>,
        targetSplit: Long? = null,
    ): SwapResult {
        if (proofs.isEmpty()) throw IllegalArgumentException("Nothing to swap")
        val total = proofs.sumOf { it.amount }
        if (targetSplit != null) {
            if (targetSplit <= 0) throw IllegalArgumentException("Target split must be > 0")
            if (targetSplit > total) throw IllegalArgumentException("Target split exceeds available proofs")
        }

        val keyset = fetchKeyset()

        val sendOutputs =
            if (targetSplit != null) splitAmounts(targetSplit).map { secretOutputFor(it, keyset) } else emptyList()
        val keepOutputs =
            splitAmounts(
                if (targetSplit != null) total - targetSplit else total,
            ).map { secretOutputFor(it, keyset) }

        val allOutputs = sendOutputs + keepOutputs

        val response =
            client.swap(
                SwapRequestDto(
                    inputs = proofs.map { it.toDto() },
                    outputs = allOutputs.map { it.toDto() },
                ),
            )

        // The mint MUST return one signature per output, in the same order.
        // We cannot match by content alone (signatures only echo `amount` +
        // `id`, not the blinded message `B_`), so the protocol relies on
        // positional correspondence. nutshell + cdk + cashu-ts all preserve
        // this — kept here as an invariant, not a heuristic.
        if (response.signatures.size != allOutputs.size) {
            throw MintProtocolException(
                "Mint returned ${response.signatures.size} signatures for ${allOutputs.size} outputs",
            )
        }

        val unblinded = unblindAll(allOutputs, response.signatures, keyset)
        val sendProofs = unblinded.subList(0, sendOutputs.size)
        val keepProofs = unblinded.subList(sendOutputs.size, unblinded.size)
        return SwapResult(send = sendProofs, keep = keepProofs, keysetId = keyset.id)
    }

    /**
     * Swap NIP-61 nutzap proofs (locked with NUT-11 P2PK) into fresh unlocked
     * proofs by signing the unlock witness with our wallet's P2PK private key
     * and routing through /v1/swap.
     *
     * Returns the unlocked proofs (all in [SwapResult.keep] since there's no
     * target split — we're absorbing all of the incoming amount).
     */
    suspend fun redeemNutzap(
        lockedProofs: List<CashuProof>,
        walletPrivkeyHex: String,
    ): SwapResult {
        if (lockedProofs.isEmpty()) throw IllegalArgumentException("No proofs to redeem")
        val unlocked =
            lockedProofs.map { proof ->
                P2PK.parseSecret(proof.secret)
                    ?: throw IllegalArgumentException("Proof secret is not a NUT-11 P2PK secret")
                val witness = P2PK.signWitness(proof.secret, walletPrivkeyHex)
                proof.copy(witness = witness)
            }
        return swap(unlocked, targetSplit = null)
    }

    /**
     * Swap our unlocked proofs so that [targetSplit] sats are returned as
     * NUT-11 P2PK-locked proofs (recipient-spendable only with their
     * private key), and the remainder stays unlocked in our wallet.
     *
     * The caller bundles `result.send` into a kind:9321 NutzapEvent and
     * keeps `result.keep` as the change in a new kind:7375.
     */
    suspend fun swapToLocked(
        proofs: List<CashuProof>,
        recipientP2pkPubkeyHex: String,
        targetSplit: Long,
    ): SwapResult {
        if (proofs.isEmpty()) throw IllegalArgumentException("Nothing to swap")
        if (targetSplit <= 0) throw IllegalArgumentException("Target split must be > 0")
        val total = proofs.sumOf { it.amount }
        if (targetSplit > total) throw IllegalArgumentException("Target split exceeds available proofs")

        val keyset = fetchKeyset()
        val sendOutputs = splitAmounts(targetSplit).map { lockedOutputFor(it, keyset, recipientP2pkPubkeyHex) }
        val keepOutputs = splitAmounts(total - targetSplit).map { secretOutputFor(it, keyset) }
        val allOutputs = sendOutputs + keepOutputs

        val response =
            client.swap(
                SwapRequestDto(
                    inputs = proofs.map { it.toDto() },
                    outputs = allOutputs.map { it.toDto() },
                ),
            )

        if (response.signatures.size != allOutputs.size) {
            throw MintProtocolException(
                "Mint returned ${response.signatures.size} signatures for ${allOutputs.size} outputs",
            )
        }

        val unblinded = unblindAll(allOutputs, response.signatures, keyset)
        val sendProofs = unblinded.subList(0, sendOutputs.size)
        val keepProofs = unblinded.subList(sendOutputs.size, unblinded.size)
        return SwapResult(send = sendProofs, keep = keepProofs, keysetId = keyset.id)
    }

    /**
     * Ask the mint for a bolt11 melt quote: how much in fees this invoice will
     * cost on top of its amount. Caller selects the proofs that cover
     * `amount + fee_reserve` and passes them to [meltProofs].
     */
    suspend fun requestMeltQuote(invoice: String): MeltQuoteBolt11ResponseDto =
        client.meltQuoteBolt11(
            MeltQuoteBolt11RequestDto(unit = "sat", request = invoice),
        )

    suspend fun meltQuoteStatus(quote: String): MeltQuoteBolt11ResponseDto = client.meltQuoteBolt11Status(quote)

    /**
     * Pay the bolt11 invoice. Spends [inputs], which must total at least
     * `quote.amount + quote.fee_reserve`. Any change is returned blinded so the
     * mint signs unused fee proofs that the wallet can later spend.
     */
    suspend fun meltProofs(
        quote: MeltQuoteBolt11ResponseDto,
        inputs: List<CashuProof>,
    ): MeltResult {
        val total = inputs.sumOf { it.amount }
        val required = quote.amount + quote.feeReserve
        if (total < required) throw IllegalArgumentException("Inputs total $total < required $required")

        val keyset = fetchKeyset()
        // Pre-blind change outputs at the fee_reserve denominations so the
        // mint can return whatever fees were not consumed.
        val changeAmount = total - quote.amount // upper bound; mint will use ≤ this much
        val changeOutputs =
            if (changeAmount > 0) splitAmounts(changeAmount).map { secretOutputFor(it, keyset) } else emptyList()

        val response =
            client.meltBolt11(
                MeltBolt11RequestDto(
                    quote = quote.quote,
                    inputs = inputs.map { it.toDto() },
                    outputs = changeOutputs.map { it.toDto() }.ifEmpty { null },
                ),
            )

        val paid = response.paid == true || response.state == "PAID"
        if (!paid) {
            throw MintProtocolException("Melt not completed (state=${response.state})")
        }

        // Unblind any change the mint returned. The mint can return *fewer*
        // change signatures than we provided outputs for (only the actually-
        // unused fee gets signed) — we match by output amount.
        val changeProofs =
            response.change?.let { sigs ->
                val byAmount = changeOutputs.associateBy { it.amount }.toMutableMap()
                val out = mutableListOf<CashuProof>()
                for (sig in sigs) {
                    val src =
                        byAmount.remove(sig.amount)
                            ?: throw IllegalStateException("Mint returned change for amount ${sig.amount} we didn't request")
                    out += unblindOne(src, sig, keyset)
                }
                out
            } ?: emptyList()

        return MeltResult(
            preimage = response.payment_preimage,
            changeProofs = changeProofs,
            keysetId = keyset.id,
        )
    }

    private suspend fun fetchKeyset(): KeysetDto {
        val response = client.activeKeysets()
        return response.keysets.firstOrNull { it.unit == "sat" }
            ?: response.keysets.firstOrNull()
            ?: throw IllegalStateException("Mint exposes no keysets")
    }

    private fun createBlindedOutputs(
        amount: Long,
        keyset: KeysetDto,
    ): List<BlindOutput> = splitAmounts(amount).map { secretOutputFor(it, keyset) }

    private fun secretOutputFor(
        amount: Long,
        keyset: KeysetDto,
    ): BlindOutput {
        val secret = Bdhke.randomSecret()
        val r = Bdhke.randomScalar()
        // NUT-00 spec: secret is a hex string of 32 random bytes.
        val secretHex = secret.toHexKey()
        val bTick = Bdhke.blind(secretHex.encodeToByteArray(), r)
        return BlindOutput(amount, keyset.id, r, secretHex, bTick)
    }

    /**
     * Like [secretOutputFor] but the secret is a NUT-11 P2PK lock string so
     * the resulting proof can only be redeemed with [recipientPubKeyHex]'s
     * private key. Used by [swapToLocked] when minting nutzap outputs.
     */
    private fun lockedOutputFor(
        amount: Long,
        keyset: KeysetDto,
        recipientPubKeyHex: String,
    ): BlindOutput {
        val r = Bdhke.randomScalar()
        val secret = P2PK.lockedSecret(recipientPubKeyHex)
        val bTick = Bdhke.blind(secret.encodeToByteArray(), r)
        return BlindOutput(amount, keyset.id, r, secret, bTick)
    }

    private fun unblindAll(
        outputs: List<BlindOutput>,
        signatures: List<BlindSignatureDto>,
        keyset: KeysetDto,
    ): List<CashuProof> {
        if (signatures.size != outputs.size) {
            throw IllegalStateException(
                "Got ${signatures.size} signatures for ${outputs.size} outputs",
            )
        }
        return outputs.mapIndexed { i, o -> unblindOne(o, signatures[i], keyset) }
    }

    private fun unblindOne(
        output: BlindOutput,
        signature: BlindSignatureDto,
        keyset: KeysetDto,
    ): CashuProof {
        if (signature.amount != output.amount) {
            throw IllegalStateException(
                "Signature amount ${signature.amount} != output amount ${output.amount}",
            )
        }
        val mintPubKeyHex =
            keyset.keys[output.amount.toString()]
                ?: throw IllegalStateException("Mint keyset has no key for amount ${output.amount}")
        val c =
            Bdhke.unblind(
                blindSignature = signature.cTick.hexToByteArray(),
                r = output.r,
                mintPubKey = mintPubKeyHex.hexToByteArray(),
            )
        return CashuProof(
            id = output.keysetId,
            amount = output.amount,
            secret = output.secretHex,
            c = c.toHexKey(),
        )
    }

    companion object {
        /** Re-exported from [splitAmountIntoDenominations] for convenience. */
        fun splitAmounts(amount: Long): List<Long> = splitAmountIntoDenominations(amount)
    }

    private data class BlindOutput(
        val amount: Long,
        val keysetId: String,
        val r: ByteArray,
        val secretHex: String,
        val bTick: ByteArray,
    ) {
        fun toDto() = BlindedMessageDto(amount = amount, id = keysetId, bTick = bTick.toHexKey())
    }

    private fun CashuProof.toDto() = ProofDto(amount = amount, id = id, secret = secret, c = c, witness = witness)
}

data class MintedProofs(
    val proofs: List<CashuProof>,
    val keysetId: String,
) {
    fun toTokenContent(mintUrl: String): TokenContent = TokenContent(mint = mintUrl, proofs = proofs)
}

data class SwapResult(
    val send: List<CashuProof>,
    val keep: List<CashuProof>,
    val keysetId: String,
)

data class MeltResult(
    val preimage: String?,
    val changeProofs: List<CashuProof>,
    val keysetId: String,
)
