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
package com.vitorpamplona.amethyst.service.namecoin

import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxClient
import com.vitorpamplona.quartz.nip05.namecoin.ElectrumxServer
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NameAvailability
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NameNewData
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcBalance
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcHistoryEntry
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcKeyManager
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcNameScripts
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcUtxo
import com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcWallet
import com.vitorpamplona.quartz.nip05.namecoin.wallet.PendingNameRegistration
import com.vitorpamplona.quartz.utils.Hex
import com.vitorpamplona.quartz.utils.TimeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Application-level NMC wallet service.
 *
 * Wraps [NmcWallet] with reactive StateFlows for the UI,
 * pending registration tracking, and integration with the
 * existing Amethyst Namecoin preferences (server list, Tor).
 *
 * Wire into AppModules:
 * ```kotlin
 * val nmcWallet = NmcWallet(
 *     electrumClient = electrumxClient,
 *     socketFactory = { socksSocketFactory },
 * )
 * val nmcWalletService = NmcWalletService(nmcWallet) {
 *     namecoinPrefs.customServersOrNull
 *         ?: if (torSettings.torType != TorType.OFF) ElectrumxClient.TOR_SERVERS
 *            else ElectrumxClient.DEFAULT_SERVERS
 * }
 * ```
 */
class NmcWalletService(
    val wallet: NmcWallet,
    private val serverListProvider: () -> List<ElectrumxServer> = { ElectrumxClient.DEFAULT_SERVERS },
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _balance = MutableStateFlow(NmcBalance())
    val balance: StateFlow<NmcBalance> = _balance.asStateFlow()

    private val _history = MutableStateFlow<List<NmcHistoryEntry>>(emptyList())
    val history: StateFlow<List<NmcHistoryEntry>> = _history.asStateFlow()

    private val _utxos = MutableStateFlow<List<NmcUtxo>>(emptyList())
    val utxos: StateFlow<List<NmcUtxo>> = _utxos.asStateFlow()

    private val _blockHeight = MutableStateFlow<Int?>(null)
    val blockHeight: StateFlow<Int?> = _blockHeight.asStateFlow()

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    private val _address = MutableStateFlow<String?>(null)
    val address: StateFlow<String?> = _address.asStateFlow()

    private val _pendingRegistrations = MutableStateFlow<List<PendingNameRegistration>>(emptyList())
    val pendingRegistrations: StateFlow<List<PendingNameRegistration>> = _pendingRegistrations.asStateFlow()

    private fun servers() = serverListProvider()

    // ── Key Loading ────────────────────────────────────────────────────

    fun loadFromNostrKey(nostrPrivKey: ByteArray) {
        wallet.loadFromNostrKey(nostrPrivKey)
        _isLoaded.value = true
        _address.value = wallet.address
    }

    fun loadFromMnemonic(mnemonic: String) {
        wallet.loadFromMnemonic(mnemonic)
        _isLoaded.value = true
        _address.value = wallet.address
    }

    fun loadFromWif(wif: String) {
        wallet.loadFromWif(wif)
        _isLoaded.value = true
        _address.value = wallet.address
    }

    /** Switch address type and update displayed address. */
    fun setAddressType(type: com.vitorpamplona.quartz.nip05.namecoin.wallet.NmcAddressType) {
        wallet.setAddressType(type)
        _address.value = wallet.address
        refreshAll()
    }

    fun lock() {
        wallet.lock()
        _isLoaded.value = false
        _address.value = null
        _balance.value = NmcBalance()
        _history.value = emptyList()
        _utxos.value = emptyList()
    }

    /** Export private key as WIF for Electrum-NMC import. */
    fun exportWif(): String = wallet.exportWif()

    fun exportPrivKeyHex(): String = wallet.exportPrivKeyHex()

    // ── Refresh ────────────────────────────────────────────────────────

    fun refreshAll() {
        scope.launch {
            try {
                _balance.value = wallet.getBalance(servers())
            } catch (_: Exception) {
            }
            try {
                _history.value = wallet.getHistory(servers()).sortedByDescending { it.height }
            } catch (_: Exception) {
            }
            try {
                _utxos.value = wallet.listUnspent(servers())
            } catch (_: Exception) {
            }
            try {
                _blockHeight.value = wallet.getBlockHeight(servers())
            } catch (_: Exception) {
            }
        }
    }

    // ── Name Operations ────────────────────────────────────────────────

    suspend fun checkNameAvailability(name: String): NameAvailability = wallet.checkNameAvailability(name, servers())

    /** Look up full name details (txid, vout, value, expiry) for update/transfer. */
    suspend fun lookupNameDetails(name: String): com.vitorpamplona.quartz.nip05.namecoin.wallet.NameDetails? = wallet.lookupNameDetails(name, servers())

    /**
     * Register a name (step 1): broadcasts NAME_NEW and stores the
     * pending registration for step 2.
     */
    suspend fun registerNameNew(name: String): PendingNameRegistration {
        val (txid, data) = wallet.nameNew(name, servers = servers())
        val height = _blockHeight.value ?: wallet.getBlockHeight(servers()) ?: 0

        val pending =
            PendingNameRegistration(
                name = name,
                saltHex = data.saltHex,
                nameNewTxid = txid,
                nameNewHeight = height,
                proposedValue =
                    if (name.startsWith("d/")) {
                        NmcNameScripts.buildDomainValue(wallet.pubKeyHex ?: "")
                    } else {
                        NmcNameScripts.buildIdentityValue(wallet.pubKeyHex ?: "")
                    },
                timestampSecs = TimeUtils.now(),
            )
        _pendingRegistrations.value = _pendingRegistrations.value + pending
        return pending
    }

    /**
     * Register a name (step 2): broadcasts NAME_FIRSTUPDATE.
     * Must be called ≥12 blocks after step 1.
     */
    suspend fun completeRegistration(
        pending: PendingNameRegistration,
        value: String = pending.proposedValue,
    ): String {
        val salt = Hex.decode(pending.saltHex)
        val data = NameNewData(pending.name, salt, NmcKeyManager.hash160(salt + pending.name.toByteArray()))

        val txid =
            wallet.nameFirstUpdate(
                nameNewTxid = pending.nameNewTxid,
                nameNewVout = 0, // NAME_NEW is typically output 0
                data = data,
                value = value,
                servers = servers(),
            )

        // Remove from pending
        _pendingRegistrations.value = _pendingRegistrations.value.filter { it.nameNewTxid != pending.nameNewTxid }
        return txid
    }

    /** Update an existing name's value. */
    suspend fun updateName(
        nameTxid: String,
        nameVout: Int,
        name: String,
        currentScript: ByteArray,
        currentOutputValue: Long,
        newValue: String,
    ): String = wallet.nameUpdate(nameTxid, nameVout, name, currentScript, currentOutputValue, newValue, servers = servers())

    /** Transfer a name to a new owner. */
    suspend fun transferName(
        nameTxid: String,
        nameVout: Int,
        name: String,
        currentScript: ByteArray,
        currentOutputValue: Long,
        currentNameValue: String,
        newOwnerAddress: String,
    ): String =
        wallet.transferName(
            nameTxid,
            nameVout,
            name,
            currentScript,
            currentOutputValue,
            currentNameValue,
            newOwnerAddress,
            servers = servers(),
        )

    /** Send NMC to an address. */
    suspend fun send(
        toAddress: String,
        amountSatoshis: Long,
    ): String = wallet.sendToAddress(toAddress, amountSatoshis, servers = servers())

    /** Validate a name for registration. */
    fun isValidName(name: String) = NmcNameScripts.isValidName(name)

    fun isValueWithinLimit(value: String) = NmcNameScripts.isValueWithinLimit(value)

    fun estimateRegistrationCost() = NmcNameScripts.NAME_NEW_COST
}
