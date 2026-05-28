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
import com.vitorpamplona.quartz.nip60Cashu.bdhke.BdhkeScratchpad
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.utils.Log

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

        // Batch all output construction into ONE secret reservation —
        // see [secretOutputsFor]. The wallet's persisted counter
        // advances by `sendAmounts.size + keepAmounts.size` in one
        // synchronized critical section.
        val sendAmounts = if (targetSplit != null) splitAmounts(targetSplit) else emptyList()
        val keepAmount = if (targetSplit != null) outputTotal - targetSplit else outputTotal
        val keepAmounts = if (keepAmount > 0L) splitAmounts(keepAmount) else emptyList()
        val combined = secretOutputsFor(sendAmounts + keepAmounts, keyset)
        val sendOutputs = combined.subList(0, sendAmounts.size)
        val keepOutputs = combined.subList(sendAmounts.size, combined.size)

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
        Log.i("CashuTrace") { "swapToLocked enter: inputs=${proofs.size}, target=$targetSplit" }
        if (proofs.isEmpty()) throw IllegalArgumentException("Nothing to swap")
        if (targetSplit <= 0) throw IllegalArgumentException("Target split must be > 0")
        val total = proofs.sumOf { it.amount }
        if (targetSplit > total) throw IllegalArgumentException("Target split exceeds available proofs")

        Log.i("CashuTrace") { "swapToLocked: fetchKeyset begin" }
        val keyset = fetchKeyset()
        Log.i("CashuTrace") { "swapToLocked: fetchKeyset end id=${keyset.id}" }
        // NUT-02: reserve per-input fees; change shrinks by the fee, send
        // amount stays whole (the recipient gets exactly targetSplit sats).
        val feeAtoms = computeInputFee(proofs.size, keyset.inputFeePpk)
        val keepAmount = total - targetSplit - feeAtoms
        if (keepAmount < 0L) {
            throw IllegalArgumentException("Inputs $total don't cover send $targetSplit + fee $feeAtoms")
        }
        // Recipient-locked outputs use random `r` (the recipient owns the
        // proof, so NUT-13 recovery doesn't apply to those bytes), but
        // our change outputs go through the deterministic factory in one
        // batch — see [secretOutputsFor].
        Log.i("CashuTrace") { "swapToLocked: building ${splitAmounts(targetSplit).size} locked outputs (P2PK blinds)" }
        val sendOutputs = splitAmounts(targetSplit).map { lockedOutputFor(it, keyset, recipientP2pkPubkeyHex) }
        Log.i("CashuTrace") { "swapToLocked: building ${if (keepAmount > 0L) splitAmounts(keepAmount).size else 0} keep outputs (NUT-13 deterministic blinds)" }
        val keepOutputs =
            if (keepAmount > 0L) secretOutputsFor(splitAmounts(keepAmount), keyset) else emptyList()
        val allOutputs = sendOutputs + keepOutputs
        Log.i("CashuTrace") { "swapToLocked: POST /v1/swap with ${allOutputs.size} outputs" }

        val response =
            client.swap(
                SwapRequestDto(
                    inputs = proofs.map { it.toDto() },
                    outputs = allOutputs.map { it.toDto() },
                ),
            )
        Log.i("CashuTrace") { "swapToLocked: swap response sigs=${response.signatures.size}" }

        if (response.signatures.size != allOutputs.size) {
            throw MintProtocolException(
                "Mint returned ${response.signatures.size} signatures for ${allOutputs.size} outputs",
            )
        }

        Log.i("CashuTrace") { "swapToLocked: unblindAll begin (${allOutputs.size})" }
        val unblinded = unblindAll(allOutputs, response.signatures, keyset)
        Log.i("CashuTrace") { "swapToLocked: unblindAll end" }
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
            if (changeAmount > 0) secretOutputsFor(splitAmounts(changeAmount), keyset) else emptyList()

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
                val scratch = BdhkeScratchpad()
                for (sig in sigs) {
                    val src =
                        byAmount.remove(sig.amount)
                            ?: throw IllegalStateException("Mint returned change for amount ${sig.amount} we didn't request")
                    out += unblindOne(src, sig, keyset, scratch)
                }
                out
            } ?: emptyList()

        return MeltResult(
            preimage = response.payment_preimage,
            changeProofs = changeProofs,
            keysetId = keyset.id,
        )
    }

    /**
     * NUT-09 restore — recover previously-minted blind signatures from
     * the mint by replaying deterministic blind messages and asking which
     * the mint has signed. Bridges a wallet's NUT-13 seed back into a
     * full proof set when the on-disk kind:7375s have been lost.
     *
     * Driver loop:
     *  1. Re-derive blind messages for counters `[startCounter, startCounter+batchSize)`
     *     at every NUT-13 amount denomination the mint exposes.
     *  2. POST to /v1/restore. Mint returns the subset it has signed —
     *     paired arrays of original blinded messages and the matching
     *     blind signatures, omitting any counter it never signed.
     *  3. Unblind each returned signature. The DLEQ check on
     *     [unblindOne] still applies — recovered proofs are verified
     *     identically to freshly-minted ones.
     *  4. Repeat with the next counter window. Stop after [emptyBatchesToStop]
     *     consecutive batches return zero proofs — that's the gap-limit
     *     heuristic (analogous to BIP-32 wallet recovery), bounding the
     *     scan to "where the seed has actually been used + one safety
     *     buffer" instead of scanning to Long.MAX.
     *
     * Note: NUT-09 only guarantees the mint returns signatures for
     * blinded messages it has signed. It does NOT tell us whether the
     * resulting proofs are unspent. The caller MUST run /v1/checkstate
     * on the returned proofs to filter out spent ones before treating
     * them as wallet balance. [CashuWalletOps] does this.
     *
     * @param seed The wallet's NUT-13 seed (64 bytes).
     * @param keysetId Single keyset to restore against. Caller iterates
     *                 across multiple keysets if needed.
     * @param startCounter First counter to try (0 for fresh recovery).
     * @param batchSize Counters per /v1/restore round-trip. CDK defaults
     *                  to 100; larger means fewer round-trips but
     *                  larger request bodies. 100 is a reasonable
     *                  compromise.
     * @param emptyBatchesToStop Consecutive zero-hit batches before we
     *                           call it done. CDK's default is 3 —
     *                           enough buffer for accidental gaps from
     *                           failed mints.
     * @param amounts Denominations to try at each counter. Defaults to
     *                the keyset's full amount list. The wallet may
     *                narrow this if it knows specific amounts were used.
     */
    suspend fun restore(
        seed: ByteArray,
        keysetId: String,
        startCounter: Long = 0L,
        batchSize: Int = 100,
        emptyBatchesToStop: Int = 3,
        amounts: List<Long>? = null,
    ): RestoreResult {
        val keyset = fetchKeysetById(keysetId)
        val denominations = (amounts ?: keyset.keys.keys.mapNotNull { it.toLongOrNull() }).sorted()
        if (denominations.isEmpty()) {
            throw IllegalStateException("Keyset $keysetId exposes no amount denominations")
        }

        // Cap each /v1/restore request at MAX_RESTORE_REQUEST_ITEMS outputs.
        // A keyset with the full power-of-2 denomination set (up to ~63 amounts)
        // multiplied by the default batchSize=100 produces 6300 outputs per
        // round-trip — far above the 1000-item validation cap that nutshell /
        // CDK enforce, which surfaces as "List should have at most 1000 items".
        // Honour the caller's batchSize when it already fits.
        val effectiveBatchSize =
            if (batchSize * denominations.size > MAX_RESTORE_REQUEST_ITEMS) {
                (MAX_RESTORE_REQUEST_ITEMS / denominations.size).coerceAtLeast(1)
            } else {
                batchSize
            }

        val recovered = mutableListOf<RecoveredProof>()
        var counter = startCounter
        var emptyStreak = 0
        var highestSeenCounter = startCounter - 1

        val perBatchSize = effectiveBatchSize * denominations.size
        while (emptyStreak < emptyBatchesToStop) {
            // Pre-sized collections — without these, the ArrayList /
            // HashMap resize ~10 times per 1000-output batch.
            // One materials slot per counter (bTick is 1-to-1 with the
            // deterministic (secret, r) pair derived from the counter),
            // not per (counter, amount) — the wallet only ever minted
            // one denomination per counter under NUT-13.
            Log.i("CashuTrace") { "restore: batch counter=$counter size=$effectiveBatchSize denoms=${denominations.size}" }
            val matsByBTick = HashMap<String, CounterMaterials>(effectiveBatchSize)
            val recoveredCounters = HashSet<Long>(effectiveBatchSize)
            val outputDtos = ArrayList<BlindedMessageDto>(perBatchSize)
            // One scratchpad reused across all per-counter Bdhke.blind
            // calls in this batch — see [BdhkeScratchpad]. Critical for
            // restore: without it we'd allocate ~6 short-lived holders
            // per counter × batchSize counters per batch and trip the
            // Android 15+ ART JIT escape-analysis crash.
            val batchScratch = BdhkeScratchpad()

            for (offset in 0 until effectiveBatchSize) {
                val c = counter + offset
                Log.i("CashuTrace") { "restore.derive c=$c: CashuDeterministic" }
                // Per-counter derivation: same (secret, r) pair the
                // wallet would have minted at this counter slot. We
                // try each amount denomination — the mint will only
                // return the signature(s) it actually issued at this
                // slot, if any.
                val secretBytes = CashuDeterministic.secretBytes(seed, keysetId, c)
                val r = CashuDeterministic.blindingFactor(seed, keysetId, c)
                val secretHex = secretBytes.toHexKey()
                Log.i("CashuTrace") { "restore.derive c=$c: Bdhke.blind" }
                val bTick = Bdhke.blind(secretHex.encodeToByteArray(), r, batchScratch)
                Log.i("CashuTrace") { "restore.derive c=$c: build dtos for ${denominations.size} denoms" }
                val bTickHex = bTick.toHexKey()
                matsByBTick[bTickHex] = CounterMaterials(c, secretHex, r, bTick)
                for (amount in denominations) {
                    outputDtos += BlindedMessageDto(amount = amount, id = keysetId, bTick = bTickHex)
                }
            }

            Log.i("CashuTrace") { "restore: HTTP /v1/restore (request ${outputDtos.size} outputs)" }
            val response = client.restore(RestoreRequestDto(outputs = outputDtos))
            Log.i("CashuTrace") { "restore: HTTP response sigs=${response.signatures.size} echoes=${response.outputs.size}" }
            if (response.signatures.isEmpty()) {
                emptyStreak++
            } else {
                emptyStreak = 0
                // Mint echoes the original outputs alongside signatures
                // (NUT-09 §2). Match by bTick alone — some mints match
                // their internal table by blind point only and return
                // the signed denomination in the signature even when
                // the echoed output's amount field is one we passed
                // for a different denomination. The signed (amount, C_)
                // is the source of truth, the echoed amount is not.
                val restoreScratch = BdhkeScratchpad()
                for (i in response.signatures.indices) {
                    Log.i("CashuTrace") { "restore.unblind[$i/${response.signatures.size}] begin" }
                    val echo = response.outputs.getOrNull(i) ?: continue
                    val sig = response.signatures[i]
                    val mat = matsByBTick[echo.bTick] ?: continue
                    // Skip duplicates — a spec-tolerant mint may return
                    // one entry per (amount, bTick) we asked about even
                    // though only one of them carries the real signature.
                    if (!recoveredCounters.add(mat.counter)) continue
                    val output = BlindOutput(sig.amount, keysetId, mat.r, mat.secretHex, mat.bTick)
                    val proof = unblindOne(output, sig, keyset, restoreScratch)
                    Log.i("CashuTrace") { "restore.unblind[$i/${response.signatures.size}] end (counter=${mat.counter} amt=${sig.amount})" }
                    recovered += RecoveredProof(proof, mat.counter)
                    if (mat.counter > highestSeenCounter) highestSeenCounter = mat.counter
                }
            }

            counter += effectiveBatchSize
        }

        return RestoreResult(
            keysetId = keysetId,
            proofs = recovered,
            // Caller should bump its persisted counter past this so a
            // subsequent mint doesn't reuse a slot we just confirmed
            // (with `+ 1` because the highest seen IS used).
            nextCounterAfterScan = if (recovered.isEmpty()) startCounter else highestSeenCounter + 1L,
        )
    }

    private suspend fun fetchKeysetById(keysetId: String): KeysetDto {
        val response = client.activeKeysets()
        return response.keysets.firstOrNull { it.id == keysetId }
            ?: throw IllegalStateException("Mint doesn't expose keyset $keysetId")
    }

    /** Public surface for callers that need the active keyset (NUT-09 restore driver). */
    suspend fun activeKeyset(): KeysetDto = fetchKeyset()

    /**
     * NUT-12 §3 Carol-side DLEQ verification on a batch of proofs that
     * arrived from OUTSIDE the wallet's own mint round-trips (an
     * incoming nutzap, an imported cashuB token, anything with a
     * `dleq.r` field). Without this, a malicious sender can hand us
     * junk proofs that look fine until spend time.
     *
     * For each proof:
     *  - if `proof.dleq` is null → skip silently. The sender's mint
     *    didn't emit NUT-12 or stripped it in transit. Treat as
     *    "no DLEQ data" rather than failure; spend-time validation
     *    still catches outright-invalid proofs.
     *  - if `proof.dleq.r` is null → skip. We can't reconstruct B'
     *    without the blinding factor. This is the case for the
     *    Alice-side proof (mint→wallet) where r is only known
     *    locally; should never happen on a proof we received from
     *    another wallet.
     *  - otherwise, look up the keyset's amount-pubkey and call
     *    [Bdhke.verifyDleqCarol]. Mismatch returns false and the
     *    caller should reject the whole token.
     *
     * Per-keyset: the function fetches whatever keyset id the first
     * proof references and assumes every other proof uses the same
     * one (or one of the mint's exposed keysets). Cross-keyset proofs
     * are allowed; each proof is verified against its own keyset's
     * amount key.
     */
    suspend fun verifyTokenDleq(proofs: List<CashuProof>): Boolean {
        if (proofs.isEmpty()) return true
        // Fetch all keysets the mint exposes so cross-keyset tokens
        // verify against the right amount key. Cheap — one round-trip.
        val allKeysets = client.activeKeysets().keysets.associateBy { it.id }
        // One scratchpad reused across every per-proof Carol verification.
        // Carol is the heaviest BDHKE pipeline (blind + addRTimesA +
        // verifyDleq); without sharing this scratch the inbound-nutzap
        // loop allocates dozens of short-lived holders per proof and
        // trips the Android 15+ ART JIT crash.
        val scratch = BdhkeScratchpad()
        for (proof in proofs) {
            val dleq = proof.dleq ?: continue
            val r = dleq.r ?: continue
            val keyset =
                allKeysets[proof.id]
                    // Mint that issued the proof may have rotated keysets
                    // since — without the original keyset's keys we can't
                    // verify offline. Fall through to spend-time check.
                    ?: continue
            val mintPubKeyHex = keyset.keys[proof.amount.toString()] ?: return false
            val ok =
                Bdhke.verifyDleqCarol(
                    secret = proof.secret.encodeToByteArray(),
                    r = r.hexToByteArray(),
                    e = dleq.e.hexToByteArray(),
                    s = dleq.s.hexToByteArray(),
                    unblindedC = proof.c.hexToByteArray(),
                    mintPubKey = mintPubKeyHex.hexToByteArray(),
                    scratch = scratch,
                )
            if (!ok) return false
        }
        return true
    }

    /**
     * NUT-07 batched proof-state query. Returns a map from each proof's
     * secret to its mint-side state ("UNSPENT", "SPENT", "PENDING").
     * Used by NUT-09 restore to filter spent proofs out of the recovered
     * set before publishing — the mint will sign blind messages whether
     * the underlying secret has been spent or not.
     */
    suspend fun checkStates(proofs: List<CashuProof>): Map<String, ProofState> {
        if (proofs.isEmpty()) return emptyMap()
        // NUT-07 keys check requests by `Y` (hash-to-curve of the secret).
        // One scratchpad reused across the per-proof hashToCurve calls so
        // the pre-send scrub doesn't allocate per-proof on wallets with
        // many entries (and doesn't trip the ART JIT crash).
        val scratch = BdhkeScratchpad()
        val ys = proofs.map { Bdhke.hashToCurveCompressed(it.secret.encodeToByteArray(), scratch).toHexKey() }
        val response = client.checkState(CheckStateRequestDto(ys = ys))
        val secretByY =
            proofs.associateBy {
                Bdhke.hashToCurveCompressed(it.secret.encodeToByteArray(), scratch).toHexKey()
            }
        val out = mutableMapOf<String, ProofState>()
        for (row in response.states) {
            val proof = secretByY[row.y] ?: continue
            out[proof.secret] = ProofState.fromWire(row.state)
        }
        return out
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
    ): List<BlindOutput> = secretOutputsFor(splitAmounts(amount), keyset)

    /**
     * Construct standard (non-P2PK) blind outputs for a list of amounts
     * with ONE batch call to the secret factory. Reserves all required
     * counters in a single atomic critical section — calling
     * [SecretFactory.nextSecret] per amount instead would take the
     * @Synchronized lock + dirty `AccountSettings.saveable` N times.
     */
    private fun secretOutputsFor(
        amounts: List<Long>,
        keyset: KeysetDto,
    ): List<BlindOutput> {
        if (amounts.isEmpty()) return emptyList()
        Log.i("CashuTrace") { "secretOutputsFor: nextSecrets(${amounts.size})" }
        // NUT-00: secret is a UTF-8 hex string of 32 secret bytes.
        // [secretFactory] decides whether those bytes are pure-random or
        // NUT-13-derived from a wallet seed; either way the on-wire shape
        // is identical so the mint can't tell which scheme we're using.
        val derived = secretFactory.nextSecrets(keyset.id, amounts.size)
        val scratch = BdhkeScratchpad()
        return amounts.mapIndexed { i, amount ->
            val pair = derived[i]
            val bTick = Bdhke.blind(pair.secretHex.encodeToByteArray(), pair.blindingFactor, scratch)
            BlindOutput(amount, keyset.id, pair.blindingFactor, pair.secretHex, bTick)
        }
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
        // One scratchpad per loop, reused across every unblind call —
        // makes the bdhke crypto allocation-free on the hot path and
        // dodges the Android 15+ ART JIT escape-analysis crash that
        // hit on tight per-iteration Fe4 / MutablePoint allocation.
        // See [BdhkeScratchpad] for the full rationale.
        val scratch = BdhkeScratchpad()
        val out = ArrayList<CashuProof>(outputs.size)
        for (i in outputs.indices) {
            out += unblindOne(outputs[i], signatures[i], keyset, scratch)
        }
        return out
    }

    private fun unblindOne(
        output: BlindOutput,
        signature: BlindSignatureDto,
        keyset: KeysetDto,
        scratch: BdhkeScratchpad = BdhkeScratchpad(),
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

        // NUT-12 DLEQ on the mint's response is intentionally NOT
        // verified here. unblindOne only runs for outputs we asked the
        // mint to sign (swap, mint-from-LN, swap-to-locked) — a
        // malicious mint could just refuse the request, so the DLEQ
        // check on our own outputs offers little marginal security
        // beyond "fail fast vs. fail-at-next-spend". Third-party proofs
        // (incoming cashu tokens, nutzap redeems) continue to be
        // Carol-verified via [verifyTokenDleq] — that's where the
        // untrust boundary actually lives.

        val c =
            Bdhke.unblind(
                blindSignature = cTickBytes,
                r = output.r,
                mintPubKey = mintPubKey,
                scratch = scratch,
            )
        // Retain (e, s, r) on the resulting proof per NUT-12 §3 so
        // anything that later forwards this proof to another wallet —
        // a cashuB token, a kind:9321 nutzap — gives the recipient the
        // full Carol-verification tuple. Without `r`, the recipient
        // can't reconstruct B' and falls back to "trust on first
        // spend"; with all three, they can verify against the mint's
        // keyset key offline. When the mint doesn't emit dleq the
        // field stays null and downstream consumers handle that the
        // same way they do today (no Carol check, fall back to spend-
        // time validation).
        val dleq =
            signature.dleq?.let { src ->
                DleqProofDto(
                    e = src.e,
                    s = src.s,
                    r = output.r.toHexKey(),
                )
            }
        return CashuProof(
            id = output.keysetId,
            amount = output.amount,
            secret = output.secretHex,
            c = c.toHexKey(),
            dleq = dleq,
        )
    }

    companion object {
        /**
         * Upper bound on outputs per `/v1/restore` request body. The cashu
         * spec doesn't pin a value; nutshell + CDK + minibits all enforce
         * a 1000-item Pydantic cap. 500 leaves headroom for response
         * doubling (mint echoes outputs alongside signatures) and any
         * future tightening.
         */
        const val MAX_RESTORE_REQUEST_ITEMS: Int = 500

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

/** A single proof recovered via NUT-09 plus the counter that derived it. */
data class RecoveredProof(
    val proof: CashuProof,
    val counter: Long,
)

/**
 * Per-counter derivation materials reused across every denomination
 * we probe inside one /v1/restore batch. `bTick` is deterministic in
 * `(seed, keysetId, counter)` so it identifies the counter slot on
 * its own — the amount is whatever the mint actually signed.
 */
private data class CounterMaterials(
    val counter: Long,
    val secretHex: String,
    val r: ByteArray,
    val bTick: ByteArray,
)

/**
 * Outcome of [CashuMintOperations.restore]. [proofs] are unblinded
 * NUT-12-verified blind signatures the mint returned for this keyset;
 * the caller must still run /v1/checkstate to filter out spent ones.
 * [nextCounterAfterScan] is `max(scanned_counter) + 1` (or
 * `startCounter` when nothing was recovered) — the wallet should bump
 * its persisted counter to at least this value to avoid future reuse.
 */
data class RestoreResult(
    val keysetId: String,
    val proofs: List<RecoveredProof>,
    val nextCounterAfterScan: Long,
)
