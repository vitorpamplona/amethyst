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
package com.vitorpamplona.amethyst.commons.cashu.ops

import com.vitorpamplona.crypto.random.RandomInstance
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.mintApi.BlindSignatureDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.BlindedMessageDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CheckStateRequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.DeterministicSecretFactory
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltBolt11RequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11RequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintBolt11RequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpClient
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpException
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpResponse
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11RequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.RestoreRequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.RestoreResponseDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SwapRequestDto
import com.vitorpamplona.quartz.nip60Cashu.p2pk.P2PK
import com.vitorpamplona.quartz.nip60Cashu.seed.CashuDeterministic
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenB64Parser
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The NUT-13 seed belongs to the wallet, so every install derives the same secrets from it; the
 * counter that picks which one is stored per install. After a reinstall, or on a second device,
 * it starts at 0 again and the wallet re-derives outputs the mint has already signed. Swaps,
 * melts with change, nutzap redeems and mints all create outputs, so all are refused.
 *
 * The wallet gets past it twice over: [CashuWalletOps.settleCounter] moves the counter past
 * every slot the mint signed, once per keyset per install, before anything is spent; and a
 * refusal that still comes (a keyset settled nowhere yet, two devices spending together) is
 * answered by the same move and one retry.
 *
 * The mint here behaves like cdk-mintd (testnut.cashu.space): it remembers every blinded message
 * it signed and refuses to sign one again, 11008 `Duplicate outputs` on 0.18 and 11003 `Blinded
 * Message is already signed` since.
 */
class CashuSecretCounterReuseTest {
    private val mintUrl = "https://mint.example.com"
    private val keysetId = "001711afb1de20cb"
    private val seed = RandomInstance.bytes(64)

