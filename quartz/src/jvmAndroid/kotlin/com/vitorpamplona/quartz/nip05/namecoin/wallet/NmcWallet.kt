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
package com.vitorpamplona.quartz.nip05.namecoin.wallet

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxClient
import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicInteger
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Full Namecoin wallet with key management, balance tracking,
 * transaction signing, and name operations.
 *
 * This is the complete Electrum-NMC wallet functionality ported
 * to Amethyst's architecture. It can:
 *
 * - **Generate/import keys**: BIP44 derivation from mnemonic, derivation from
 *   Nostr key, WIF import/export
 * - **Query balances**: confirmed/unconfirmed NMC balance via ElectrumX
 * - **List UTXOs**: for coin selection during transaction building
 * - **Build transactions**: P2PKH sends, name operations
 * - **Sign transactions**: ECDSA signing with the wallet's private key
 * - **Broadcast**: submit signed transactions to the Namecoin network
 * - **Name operations**: name_new, name_firstupdate, name_update with
 *   automatic UTXO selection and change output
 * - **Fee estimation**: query ElectrumX for current fee rates
 *
 * Name availability checks delegate to the existing [ElectrumxClient]
 * from the Namecoin resolution layer. Wallet-specific RPC queries
 * (balance, UTXOs, broadcast, etc.) use a minimal internal RPC helper.
 *
 * Does NOT handle its own persistence — the caller (NmcWalletService)
 * manages state storage.
 */
