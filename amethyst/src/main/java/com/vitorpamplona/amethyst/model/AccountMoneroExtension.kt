/*
 * Copyright (c) 2024 Vitor Pamplona
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.vitorpamplona.amethyst.model.preferences.MoneroSettings
import com.vitorpamplona.amethyst.service.MoneroDataSource
import com.vitorpamplona.amethyst.service.WalletService
import com.vitorpamplona.quartz.experimental.moneroTips.TipSplitSetup
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages Monero wallet lifecycle and operations for an Account.
 * Kept separate from Account.kt to minimize merge conflicts with upstream Amethyst.
 */
object AccountMoneroManager {
    private var walletService: WalletService? = null
    private var isBound = false

    private val _moneroSettings = MutableStateFlow(MoneroSettings())
    val moneroSettings = _moneroSettings.asStateFlow()

    val moneroConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder,
            ) {
                val binder = service as WalletService.WalletBinder
                walletService = binder.getService()
                MoneroDataSource.setMoneroService(walletService!!)
                isBound = true
            }

            override fun onServiceDisconnected(className: ComponentName) {
                walletService = null
                isBound = false
            }
        }

    fun startMonero(
        context: Context,
        spendKey: String,
        walletName: String,
        password: String,
        settings: MoneroSettings,
    ) {
        _moneroSettings.value = settings

        val intent = Intent(context, WalletService::class.java)
        context.bindService(intent, moneroConnection, Context.BIND_AUTO_CREATE)
    }

    fun startMoneroWallet(
        walletName: String,
        password: String,
        spendKey: String,
        settings: MoneroSettings,
    ) {
        walletService?.loadWallet(
            name = walletName,
            password = password,
            spendKey = spendKey,
            daemonAddress = settings.daemon,
            daemonUsername = settings.daemonUsername,
            daemonPassword = settings.daemonPassword,
        )
    }

    fun stopMonero(context: Context) {
        if (isBound) {
            walletService?.closeWallet()
            context.unbindService(moneroConnection)
            isBound = false
        }
    }

    fun getMoneroBalance(): Long = walletService?.balance ?: 0

    fun getMoneroLockedBalance(): Long = walletService?.lockedBalance ?: 0

    fun getMoneroAddress(): String? = walletService?.address

    fun isAddressValid(address: String): Boolean = walletService?.isAddressValid(address) ?: false

    fun sendTransaction(
        destination: String,
        amount: Long,
        priority: TransactionPriority,
    ): PendingTransaction.Status? =
        walletService?.sendTransaction(destination, amount, priority)

    fun sendTransactionMultDest(
        destinations: Array<String>,
        amounts: Array<Long>,
        priority: TransactionPriority,
    ): PendingTransaction? =
        walletService?.sendTransactionMultDest(destinations, amounts, priority)

    fun tip(
        tips: List<TipSplitSetup>,
        amount: ULong,
        priority: TransactionPriority,
        eventId: String? = null,
    ): PendingTransaction? {
        val destinations = tips.map { it.addressOrPubKeyHex }.toTypedArray()

        val totalWeight =
            if (tips.any { it.weight != null }) {
                tips.sumOf { it.weight ?: 0.0 }
            } else {
                1.0
            }

        val amounts =
            tips.map {
                val weight = it.weight?.let { w -> w / totalWeight } ?: (totalWeight / tips.size)
                (amount.toDouble() * weight).toLong()
            }.toTypedArray()

        return walletService?.sendTransactionMultDest(destinations, amounts, priority)
    }

    fun getProofs(
        txId: String,
        tips: List<TipSplitSetup>,
        message: String,
    ): List<Pair<Proof, String>> =
        tips.mapNotNull { tip ->
            walletService?.getTxProof(txId, tip.addressOrPubKeyHex, message)?.let { proof ->
                Pair(proof, tip.addressOrPubKeyHex)
            }
        }

    fun checkProof(
        pubKey: HexKey,
        address: String,
        txId: String,
        signature: String,
    ): ProofInfo? = walletService?.checkTxProof(txId, address, signature = signature)

    fun estimateTransactionFee(
        destinations: Array<String>,
        amounts: Array<Long>,
        priority: TransactionPriority,
    ): Long = walletService?.estimateTransactionFee(destinations, amounts, priority) ?: 0

    fun newSubaddress(label: String = ""): Subaddress? =
        walletService?.newSubaddress(0, label)

    fun lastSubaddress(): Subaddress? =
        walletService?.lastSubaddress(0)

    fun listAddresses(): List<Subaddress>? =
        walletService?.listAddresses()

    fun setSubaddressLabel(index: Int, label: String) {
        walletService?.setSubaddressLabel(index, label)
    }

    fun seedWithPassphrase(passphrase: String): String? =
        walletService?.seedWithPassphrase(passphrase)

    fun getTransactionHistory(): TransactionHistory? =
        walletService?.getHistory()

    fun setRestoreHeight(
        height: Long,
        name: String,
        password: String,
        spendKey: String,
        settings: MoneroSettings,
    ) {
        walletService?.setRestoreHeight(
            height,
            name,
            password,
            spendKey,
            settings.daemon,
            settings.daemonUsername,
            settings.daemonPassword,
        )
    }
}
