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
import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.commons.relayClient.assemblers.CashuWalletQueryState
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletOps
import com.vitorpamplona.amethyst.model.nip60Cashu.MintQuoteStarted
import com.vitorpamplona.amethyst.model.nip60Cashu.TokenEntry
import com.vitorpamplona.amethyst.service.cashu.v3.V3Parser
import com.vitorpamplona.amethyst.service.cashu.v4.V4Parser
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip60Cashu.bdhke.Bdhke
import com.vitorpamplona.quartz.nip60Cashu.history.CashuSpendingHistoryEvent
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip60Cashu.token.CashuTokenEvent
import com.vitorpamplona.quartz.nip60Cashu.token.TokenContent
import com.vitorpamplona.quartz.nip60Cashu.wallet.CashuWalletEvent
import com.vitorpamplona.quartz.nip61Nutzaps.nutzap.NutzapEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed class CashuWalletCreateState {
    data object Idle : CashuWalletCreateState()

    data object Saving : CashuWalletCreateState()

    data object Success : CashuWalletCreateState()

    data class Error(
        val message: String,
    ) : CashuWalletCreateState()
}

sealed class CashuMintFlowState {
    data object Idle : CashuMintFlowState()

    data object Requesting : CashuMintFlowState()

    data class AwaitingPayment(
        val flow: MintQuoteStarted,
        val mintUrl: String,
        val amountSats: Long,
    ) : CashuMintFlowState()

    data object Completing : CashuMintFlowState()

    data class Completed(
        val amountSats: Long,
    ) : CashuMintFlowState()

    data class Error(
        val message: String,
    ) : CashuMintFlowState()
}

sealed class CashuMeltFlowState {
    data object Idle : CashuMeltFlowState()

    data object Paying : CashuMeltFlowState()

    data class Completed(
        val paidAmount: Long,
        val fees: Long,
        val preimage: String?,
    ) : CashuMeltFlowState()

    data class Error(
        val message: String,
    ) : CashuMeltFlowState()
}

sealed class CashuSendTokenFlowState {
    data object Idle : CashuSendTokenFlowState()

    data object Building : CashuSendTokenFlowState()

    data class Ready(
        val token: String,
        val amount: Long,
    ) : CashuSendTokenFlowState()

    data class Error(
        val message: String,
    ) : CashuSendTokenFlowState()
}

sealed class CashuRedeemFlowState {
    data object Idle : CashuRedeemFlowState()

    data object Redeeming : CashuRedeemFlowState()

    data class Completed(
        val amount: Long,
    ) : CashuRedeemFlowState()

    data class Error(
        val message: String,
    ) : CashuRedeemFlowState()
}

/**
 * ViewModel for the NIP-60 Cashu wallet screens.
 *
 * Exposes:
 *  - wallet bootstrap (create/edit kind:17375 + kind:10019)
 *  - balance / mint list / history / unspent token entries derived from LocalCache
 *  - mint-from-LN flow (start quote → poll → complete)
 *  - melt-to-LN flow (pay bolt11)
 *  - send-as-token flow (produce cashuB)
 *  - redeem cashuB token flow
 */
class CashuWalletViewModel : ViewModel() {
    private var account: Account? = null
    private var accountViewModel: AccountViewModel? = null
    private val ops by lazy {
        val acc = account ?: error("init() not called")
        CashuWalletOps(acc, Amethyst.instance.roleBasedHttpClientBuilder::okHttpClientForMoney)
    }
    private val assembler get() = Amethyst.instance.sources.cashuWallet
    private var subscription: CashuWalletQueryState? = null
    private var observerJob: Job? = null

    private val _walletEvent = MutableStateFlow<CashuWalletEvent?>(null)
    val walletEvent = _walletEvent.asStateFlow()

    private val _mints = MutableStateFlow<List<String>>(emptyList())
    val mints = _mints.asStateFlow()

    private val _balanceSats = MutableStateFlow(0L)
    val balanceSats = _balanceSats.asStateFlow()

