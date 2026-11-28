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
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.mintApi.BlindedMessageDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CashuMintOperations
import com.vitorpamplona.quartz.nip60Cashu.mintApi.CheckStateRequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltBolt11RequestDto
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpResponse
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MintHttpTransport
import com.vitorpamplona.quartz.nip60Cashu.mintApi.SwapRequestDto
import com.vitorpamplona.quartz.utils.secp256k1.Secp256k1
import kotlinx.serialization.json.Json

/**
 * A fee-free mint that really signs with one private key for every amount. Answers the
 * endpoints the wallet-ops tests touch: /v1/keys, /v1/keysets, /v1/swap and
 * /v1/melt/bolt11. Extracted from [MeltToLightningRolloverTest] so the
 * sign-denial tests can reuse it.
 *
 * The melt follows NUT-08: the mint imprints its refund — what the inputs hold beyond
 * [invoiceAmount] and the [lightningFee] its backend kept, largest denomination first — into as
 * many of the leading outputs as it takes, whatever amounts the wallet wrote in them. A mint
 * decides the amounts; whether the wallet sends its outputs blank is quartz's to keep. Without
 * [invoiceAmount] there is no refund at all: a mint without NUT-08 keeps the overpayment.
 */
internal class FakeMint(
    private val keysetId: String,
    private val invoiceAmount: Long? = null,
    private val lightningFee: Long = 0L,
    /**
     * When set, /v1/melt/bolt11 answers a definitive NUT-00 refusal (HTTP 400)
     * WITHOUT spending the inputs — the shape a mint returns when the LN route
     * failed or the quote expired. The melt-failure recovery path re-publishes
     * those inputs after a NUT-07 check confirms them unspent.
     */
    private val refuseMelt: Boolean = false,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val privKey = RandomInstance.bytes(32)
    private val pubKeyHex = Secp256k1.pubKeyCompress(Secp256k1.pubkeyCreate(privKey)).toHexKey()
    private val spent = HashSet<String>()

    /** Total amount of proofs handed to the melt (the swap-down's send side). */
    var meltedTotal = 0L
        private set

    fun isSpent(secret: String) = secret in spent

    private fun handleGet(path: String): MintHttpResponse {
        val keys = (0..20).joinToString(",") { "\"${1L shl it}\":\"$pubKeyHex\"" }
        return when (path) {
            "/v1/keys" -> MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","keys":{$keys},"input_fee_ppk":0}]}""")
            "/v1/keysets" -> MintHttpResponse(200, """{"keysets":[{"id":"$keysetId","unit":"sat","active":true,"input_fee_ppk":0}]}""")
            else -> MintHttpResponse(404, """{"detail":"not found"}""")
        }
    }

    private fun signature(
        out: BlindedMessageDto,
        amount: Long,
    ): String {
        val cTick = Bdhke.sign(out.bTick.hexToByteArray(), privKey).toHexKey()
        return """{"amount":$amount,"id":"${out.id}","C_":"$cTick"}"""
    }

    private fun handleMelt(body: String): MintHttpResponse {
        if (refuseMelt) {
            return MintHttpResponse(400, """{"code":20000,"detail":"Lightning payment failed"}""")
        }
        val request = json.decodeFromString<MeltBolt11RequestDto>(body)
        request.inputs.forEach {
            spent += it.secret
            meltedTotal += it.amount
        }
        val outputs = request.outputs.orEmpty()
        val refund = invoiceAmount?.let { request.inputs.sumOf { proof -> proof.amount } - it - lightningFee } ?: 0L
        // What doesn't fit into the outputs it was given, the mint keeps.
        val change =
            CashuMintOperations.splitAmounts(refund.coerceAtLeast(0L)).asReversed().mapIndexedNotNull { index, amount ->
                outputs.getOrNull(index)?.let { signature(it, amount) }
            }
        val changeJson = change.joinToString(",")
        return MintHttpResponse(200, "{\"state\":\"PAID\",\"paid\":true,\"payment_preimage\":\"aabb\",\"change\":[$changeJson]}")
    }

    /** NUT-07: reports the spent secrets (by their Y = hash-to-curve) as SPENT, the rest UNSPENT. */
    private fun handleCheckState(body: String): MintHttpResponse {
        val request = json.decodeFromString<CheckStateRequestDto>(body)
        val spentYs = spent.map { Bdhke.hashToCurveCompressed(it.encodeToByteArray()).toHexKey() }.toSet()
        val states =
            request.ys.joinToString(",") { y ->
                val state = if (y in spentYs) "SPENT" else "UNSPENT"
                """{"Y":"$y","state":"$state"}"""
            }
        return MintHttpResponse(200, """{"states":[$states]}""")
    }

    private fun swapEndpoints(body: String): MintHttpResponse {
        val request = json.decodeFromString<SwapRequestDto>(body)
        request.inputs.forEach { spent += it.secret }
        return MintHttpResponse(200, """{"signatures":[${request.outputs.joinToString(",") { signature(it, it.amount) }}]}""")
    }

    inner class Transport : MintHttpTransport {
        private fun route(url: String): String = "/" + url.removePrefix("https://").substringAfter('/')

        override suspend fun get(url: String): MintHttpResponse = handleGet(route(url))

        override suspend fun postJson(
            url: String,
            json: String,
        ): MintHttpResponse =
            when (route(url)) {
                "/v1/swap" -> swapEndpoints(json)
                "/v1/melt/bolt11" -> handleMelt(json)
                "/v1/checkstate" -> handleCheckState(json)
                else -> MintHttpResponse(404, """{"detail":"not found"}""")
            }
    }
}
