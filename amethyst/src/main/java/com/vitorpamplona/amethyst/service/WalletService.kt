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
package com.vitorpamplona.amethyst.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.R
import com.vitorpamplona.amethyst.commons.threading.checkNotInMainThread
import com.vitorpamplona.amethyst.model.MoneroWalletListener
import com.vitorpamplona.amethyst.model.PendingTransaction
import com.vitorpamplona.amethyst.model.Proof
import com.vitorpamplona.amethyst.model.ProofInfo
import com.vitorpamplona.amethyst.model.Subaddress
import com.vitorpamplona.amethyst.model.TransactionHistory
import com.vitorpamplona.amethyst.model.TransactionInfo
import com.vitorpamplona.amethyst.model.TransactionPriority
import com.vitorpamplona.amethyst.model.Wallet
import com.vitorpamplona.amethyst.model.WalletManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class WalletService : Service() {
    private val binder = WalletBinder()

    inner class WalletBinder : Binder() {
        fun getService(): WalletService = this@WalletService
    }

    enum class WalletStatusType {
        OPENING,
        CONNECTING,
        DISCONNECTING,
        SYNCING,
        SYNCED,
        ERROR,
        DISCONNECTED,
        ;

        fun toLocalizedString(context: Context): String =
            when (this) {
                OPENING -> {
                    context.getString(R.string.wallet_status_opening)
                }

                CONNECTING -> {
                    context.getString(R.string.wallet_status_connecting)
                }

                DISCONNECTING -> {
                    context.getString(R.string.wallet_status_disconnecting)
                }

                SYNCING -> {
                    context.getString(R.string.wallet_status_syncing)
                }

                SYNCED -> {
                    context.getString(R.string.wallet_status_synced)
                }

                ERROR -> {
                    context.getString(R.string.wallet_status_error)
                }

                DISCONNECTED -> {
                    context.getString(R.string.wallet_status_disconnected)
                }
            }
    }

    class WalletStatus(
        val type: WalletStatusType,
        val description: String?,
    ) {
        fun toLocalizedString(context: Context): String {
            val description = description?.let { ": $it" } ?: ""
            return "${type.toLocalizedString(context)}$description"
        }
    }

    var status: WalletStatus = WalletStatus(WalletStatusType.OPENING, null)
        private set(value) {
            field = value

            _statusStateFlow.update { value }
        }

    private val lock = Any()

    private val _statusStateFlow = MutableStateFlow(status)
    val statusStateFlow = _statusStateFlow.asStateFlow()

    val balance: Long
        get() = synchronized(lock) { return wallet?.balance ?: 0 }
    val lockedBalance: Long
        get() = synchronized(lock) { return wallet?.lockedBalance ?: 0 }

    private val _balanceStateFlow = MutableStateFlow(balance)
    val balanceStateFlow = _balanceStateFlow.asStateFlow()

    private val _lockedBalanceStateFlow = MutableStateFlow(lockedBalance)
    val lockedBalanceStateFlow = _lockedBalanceStateFlow.asStateFlow()

    val walletHeight: Long
        get() = synchronized(lock) { wallet?.height ?: 0 }

    private val _walletHeightStateFlow = MutableStateFlow(walletHeight)
    val walletHeightStateFlow = _walletHeightStateFlow.asStateFlow()

    val daemonHeight: Long
        get() = synchronized(lock) { wallet?.daemonHeight ?: 0 }

    private val _daemonHeightStateFlow = MutableStateFlow(daemonHeight)
    val daemonHeightStateFlow = _daemonHeightStateFlow.asStateFlow()

    val address: String?
        get() = synchronized(lock) { wallet?.address }

    val connectionStatus: Wallet.ConnectionStatus
        get() =
            synchronized(lock) {
                wallet?.connectionStatus ?: Wallet.ConnectionStatus.DISCONNECTED
            }

    private val _connectionStatusStateFlow = MutableStateFlow(connectionStatus)
    val connectionStatusStateFlow = _connectionStatusStateFlow.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransactionInfo>>(listOf())
    val transactions = _transactions.asStateFlow()

    val walletListener =
        object : MoneroWalletListener {
            val MIN_NEWBLOCK_DELAY: Duration = 500.milliseconds

            var lastNewBlock = TimeSource.Monotonic.markNow()
            val scope = Amethyst.instance.applicationIOScope

            override fun moneyReceived(
                txId: String,
                amount: Long,
            ) {
                scope.launch {
                    synchronized(lock) {
                        _balanceStateFlow.update { balance }
                        _lockedBalanceStateFlow.update { lockedBalance }

                        wallet?.let {
                            it.refreshHistory()
                            _transactions.update { _ -> it.transactionHistory.transactions }
                        }
                    }
                }
            }

            override fun moneySpent(
                txId: String,
                amount: Long,
            ) {
                scope.launch {
                    synchronized(lock) {
                        _balanceStateFlow.update { balance }
                        _lockedBalanceStateFlow.update { lockedBalance }

                        wallet?.let {
                            it.refreshHistory()
                            _transactions.update { _ -> it.transactionHistory.transactions }
                        }
                    }
                }
            }

            override fun unconfirmedMoneyReceived(
                txId: String,
                amount: Long,
            ) {
                scope.launch {
                    synchronized(lock) {
                        _lockedBalanceStateFlow.update { lockedBalance }
                    }
                }
            }

            override fun newBlock(height: Long) {
                val now = TimeSource.Monotonic.markNow()
                if (now - lastNewBlock >= MIN_NEWBLOCK_DELAY) {
                    lastNewBlock = now
                    // store() doesn't need to be synchronized, and calling it concurrently seems to cause issues
                    store()
                    scope.launch {
                        synchronized(lock) {
                            val left = ((wallet?.daemonHeight ?: 0) - (wallet?.height ?: 0)).coerceAtLeast(minimumValue = 0)
                            if (left != 0L) {
                                val context = Amethyst.instance.applicationContext
                                status =
                                    WalletStatus(
                                        WalletStatusType.SYNCING,
                                        context.getString(R.string.x_blocks_left, "$left"),
                                    )
                            }

                            _daemonHeightStateFlow.update { wallet?.daemonHeight ?: 0 }
                            _connectionStatusStateFlow.update { wallet?.connectionStatus ?: Wallet.ConnectionStatus.DISCONNECTED }
                            _walletHeightStateFlow.update { wallet?.height ?: 0 }
                            _balanceStateFlow.update { wallet?.balance ?: 0 }
                            _lockedBalanceStateFlow.update { wallet?.lockedBalance ?: 0 }

                            wallet?.let {
                                it.refreshHistory()
                                _transactions.update { _ -> it.transactionHistory.transactions }
                            }
                        }
                    }
                }
            }

            override fun updated() {
            }

            override fun refreshed() {
                if (!synced) {
                    store()
                    synced = true
                }

                scope.launch {
                    synchronized(lock) {
                        checkNotInMainThread()

                        status =
                            WalletStatus(
                                WalletStatusType.SYNCED,
                                getString(R.string.x_blocks, "$walletHeight"),
                            )

                        _daemonHeightStateFlow.update { daemonHeight }
                        _connectionStatusStateFlow.update { connectionStatus }

                        wallet?.let {
                            it.refreshHistory()
                            _transactions.update { _ -> it.transactionHistory.transactions }
                        }
                    }
                }
            }
        }

    var wallet: Wallet? = null
        set(value) {
            field = value
        }

    private var synced = false

    fun loadWallet(
        name: String,
        password: String,
        spendKey: String = "",
        daemonAddress: String,
        daemonUsername: String = "",
        daemonPassword: String = "",
        proxy: String = "",
        restoreHeight: Long? = null,
    ): Wallet =
        synchronized(lock) {
            load(name, password, spendKey, daemonAddress, daemonUsername, daemonPassword, proxy, restoreHeight)
        }

    fun load(
        name: String,
        password: String,
        spendKey: String = "",
        daemonAddress: String,
        daemonUsername: String = "",
        daemonPassword: String = "",
        proxy: String = "",
        restoreHeight: Long? = null,
    ): Wallet {
        checkNotInMainThread()

        if (name.isBlank()) {
            throw IllegalArgumentException("Wallet name not specified")
        }

        wallet?.let {
            close()

            status = WalletStatus(WalletStatusType.DISCONNECTING, null)
            synced = false

            _balanceStateFlow.update { 0 }
            _lockedBalanceStateFlow.update { 0 }
        }

        val isCreation = !WalletManager.walletExists(name) && restoreHeight == null

        val myWallet =
            if (WalletManager.walletExists(name)) {
                val wallet = WalletManager.openWallet(name, password)
                if (!wallet.status.isOk()) {
                    Log.w("WalletService", "Unable to open wallet: ${wallet.status.error}")
                    Log.w("WalletService", "Deleting corrupted wallet cache for $name")
                    WalletManager.deleteCache(name)
                    WalletManager.openWallet(name, password)
                } else {
                    wallet
                }
            } else {
                if (spendKey.isBlank()) {
                    WalletManager.createWallet(name, password)
                } else {
                    WalletManager.createWalletFromSpendKey(name, password, spendKey)
                }
            }

        if (!myWallet.status.isOk()) {
            status = WalletStatus(WalletStatusType.ERROR, myWallet.status.error)
            return myWallet
        } else {
            wallet = myWallet
            _balanceStateFlow.update { myWallet.balance }
            _lockedBalanceStateFlow.update { myWallet.lockedBalance }
        }

        connect(
            daemonAddress,
            daemonUsername,
            daemonPassword,
            proxy,
            isCreation,
            restoreHeight,
            password,
        )
        return myWallet
    }

    fun connectToDaemon(
        address: String,
        username: String = "",
        password: String = "",
        proxy: String = "",
        restoreHeight: Long? = null,
        walletPassword: String? = null,
    ): Wallet.Status? =
        synchronized(lock) {
            connect(address, username, password, proxy, false, restoreHeight, walletPassword)
        }

    fun connect(
        address: String,
        username: String = "",
        password: String = "",
        proxy: String = "",
        isCreation: Boolean,
        restoreHeight: Long? = null,
        walletPassword: String? = null,
    ): Wallet.Status? {
        checkNotInMainThread()

        if (address.isBlank()) {
            throw IllegalArgumentException("Daemon address not specified")
        }

        status = WalletStatus(WalletStatusType.CONNECTING, null)
        return wallet?.let {
            it.init(address, daemonUsername = username, daemonPassword = password, proxyAddress = proxy)
            if (!it.status.isOk()) {
                status = WalletStatus(WalletStatusType.ERROR, it.status.error)
                return it.status
            }

            if (isCreation) {
                val height = it.estimateBlockchainHeight()
                it.setRestoreHeight(height)
                if (walletPassword != null) {
                    it.setPassword(walletPassword)
                }
            } else {
                restoreHeight?.let { height ->
                    it.setRestoreHeight(height)
                }
            }

            it.setListener(walletListener)

            it.startRefresh()

            _daemonHeightStateFlow.update { _ -> it.daemonHeight }

            _walletHeightStateFlow.update { _ -> it.height }

            status = WalletStatus(WalletStatusType.SYNCING, "${it.height} blocks")
            it.status
        }
    }

    fun sendTransaction(
        destination: String,
        amount: Long,
        priority: TransactionPriority = TransactionPriority.UNIMPORTANT,
    ): PendingTransaction.Status? =
        synchronized(lock) {
            checkNotInMainThread()
            if (destination.isEmpty()) {
                throw IllegalArgumentException("Destination not specified")
            }

            if (amount == 0L) {
                throw IllegalArgumentException("Amount must not be zero")
            }

            wallet?.let {
                val pendingTransaction = it.createTransaction(destination, amount = amount, priority = priority)
                if (pendingTransaction.status.type != PendingTransaction.StatusType.OK) {
                    return pendingTransaction.status
                }

                pendingTransaction.saveTxId()

                if (!pendingTransaction.commit()) {
                    return pendingTransaction.status
                }

                store()

                _balanceStateFlow.update { _ -> it.balance }
                _lockedBalanceStateFlow.update { _ -> it.lockedBalance }
                it.refreshHistory()
                _transactions.update { _ -> it.transactionHistory.transactions }

                pendingTransaction.status
            }
        }

    fun sendTransactionMultDest(
        destinations: Array<String>,
        amounts: Array<Long>,
        priority: TransactionPriority = TransactionPriority.UNIMPORTANT,
    ): PendingTransaction? =
        synchronized(lock) {
            checkNotInMainThread()

            if (destinations.isEmpty()) {
                throw IllegalArgumentException("No destinations specified")
            }

            if (amounts.isEmpty()) {
                throw IllegalArgumentException("No amounts specified")
            }

            if (destinations.size != amounts.size) {
                throw IllegalArgumentException("The destinations array must be of the same size as the amounts array")
            }

            if (amounts.any { it == 0L }) {
                throw IllegalArgumentException("Amounts must not be zero")
            }

            if (destinations.any { it.isEmpty() }) {
                throw IllegalArgumentException("Destinations must not be empty")
            }

            wallet?.let {
                val pendingTransaction = it.createTransactionMultDest(destinations, amounts, priority = priority)
                if (pendingTransaction.status.type != PendingTransaction.StatusType.OK) {
                    return pendingTransaction
                }

                pendingTransaction.saveTxId()

                if (!pendingTransaction.commit()) {
                    return pendingTransaction
                }

                store()

                _balanceStateFlow.update { _ -> it.balance }
                _lockedBalanceStateFlow.update { _ -> it.lockedBalance }
                it.refreshHistory()
                _transactions.update { _ -> it.transactionHistory.transactions }

                pendingTransaction
            }
        }

    fun getTxProof(
        txId: String,
        destination: String,
        message: String = "",
    ): Proof? =
        synchronized(lock) {
            wallet?.let {
                val proof = it.getTxProof(txId, destination, message)
                val status = it.status
                Proof(proof, status)
            }
        }

    fun setRestoreHeight(
        height: Long,
        name: String,
        password: String,
        spendKey: String,
        daemonAddress: String,
        daemonUsername: String = "",
        daemonPassword: String = "",
        proxy: String = "",
    ): Wallet.Status? =
        synchronized(lock) {
            if (name.isBlank()) {
                throw IllegalArgumentException("Wallet name not specified")
            }

            if (spendKey.isBlank()) {
                throw IllegalArgumentException("Spend key not specified")
            }

            if (daemonAddress.isBlank()) {
                throw IllegalArgumentException("Daemon address not specified")
            }

            wallet?.let {
                close()
                WalletManager.deleteWallet(name)

                val myWallet = load(name, password, spendKey, daemonAddress, daemonUsername, daemonPassword, proxy, height)
                return myWallet.status
            }
        }

    fun setProxy(proxy: String): Boolean? =
        synchronized(lock) {
            if (!WalletManager.setProxy(proxy)) {
                return false
            }
            return wallet?.setProxy(proxy)
        }

    fun storeWallet(): Wallet.Status? =
        synchronized(lock) {
            store()
        }

    private fun store(): Wallet.Status? {
        val status =
            wallet?.let {
                it.store()
                if (!it.status.isOk()) {
                    status = WalletStatus(WalletStatusType.ERROR, it.status.error)
                }
                it.status
            }
        return status
    }

    fun closeWallet() =
        synchronized(lock) {
            checkNotInMainThread()

            close()
        }

    private fun close() {
        wallet?.let {
            it.pauseRefresh()

            WalletManager.close(it)
        }

        wallet = null
    }

    fun newSubaddress(
        accountIndex: Int,
        label: String,
    ): Subaddress? =
        synchronized(lock) {
            val subaddress = wallet?.newSubaddress()
            store()
            return subaddress
        }

    fun lastSubaddress(accountIndex: Int): Subaddress? =
        synchronized(lock) {
            return wallet?.lastSubaddress(accountIndex)
        }

    fun listAddresses(): List<Subaddress>? =
        synchronized(lock) {
            return wallet?.let {
                val addresses = mutableListOf<Subaddress>()
                val numAddresses = it.getNumSubaddresses(0)
                for (i in 0..<numAddresses) {
                    val address = it.getAddressWithIndex(0, i)
                    addresses += address
                }
                addresses
            }
        }

    fun setSubaddressLabel(
        index: Int,
        label: String,
    ): Wallet.Status? {
        synchronized(lock) {
            return wallet?.let {
                it.setSubaddressLabel(0, index, label)
                if (!it.status.isOk()) {
                    return it.status
                }
                store()
            }
        }
    }

    fun seedWithPassphrase(passphrase: String): String? {
        synchronized(lock) {
            return wallet?.seedWithPassphrase(passphrase)
        }
    }

    fun checkTxProof(
        txId: String,
        address: String,
        message: String = "",
        signature: String,
    ): ProofInfo? =
        synchronized(lock) {
            return wallet?.checkTxProof(txId, address, message, signature)
        }

    fun isAddressValid(address: String): Boolean? =
        synchronized(lock) {
            return wallet?.isAddressValid(address)
        }

    fun getRestoreHeight(): Long? {
        synchronized(lock) {
            return wallet?.getRestoreHeight()
        }
    }

    fun getHistory(): TransactionHistory? {
        synchronized(lock) {
            return wallet?.transactionHistory
        }
    }

    fun setUserNote(
        txId: String,
        note: String,
    ): Boolean? {
        synchronized(lock) {
            return wallet?.setUserNote(txId, note)
        }
    }

    fun estimateTransactionFee(
        destinations: Array<String>,
        amounts: Array<Long>,
        priority: TransactionPriority,
    ): Long? {
        synchronized(lock) {
            return wallet?.estimateTransactionFee(destinations, amounts, priority)
        }
    }

    override fun onCreate() {
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = super.onStartCommand(intent, flags, startId)

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean = false

    override fun onDestroy() {
        synchronized(lock) {
            close()
        }
    }
}

data class ServiceState(
    val wallet: Wallet?,
)
