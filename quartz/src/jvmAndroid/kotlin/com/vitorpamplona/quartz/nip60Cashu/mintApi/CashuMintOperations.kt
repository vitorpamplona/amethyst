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
    /**
     * NUT-13 strategy for secret + blinding-factor generation. Defaults to
     * pure-random; the wallet supplies a [DeterministicSecretFactory] when
     * it has a seed available so future kind:7375 losses can be recovered
     * via NUT-09 /v1/restore.
     */
    private val secretFactory: SecretFactory = RandomSecretFactory,
) {
    /**
     * Step 1 of mint-from-LN: ask the mint for a bolt11 invoice for `amount`
     * sats. Returns the quote, which contains the invoice the user must pay.
     *
     * The wallet should publish a kind:7374 quote event before returning to the
     * UI so that an interrupted flow can be recovered on next login.
     */
    suspend fun requestMintQuote(
        amountSats: Long,
        /**
         * NUT-20: optional 33-byte compressed pubkey hex that binds this
         * quote to a wallet keypair. Mints that support NUT-20 will refuse
         * to honour [mintProofs] without a matching signature. Mints that
         * don't ignore the field (extra-JSON tolerance), so always-on is
         * safe — no feature detection needed at quote time.
         */
        signingPubkey: String? = null,
    ): MintQuoteBolt11ResponseDto =
        client.mintQuoteBolt11(
            MintQuoteBolt11RequestDto(unit = "sat", amount = amountSats, pubkey = signingPubkey),
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
        /**
         * NUT-20: when [requestMintQuote] was called with `signingPubkey`,
         * the matching mint request MUST carry a BIP-340 Schnorr signature
         * from the same key over `sha256(quote || B_0 || B_1 || …)`.
         * Pass the 32-byte private key (hex) here and the operation
         * computes + attaches the signature; pass null to skip NUT-20 and
         * fall back to NUT-04 behaviour.
         */
        signingPrivkey: String? = null,
    ): MintedProofs {
        val keyset = fetchKeyset()
        val outputs = createBlindedOutputs(amountSats, keyset)

        val outputDtos = outputs.map { it.toDto() }
        val signature =
            signingPrivkey?.let {
                MintQuoteSignature.sign(
                    quoteId = quote,
                    blindedMessageHexes = outputDtos.map { dto -> dto.bTick },
                    signingPrivkey = it.hexToByteArray(),
                )
            }

        val response =
            client.mintBolt11(
                MintBolt11RequestDto(
                    quote = quote,
                    outputs = outputDtos,
                    signature = signature,
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

        // NUT-02: reserve per-input fees from the output total. Without
        // this, fee-charging mints reject the swap with "amount mismatch".
        val feeAtoms = computeInputFee(proofs.size, keyset.inputFeePpk)
        val outputTotal = total - feeAtoms
        if (targetSplit != null && targetSplit > outputTotal) {
            throw IllegalArgumentException("Target split $targetSplit exceeds outputs after fee ($outputTotal)")
        }
        if (outputTotal < 0L) throw IllegalArgumentException("Inputs $total don't cover NUT-02 fee $feeAtoms")

        val sendOutputs =
            if (targetSplit != null) splitAmounts(targetSplit).map { secretOutputFor(it, keyset) } else emptyList()
        val keepAmount = if (targetSplit != null) outputTotal - targetSplit else outputTotal
        val keepOutputs =
            if (keepAmount > 0L) splitAmounts(keepAmount).map { secretOutputFor(it, keyset) } else emptyList()

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
        // NUT-02: reserve per-input fees; change shrinks by the fee, send
        // amount stays whole (the recipient gets exactly targetSplit sats).
        val feeAtoms = computeInputFee(proofs.size, keyset.inputFeePpk)
        val keepAmount = total - targetSplit - feeAtoms
        if (keepAmount < 0L) {
            throw IllegalArgumentException("Inputs $total don't cover send $targetSplit + fee $feeAtoms")
        }
        val sendOutputs = splitAmounts(targetSplit).map { lockedOutputFor(it, keyset, recipientP2pkPubkeyHex) }
        val keepOutputs =
            if (keepAmount > 0L) splitAmounts(keepAmount).map { secretOutputFor(it, keyset) } else emptyList()
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
        val keyset = fetchKeyset()
        // NUT-02 input fee — separate from quote.feeReserve, which is the
        // upper bound on LN routing fees. The mint subtracts both from
        // inputs before paying the invoice; we must reserve both.
        val inputFee = computeInputFee(inputs.size, keyset.inputFeePpk)
        val required = quote.amount + quote.feeReserve + inputFee
        if (total < required) throw IllegalArgumentException("Inputs total $total < required $required")

        // Pre-blind change outputs at the fee_reserve denominations so the
        // mint can return whatever LN fees were not consumed. Upper bound
        // excludes the (already-paid) NUT-02 input fee.
        val changeAmount = total - quote.amount - inputFee
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
        // NUT-00: secret is a UTF-8 hex string of 32 secret bytes.
        // [secretFactory] decides whether those bytes are pure-random or
        // NUT-13-derived from a wallet seed; either way the on-wire shape
        // is identical so the mint can't tell which scheme we're using.
        val derived = secretFactory.nextSecret(keyset.id)
        val bTick = Bdhke.blind(derived.secretHex.encodeToByteArray(), derived.blindingFactor)
        return BlindOutput(amount, keyset.id, derived.blindingFactor, derived.secretHex, bTick)
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
        val mintPubKey = mintPubKeyHex.hexToByteArray()
        val cTickBytes = signature.cTick.hexToByteArray()

        // NUT-12 DLEQ verification. When the mint emits a DLEQ proof
        // alongside the blind signature, we MUST verify it before we
        // treat the resulting proof as valid — without this check, a
        // malicious or buggy mint can hand us a junk C' that fails only
        // at spend time, by which point a sender already considers the
        // payment complete. Older mints omit the dleq field entirely;
        // we accept those silently for backwards compatibility.
        signature.dleq?.let { dleq ->
            val ok =
                Bdhke.verifyDleq(
                    e = dleq.e.hexToByteArray(),
                    s = dleq.s.hexToByteArray(),
                    blindedMessage = output.bTick,
                    blindSignature = cTickBytes,
                    mintPubKey = mintPubKey,
                )
            if (!ok) {
                throw MintProtocolException(
                    "NUT-12 DLEQ verification failed for amount ${output.amount} — mint signature does not match its published keyset key",
                )
            }
        }

        val c =
            Bdhke.unblind(
                blindSignature = cTickBytes,
                r = output.r,
                mintPubKey = mintPubKey,
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

        /**
         * NUT-02 input-fee math. Total fee in atoms for [numInputs] proofs
         * spent against a keyset with [inputFeePpk] parts-per-thousand:
         * `ceil(numInputs * inputFeePpk / 1000)`. Absent (null) ppk means
         * the mint is on an older NUT-02 release and charges no fee.
         *
         * Ceiling division avoids the underpay-by-one-sat case that mints
         * reject as "amount-mismatch". `(a + b - 1) / b` is the standard
         * positive-integer ceiling formula; no overflow concerns at the
         * scales any wallet hits (numInputs * 1000 fits in Long).
         */
        fun computeInputFee(
            numInputs: Int,
            inputFeePpk: Long?,
        ): Long {
            val ppk = inputFeePpk ?: 0L
            if (ppk <= 0L || numInputs <= 0) return 0L
            return (numInputs.toLong() * ppk + 999L) / 1000L
        }
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