class NmcWallet(
    private val electrumClient: ElectrumxClient = ElectrumxClient(),
    private val connectTimeoutMs: Long = 10_000L,
    private val readTimeoutMs: Long = 15_000L,
    private val socketFactory: () -> SocketFactory = { SocketFactory.getDefault() },
) {
    private val rpc = ElectrumxRpc(connectTimeoutMs, readTimeoutMs, socketFactory)

    // ── Key State ──────────────────────────────────────────────────────

    private var privKey: ByteArray? = null
    private var pubKey: ByteArray? = null
    private var nmcAddress: String? = null
    private var scripthash: String? = null

    val isLoaded: Boolean get() = privKey != null
    val address: String? get() = nmcAddress
    val pubKeyHex: String? get() = pubKey?.toHexKey()

    // ── Key Management ─────────────────────────────────────────────────

    /** Load wallet from a raw 32-byte private key. */
    fun loadFromPrivateKey(key: ByteArray) {
        privKey = key.copyOf()
        pubKey = NmcKeyManager.compressedPubKey(key)
        nmcAddress = NmcKeyManager.addressFromPubKey(pubKey!!)
        scripthash = computeScripthash(pubKey!!)
    }

    /** Load wallet from a BIP39 mnemonic using BIP44 m/44'/7'/0'/0/0. */
    fun loadFromMnemonic(
        mnemonic: String,
        account: Long = 0,
        index: Long = 0,
    ) {
        loadFromPrivateKey(NmcKeyManager.privateKeyFromMnemonic(mnemonic, account, index))
    }

    /** Load wallet from a WIF-encoded private key. */
    fun loadFromWif(wif: String) {
        val key = NmcKeyManager.wifToPrivateKey(wif) ?: throw IllegalArgumentException("Invalid WIF")
        loadFromPrivateKey(key)
    }

    /** Derive NMC wallet from the user's Nostr private key. */
    fun loadFromNostrKey(nostrPrivKey: ByteArray) {
        loadFromPrivateKey(NmcKeyManager.privateKeyFromNostrKey(nostrPrivKey))
    }

    /** Export the private key as WIF (for import into Electrum-NMC). */
    fun exportWif(): String {
        val key = privKey ?: throw IllegalStateException("No key loaded")
        return NmcKeyManager.privateKeyToWif(key)
    }

    /** Export the raw 32-byte private key hex. */
    fun exportPrivKeyHex(): String {
        val key = privKey ?: throw IllegalStateException("No key loaded")
        return key.toHexKey()
    }

    /** Wipe the private key from memory. */
    fun lock() {
        privKey?.fill(0)
        privKey = null
    }

    // ── Balance & UTXOs ────────────────────────────────────────────────

    suspend fun getBalance(servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS): NmcBalance {
        val sh = scripthash ?: return NmcBalance()
        return rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.scripthash.get_balance", listOf(sh))
            val result = resp?.get("result")?.jsonObject ?: return@withServer NmcBalance()
            NmcBalance(
                confirmed = result["confirmed"]?.jsonPrimitive?.long ?: 0L,
                unconfirmed = result["unconfirmed"]?.jsonPrimitive?.long ?: 0L,
            )
        } ?: NmcBalance()
    }

    suspend fun listUnspent(servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS): List<NmcUtxo> {
        val sh = scripthash ?: return emptyList()
        return rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.scripthash.listunspent", listOf(sh))
            resp?.get("result")?.jsonArray?.mapNotNull { e ->
                val o = e.jsonObject
                NmcUtxo(
                    txHash = o["tx_hash"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    txPos = o["tx_pos"]?.jsonPrimitive?.int ?: return@mapNotNull null,
                    height = o["height"]?.jsonPrimitive?.int ?: 0,
                    value = o["value"]?.jsonPrimitive?.long ?: return@mapNotNull null,
                )
            } ?: emptyList()
        } ?: emptyList()
    }

    suspend fun getHistory(servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS): List<NmcHistoryEntry> {
        val sh = scripthash ?: return emptyList()
        return rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.scripthash.get_history", listOf(sh))
            resp?.get("result")?.jsonArray?.mapNotNull { e ->
                val o = e.jsonObject
                NmcHistoryEntry(
                    txHash = o["tx_hash"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    height = o["height"]?.jsonPrimitive?.int ?: 0,
                )
            } ?: emptyList()
        } ?: emptyList()
    }

    suspend fun estimateFee(
        numBlocks: Int = 6,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): Double =
        rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.estimatefee", listOf(numBlocks))
            val r = resp?.get("result")?.jsonPrimitive?.double ?: -1.0
            if (r <= 0) 0.0001 else r
        } ?: 0.0001

    suspend fun getBlockHeight(servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS): Int? =
        rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.headers.subscribe", emptyList<String>())
            resp
                ?.get("result")
                ?.jsonObject
                ?.get("height")
                ?.jsonPrimitive
                ?.int
        }

    // ── Name Availability ──────────────────────────────────────────────

    /**
     * Check whether a name is available for registration.
     * Delegates to the existing [ElectrumxClient.nameShowWithFallback].
     */
    suspend fun checkNameAvailability(
        name: String,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): NameAvailability =
        try {
            val result = electrumClient.nameShowWithFallback(name, servers)
            if (result == null) {
                NameAvailability.Available
            } else {
                val expiresIn = result.expiresIn ?: 0
                if (expiresIn <= 0) {
                    NameAvailability.Expired(result.value)
                } else {
                    NameAvailability.Taken(result.value, expiresIn)
                }
            }
        } catch (e: Exception) {
            NameAvailability.Error(e.message ?: "Lookup failed")
        }

    // ── Transaction Building & Signing ─────────────────────────────────

    /**
     * Send NMC to an address.
     *
     * Automatically selects UTXOs, computes fees, creates a change
     * output, signs, and broadcasts.
     *
     * @return txid on success
     */
    suspend fun sendToAddress(
        toAddress: String,
        amountSatoshis: Long,
        feeRateNmcPerKb: Double? = null,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): String {
        requireLoaded()
        val feeRate = feeRateNmcPerKb ?: estimateFee(servers = servers)
        val utxos = listUnspent(servers).sortedByDescending { it.value }

        val (selected, totalInput) = selectUtxos(utxos, amountSatoshis, feeRate, numOutputs = 2)
        val estSize = NmcTransactionBuilder.estimateTxSize(selected.size, 2)
        val fee = (feeRate * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)
        val change = totalInput - amountSatoshis - fee

        val builder = NmcTransactionBuilder()
        selected.forEach { utxo ->
            val prevScript = NmcTransactionBuilder.buildP2PKHScript(NmcKeyManager.hash160(pubKey!!))
            builder.addInput(utxo.txHash, utxo.txPos, prevScript, utxo.value)
        }
        builder.addP2PKHOutput(toAddress, amountSatoshis)
        if (change > 546) { // dust threshold
            builder.addP2PKHOutput(nmcAddress!!, change)
        }

        val rawTx = builder.sign(List(selected.size) { privKey!! })
        return broadcast(rawTx, servers)
    }

    /**
     * Execute a NAME_NEW operation: pre-register a name.
     *
     * @return Pair of (txid, NameNewData) — save the NameNewData for step 2
     */
    suspend fun nameNew(
        name: String,
        feeRateNmcPerKb: Double? = null,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): Pair<String, NameNewData> {
        requireLoaded()
        val data = NmcNameScripts.prepareNameNew(name)
        val feeRate = feeRateNmcPerKb ?: estimateFee(servers = servers)
        val utxos = listUnspent(servers).sortedByDescending { it.value }

        val nameScript = NmcNameScripts.buildNameNewScript(data.commitment, NmcKeyManager.hash160(pubKey!!))
        val extraBytes = nameScript.size
        val needed = NmcNameScripts.NAME_NEW_COST
        val (selected, totalInput) = selectUtxos(utxos, needed, feeRate, numOutputs = 2, extraScriptBytes = extraBytes)
        val estSize = NmcTransactionBuilder.estimateTxSize(selected.size, 2, extraBytes)
        val fee = (feeRate * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)
        val change = totalInput - needed - fee

        val builder = NmcTransactionBuilder()
        selected.forEach { utxo ->
            builder.addInput(utxo.txHash, utxo.txPos, NmcTransactionBuilder.buildP2PKHScript(NmcKeyManager.hash160(pubKey!!)), utxo.value)
        }
        builder.addScriptOutput(nameScript, needed)
        if (change > 546) builder.addP2PKHOutput(nmcAddress!!, change)

        val rawTx = builder.sign(List(selected.size) { privKey!! })
        val txid = broadcast(rawTx, servers)
        return txid to data
    }

    /**
     * Execute a NAME_FIRSTUPDATE: reveal and register the name.
     * Must be called ≥12 blocks after NAME_NEW.
     *
     * @param nameNewTxid The txid of the NAME_NEW transaction (used as input)
     * @param nameNewVout The output index of the NAME_NEW in that transaction
     * @param data The [NameNewData] from the nameNew() call
     * @param value The JSON value to set for the name
     * @return txid on success
     */
    suspend fun nameFirstUpdate(
        nameNewTxid: String,
        nameNewVout: Int,
        data: NameNewData,
        value: String,
        feeRateNmcPerKb: Double? = null,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): String {
        requireLoaded()
        val feeRate = feeRateNmcPerKb ?: estimateFee(servers = servers)

        val nameScript =
            NmcNameScripts.buildNameFirstUpdateScript(
                data.name,
                data.salt,
                value,
                NmcKeyManager.hash160(pubKey!!),
            )
        // NAME_NEW output is spent as an input
        val nameNewScript = NmcNameScripts.buildNameNewScript(data.commitment, NmcKeyManager.hash160(pubKey!!))

        val builder = NmcTransactionBuilder()
        builder.addInput(nameNewTxid, nameNewVout, nameNewScript, NmcNameScripts.NAME_NEW_COST)

        // May need additional UTXOs for fee
        val utxos = listUnspent(servers).sortedByDescending { it.value }
        val extraBytes = nameScript.size
        val estSize = NmcTransactionBuilder.estimateTxSize(1, 2, extraBytes)
        val fee = (feeRate * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)

        val nameOutputValue = NmcNameScripts.NAME_NEW_COST
        var totalInput = nameOutputValue
        val addedUtxos = mutableListOf<NmcUtxo>()
        if (totalInput < nameOutputValue + fee) {
            for (utxo in utxos) {
                builder.addInput(utxo.txHash, utxo.txPos, NmcTransactionBuilder.buildP2PKHScript(NmcKeyManager.hash160(pubKey!!)), utxo.value)
                totalInput += utxo.value
                addedUtxos.add(utxo)
                if (totalInput >= nameOutputValue + fee) break
            }
        }

        builder.addScriptOutput(nameScript, nameOutputValue)
        val change = totalInput - nameOutputValue - fee
        if (change > 546) builder.addP2PKHOutput(nmcAddress!!, change)

        val keys = listOf(privKey!!) + List(addedUtxos.size) { privKey!! }
        val rawTx = builder.sign(keys)
        return broadcast(rawTx, servers)
    }

    /**
     * Execute a NAME_UPDATE: update the value of an owned name or renew it.
     *
     * @param nameTxid The txid of the current name transaction (NAME_FIRSTUPDATE or last NAME_UPDATE)
     * @param nameVout The output index of the name in that transaction
     * @param name The name being updated
     * @param currentScript The current script of the name output (for signing)
     * @param currentOutputValue The satoshi value of the current name output
     * @param newValue The new JSON value
     * @return txid on success
     */
    suspend fun nameUpdate(
        nameTxid: String,
        nameVout: Int,
        name: String,
        currentScript: ByteArray,
        currentOutputValue: Long,
        newValue: String,
        feeRateNmcPerKb: Double? = null,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): String {
        requireLoaded()
        val feeRate = feeRateNmcPerKb ?: estimateFee(servers = servers)

        val nameScript = NmcNameScripts.buildNameUpdateScript(name, newValue, NmcKeyManager.hash160(pubKey!!))

        val builder = NmcTransactionBuilder()
        builder.addInput(nameTxid, nameVout, currentScript, currentOutputValue)

        val utxos = listUnspent(servers).sortedByDescending { it.value }
        val extraBytes = nameScript.size
        val estSize = NmcTransactionBuilder.estimateTxSize(1, 2, extraBytes)
        val fee = (feeRate * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)

        var totalInput = currentOutputValue
        val addedUtxos = mutableListOf<NmcUtxo>()
        if (totalInput < currentOutputValue + fee) {
            for (utxo in utxos) {
                builder.addInput(utxo.txHash, utxo.txPos, NmcTransactionBuilder.buildP2PKHScript(NmcKeyManager.hash160(pubKey!!)), utxo.value)
                totalInput += utxo.value
                addedUtxos.add(utxo)
                if (totalInput >= currentOutputValue + fee) break
            }
        }

        builder.addScriptOutput(nameScript, currentOutputValue) // preserve name value
        val change = totalInput - currentOutputValue - fee
        if (change > 546) builder.addP2PKHOutput(nmcAddress!!, change)

        val keys = listOf(privKey!!) + List(addedUtxos.size) { privKey!! }
        val rawTx = builder.sign(keys)
        return broadcast(rawTx, servers)
    }

    /**
     * Transfer a name to a new owner address via NAME_UPDATE.
     *
     * Builds a NAME_UPDATE that preserves the name's current value but
     * changes the P2PKH owner in the output script to [newOwnerAddress].
     *
     * @param nameTxid Txid of the current name transaction
     * @param nameVout Output index of the name in that transaction
     * @param name The name being transferred (e.g. "d/example")
     * @param currentScript The current output's scriptPubKey (for signing the input)
     * @param currentOutputValue Satoshis locked in the current name output
     * @param currentNameValue The current JSON value of the name (preserved in transfer)
     * @param newOwnerAddress The recipient's Namecoin address (N… or 6…)
     * @return txid on success
     */
    suspend fun transferName(
        nameTxid: String,
        nameVout: Int,
        name: String,
        currentScript: ByteArray,
        currentOutputValue: Long,
        currentNameValue: String,
        newOwnerAddress: String,
        feeRateNmcPerKb: Double? = null,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): String {
        requireLoaded()
        require(NmcKeyManager.isValidAddress(newOwnerAddress)) { "Invalid recipient address" }
        val feeRate = feeRateNmcPerKb ?: estimateFee(servers = servers)

        // Build NAME_UPDATE with the NEW owner's hash160 in the output script
        val newOwnerHash160 =
            NmcKeyManager.addressToHash160(newOwnerAddress)
                ?: throw IllegalArgumentException("Cannot decode recipient address")
        val nameScript = NmcNameScripts.buildNameUpdateScript(name, currentNameValue, newOwnerHash160)

        val builder = NmcTransactionBuilder()
        builder.addInput(nameTxid, nameVout, currentScript, currentOutputValue)

        val utxos = listUnspent(servers).sortedByDescending { it.value }
        val extraBytes = nameScript.size
        val estSize = NmcTransactionBuilder.estimateTxSize(1, 2, extraBytes)
        val fee = (feeRate * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)

        var totalInput = currentOutputValue
        val addedUtxos = mutableListOf<NmcUtxo>()
        if (totalInput < currentOutputValue + fee) {
            for (utxo in utxos) {
                builder.addInput(utxo.txHash, utxo.txPos, NmcTransactionBuilder.buildP2PKHScript(NmcKeyManager.hash160(pubKey!!)), utxo.value)
                totalInput += utxo.value
                addedUtxos.add(utxo)
                if (totalInput >= currentOutputValue + fee) break
            }
        }

        builder.addScriptOutput(nameScript, currentOutputValue)
        val change = totalInput - currentOutputValue - fee
        if (change > 546) builder.addP2PKHOutput(nmcAddress!!, change)

        val keys = listOf(privKey!!) + List(addedUtxos.size) { privKey!! }
        val rawTx = builder.sign(keys)
        return broadcast(rawTx, servers)
    }

    /**
     * Broadcast a signed raw transaction.
     */
    suspend fun broadcast(
        rawTxHex: String,
        servers: List<ElectrumxServer> = ElectrumxClient.DEFAULT_SERVERS,
    ): String =
        rpc.withServer(servers) { writer, reader ->
            val resp = rpc.call(writer, reader, "blockchain.transaction.broadcast", listOf(rawTxHex))
            val result = resp?.get("result")
            if (result is JsonPrimitive && result.isString) {
                result.content
            } else {
                throw Exception("Broadcast failed: ${resp?.toString()}")
            }
        } ?: throw Exception("All servers unreachable")

    // ── UTXO Selection ─────────────────────────────────────────────────

    private fun selectUtxos(
        utxos: List<NmcUtxo>,
        targetSatoshis: Long,
        feeRateNmcPerKb: Double,
        numOutputs: Int,
        extraScriptBytes: Int = 0,
    ): Pair<List<NmcUtxo>, Long> {
        val selected = mutableListOf<NmcUtxo>()
        var total = 0L
        for (utxo in utxos) {
            selected.add(utxo)
            total += utxo.value
            val estSize = NmcTransactionBuilder.estimateTxSize(selected.size, numOutputs, extraScriptBytes)
            val estFee = (feeRateNmcPerKb * estSize / 1000 * 100_000_000).toLong().coerceAtLeast(1000)
            if (total >= targetSatoshis + estFee) return selected to total
        }
        throw InsufficientFundsException(targetSatoshis, total)
    }

    private fun requireLoaded() {
        requireNotNull(privKey) { "Wallet not loaded — call loadFromPrivateKey/loadFromMnemonic/loadFromNostrKey first" }
    }

    private fun computeScripthash(compressedPubKey: ByteArray): String {
        val hash160 = NmcKeyManager.hash160(compressedPubKey)
        val script = NmcTransactionBuilder.buildP2PKHScript(hash160)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(script)
        return sha256.reversedArray().toHexKey()
    }
}