    private val _tokenEntries = MutableStateFlow<List<TokenEntry>>(emptyList())
    val tokenEntries = _tokenEntries.asStateFlow()

    private val _history = MutableStateFlow<List<CashuSpendingHistoryEvent>>(emptyList())
    val history = _history.asStateFlow()

    private val _createState = MutableStateFlow<CashuWalletCreateState>(CashuWalletCreateState.Idle)
    val createState = _createState.asStateFlow()

    private val _mintState = MutableStateFlow<CashuMintFlowState>(CashuMintFlowState.Idle)
    val mintState = _mintState.asStateFlow()

    private val _meltState = MutableStateFlow<CashuMeltFlowState>(CashuMeltFlowState.Idle)
    val meltState = _meltState.asStateFlow()

    private val _sendTokenState = MutableStateFlow<CashuSendTokenFlowState>(CashuSendTokenFlowState.Idle)
    val sendTokenState = _sendTokenState.asStateFlow()

    private val _redeemState = MutableStateFlow<CashuRedeemFlowState>(CashuRedeemFlowState.Idle)
    val redeemState = _redeemState.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account

        // Subscribe to relay events so the wallet/proofs/history/nutzaps
        // arrive from outbox relays on first launch and stay in sync.
        val pubkey = accountViewModel.account.signer.pubKey
        val relays = accountViewModel.account.outboxRelays.flow.value
        if (relays.isNotEmpty()) {
            val query = CashuWalletQueryState(pubkey, relays)
            subscription = query
            assembler.subscribe(query)
        }

        // Re-derive state from the cache whenever the wallet note changes.
        // Token / history events that arrive via the same subscription are
        // captured on the next refresh tick.
        val walletNote = LocalCache.getOrCreateAddressableNote(CashuWalletEvent.createAddress(pubkey))
        observerJob?.cancel()
        observerJob =
            viewModelScope.launch(Dispatchers.IO) {
                walletNote
                    .flow()
                    .metadata.stateFlow
                    .collect { refresh() }
            }

