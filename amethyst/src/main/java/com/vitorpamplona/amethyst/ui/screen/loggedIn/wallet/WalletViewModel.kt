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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.wallet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip47WalletConnect.NwcWalletEntryNorm
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class SendState {
    data object Idle : SendState()

    data object Sending : SendState()

    data class Success(
        val preimage: String?,
    ) : SendState()

    data class Error(
        val message: String,
    ) : SendState()
}

sealed class ReceiveState {
    data object Idle : ReceiveState()

    data object Creating : ReceiveState()

    data class Created(
        val invoice: String,
        val amount: Long,
    ) : ReceiveState()

    data class Error(
        val message: String,
    ) : ReceiveState()
}

enum class TransactionFilter {
    ALL,
    ZAPS,
    NON_ZAPS,
}

data class WalletInfo(
    val walletId: String,
    val name: String,
    val alias: String? = null,
    val balanceSats: Long? = null,
    val isDefault: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

private const val NWC_TIMEOUT_MS = 30_000L
private const val PAYMENT_FAILED = "Payment failed"

class WalletViewModel : ViewModel() {
    private var account: Account? = null
    private var accountViewModel: AccountViewModel? = null

    private val _hasWalletSetup = MutableStateFlow(false)
    val hasWalletSetup = _hasWalletSetup.asStateFlow()

    private val walletInfoMap = MutableStateFlow<Map<String, WalletInfo>>(emptyMap())

    private val _wallets = MutableStateFlow<List<NwcWalletEntryNorm>>(emptyList())
    val wallets = _wallets.asStateFlow()

    private val _defaultWalletId = MutableStateFlow<String?>(null)
    val defaultWalletId = _defaultWalletId.asStateFlow()

    val walletInfoList =
        combine(_wallets, _defaultWalletId, walletInfoMap) { wallets, defaultId, infoMap ->
            wallets.map { wallet ->
                val info = infoMap[wallet.id]
                WalletInfo(
                    walletId = wallet.id,
                    name = wallet.name,
                    alias = info?.alias,
                    balanceSats = info?.balanceSats,
                    isDefault = wallet.id == defaultId || (defaultId == null && wallet == wallets.firstOrNull()),
                    isLoading = info?.isLoading == true,
                    error = info?.error,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected wallet for detail view
    private val _selectedWalletId = MutableStateFlow<String?>(null)
    val selectedWalletId = _selectedWalletId.asStateFlow()

    private val _balanceSats = MutableStateFlow<Long?>(null)
    val balanceSats = _balanceSats.asStateFlow()

    private val _walletAlias = MutableStateFlow<String?>(null)
    val walletAlias = _walletAlias.asStateFlow()

    private val allTransactions = MutableStateFlow<List<NwcTransaction>>(emptyList())

    private val _transactionFilter = MutableStateFlow(TransactionFilter.ALL)
    val transactionFilter = _transactionFilter.asStateFlow()

    val filteredTransactions =
        combine(allTransactions, _transactionFilter) { txs, filter ->
            when (filter) {
                TransactionFilter.ALL -> txs
                TransactionFilter.ZAPS -> txs.filter { it.parsedMetadata()?.nostr != null }
                TransactionFilter.NON_ZAPS -> txs.filter { it.parsedMetadata()?.nostr == null }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMoreTransactions = MutableStateFlow(true)
    val hasMoreTransactions = _hasMoreTransactions.asStateFlow()

    private val pageSize = 20

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState = _receiveState.asStateFlow()

    private fun launchTimeout(onTimeout: () -> Unit): Job =
        viewModelScope.launch(Dispatchers.IO) {
            delay(NWC_TIMEOUT_MS)
            _error.value = "Wallet request timed out"
            onTimeout()
        }

    private val _lnAddress = MutableStateFlow("")
    val lnAddress = _lnAddress.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        refreshWalletList()
    }

    fun refreshWalletList() {
        val acc = account ?: return
        _wallets.value = acc.settings.nwcWallets.value
        _defaultWalletId.value = acc.settings.defaultNwcWalletId.value
        _hasWalletSetup.value = _wallets.value.isNotEmpty()
    }

    fun refreshWalletSetup() {
        refreshWalletList()
    }

    fun loadLnAddress() {
        viewModelScope.launch(Dispatchers.IO) {
            val info =
                account
                    ?.userProfile()
                    ?.metadataOrNull()
                    ?.flow
                    ?.value
                    ?.info
            _lnAddress.value = info?.lud16 ?: ""
        }
    }

    fun updateLnAddress(address: String) {
        _lnAddress.value = address
    }

    fun saveLnAddress() {
        val accountVm = accountViewModel ?: return
        accountVm.launchSigner {
            saveLnAddressSuspend()
        }
    }

    suspend fun saveLnAddressSuspend() {
        val acc = account ?: return
        val event = acc.userMetadata.sendNewUserMetadata(lnAddress = _lnAddress.value)
        acc.sendLiterallyEverywhere(event)
    }

    fun setDefaultWallet(walletId: String) {
        val acc = account ?: return
        acc.settings.setDefaultNwcWallet(walletId)
        _defaultWalletId.value = walletId
    }

    fun removeWallet(walletId: String) {
        val acc = account ?: return
        acc.settings.removeNwcWallet(walletId)
        refreshWalletList()
    }

    fun selectWallet(walletId: String) {
        _selectedWalletId.value = walletId
        _balanceSats.value = walletInfoMap.value[walletId]?.balanceSats
        _walletAlias.value = walletInfoMap.value[walletId]?.alias
        allTransactions.value = emptyList()
    }

    private fun getWalletUri(walletId: String?): Nip47WalletConnect.Nip47URINorm? = _wallets.value.firstOrNull { it.id == walletId }?.uri

    private fun getSelectedWalletUri(): Nip47WalletConnect.Nip47URINorm? = getWalletUri(_selectedWalletId.value)

    fun fetchAllBalances() {
        _wallets.value.forEach { wallet ->
            fetchBalanceForWallet(wallet.id)
        }
    }

    private fun fetchBalanceForWallet(walletId: String) {
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            updateWalletInfo(walletId) { it.copy(isLoading = true, error = null) }
            try {
                acc.sendNwcRequestToWallet(walletUri, GetBalanceMethod.create()) { response ->
                    when (response) {
                        is GetBalanceSuccessResponse -> {
                            val sats = (response.result?.balance ?: 0L) / 1000L
                            updateWalletInfo(walletId) { it.copy(balanceSats = sats, isLoading = false) }
                        }

                        is NwcErrorResponse -> {
                            updateWalletInfo(walletId) {
                                it.copy(error = response.error?.message ?: "Balance request failed", isLoading = false)
                            }
                        }

                        else -> {
                            updateWalletInfo(walletId) { it.copy(isLoading = false) }
                        }
                    }
                }
            } catch (e: Exception) {
                updateWalletInfo(walletId) { it.copy(error = e.message, isLoading = false) }
            }
        }
    }

    fun fetchInfoForWallet(walletId: String) {
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acc.sendNwcRequestToWallet(walletUri, GetInfoMethod.create()) { response ->
                    when (response) {
                        is GetInfoSuccessResponse -> {
                            updateWalletInfo(walletId) { it.copy(alias = response.result?.alias) }
                        }

                        else -> {}
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun updateWalletInfo(
        walletId: String,
        transform: (WalletInfo) -> WalletInfo,
    ) {
        walletInfoMap.value =
            walletInfoMap.value.toMutableMap().apply {
                val current =
                    get(walletId) ?: WalletInfo(
                        walletId = walletId,
                        name = _wallets.value.firstOrNull { it.id == walletId }?.name ?: "Wallet",
                    )
                put(walletId, transform(current))
            }
    }

    // --- Methods below operate on the selected wallet ---

    fun fetchBalance() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            val timeoutJob = launchTimeout { _isLoading.value = false }
            try {
                acc.sendNwcRequestToWallet(walletUri, GetBalanceMethod.create()) { response ->
                    timeoutJob.cancel()
                    when (response) {
                        is GetBalanceSuccessResponse -> {
                            _balanceSats.value = (response.result?.balance ?: 0L) / 1000L
                            updateWalletInfo(walletId) { it.copy(balanceSats = _balanceSats.value) }
                        }

                        is NwcErrorResponse -> {
                            _error.value = response.error?.message ?: "Balance request failed"
                        }

                        else -> {}
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun fetchInfo() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acc.sendNwcRequestToWallet(walletUri, GetInfoMethod.create()) { response ->
                    when (response) {
                        is GetInfoSuccessResponse -> {
                            _walletAlias.value = response.result?.alias
                            updateWalletInfo(walletId) { it.copy(alias = response.result?.alias) }
                        }

                        else -> {}
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun fetchTransactions() {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _hasMoreTransactions.value = true
            val timeoutJob = launchTimeout { _isLoading.value = false }
            try {
                acc.sendNwcRequestToWallet(
                    walletUri,
                    ListTransactionsMethod.create(
                        limit = pageSize,
                        offset = 0,
                        unpaid = false,
                    ),
                ) { response ->
                    timeoutJob.cancel()
                    when (response) {
                        is ListTransactionsSuccessResponse -> {
                            val txs = response.result?.transactions ?: emptyList()
                            allTransactions.value = txs
                            val totalCount = response.result?.total_count
                            _hasMoreTransactions.value =
                                if (totalCount != null) {
                                    txs.size < totalCount
                                } else {
                                    txs.size >= pageSize
                                }
                        }

                        is NwcErrorResponse -> {
                            _error.value = response.error?.message ?: "Failed to load transactions"
                        }

                        else -> {}
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadMoreTransactions() {
        if (_isLoadingMore.value || !_hasMoreTransactions.value) return
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        val currentOffset = allTransactions.value.size
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val timeoutJob = launchTimeout { _isLoadingMore.value = false }
            try {
                acc.sendNwcRequestToWallet(
                    walletUri,
                    ListTransactionsMethod.create(
                        limit = pageSize,
                        offset = currentOffset,
                        unpaid = false,
                    ),
                ) { response ->
                    timeoutJob.cancel()
                    when (response) {
                        is ListTransactionsSuccessResponse -> {
                            val newTxs = response.result?.transactions ?: emptyList()
                            allTransactions.value += newTxs
                            val totalCount = response.result?.total_count
                            _hasMoreTransactions.value =
                                if (totalCount != null) {
                                    allTransactions.value.size < totalCount
                                } else {
                                    newTxs.size >= pageSize
                                }
                        }

                        is NwcErrorResponse -> {
                            _error.value = response.error?.message ?: "Failed to load more transactions"
                        }

                        else -> {}
                    }
                    _isLoadingMore.value = false
                }
            } catch (e: Exception) {
                timeoutJob.cancel()
                _error.value = e.message
                _isLoadingMore.value = false
            }
        }
    }

    fun sendPayment(bolt11: String) {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _sendState.value = SendState.Sending
            try {
                acc.sendNwcRequestToWallet(walletUri, PayInvoiceMethod.create(bolt11)) { response ->
                    when (response) {
                        is PayInvoiceSuccessResponse -> {
                            _sendState.value = SendState.Success(response.result?.preimage)
                            fetchBalance()
                        }

                        is PayInvoiceErrorResponse -> {
                            _sendState.value =
                                SendState.Error(
                                    response.error?.message ?: PAYMENT_FAILED,
                                )
                        }

                        is NwcErrorResponse -> {
                            _sendState.value =
                                SendState.Error(
                                    response.error?.message ?: PAYMENT_FAILED,
                                )
                        }

                        else -> {
                            _sendState.value = SendState.Error("Unexpected response")
                        }
                    }
                }
            } catch (e: Exception) {
                _sendState.value = SendState.Error(e.message ?: PAYMENT_FAILED)
            }
        }
    }

    fun createInvoice(
        amountSats: Long,
        description: String? = null,
    ) {
        val walletId = _selectedWalletId.value ?: _defaultWalletId.value ?: _wallets.value.firstOrNull()?.id ?: return
        val acc = account ?: return
        val walletUri = getWalletUri(walletId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _receiveState.value = ReceiveState.Creating
            try {
                acc.sendNwcRequestToWallet(
                    walletUri,
                    MakeInvoiceMethod.create(
                        amount = amountSats * 1000L,
                        description = description,
                    ),
                ) { response ->
                    when (response) {
                        is MakeInvoiceSuccessResponse -> {
                            val invoice = response.result?.invoice
                            if (invoice != null) {
                                _receiveState.value = ReceiveState.Created(invoice, amountSats)
                            } else {
                                _receiveState.value = ReceiveState.Error("No invoice returned")
                            }
                        }

                        is NwcErrorResponse -> {
                            _receiveState.value =
                                ReceiveState.Error(
                                    response.error?.message ?: "Invoice creation failed",
                                )
                        }

                        else -> {
                            _receiveState.value = ReceiveState.Error("Unexpected response")
                        }
                    }
                }
            } catch (e: Exception) {
                _receiveState.value = ReceiveState.Error(e.message ?: "Invoice creation failed")
            }
        }
    }

    fun resetSendState() {
        _sendState.value = SendState.Idle
    }

    fun resetReceiveState() {
        _receiveState.value = ReceiveState.Idle
    }

    fun clearError() {
        _error.value = null
    }

    fun setTransactionFilter(filter: TransactionFilter) {
        _transactionFilter.value = filter
    }
}
