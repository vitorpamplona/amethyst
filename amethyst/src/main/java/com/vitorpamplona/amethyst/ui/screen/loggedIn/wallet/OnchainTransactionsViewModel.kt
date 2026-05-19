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

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.BitcoinAddressTx
import com.vitorpamplona.quartz.nipBCOnchainZaps.chain.OnchainBackend
import com.vitorpamplona.quartz.nipBCOnchainZaps.taproot.TaprootAddress
import com.vitorpamplona.quartz.nipBCOnchainZaps.zap.OnchainZapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row in the on-chain transactions list. Combines the chain-level view of
 * a transaction ([tx]) with an optional [OnchainZapEvent] from `LocalCache`
 * that references the same txid — the zap event is what lets us identify the
 * Nostr counterparty (sender for incoming, recipient for outgoing) the same
 * way [WalletTransactionsScreen] does for NWC zaps via embedded zap requests.
 */
@Immutable
data class OnchainTxView(
    val tx: BitcoinAddressTx,
    val zap: OnchainZapEvent?,
) {
    val isIncoming: Boolean get() = tx.netValueSats >= 0L

    /**
     * Nostr pubkey of the counterparty if known. For an incoming transaction
     * the zap event is signed by the sender (so [OnchainZapEvent.pubKey] is
     * the sender). For an outgoing transaction the counterparty is the zap's
     * `p`-tagged recipient.
     */
    fun counterpartyPubkeyHex(): String? =
        zap?.let { z ->
            if (isIncoming) z.pubKey else z.recipient()
        }
}

class OnchainTransactionsViewModel : ViewModel() {
    private var address: String? = null
    private var backend: OnchainBackend? = null

    /** Most recent confirmed txid we've already fetched; null until we've loaded a page. */
    private var lastSeenTxid: String? = null

    private val allTransactions = MutableStateFlow<List<OnchainTxView>>(emptyList())

    private val _transactionFilter = MutableStateFlow(TransactionFilter.ALL)
    val transactionFilter = _transactionFilter.asStateFlow()

    val filteredTransactions =
        combine(allTransactions, _transactionFilter) { txs, filter ->
            when (filter) {
                TransactionFilter.ALL -> txs
                TransactionFilter.ZAPS -> txs.filter { it.zap != null }
                TransactionFilter.NON_ZAPS -> txs.filter { it.zap == null }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private val _hasMoreTransactions = MutableStateFlow(true)
    val hasMoreTransactions = _hasMoreTransactions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** The Taproot address being displayed, exposed for the UI header. */
    private val _displayAddress = MutableStateFlow<String?>(null)
    val displayAddress = _displayAddress.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        if (address != null) return
        val pubKey = accountViewModel.account.signer.pubKey
        address = runCatching { TaprootAddress.fromPubKey(pubKey) }.getOrNull()
        backend = LocalCache.onchainBackend
        _displayAddress.value = address
    }

    fun setTransactionFilter(filter: TransactionFilter) {
        _transactionFilter.value = filter
    }

    fun fetchTransactions() {
        val addr = address ?: return
        val be = backend ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            lastSeenTxid = null
            _hasMoreTransactions.value = true
            try {
                val rows = withContext(Dispatchers.IO) { be.getTxsForAddress(addr, null) }
                val views = rows.map { OnchainTxView(it, findZapForTxid(it.txid)) }
                allTransactions.value = views
                lastSeenTxid = rows.lastOrNull { it.confirmations > 0 }?.txid
                _hasMoreTransactions.value = lastSeenTxid != null
            } catch (t: Throwable) {
                _error.value = t.message ?: "Failed to load transactions"
                _hasMoreTransactions.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreTransactions() {
        if (_isLoadingMore.value || !_hasMoreTransactions.value) return
        val addr = address ?: return
        val be = backend ?: return
        val seen = lastSeenTxid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            try {
                val rows = withContext(Dispatchers.IO) { be.getTxsForAddress(addr, seen) }
                if (rows.isEmpty()) {
                    _hasMoreTransactions.value = false
                } else {
                    val views = rows.map { OnchainTxView(it, findZapForTxid(it.txid)) }
                    allTransactions.value = allTransactions.value + views
                    lastSeenTxid = rows.lastOrNull { it.confirmations > 0 }?.txid ?: seen
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "Failed to load more transactions"
            } finally {
                _isLoadingMore.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    /**
     * Scan `LocalCache.notes` for the [OnchainZapEvent] that references this
     * txid. NIP-BC verifies that the on-chain output actually pays the recipient
     * before the event is consumed, so we won't see multiple competing events
     * for the same txid in practice — last writer wins is fine.
     */
    private fun findZapForTxid(txid: String): OnchainZapEvent? {
        var found: OnchainZapEvent? = null
        LocalCache.notes.forEach { _, note ->
            val ev = note.event
            if (ev is OnchainZapEvent && ev.txid() == txid) {
                found = ev
            }
        }
        return found
    }
}