    /** The wallet's kind:10019 P2PK key, the one nutzaps to it are locked to. */
    private val walletPrivKey = RandomInstance.bytes(32)
    private val walletPubKey = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(walletPrivKey)).toHexKey()
    private val json = Json { ignoreUnknownKeys = true }

    /** What a mint answers an output it signed before. */
    private data class Refusal(
        val code: Int,
        val detail: String,
    ) {
        companion object {
            val DUPLICATE_OUTPUTS = Refusal(11008, "Duplicate outputs")
            val ALREADY_SIGNED = Refusal(11003, "Blinded Message is already signed")
        }
    }

    private inner class FakeMint : MintHttpTransport {
        private val json = Json { ignoreUnknownKeys = true }
        private val privKey = RandomInstance.bytes(32)
        private val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey)).toHexKey()

        /** Every blinded message signed so far, by `B_`: what the restore endpoint answers from. */
        private val signed = HashMap<String, BlindSignatureDto>()
        private val spentSecrets = HashSet<String>()
        private val mintQuotes = HashMap<String, Long>()
        private val meltQuotes = HashMap<String, Long>()

        /** Quotes whose outputs were signed: a mint answers 20002 for them, as cdk and Nutshell do. */
        private val issued = HashSet<String>()
        private var quotes = 0

        /** Requests by endpoint, to show what a path did and didn't ask. */
        var gets = 0
        var restores = 0

        /** When set, `/v1/restore` answers this status: a mint without NUT-09, or one failing. */
        var restoreStatus: Int? = null

        /** What an already signed output is answered with; cdk changed it between versions. */
        var refusal: Refusal = Refusal.DUPLICATE_OUTPUTS

        /** When set, every signing request is refused this way, whatever its outputs. */
        var alwaysRefuse: Refusal? = null

        /** The refusals sent, in order. */
        val refused = mutableListOf<String>()

        /** Swap requests received, refused or not. */
        var swapRequests = 0

        private fun y(secret: String) = Bdhke.hashToCurveCompressed(secret.encodeToByteArray()).toHexKey()

        private fun ok(signatures: List<BlindSignatureDto>) = MintHttpResponse(200, """{"signatures":[${signatures.joinToString(",") { json.encodeToString(it) }}]}""")

        private fun refuse(r: Refusal): MintHttpResponse {
            refused += r.detail
            return MintHttpResponse(400, """{"code":${r.code},"detail":"${r.detail}"}""")
        }

        /**
         * cdk's rule for every endpoint that signs: an output signed before is refused, and
         * nothing in the request is signed or spent.
         */
        private fun refusalFor(outputs: List<BlindedMessageDto>): MintHttpResponse? {
            alwaysRefuse?.let { return refuse(it) }
            return if (outputs.any { it.bTick in signed }) refuse(refusal) else null
        }

        private fun sign(
            output: BlindedMessageDto,
            amount: Long = output.amount,
        ): BlindSignatureDto = BlindSignatureDto(amount, output.id, Bdhke.sign(output.bTick.hexToByteArray(), privKey).toHexKey()).also { signed[output.bTick] = it }

        private fun keysetsJson() = (0..20).joinToString(",", prefix = "{", postfix = "}") { "\"${1L shl it}\":\"$pubKeyHex\"" }

        private fun newQuote(kind: String): String = "$kind-${++quotes}"

        /** Proofs locked to [lockTo] and signed here, as a sender's wallet mints them for a nutzap. */
        fun lockedProofs(
            amounts: List<Long>,
            lockTo: String,
        ): List<String> =
            amounts.map { amount ->
                val secret = P2PK.lockedSecret(lockTo)
                """{"id":"$keysetId","amount":$amount,"secret":${json.encodeToString(secret)},"C":"${Bdhke.sign(y(secret).hexToByteArray(), privKey).toHexKey()}"}"""
            }

        override suspend fun get(url: String): MintHttpResponse {
            gets++
            val path = url.removePrefix(mintUrl)
            return when {
                path == "/v1/keys" || path == "/v1/keys/$keysetId" ->
                    MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","keys":${keysetsJson()},"input_fee_ppk":0}]}""")
                path == "/v1/keysets" ->
                    MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","active":true,"input_fee_ppk":0}]}""")
                path.startsWith("/v1/mint/quote/bolt11/") -> {
                    val id = path.substringAfterLast('/')
                    MintHttpResponse(200, json.encodeToString(MintQuoteBolt11ResponseDto(id, "lnbc1", "PAID", 4_102_444_800L, true)))
                }
                else -> MintHttpResponse(404, """{"detail":"not found"}""")
            }
        }

        override suspend fun postJson(
            url: String,
            body: String,
        ): MintHttpResponse =
            when (url.removePrefix(mintUrl)) {
                "/v1/swap" -> swap(body)
                "/v1/melt/quote/bolt11" -> {
                    json.decodeFromString<MeltQuoteBolt11RequestDto>(body)
                    val id = newQuote("melt")
                    meltQuotes[id] = INVOICE_AMOUNT
                    MintHttpResponse(200, json.encodeToString(MeltQuoteBolt11ResponseDto(id, INVOICE_AMOUNT, FEE_RESERVE, "UNPAID", 4_102_444_800L)))
                }
                "/v1/melt/bolt11" -> melt(body)
                "/v1/mint/quote/bolt11" -> {
                    val request = json.decodeFromString<MintQuoteBolt11RequestDto>(body)
                    val id = newQuote("mint")
                    mintQuotes[id] = request.amount
                    MintHttpResponse(200, json.encodeToString(MintQuoteBolt11ResponseDto(id, "lnbc1", "UNPAID", 4_102_444_800L)))
                }
                "/v1/mint/bolt11" -> mint(body)
                "/v1/restore" -> restore(body)
                "/v1/checkstate" -> checkState(body)
                else -> MintHttpResponse(404, """{"detail":"not found"}""")
            }

        private fun alreadySpent() = MintHttpResponse(400, """{"code":11001,"detail":"Token already spent."}""")

        private fun swap(body: String): MintHttpResponse {
            val request = json.decodeFromString<SwapRequestDto>(body)
            swapRequests++
            refusalFor(request.outputs)?.let { return it }
            if (request.inputs.any { it.secret in spentSecrets }) return alreadySpent()
            if (request.inputs.sumOf { it.amount } != request.outputs.sumOf { it.amount }) {
                return MintHttpResponse(400, """{"code":11005,"detail":"Transaction not balanced."}""")
            }
            request.inputs.forEach { spentSecrets += it.secret }
            return ok(request.outputs.map { sign(it) })
        }

        /** NUT-08: the refund is imprinted, in a split of the mint's choosing, into the leading blank outputs. */
        private fun melt(body: String): MintHttpResponse {
            val request = json.decodeFromString<MeltBolt11RequestDto>(body)
            val amount = meltQuotes[request.quote] ?: return MintHttpResponse(404, """{"detail":"quote not found"}""")
            val outputs = request.outputs.orEmpty()
            refusalFor(outputs)?.let { return it }
            if (request.inputs.any { it.secret in spentSecrets }) return alreadySpent()
            val total = request.inputs.sumOf { it.amount }
            if (total < amount + FEE_RESERVE) return MintHttpResponse(400, """{"code":11002,"detail":"Inputs total is less than the amount."}""")
            request.inputs.forEach { spentSecrets += it.secret }
            val change = powerOfTwoSplit(total - amount - FEE_RESERVE).take(outputs.size)
            val signatures = change.mapIndexed { i, part -> sign(outputs[i], part) }
            return MintHttpResponse(200, json.encodeToString(MeltBolt11ResponseDto(state = "PAID", paid = true, payment_preimage = PREIMAGE, change = signatures)))
        }

        private fun mint(body: String): MintHttpResponse {
            val request = json.decodeFromString<MintBolt11RequestDto>(body)
            if (request.quote in issued) return MintHttpResponse(400, """{"code":20002,"detail":"quote already issued"}""")
            val amount = mintQuotes[request.quote] ?: return MintHttpResponse(400, """{"code":20000,"detail":"quote not found"}""")
            refusalFor(request.outputs)?.let { return it }
            if (request.outputs.sumOf { it.amount } != amount) return MintHttpResponse(400, """{"code":11005,"detail":"Transaction not balanced."}""")
            mintQuotes.remove(request.quote)
            issued += request.quote
            return ok(request.outputs.map { sign(it) })
        }

        /** NUT-09: one signature per output it signed, at the amount it signed it for. */
        private fun restore(body: String): MintHttpResponse {
            restores++
            restoreStatus?.let { return MintHttpResponse(it, """{"detail":"not found"}""") }
            val request = json.decodeFromString<RestoreRequestDto>(body)
            val known = request.outputs.filter { it.bTick in signed }
            return MintHttpResponse(200, json.encodeToString(RestoreResponseDto(known, known.map { signed.getValue(it.bTick) })))
        }

        private fun checkState(body: String): MintHttpResponse {
            val spentYs = spentSecrets.mapTo(HashSet()) { y(it) }
            val rows =
                json.decodeFromString<CheckStateRequestDto>(body).ys.joinToString(",") {
                    """{"Y":"$it","state":"${if (it in spentYs) "SPENT" else "UNSPENT"}"}"""
                }
            return MintHttpResponse(200, """{"states":[$rows]}""")
        }
    }

    /**
     * One install of the app: its own counters and its own seed cache, the wallet's seed. What a
     * reinstall or a second device shares is the seed; what it does not is [counters].
     */
    private inner class Install(
        /** The Nostr account is the wallet's; only what is stored per install differs. */
        val signer: NostrSignerInternal = NostrSignerInternal(KeyPair()),
    ) {
        val counters = HashMap<String, Long>()

        /** Keysets this install aligned with their mint, as the host's counter store remembers them. */
        val settled = HashSet<String>()
        val published = mutableListOf<Event>()

        /** True once an operation warmed the seed, as [CashuWalletOps] does before it derives. */
        var seedWarm = false

        fun reserve(
            keysetId: String,
            count: Int,
        ): Long {
            val first = counters[keysetId] ?: 0L
            counters[keysetId] = first + count
            return first
        }

        fun ops(mint: MintHttpTransport) =
            CashuWalletOps(
                signer = signer,
                publish = { published += it },
                mintTransport = mint,
                secretFactory = DeterministicSecretFactory(seedProvider = { seed.takeIf { seedWarm } }, reserveCounters = ::reserve),
                seedWarmer = { seedWarm = true },
                // As the app's and amy's do: decrypt the wallet event when the cache is cold.
                seedForRestore = {
                    seedWarm = true
                    seed
                },
                peekCashuCounter = { counters[it] ?: 0L },
                reserveCashuCounters = ::reserve,
                isCashuCounterSettled = { it in settled },
                markCashuCounterSettled = { settled += it },
            )

        /** The wallet's proof of a first mint, as a kind:7375 hands it to a spend. */
        suspend fun holds(proofs: List<CashuProof>): List<TokenEntry> {
            val content = TokenContent(mint = mintUrl, proofs = proofs)
            return listOf(TokenEntry(signer.sign(CashuTokenEvent.build(content, signer)), content))
        }
    }

    /** Mints straight at the mint with [install]'s counters, as a paid invoice's mint does. */
    private suspend fun mintWith(
        install: Install,
        mint: FakeMint,
        amount: Long,
    ): List<CashuProof> {
        val quote = mint.postJson("$mintUrl/v1/mint/quote/bolt11", json.encodeToString(MintQuoteBolt11RequestDto(unit = "sat", amount = amount)))
        val factory = DeterministicSecretFactory({ seed }, install::reserve)
        return CashuMintOperations(MintHttpClient(mintUrl, userConfigured = true, transport = mint), factory)
            .mintProofs(json.decodeFromString<MintQuoteBolt11ResponseDto>(quote.body).quote, amount)
            .proofs
    }

    private fun powerOfTwoSplit(amount: Long): List<Long> {
        val parts = mutableListOf<Long>()
        var left = amount
        while (left > 0) {
            val part = 1L shl (63 - left.countLeadingZeroBits())
            parts += part
            left -= part
        }
        return parts
    }

    @Test
    fun aSecondInstallCanMeltWithChange() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 256)
            // A reinstall: the proofs came back from the relays, the counters did not.
            val second = Install()
            val ops = second.ops(mint)

            val melt = ops.meltToLightning(mintUrl, ops.requestMeltQuote(mintUrl, "lnbc21"), second.holds(held))

            assertEquals(PREIMAGE, melt.preimage)
            assertEquals(listOf("Duplicate outputs"), mint.refused)
            assertTrue("counter ${second.counters[keysetId]} still reuses the first install's slots", second.counters.getValue(keysetId) > 0)
        }

    @Test
    fun aSecondInstallCanSendAToken() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 64)
            val second = Install().apply { seedWarm = true }
            val sent = second.ops(mint).sendAsToken(mintUrl, 21, second.holds(held))

            assertEquals(21L, sent.amount)
            assertEquals(listOf("Duplicate outputs"), mint.refused)
        }

    @Test
    fun anAlreadySignedAnswerIsRecoveredTheSameWay() =
        runBlocking {
            val mint = FakeMint().apply { refusal = Refusal.ALREADY_SIGNED }
            val held = mintWith(Install(), mint, 64)
            val second = Install().apply { seedWarm = true }

            second.ops(mint).sendAsToken(mintUrl, 21, second.holds(held))

            assertEquals(listOf("Blinded Message is already signed"), mint.refused)
        }

    @Test
    fun aRefusalThatOutlivesTheRetryIsReported() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 64)
            val second = Install().apply { seedWarm = true }
            mint.alwaysRefuse = Refusal.DUPLICATE_OUTPUTS

            try {
                second.ops(mint).sendAsToken(mintUrl, 21, second.holds(held))
                fail("the mint refused every output")
            } catch (e: MintHttpException) {
                assertEquals("Duplicate outputs", e.detail)
            }
            assertEquals("one retry, not a loop", 2, mint.swapRequests)
        }

    @Test
    fun aMintOnAFreshInstallRetriesWithFreshCounters() =
        runBlocking {
            val mint = FakeMint()
            mintWith(Install(), mint, 256)
            val second = Install()
            val ops = second.ops(mint)
            val started = ops.startMintFromLightning(mintUrl, 21)

            val completed = ops.completeMintFromLightning(mintUrl, started.quoteEvent, 21)

            assertEquals(listOf("Duplicate outputs"), mint.refused)
            // Minted on slots past the first install's, not answered with its proof at counter 0.
            assertEquals(21L, completed.mintedAmount)
            assertTrue("counter ${second.counters[keysetId]}", second.counters.getValue(keysetId) > 1)
        }

    @Test
    fun aColdStartDerivesSendTokenProofsFromTheSeed() =
        runBlocking {
            val mint = FakeMint()
            val install = Install()
            val held = mintWith(install, mint, 64)
            val before = install.counters.getValue(keysetId)

            // Nothing warmed the seed in this session: the operation has to do it itself.
            val sent = install.ops(mint).sendAsToken(mintUrl, 21, install.holds(held))

            val secrets =
                CashuTokenB64Parser
                    .parse(sent.cashuToken)
                    .orEmpty()
                    .flatMap { it.proofs }
                    .map { it.secret }
            assertTrue("send token minted ${secrets.size} proofs", secrets.isNotEmpty())
            val derived = (0L..64L).map { CashuDeterministic.secretBytes(seed, keysetId, it).toHexKey() }
            assertTrue("the token's secrets are not the seed's: $secrets", secrets.all { it in derived })
            assertTrue("no counter was reserved", install.counters.getValue(keysetId) > before)
        }

    /** Nothing can move the counter there, so the same outputs are not sent a second time. */
    @Test
    fun aMintWithoutRestoreReportsTheRefusalWithoutARetry() =
        runBlocking {
            val mint = FakeMint().apply { restoreStatus = 404 }
            val held = mintWith(Install(), mint, 64)
            val second = Install().apply { seedWarm = true }

            try {
                second.ops(mint).sendAsToken(mintUrl, 21, second.holds(held))
                fail("the mint refuses outputs it signed and offers no way past them")
            } catch (e: MintHttpException) {
                assertEquals("Duplicate outputs", e.detail)
            }
            assertEquals("the refused outputs are not sent again", 1, mint.swapRequests)
            assertEquals(1, mint.restores)
        }

    @Test
    fun nutzapsAreRedeemedOnceTheCounterHasMoved() =
        runBlocking {
            val mint = FakeMint()
            mintWith(Install(), mint, 64)
            val first = Install()
            val second = Install()
            val zaps =
                listOf(
                    nutzap(mint, first, listOf(8L)),
                    nutzap(mint, first, listOf(16L)),
                )

            val result = second.ops(mint).redeemNutzaps(zaps, walletPrivKey.toHexKey(), listOf(mintUrl))

            assertEquals(listOf("Duplicate outputs"), mint.refused)
            assertEquals(
                zaps.map { it.id }.toSet(),
                result.redeemed
                    .single()
                    .nutzaps
                    .map { it.id }
                    .toSet(),
            )
            assertTrue("no nutzap is condemned or left for later: ${result.unredeemable} ${result.failed}", result.unredeemable.isEmpty() && result.failed.isEmpty())
        }

    @Test
    fun aMintWithoutRestoreLeavesTheNutzapsForALaterRetry() =
        runBlocking {
            val mint = FakeMint().apply { restoreStatus = 404 }
            mintWith(Install(), mint, 64)
            val first = Install()
            val zaps = listOf(nutzap(mint, first, listOf(8L)), nutzap(mint, first, listOf(16L)))

            // The refusal is about this install's outputs, not about either nutzap: neither is spent.
            val result = first.ops(mint).redeemNutzaps(zaps, walletPrivKey.toHexKey(), listOf(mintUrl))

            assertTrue(result.unredeemable.toString(), result.unredeemable.isEmpty())
            assertEquals(
                zaps.map { it.id }.toSet(),
                result.failed.keys
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun aQuoteIssuedAlreadyIsRecoveredFromTheSeed() =
        runBlocking {
            val mint = FakeMint()
            val first = Install()
            val started = first.ops(mint).startMintFromLightning(mintUrl, 21)
            val minted = first.ops(mint).completeMintFromLightning(mintUrl, started.quoteEvent, 21)
            // The kind:7375 never reached a relay, and the counter file is gone: a new install.
            val second = Install(first.signer)

            val recovered = second.ops(mint).completeMintFromLightning(mintUrl, started.quoteEvent, 21)

            // The mint looks at the quote before the outputs: no "already signed", no retry,
            // straight to the recovery that hands back what was issued for the quote.
            assertEquals(emptyList<String>(), mint.refused)
            assertEquals(21L, recovered.mintedAmount)
            val mintedSecrets = requireNotNull(minted.tokenEvent.tokenContent(second.signer)).proofs.map { it.secret }.toSet()
            assertEquals(
                "the proofs are the ones the mint issued for the quote, not the first install's at counter 0",
                mintedSecrets,
                requireNotNull(recovered.tokenEvent.tokenContent(second.signer)).proofs.map { it.secret }.toSet(),
            )
        }

    // --- The counter is settled before anything is spent ---

    @Test
    fun aFreshInstallSettlesItsCounterOnce() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 256)
            val second = Install()

            val settlement = second.ops(mint).settleCounter(mintUrl)

            assertTrue(settlement.toString(), settlement is CounterSettlement.Settled)
            assertEquals(keysetId, settlement!!.keysetId)
            assertEquals("the first install's slot", 1L, (settlement as CounterSettlement.Settled).slotsSkipped)
            assertEquals(1L, second.counters.getValue(keysetId))
            assertTrue(keysetId in second.settled)
            // The proof on that slot is the wallet's, and this install didn't hold it.
            assertEquals(
                held.map { it.secret }.toSet(),
                second.published
                    .filterIsInstance<CashuTokenEvent>()
                    .flatMap { requireNotNull(it.tokenContent(second.signer)).proofs }
                    .map { it.secret }
                    .toSet(),
            )

            val postsBefore = mint.restores
            val again = second.ops(mint).settleCounter(mintUrl)

            assertTrue(again.toString(), again is CounterSettlement.AlreadySettled)
            assertEquals("setttled keysets are not scanned again", postsBefore, mint.restores)
        }

    @Test
    fun aFreshInstallSettlesWithoutRepublishingTheProofsItHolds() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 256)
            val second = Install()

            val settlement = second.ops(mint).settleCounter(mintUrl, existingSecrets = held.map { it.secret }.toSet())

            assertEquals(0, (settlement as CounterSettlement.Settled).proofsRecovered)
            assertEquals(1L, second.counters.getValue(keysetId))
            assertFalse(second.published.any { it is CashuTokenEvent })
        }

    @Test
    fun aSettledInstallSpendsWithoutARefusal() =
        runBlocking {
            val mint = FakeMint()
            val held = mintWith(Install(), mint, 256)
            val second = Install()
            second.ops(mint).settleCounter(mintUrl)
            val postsBefore = mint.swapRequests

            val sent = second.ops(mint).sendAsToken(mintUrl, 21, second.holds(held))

            assertEquals(21L, sent.amount)
            assertEquals("nothing was refused", emptyList<String>(), mint.refused)
            assertEquals(postsBefore + 1, mint.swapRequests)
        }

    @Test
    fun aMintWithoutRestoreIsNotAskedAgain() =
        runBlocking {
            val mint = FakeMint().apply { restoreStatus = 404 }
            val install = Install()

            val settlement = install.ops(mint).settleCounter(mintUrl)
            val again = install.ops(mint).settleCounter(mintUrl)

            assertTrue(settlement.toString(), settlement is CounterSettlement.Unsupported)
            assertTrue(again.toString(), again is CounterSettlement.AlreadySettled)
            assertEquals("nothing was aligned", 0L, install.counters[keysetId] ?: 0L)
        }

    @Test
    fun aFailedScanLeavesTheCounterUnsettled() =
        runBlocking {
            val mint = FakeMint().apply { restoreStatus = 500 }
            val install = Install()

            try {
                install.ops(mint).settleCounter(mintUrl)
                fail("the scan failed")
            } catch (e: MintHttpException) {
                assertEquals(500, e.httpStatus)
            }
            assertFalse(keysetId in install.settled)

            mint.restoreStatus = null
            assertTrue(install.ops(mint).settleCounter(mintUrl) is CounterSettlement.Settled)
        }

    /** A nutzap from [sender], its proofs locked to the wallet's kind:10019 key. */
    private suspend fun nutzap(
        mint: FakeMint,
        sender: Install,
        amounts: List<Long>,
    ): NutzapEvent = sender.signer.sign(NutzapEvent.buildToUser("zap", mint.lockedProofs(amounts, walletPubKey), mintUrl, "sat", sender.signer.pubKey))

    private companion object {
        const val INVOICE_AMOUNT = 21L
        const val FEE_RESERVE = 1L
        const val PREIMAGE = "00"
    }
}
