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
package com.vitorpamplona.quartz.nipBCOnchainZaps.chain

import com.vitorpamplona.quartz.nip01Core.jackson.JacksonMapper
import com.vitorpamplona.quartz.utils.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.coroutines.executeAsync

/**
 * [OnchainBackend] implementation that talks to an Esplora-compatible HTTP
 * API (mempool.space, blockstream.info, self-hosted).
 *
 * The API surface used:
 * - `GET /tx/{txid}`            — transaction JSON
 * - `GET /address/{addr}/utxo`  — UTXO list for a derived address
 * - `POST /tx`                  — broadcast (body = raw tx hex)
 * - `GET /blocks/tip/height`    — chain tip
 * - `GET /v1/fees/recommended`  — fee tiers (mempool.space); falls back to
 *   `GET /fee-estimates` (the standard Esplora target→rate map) on 404
 *
 * The `baseUrl` is supplied as a function so callers can hot-swap endpoints
 * from `AccountSettings` without rebuilding the backend.
 *
 * @param baseUrl Function returning the base URL with trailing slash stripped
 *                (e.g. "https://mempool.space/api").
 * @param client Shared OkHttp client.
 */
class EsploraBackend(
    private val baseUrl: () -> String,
    private val client: OkHttpClient,
) : OnchainBackend {
    private val logTag = "EsploraBackend"

    override suspend fun getTx(txid: String): BitcoinTx? {
        val url = "${baseUrl()}/tx/$txid"
        val request =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(url)
                .get()
                .build()
        return client.newCall(request).executeAsync().use { response ->
            when {
                response.code == 404 -> null

                response.isSuccessful -> parseTx(response.body.string())

                else -> throw OnchainBackendException(
                    "GET $url failed: ${response.code} ${response.message}",
                )
            }
        }
    }

    override suspend fun getUtxosForAddress(address: String): List<Utxo> {
        val url = "${baseUrl()}/address/$address/utxo"
        val request =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(url)
                .get()
                .build()
        return client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw OnchainBackendException(
                    "GET $url failed: ${response.code} ${response.message}",
                )
            }
            parseUtxoList(response.body.string())
        }
    }

    override suspend fun getTxsForAddress(
        address: String,
        afterTxid: String?,
    ): List<BitcoinAddressTx> {
        // Esplora returns mempool + most-recent confirmed for the first call,
        // and successive pages of confirmed via /chain/:last_seen_txid.
        val url =
            if (afterTxid == null) {
                "${baseUrl()}/address/$address/txs"
            } else {
                "${baseUrl()}/address/$address/txs/chain/$afterTxid"
            }
        val request =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(url)
                .get()
                .build()
        return client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw OnchainBackendException(
                    "GET $url failed: ${response.code} ${response.message}",
                )
            }
            parseAddressTxList(response.body.string(), address)
        }
    }

    override suspend fun broadcast(rawTxHex: String): String {
        val url = "${baseUrl()}/tx"
        val request =
            Request
                .Builder()
                .url(url)
                .post(rawTxHex.toRequestBody("text/plain".toMediaType()))
                .build()
        return client.newCall(request).executeAsync().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw OnchainBackendException(
                    "POST $url failed: ${response.code} ${response.message} body=$body",
                )
            }
            body.trim()
        }
    }

    override suspend fun tipHeight(): Long {
        val url = "${baseUrl()}/blocks/tip/height"
        val request =
            Request
                .Builder()
                .url(url)
                .get()
                .build()
        return client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw OnchainBackendException(
                    "GET $url failed: ${response.code} ${response.message}",
                )
            }
            response.body
                .string()
                .trim()
                .toLong()
        }
    }

    override suspend fun feeEstimates(): FeeEstimates {
        // mempool.space-style recommended-fees endpoint.
        val recommendedUrl = "${baseUrl()}/v1/fees/recommended"
        val recommended =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(recommendedUrl)
                .get()
                .build()
        client.newCall(recommended).executeAsync().use { response ->
            when {
                response.isSuccessful -> {
                    return parseRecommendedFees(response.body.string())
                }

                // Standard Esplora servers (blockstream.info, self-hosted) don't
                // expose /v1/fees/recommended — fall back to /fee-estimates.
                response.code == 404 -> {
                    Unit
                }

                else -> {
                    throw OnchainBackendException(
                        "GET $recommendedUrl failed: ${response.code} ${response.message}",
                    )
                }
            }
        }

        val estimatesUrl = "${baseUrl()}/fee-estimates"
        val estimates =
            Request
                .Builder()
                .header("Accept", "application/json")
                .url(estimatesUrl)
                .get()
                .build()
        return client.newCall(estimates).executeAsync().use { response ->
            if (!response.isSuccessful) {
                throw OnchainBackendException(
                    "GET $estimatesUrl failed: ${response.code} ${response.message}",
                )
            }
            parseFeeEstimates(response.body.string())
        }
    }

    internal fun parseTx(json: String): BitcoinTx {
        val node = JacksonMapper.mapper.readTree(json)
        val txid = node["txid"].asText()
        val status = node["status"]
        val confirmed = status?.get("confirmed")?.asBoolean() == true
        val blockHeight = status?.get("block_height")?.asLong()
        val blockHash = status?.get("block_hash")?.asText()

        val outputs =
            node["vout"].mapIndexed { idx, vout ->
                BitcoinTxOutput(
                    index = idx,
                    valueSats = vout["value"].asLong(),
                    scriptPubKeyHex = vout["scriptpubkey"].asText().lowercase(),
                )
            }

        val confirmations =
            if (confirmed && blockHeight != null) {
                // Esplora returns confirmations only via /tx/{txid}/status sometimes;
                // we conservatively report 1 here and let callers re-query tip if they
                // need a precise count.
                1
            } else {
                0
            }

        return BitcoinTx(
            txid = txid,
            outputs = outputs,
            confirmations = confirmations,
            blockHashHex = blockHash,
            blockHeight = blockHeight,
        )
    }

    /**
     * Parse an Esplora `/address/:addr/txs[...] `response — an array of full
     * tx objects with vin/vout/status — into address-perspective rows.
     */
    internal fun parseAddressTxList(
        json: String,
        address: String,
    ): List<BitcoinAddressTx> {
        val node = JacksonMapper.mapper.readTree(json)
        return node.map { tx ->
            val txid = tx["txid"].asText()
            val status = tx["status"]
            val confirmed = status?.get("confirmed")?.asBoolean() == true
            val blockHeight = status?.get("block_height")?.asLong()
            val blockTime = status?.get("block_time")?.asLong()

            var spentByMe = 0L
            val incomingCounterparties = LinkedHashSet<String>()
            tx["vin"]?.forEach { vin ->
                val prevout = vin["prevout"]
                val prevAddress = prevout?.get("scriptpubkey_address")?.asText()
                val value = prevout?.get("value")?.asLong() ?: 0L
                if (prevAddress == address) {
                    spentByMe += value
                } else if (prevAddress != null) {
                    incomingCounterparties.add(prevAddress)
                }
            }

            var receivedByMe = 0L
            val outgoingCounterparties = LinkedHashSet<String>()
            tx["vout"]?.forEach { vout ->
                val outAddress = vout["scriptpubkey_address"]?.asText()
                val value = vout["value"]?.asLong() ?: 0L
                if (outAddress == address) {
                    receivedByMe += value
                } else if (outAddress != null) {
                    outgoingCounterparties.add(outAddress)
                }
            }

            val net = receivedByMe - spentByMe
            val counterparties =
                if (net >= 0L) incomingCounterparties.toList() else outgoingCounterparties.toList()

            BitcoinAddressTx(
                txid = txid,
                netValueSats = net,
                confirmations = if (confirmed) 1 else 0,
                blockHeight = blockHeight,
                blockTime = blockTime,
                counterpartyAddresses = counterparties,
            )
        }
    }

    internal fun parseUtxoList(json: String): List<Utxo> {
        val node = JacksonMapper.mapper.readTree(json)
        return node.map { utxo ->
            val confirmed = utxo["status"]?.get("confirmed")?.asBoolean() == true
            Utxo(
                txid = utxo["txid"].asText(),
                vout = utxo["vout"].asInt(),
                valueSats = utxo["value"].asLong(),
                confirmations = if (confirmed) 1 else 0,
            )
        }
    }

    /** mempool.space `/v1/fees/recommended`: `{ fastestFee, halfHourFee, hourFee, minimumFee }`. */
    internal fun parseRecommendedFees(json: String): FeeEstimates {
        val node = JacksonMapper.mapper.readTree(json)
        val fast = node["fastestFee"]?.asDouble()
        val normal = node["halfHourFee"]?.asDouble() ?: fast
        val slow = node["hourFee"]?.asDouble() ?: node["minimumFee"]?.asDouble() ?: normal

        if (fast == null) {
            Log.w(logTag) { "fee response missing 'fastestFee': $json" }
            return FeeEstimates(20.0, 10.0, 5.0)
        }
        return FeeEstimates(
            fastSatPerVbyte = fast,
            normalSatPerVbyte = normal ?: fast,
            slowSatPerVbyte = slow ?: fast,
        )
    }

    /**
     * Standard Esplora `/fee-estimates`: a JSON object mapping a confirmation
     * target (in blocks, as a string key) to the estimated sat/vB. We map the
     * 2-block / 6-block / 144-block targets to the fast / normal / slow tiers.
     */
    internal fun parseFeeEstimates(json: String): FeeEstimates {
        val node = JacksonMapper.mapper.readTree(json)

        fun rate(vararg targets: String): Double? = targets.firstNotNullOfOrNull { node[it]?.asDouble() }

        val fast = rate("1", "2")
        val normal = rate("4", "6", "3")
        val slow = rate("144", "1008", "10")

        if (fast == null) {
            Log.w(logTag) { "fee-estimates response missing block targets: $json" }
            return FeeEstimates(20.0, 10.0, 5.0)
        }
        return FeeEstimates(
            fastSatPerVbyte = fast,
            normalSatPerVbyte = normal ?: fast,
            slowSatPerVbyte = slow ?: normal ?: fast,
        )
    }

    companion object {
        const val MEMPOOL_API_URL = "https://mempool.space/api"
        const val BLOCKSTREAM_API_URL = "https://blockstream.info/api"
    }
}
