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
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CashuWalletCreateState {
    data object Idle : CashuWalletCreateState()

    data object Saving : CashuWalletCreateState()

    data object Success : CashuWalletCreateState()

    data class Error(
        val message: String,
    ) : CashuWalletCreateState()
}

/**
 * Lightweight ViewModel for the NIP-60 Cashu wallet screens — view + create.
 *
 * Send/receive/mint operations will land in a follow-up commit alongside the
 * BDHKE+mint-HTTP layer; this model only covers the view + bootstrap path.
 */
class CashuWalletViewModel : ViewModel() {
    private var account: Account? = null
    private var accountViewModel: AccountViewModel? = null

    private val _walletEvent = MutableStateFlow<CashuWalletEvent?>(null)
    val walletEvent = _walletEvent.asStateFlow()

    private val _mints = MutableStateFlow<List<String>>(emptyList())
    val mints = _mints.asStateFlow()

    private val _balanceSats = MutableStateFlow(0L)
    val balanceSats = _balanceSats.asStateFlow()

    private val _tokenEvents = MutableStateFlow<List<CashuTokenEvent>>(emptyList())
    val tokenEvents = _tokenEvents.asStateFlow()

    private val _history = MutableStateFlow<List<CashuSpendingHistoryEvent>>(emptyList())
    val history = _history.asStateFlow()

    private val _createState = MutableStateFlow<CashuWalletCreateState>(CashuWalletCreateState.Idle)
    val createState = _createState.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        refresh()
    }

    /**
     * Scans the local cache for our wallet event and its associated token /
     * history events, decrypting whatever the current signer can decrypt and
     * re-emitting state flows.
     *
     * Today this is fire-and-forget. Reactive observation of the underlying
     * notes is a follow-up — for the first slice, the screen calls refresh()
     * on entry and after a write.
     */
    fun refresh() {
        val acc = account ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val pubKey = acc.signer.pubKey
            val cache = LocalCache

            val walletNote = cache.getOrCreateAddressableNote(CashuWalletEvent.createAddress(pubKey))
            val walletEvt = walletNote.event as? CashuWalletEvent
            _walletEvent.value = walletEvt

            if (walletEvt != null) {
                runCatching { walletEvt.mints(acc.signer) }
                    .onSuccess { _mints.value = it }
            } else {
                _mints.value = emptyList()
            }

            val tokenList = mutableListOf<CashuTokenEvent>()
            val historyList = mutableListOf<CashuSpendingHistoryEvent>()
            cache.notes.forEach { _, note ->
                val e = note.event ?: return@forEach
                if (e.pubKey != pubKey) return@forEach
                when (e) {
                    is CashuTokenEvent -> tokenList.add(e)
                    is CashuSpendingHistoryEvent -> historyList.add(e)
                    else -> Unit
                }
            }

            // Apply rollover: drop any token whose ID appears in a newer token's `del`.
            val deletedIds = mutableSetOf<String>()
            val decoded = mutableMapOf<String, TokenContent>()
            tokenList.forEach { tok ->
                runCatching { tok.tokenContent(acc.signer) }
                    .getOrNull()
                    ?.let { content ->
                        decoded[tok.id] = content
                        deletedIds.addAll(content.del)
                    }
            }
            val unspent = tokenList.filter { it.id !in deletedIds }
            _tokenEvents.value = unspent.sortedByDescending { it.createdAt }

            val balance = unspent.sumOf { decoded[it.id]?.totalAmount() ?: 0L }
            _balanceSats.value = balance

            _history.value = historyList.sortedByDescending { it.createdAt }
        }
    }

    /**
     * Builds and publishes a kind:17375 wallet event. If `autoGenPrivkey` is true,
     * a fresh P2PK private key is generated for receiving NIP-61 nutzaps.
     *
     * The wallet event is replaceable, so calling this again with different mints
     * (or a different privkey) overwrites the previous one — the existing logic
     * in account.sendLiterallyEverywhere takes care of broadcasting and local
     * cache consumption.
     */
    fun saveWallet(
        mints: List<String>,
        autoGenPrivkey: Boolean,
        manualPrivkey: String? = null,
    ) {
        val vm = accountViewModel ?: return
        val acc = account ?: return

        if (mints.isEmpty()) {
            _createState.value = CashuWalletCreateState.Error("Add at least one mint")
            return
        }

        _createState.value = CashuWalletCreateState.Saving
        vm.launchSigner {
            try {
                val privkey =
                    when {
                        autoGenPrivkey -> Bdhke.randomScalar().toHexKey()
                        !manualPrivkey.isNullOrBlank() -> manualPrivkey.trim()
                        else -> null
                    }

                val template = CashuWalletEvent.build(mints, privkey, acc.signer)
                val event = acc.signer.sign(template)
                acc.sendLiterallyEverywhere(event)
                _createState.value = CashuWalletCreateState.Success
                refresh()
            } catch (e: Exception) {
                _createState.value =
                    CashuWalletCreateState.Error(e.message ?: "Failed to save wallet")
            }
        }
    }

    fun resetCreateState() {
        _createState.value = CashuWalletCreateState.Idle
    }
}