        refresh()
    }

    override fun onCleared() {
        observerJob?.cancel()
        observerJob = null
        subscription?.let { runCatching { assembler.unsubscribe(it) } }
        subscription = null
        super.onCleared()
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
            val unspent = tokenList.filter { it.id !in deletedIds && decoded[it.id] != null }
            val entries = unspent.mapNotNull { e -> decoded[e.id]?.let { TokenEntry(e, it) } }
            _tokenEntries.value = entries.sortedByDescending { it.event.createdAt }

            _balanceSats.value = entries.sumOf { it.content.totalAmount() }
            _history.value = historyList.sortedByDescending { it.createdAt }

            // Best-effort auto-redeem of any inbound NIP-61 nutzaps that we
            // haven't already redeemed. Idempotent — guarded by the redeemed
            // marker in our kind:7376 history.
            redeemPendingNutzaps(walletEvt, historyList)
        }
    }

    private suspend fun redeemPendingNutzaps(
        walletEvt: CashuWalletEvent?,
        history: List<CashuSpendingHistoryEvent>,
    ) {
        val acc = account ?: return
        if (walletEvt == null) return
        val privkey = runCatching { walletEvt.privkey(acc.signer) }.getOrNull() ?: return

        val alreadyRedeemed =
            history
                .flatMap { it.redeemedReferences() }
                .map { it.eventId }
                .toSet()

        val candidates =
            buildList {
                LocalCache.notes.forEach { _, note ->
                    val e = note.event ?: return@forEach
                    if (e is NutzapEvent && e.id !in alreadyRedeemed) add(e)
                }
            }
        if (candidates.isEmpty()) return

        candidates.forEach { ev ->
            runCatching { ops.redeemNutzap(ev, privkey) }
                .onFailure {
                    // Swallow; we'll retry on the next refresh. The mint may
                    // have rejected for double-spend (someone else redeemed
                    // first) or other transient reasons.
                }
        }
    }

    /**
     * Builds and publishes a kind:17375 + kind:10019 pair. The wallet event is
     * replaceable so calling this again with different mints (or a different
     * privkey) overwrites the previous wallet.
     */
    fun saveWallet(
        mints: List<String>,
        autoGenPrivkey: Boolean,
        manualPrivkey: String? = null,
    ) {
        val vm = accountViewModel ?: return

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

                ops.publishWalletEvents(mints, privkey)
                _createState.value = CashuWalletCreateState.Success
                refresh()
            } catch (e: Exception) {
                _createState.value =
                    CashuWalletCreateState.Error(ops.describe(e))
            }
        }
    }

    fun resetCreateState() {
        _createState.value = CashuWalletCreateState.Idle
    }

    // -------- Mint from LN --------

    fun startMintFromLightning(
        mintUrl: String,
        amountSats: Long,
    ) {
        val vm = accountViewModel ?: return
        if (amountSats <= 0) {
            _mintState.value = CashuMintFlowState.Error("Amount must be positive")
            return
        }
        if (mintUrl.isBlank()) {
            _mintState.value = CashuMintFlowState.Error("Pick a mint")
            return
        }
        _mintState.value = CashuMintFlowState.Requesting
        vm.launchSigner {
            try {
                val flow = ops.startMintFromLightning(mintUrl, amountSats)
                _mintState.value = CashuMintFlowState.AwaitingPayment(flow, mintUrl, amountSats)
                refresh()
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(ops.describe(e))
            }
        }
    }

    /**
     * Polls the mint to see if the user's bolt11 has been paid yet. If so,
     * completes the mint flow (proof issuance + kind:7375/7376 publish).
     */
    fun checkAndCompleteMint() {
        val vm = accountViewModel ?: return
        val state = _mintState.value as? CashuMintFlowState.AwaitingPayment ?: return
        vm.launchSigner {
            try {
                val status = ops.checkMintQuote(state.mintUrl, state.flow.mintQuote.quote)
                val paid = status.paid == true || status.state == "PAID" || status.state == "ISSUED"
                if (!paid) return@launchSigner

                _mintState.value = CashuMintFlowState.Completing
                ops.completeMintFromLightning(state.mintUrl, state.flow.quoteEvent, state.amountSats)
                _mintState.value = CashuMintFlowState.Completed(state.amountSats)
                refresh()
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(ops.describe(e))
            }
        }
    }

    fun resetMintState() {
        _mintState.value = CashuMintFlowState.Idle
    }

    // -------- Melt to LN --------

    fun meltToLightning(
        mintUrl: String,
        invoice: String,
    ) {
        val vm = accountViewModel ?: return
        if (invoice.isBlank()) {
            _meltState.value = CashuMeltFlowState.Error("Paste a bolt11 invoice")
            return
        }
        if (mintUrl.isBlank()) {
            _meltState.value = CashuMeltFlowState.Error("Pick a mint")
            return
        }
        val available = _tokenEntries.value.filter { it.content.mint == mintUrl }
        if (available.isEmpty()) {
            _meltState.value = CashuMeltFlowState.Error("No proofs available at $mintUrl")
            return
        }

        _meltState.value = CashuMeltFlowState.Paying
        vm.launchSigner {
            try {
                val result = ops.meltToLightning(mintUrl, invoice.trim(), available)
                _meltState.value = CashuMeltFlowState.Completed(result.paidAmount, result.fees, result.preimage)
                refresh()
            } catch (e: Exception) {
                _meltState.value = CashuMeltFlowState.Error(ops.describe(e))
            }
        }
    }

    fun resetMeltState() {
        _meltState.value = CashuMeltFlowState.Idle
    }

    // -------- Send-as-token --------

    fun sendAsToken(
        mintUrl: String,
        amountSats: Long,
        memo: String? = null,
    ) {
        val vm = accountViewModel ?: return
        if (amountSats <= 0) {
            _sendTokenState.value = CashuSendTokenFlowState.Error("Amount must be positive")
            return
        }
        if (mintUrl.isBlank()) {
            _sendTokenState.value = CashuSendTokenFlowState.Error("Pick a mint")
            return
        }
        val available = _tokenEntries.value.filter { it.content.mint == mintUrl }
        val balanceAtMint = available.sumOf { it.content.totalAmount() }
        if (balanceAtMint < amountSats) {
            _sendTokenState.value =
                CashuSendTokenFlowState.Error("Mint $mintUrl has only $balanceAtMint sat")
            return
        }

        _sendTokenState.value = CashuSendTokenFlowState.Building
        vm.launchSigner {
            try {
                val result = ops.sendAsToken(mintUrl, amountSats, available, memo)
                _sendTokenState.value = CashuSendTokenFlowState.Ready(result.cashuToken, result.amount)
                refresh()
            } catch (e: Exception) {
                _sendTokenState.value = CashuSendTokenFlowState.Error(ops.describe(e))
            }
        }
    }

    fun resetSendTokenState() {
        _sendTokenState.value = CashuSendTokenFlowState.Idle
    }

    // -------- Redeem inbound cashuB / cashuA --------

    fun redeemToken(rawToken: String) {
        val vm = accountViewModel ?: return
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            _redeemState.value = CashuRedeemFlowState.Error("Paste a Cashu token")
            return
        }

        val (mintUrl, proofs) =
            when {
                trimmed.startsWith("cashuB") -> {
                    when (val parsed = V4Parser.parseCashuB(trimmed)) {
                        is GenericLoadable.Loaded -> {
                            val tok = parsed.loaded.firstOrNull()
                            if (tok == null) {
                                _redeemState.value = CashuRedeemFlowState.Error("Token has no proofs")
                                return
                            }
                            tok.mint to
                                tok.proofs.map {
                                    CashuProof(
                                        id = it.id,
                                        amount = it.amount.toLong(),
                                        secret = it.secret,
                                        c = it.C,
                                    )
                                }
                        }

                        is GenericLoadable.Error -> {
                            _redeemState.value = CashuRedeemFlowState.Error(parsed.errorMessage)
                            return
                        }

                        else -> {
                            _redeemState.value = CashuRedeemFlowState.Error("Could not parse token")
                            return
                        }
                    }
                }

                trimmed.startsWith("cashuA") -> {
                    when (val parsed = V3Parser.parseCashuA(trimmed)) {
                        is GenericLoadable.Loaded -> {
                            val tok = parsed.loaded.firstOrNull()
                            if (tok == null) {
                                _redeemState.value = CashuRedeemFlowState.Error("Token has no proofs")
                                return
                            }
                            tok.mint to
                                tok.proofs.map {
                                    CashuProof(
                                        id = it.id,
                                        amount = it.amount.toLong(),
                                        secret = it.secret,
                                        c = it.C,
                                    )
                                }
                        }

                        is GenericLoadable.Error -> {
                            _redeemState.value = CashuRedeemFlowState.Error(parsed.errorMessage)
                            return
                        }

                        else -> {
                            _redeemState.value = CashuRedeemFlowState.Error("Could not parse token")
                            return
                        }
                    }
                }

                else -> {
                    _redeemState.value =
                        CashuRedeemFlowState.Error("Not a Cashu token (must start with cashuA or cashuB)")
                    return
                }
            }

        if (mintUrl !in _mints.value) {
            _redeemState.value =
                CashuRedeemFlowState.Error("Token mint ($mintUrl) is not in your wallet. Add it first.")
            return
        }

        _redeemState.value = CashuRedeemFlowState.Redeeming
        vm.launchSigner {
            try {
                val result = ops.redeemToken(trimmed, proofs, mintUrl)
                _redeemState.value = CashuRedeemFlowState.Completed(result.amount)
                refresh()
            } catch (e: Exception) {
                _redeemState.value = CashuRedeemFlowState.Error(ops.describe(e))
            }
        }
    }

    fun resetRedeemState() {
        _redeemState.value = CashuRedeemFlowState.Idle
    }
}
