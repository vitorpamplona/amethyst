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
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoMethod
import com.vitorpamplona.quartz.nip47WalletConnect.GetInfoSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsMethod
import com.vitorpamplona.quartz.nip47WalletConnect.ListTransactionsSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.MakeInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.NwcTransaction
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceErrorResponse
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceMethod
import com.vitorpamplona.quartz.nip47WalletConnect.PayInvoiceSuccessResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
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

class WalletViewModel : ViewModel() {
    private var account: Account? = null

    private val _hasWalletSetup = MutableStateFlow(false)
    val hasWalletSetup = _hasWalletSetup.asStateFlow()

    private val _balanceSats = MutableStateFlow<Long?>(null)
    val balanceSats = _balanceSats.asStateFlow()

    private val _walletAlias = MutableStateFlow<String?>(null)
    val walletAlias = _walletAlias.asStateFlow()

    private val _transactions = MutableStateFlow<List<NwcTransaction>>(emptyList())

    private val _transactionFilter = MutableStateFlow(TransactionFilter.ALL)
    val transactionFilter = _transactionFilter.asStateFlow()

    val filteredTransactions =
        combine(_transactions, _transactionFilter) { txs, filter ->
            when (filter) {
                TransactionFilter.ALL -> txs
                TransactionFilter.ZAPS -> txs.filter { it.parsedMetadata()?.nostr != null }
                TransactionFilter.NON_ZAPS -> txs.filter { it.parsedMetadata()?.nostr == null }
            }
        }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMoreTransactions = MutableStateFlow(true)
    val hasMoreTransactions = _hasMoreTransactions.asStateFlow()

    private val pageSize = 100

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _sendState = MutableStateFlow<SendState>(SendState.Idle)
    val sendState = _sendState.asStateFlow()

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState = _receiveState.asStateFlow()

    fun init(account: Account) {
        this.account = account
        _hasWalletSetup.value = account.nip47SignerState.hasWalletConnectSetup()
    }

    fun refreshWalletSetup() {
        _hasWalletSetup.value = account?.nip47SignerState?.hasWalletConnectSetup() == true
    }

    fun fetchBalance() {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                acc.sendNwcRequest(GetBalanceMethod.create()) { response ->
                    when (response) {
                        is GetBalanceSuccessResponse -> {
                            // NWC balance is in millisats, convert to sats
                            _balanceSats.value = (response.result?.balance ?: 0L) / 1000L
                        }

                        is NwcErrorResponse -> {
                            _error.value = response.error?.message ?: "Balance request failed"
                        }

                        else -> {}
                    }
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun fetchInfo() {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                acc.sendNwcRequest(GetInfoMethod.create()) { response ->
                    when (response) {
                        is GetInfoSuccessResponse -> {
                            _walletAlias.value = response.result?.alias
                        }

                        else -> {}
                    }
                }
            } catch (e: Exception) {
                // ignore info errors
            }
        }
    }

    fun fetchTransactions() {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _hasMoreTransactions.value = true
            try {
                acc.sendNwcRequest(
                    ListTransactionsMethod.create(
                        limit = pageSize,
                        offset = 0,
                        unpaid = false,
                    ),
                ) { response ->
                    when (response) {
                        is ListTransactionsSuccessResponse -> {
                            val txs = response.result?.transactions ?: emptyList()
                            _transactions.value = txs
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
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun loadMoreTransactions() {
        if (_isLoadingMore.value || !_hasMoreTransactions.value) return
        val acc = account ?: return
        val currentOffset = _transactions.value.size
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            try {
                acc.sendNwcRequest(
                    ListTransactionsMethod.create(
                        limit = pageSize,
                        offset = currentOffset,
                        unpaid = false,
                    ),
                ) { response ->
                    when (response) {
                        is ListTransactionsSuccessResponse -> {
                            val newTxs = response.result?.transactions ?: emptyList()
                            _transactions.value = _transactions.value + newTxs
                            val totalCount = response.result?.total_count
                            _hasMoreTransactions.value =
                                if (totalCount != null) {
                                    _transactions.value.size < totalCount
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
                _error.value = e.message
                _isLoadingMore.value = false
            }
        }
    }

    fun sendPayment(bolt11: String) {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _sendState.value = SendState.Sending
            try {
                acc.sendNwcRequest(PayInvoiceMethod.create(bolt11)) { response ->
                    when (response) {
                        is PayInvoiceSuccessResponse -> {
                            _sendState.value = SendState.Success(response.result?.preimage)
                            // Refresh balance after payment
                            fetchBalance()
                        }

                        is PayInvoiceErrorResponse -> {
                            _sendState.value =
                                SendState.Error(
                                    response.error?.message ?: "Payment failed",
                                )
                        }

                        is NwcErrorResponse -> {
                            _sendState.value =
                                SendState.Error(
                                    response.error?.message ?: "Payment failed",
                                )
                        }

                        else -> {
                            _sendState.value = SendState.Error("Unexpected response")
                        }
                    }
                }
            } catch (e: Exception) {
                _sendState.value = SendState.Error(e.message ?: "Payment failed")
            }
        }
    }

    fun createInvoice(
        amountSats: Long,
        description: String? = null,
    ) {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _receiveState.value = ReceiveState.Creating
            try {
                // NWC expects millisats
                acc.sendNwcRequest(
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
