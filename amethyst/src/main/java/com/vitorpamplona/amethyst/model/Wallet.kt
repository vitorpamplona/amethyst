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
package com.vitorpamplona.amethyst.model

import com.vitorpamplona.amethyst.model.TransactionPriority

class Wallet(
    val handle: Long,
) {
    companion object {
        init {
            System.loadLibrary("monerujo")
        }
    }

    var listenerHandle: Long = 0
    val address: String
        get() = getAddressJ(0, 0)
    val seed: String
        get() = getSeedJ()

    enum class StatusType {
        OK,
        ERROR,
        CRITICAL,
    }

    class Status(
        val status: Int,
        val error: String,
    ) {
        fun isOk(): Boolean = status == StatusType.OK.ordinal
    }

    val status: Status
        get() = statusWithErrorString()

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTED,
        WRONG_VERSION,
    }

    val connectionStatus: ConnectionStatus
        get() {
            val status = getConnectionStatusJ()
            return ConnectionStatus.entries.first { it.ordinal == status }
        }

    private external fun statusWithErrorString(): Status

    val balance: Long
        get() = getUnlockedBalanceAll()
    val lockedBalance: Long
        get() = getBalanceAll() - balance

    val height: Long
        get() = getBlockChainHeight()
    val daemonHeight: Long
        get() = getDaemonBlockChainHeight()

    val netType: NetworkType
        get() {
            val net = nettype()
            return NetworkType.entries.first { it.ordinal == net }
        }

    lateinit var transactionHistory: TransactionHistory
        private set

    fun init(
        daemonAddress: String,
        upperTransactionSizeLimit: Long = 0,
        daemonUsername: String = "",
        daemonPassword: String = "",
        proxyAddress: String = "",
    ) {
        initJ(daemonAddress, upperTransactionSizeLimit, daemonUsername, daemonPassword, proxyAddress)
        transactionHistory = TransactionHistory(getHistoryJ())
    }

    private external fun initJ(
        daemonAddress: String,
        upperTransactionSizeLimit: Long,
        daemonUsername: String,
        daemonPassword: String,
        proxyAddress: String,
    )

    fun setListener(listener: MoneroWalletListener?) {
        listenerHandle = setListenerJ(listener)
    }

    fun unsetListener() {
        unsetListenerJ()
        listenerHandle = 0
    }

    fun store(path: String = "") {
        storeJ(path)
    }

    private external fun storeJ(path: String)

    fun getAddressWithIndex(
        accountIndex: Int,
        addressIndex: Int,
    ): Subaddress {
        val address = getAddressJ(accountIndex, addressIndex)
        val label = getSubaddressLabel(accountIndex, addressIndex)
        return Subaddress(address, label, addressIndex)
    }

    fun createTransaction(
        destination: String,
        paymentId: String = "",
        amount: Long,
        mixinCount: Int = 0,
        priority: TransactionPriority = TransactionPriority.UNIMPORTANT,
        accountIndex: Int = 0,
    ): PendingTransaction {
        val handle = createTransactionJ(destination, paymentId, amount, mixinCount, priority.ordinal, accountIndex)
        return PendingTransaction(handle)
    }

    private external fun createTransactionJ(
        destination: String,
        paymentId: String,
        amount: Long,
        mixinCount: Int,
        priority: Int,
        accountIndex: Int,
    ): Long

    fun createTransactionMultDest(
        destinations: Array<String>,
        amounts: Array<Long>,
        paymentId: String = "",
        mixinCount: Int = 0,
        priority: TransactionPriority = TransactionPriority.UNIMPORTANT,
        accountIndex: Int = 0,
        subAddresses: Array<Int> = emptyArray(),
    ): PendingTransaction {
        val handle =
            createTransactionMultDestJ(
                destinations,
                paymentId,
                amounts.toLongArray(),
                mixinCount,
                priority.ordinal,
                accountIndex,
                subAddresses.toIntArray(),
            )
        return PendingTransaction(handle)
    }

    private external fun createTransactionMultDestJ(
        destinations: Array<String>,
        paymentId: String,
        amounts: LongArray,
        mixinCount: Int,
        priority: Int,
        accountIndex: Int,
        subAddresses: IntArray,
    ): Long

    private val subaddressLock = Any()

    fun newSubaddress(
        accountIndex: Int = 0,
        label: String = "",
    ): Subaddress {
        synchronized(subaddressLock) {
            addSubaddress(accountIndex, label)
            val index = getNumSubaddresses(accountIndex) - 1
            val address = getAddressJ(accountIndex, index)
            return Subaddress(address, label, index)
        }
    }

    fun lastSubaddress(accountIndex: Int): Subaddress {
        synchronized(subaddressLock) {
            val index = getNumSubaddresses(accountIndex) - 1
            return Subaddress(
                getAddressJ(accountIndex, index),
                getSubaddressLabel(accountIndex, index),
                index,
            )
        }
    }

    private external fun addSubaddress(
        accountIndex: Int,
        label: String,
    )

    external fun getNumSubaddresses(accountIndex: Int): Int

    private external fun getAddressJ(
        accountIndex: Int,
        addressIndex: Int,
    ): String

    private external fun getSeedJ(seedOffset: String = ""): String

    fun seedWithPassphrase(passphrase: String): String = getSeedJ(passphrase)

    external fun getSubaddressLabel(
        accountIndex: Int,
        addressIndex: Int,
    ): String

    external fun setSubaddressLabel(
        accountIndex: Int,
        addressIndex: Int,
        label: String,
    )

    fun checkTxProof(
        txId: String,
        address: String,
        message: String = "",
        signature: String,
    ): ProofInfo? = checkTxProofJ(txId, address, message, signature)

    private external fun checkTxProofJ(
        txId: String,
        address: String,
        message: String,
        signature: String,
    ): ProofInfo?

    external fun getTxProof(
        txId: String,
        address: String,
        message: String = "",
    ): String

    fun isAddressValid(
        address: String,
        netType: NetworkType = WalletManager.getNetworkType(),
    ): Boolean = isAddressValidJ(address, netType.ordinal)

    private external fun isAddressValidJ(
        address: String,
        netType: Int = WalletManager.getNetworkType().ordinal,
    ): Boolean

    fun refreshHistory() {
        transactionHistory.refresh(0)
    }

    fun estimateTransactionFee(
        addresses: Array<String>,
        amounts: Array<Long>,
        priority: TransactionPriority,
    ): Long = estimateTransactionFeeJ(addresses, amounts.toLongArray(), priority.ordinal)

    external fun estimateTransactionFeeJ(
        addresses: Array<String>,
        amounts: LongArray,
        priority: Int,
    ): Long

    external fun setUserNote(
        txId: String,
        note: String,
    ): Boolean

    private external fun getHistoryJ(): Long

    external fun getBalanceAll(): Long

    external fun getUnlockedBalanceAll(): Long

    external fun getDisplayAmount(amount: Long): String

    external fun estimateBlockchainHeight(): Long

    external fun getBlockChainHeight(): Long

    external fun getDaemonBlockChainHeight(): Long

    private external fun getConnectionStatusJ(): Int

    external fun setProxy(proxy: String): Boolean

    external fun setListenerJ(listener: MoneroWalletListener?): Long

    external fun unsetListenerJ()

    external fun setRestoreHeight(height: Long)

    external fun getRestoreHeight(): Long

    external fun pauseRefresh()

    external fun startRefresh()

    external fun rescanBlockchainAsync()

    external fun setPassword(password: String): Boolean

    external fun nettype(): Int
}