/**
 * Minimal ElectrumX RPC helper for wallet-specific queries
 * (balance, UTXOs, fee estimation, broadcast, headers).
 *
 * Name resolution is handled by the existing [ElectrumxClient] —
 * this helper only covers the general Bitcoin/Namecoin RPC methods
 * that the wallet needs and [ElectrumxClient] does not expose.
 */
private class ElectrumxRpc(
    private val connectTimeoutMs: Long,
    private val readTimeoutMs: Long,
    private val socketFactory: () -> SocketFactory,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    private val requestId = AtomicInteger(0)

    /**
     * Connect to the first reachable server, negotiate, and run [block].
     */
    suspend fun <T> withServer(
        servers: List<ElectrumxServer>,
        block: (PrintWriter, BufferedReader) -> T,
    ): T? =
        withContext(Dispatchers.IO) {
            for (server in servers) {
                try {
                    val socket = createSocket(server)
                    socket.soTimeout = readTimeoutMs.toInt()
                    val writer = PrintWriter(socket.outputStream, true)
                    val reader = BufferedReader(InputStreamReader(socket.inputStream))
                    try {
                        // Negotiate protocol version
                        writer.println(buildRpcRequest("server.version", listOf("AmethystNMC/0.2", "1.4")))
                        reader.readLine()
                        return@withContext block(writer, reader)
                    } finally {
                        runCatching { writer.close() }
                        runCatching { reader.close() }
                        runCatching { socket.close() }
                    }
                } catch (_: Exception) {
                    // try next server
                }
            }
            null
        }

    fun call(
        writer: PrintWriter,
        reader: BufferedReader,
        method: String,
        params: List<Any>,
    ): JsonObject? {
        writer.println(buildRpcRequest(method, params))
        val line = reader.readLine() ?: return null
        val obj = json.parseToJsonElement(line).jsonObject
        if (obj["error"] != null && obj["error"] !is JsonNull) return null
        return obj
    }

    private fun buildRpcRequest(
        method: String,
        params: List<Any>,
    ): String {
        val id = requestId.incrementAndGet()
        val obj =
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("id", id)
                put("method", method)
                put(
                    "params",
                    json.encodeToJsonElement(
                        ListSerializer(JsonElement.serializer()),
                        params.map {
                            when (it) {
                                is Boolean -> JsonPrimitive(it)
                                is Number -> JsonPrimitive(it)
                                else -> JsonPrimitive(it.toString())
                            }
                        },
                    ),
                )
            }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun createSocket(server: ElectrumxServer): java.net.Socket {
        val base =
            socketFactory().createSocket().apply {
                connect(InetSocketAddress(server.host, server.port), connectTimeoutMs.toInt())
            }
        if (!server.useSsl) return base
        val factory = if (server.trustAllCerts) trustAllSslFactory() else SSLSocketFactory.getDefault() as SSLSocketFactory
        return factory.createSocket(base, server.host, server.port, true)
    }

    private fun trustAllSslFactory(): SSLSocketFactory {
        val tm =
            arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(
                        c: Array<java.security.cert.X509Certificate>,
                        a: String,
                    ) {}

                    override fun checkServerTrusted(
                        c: Array<java.security.cert.X509Certificate>,
                        a: String,
                    ) {}

                    override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()
                },
            )
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, tm, SecureRandom())
        return ctx.socketFactory
    }
}

class InsufficientFundsException(
    val needed: Long,
    val available: Long,
) : Exception("Insufficient funds: need ${needed / 1e8} NMC, have ${available / 1e8} NMC")
