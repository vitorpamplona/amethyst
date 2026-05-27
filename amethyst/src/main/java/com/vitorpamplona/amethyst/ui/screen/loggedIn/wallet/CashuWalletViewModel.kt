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
import com.vitorpamplona.amethyst.model.Account
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletOps
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.model.nip60Cashu.MintQuoteStarted
import com.vitorpamplona.amethyst.model.nip60Cashu.TokenEntry
import com.vitorpamplona.amethyst.model.nip60Cashu.describeMintError
import com.vitorpamplona.amethyst.service.cashu.v3.V3Parser
import com.vitorpamplona.amethyst.service.cashu.v4.V4Parser
import com.vitorpamplona.amethyst.ui.components.GenericLoadable
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip60Cashu.mintApi.MeltQuoteBolt11ResponseDto
import com.vitorpamplona.quartz.nip60Cashu.token.CashuProof
import com.vitorpamplona.quartz.nip87Ecash.recommendation.MintRecommendationEvent
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    data object Quoting : CashuMeltFlowState()

    /** Mint returned a quote — show amount + fee, await user confirmation. */
    data class Quoted(
        val mintUrl: String,
        val invoice: String,
        val quote: MeltQuoteBolt11ResponseDto,
    ) : CashuMeltFlowState()

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
 * Presenter ViewModel for the NIP-60 Cashu wallet screens.
 *
 * All persistent state lives on [Account.cashuWalletState]; this VM only
 * holds the transient per-flow UI state (mint quote in progress, melt
 * confirmation pending, redeem result). State flows are forwarded directly
 * from [CashuWalletState] so they survive screen lifecycle.
 */
class CashuWalletViewModel : ViewModel() {
    private var account: Account? = null
    private var accountViewModel: AccountViewModel? = null
    private val state: CashuWalletState get() = account!!.cashuWalletState
    private val ops: CashuWalletOps get() = state.ops

    val walletEvent get() = state.walletEvent
    val mints get() = state.mints
    val balanceSats get() = state.balanceSats
    val tokenEntries: StateFlow<List<TokenEntry>> get() = state.tokenEntries
    val history get() = state.history
    val pendingQuotes get() = state.pendingQuotes
    val discovering get() = state.discovering
    val ownRecommendations get() = state.ownRecommendations

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

    private val _mintPingState = MutableStateFlow<MintPingState>(MintPingState.Idle)
    val mintPingState = _mintPingState.asStateFlow()

    fun init(accountViewModel: AccountViewModel) {
        this.accountViewModel = accountViewModel
        this.account = accountViewModel.account
        // No subscription / observer / refresh here — CashuWalletState owns
        // that lifecycle and is alive for the whole login session.
    }

    /** Verify a mint URL is reachable + speaks Cashu v1. */
    fun pingMint(url: String) {
        val vm = accountViewModel ?: return
        if (url.isBlank()) return
        _mintPingState.value = MintPingState.Pinging
        vm.launchSigner {
            try {
                val name = ops.pingMint(url.trim().trimEnd('/'))
                _mintPingState.value = MintPingState.Ok(name)
            } catch (e: Exception) {
                _mintPingState.value = MintPingState.Failed(describeMintError(e))
            }
        }
    }

    fun resetMintPing() {
        _mintPingState.value = MintPingState.Idle
    }

    /**
     * Reference to the account-scoped NIP-87 mint directory state. The picker
     * UI calls .open()/.close() on entry/exit and observes .entries directly.
     */
    val directory get() = account!!.cashuMintDirectoryState

    /** Open the NIP-87 mint directory subscription against the account's outbox relays. */
    fun openMintDirectory() {
        val acc = account ?: return
        directory.open(this, acc.outboxRelays.flow.value)
    }

    fun closeMintDirectory() {
        directory.close(this)
    }

    /** Publish a NIP-87 kind:38000 recommendation for [mintUrl]. */
    fun recommendMint(
        mintUrl: String,
        review: String = "",
    ) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            try {
                val dTag = directory.lookup(mintUrl)?.announcement?.dTag()
                ops.recommendMint(mintUrl = mintUrl, mintAnnouncementDTag = dTag, review = review)
            } catch (e: Exception) {
                // Best-effort — the UI doesn't need to block on the result.
                // Pass the throwable through so support reports keep the
                // stack trace; the lambda-only Log.w overload swallows it.
                Log.w("CashuWallet", "recommendMint($mintUrl) failed: ${describeMintError(e)}", e)
            }
        }
    }

    /**
     * NIP-09-retract a previously-published mint recommendation. The local
     * `ownRecommendations` list updates reactively once the delete event
     * round-trips through LocalCache.
     */
    fun deleteRecommendation(event: MintRecommendationEvent) {
        val vm = accountViewModel ?: return
        vm.launchSigner {
            runCatching { ops.deleteRecommendation(event) }
                .onFailure {
                    Log.w("CashuWallet", "deleteRecommendation failed: ${describeMintError(it)}", it)
                }
        }
    }

    // -------- NUT-09 restore --------

    sealed class RestoreFlowState {
        data object Idle : RestoreFlowState()

        data object Running : RestoreFlowState()

        data class Completed(
            val totalSatsRecovered: Long,
            val proofsRecovered: Int,
            val mintsScanned: Int,
        ) : RestoreFlowState()

        data class Error(
            val message: String,
        ) : RestoreFlowState()
    }

    private val _restoreState = MutableStateFlow<RestoreFlowState>(RestoreFlowState.Idle)
    val restoreState = _restoreState.asStateFlow()

    /**
     * NUT-09 wallet restore — scans every mint in the wallet's mint list
     * for proofs the user previously minted but whose kind:7375 events
     * have been lost. Recovered unspent proofs are republished as fresh
     * kind:7375 + kind:7376 IN history rows.
     *
     * Best-effort across mints: a failure on one mint logs and moves to
     * the next. The total reported in [RestoreFlowState.Completed]
     * aggregates across all mints scanned.
     */
    fun restoreFromAllMints() {
        val vm = accountViewModel ?: return
        if (_restoreState.value is RestoreFlowState.Running) return
        _restoreState.value = RestoreFlowState.Running
        vm.launchSigner {
            try {
                val mintsToScan = state.mints.value
                var totalSats = 0L
                var totalProofs = 0
                for (mint in mintsToScan) {
                    runCatching { state.restoreFromMint(mint) }
                        .onSuccess { outcome ->
                            if (outcome != null) {
                                totalSats += outcome.amountRecoveredSats
                                totalProofs += outcome.proofsRecovered
                            }
                        }.onFailure {
                            Log.w("CashuWallet", "restoreFromMint($mint) failed: ${describeMintError(it)}", it)
                        }
                }
                _restoreState.value =
                    RestoreFlowState.Completed(
                        totalSatsRecovered = totalSats,
                        proofsRecovered = totalProofs,
                        mintsScanned = mintsToScan.size,
                    )
            } catch (e: Exception) {
                _restoreState.value = RestoreFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetRestoreState() {
        _restoreState.value = RestoreFlowState.Idle
    }

    /** What to do with the wallet's P2PK key during a save. */
    enum class P2pkKeyMode {
        /** Keep the existing key from the on-cache wallet (edit only). */
        KeepCurrent,

        /** Generate a fresh random key — invalidates inbound nutzaps locked to the old one. */
        AutoGenerate,

        /** Use a user-pasted hex key. */
        Manual,
    }

    fun saveWallet(
        mints: List<String>,
        keyMode: P2pkKeyMode,
        manualPrivkey: String? = null,
    ) {
        val vm = accountViewModel ?: return
        val acc = account ?: return

        if (mints.isEmpty()) {
            _createState.value = CashuWalletCreateState.Error("Add at least one mint")
            return
        }
        if (keyMode == P2pkKeyMode.Manual && manualPrivkey.isNullOrBlank()) {
            _createState.value = CashuWalletCreateState.Error("Paste a P2PK private key")
            return
        }

        _createState.value = CashuWalletCreateState.Saving
        vm.launchSigner {
            try {
                val privkey: String? =
                    when (keyMode) {
                        P2pkKeyMode.KeepCurrent -> {
                            val existing = state.exportP2pkPrivkeyHex()
                            if (existing == null) {
                                _createState.value =
                                    CashuWalletCreateState.Error(
                                        "Could not read the existing wallet key. " +
                                            "Use auto-generate or paste a key instead.",
                                    )
                                return@launchSigner
                            }
                            existing
                        }
                        P2pkKeyMode.AutoGenerate -> null // ops generates one
                        P2pkKeyMode.Manual -> manualPrivkey?.trim()
                    }
                ops.publishWalletEvents(
                    mints = mints,
                    p2pkPrivkeyHex = privkey,
                    nutzapRelays =
                        acc.outboxRelays.flow.value
                            .toList(),
                )
                _createState.value = CashuWalletCreateState.Success
            } catch (e: Exception) {
                _createState.value = CashuWalletCreateState.Error(describeMintError(e))
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
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(describeMintError(e))
            }
        }
    }

    /**
     * Polls the mint to see if the user's bolt11 has been paid yet. If so,
     * completes the mint flow (proof issuance + kind:7375/7376 publish).
     */
    fun checkAndCompleteMint() {
        val vm = accountViewModel ?: return
        val current = _mintState.value as? CashuMintFlowState.AwaitingPayment ?: return
        vm.launchSigner {
            try {
                val status = ops.checkMintQuote(current.mintUrl, current.flow.mintQuote.quote)
                val paid = status.paid == true || status.state == "PAID" || status.state == "ISSUED"
                if (!paid) return@launchSigner

                _mintState.value = CashuMintFlowState.Completing
                ops.completeMintFromLightning(current.mintUrl, current.flow.quoteEvent, current.amountSats)
                _mintState.value = CashuMintFlowState.Completed(current.amountSats)
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(describeMintError(e))
            }
        }
    }

    /** Resume polling an unfulfilled kind:7374 quote left over from a previous session. */
    fun resumeMintQuote(quoteEvent: com.vitorpamplona.quartz.nip60Cashu.quote.CashuMintQuoteEvent) {
        val vm = accountViewModel ?: return
        val mintUrl =
            quoteEvent.mint() ?: run {
                _mintState.value = CashuMintFlowState.Error("Quote has no mint tag")
                return
            }
        vm.launchSigner {
            try {
                val quoteId = quoteEvent.quoteId(account!!.signer)
                val status = ops.checkMintQuote(mintUrl, quoteId)
                _mintState.value =
                    CashuMintFlowState.AwaitingPayment(
                        flow = MintQuoteStarted(quoteEvent = quoteEvent, mintQuote = status, invoice = status.request),
                        mintUrl = mintUrl,
                        amountSats = 0L, // unknown — caller may have to re-enter, mint will validate
                    )
            } catch (e: Exception) {
                _mintState.value = CashuMintFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetMintState() {
        _mintState.value = CashuMintFlowState.Idle
    }

    /**
     * User-initiated discard of the pending mint quote currently showing in
     * the Receive dialog. NIP-09 deletes the kind:7374 so the pending-quote
     * banner doesn't keep re-surfacing it, then drops back to Idle.
     *
     * Works from both the AwaitingPayment state (just-requested invoice)
     * and the Error state (failed completion). No-op otherwise.
     */
    fun discardMintQuote() {
        val vm = accountViewModel ?: return
        val quoteEvent =
            when (val s = _mintState.value) {
                is CashuMintFlowState.AwaitingPayment -> s.flow.quoteEvent
                else -> null
            } ?: run {
                _mintState.value = CashuMintFlowState.Idle
                return
            }
        vm.launchSigner {
            runCatching { ops.cancelMintQuote(quoteEvent) }
                .onFailure {
                    Log.w("CashuWallet", "cancelMintQuote failed: ${describeMintError(it)}", it)
                }
            _mintState.value = CashuMintFlowState.Idle
        }
    }

    // -------- Melt to LN --------

    /**
     * Phase 1 of melt — request a quote so the user can see amount + fees
     * before committing. Use [confirmMelt] to actually pay.
     */
    fun startMelt(
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
        val available = tokenEntries.value.filter { it.content.mint == mintUrl }
        if (available.isEmpty()) {
            _meltState.value = CashuMeltFlowState.Error("No proofs available at $mintUrl")
            return
        }

        _meltState.value = CashuMeltFlowState.Quoting
        vm.launchSigner {
            try {
                val quote = ops.requestMeltQuote(mintUrl, invoice.trim())
                val balance = available.sumOf { it.content.totalAmount() }
                if (balance < quote.amount + quote.feeReserve) {
                    _meltState.value =
                        CashuMeltFlowState.Error(
                            "Need ${quote.amount + quote.feeReserve} sat (incl. fees) but only have $balance",
                        )
                    return@launchSigner
                }
                _meltState.value = CashuMeltFlowState.Quoted(mintUrl, invoice.trim(), quote)
            } catch (e: Exception) {
                _meltState.value = CashuMeltFlowState.Error(describeMintError(e))
            }
        }
    }

    /** Phase 2 of melt — user confirmed the fee, pay the invoice now. */
    fun confirmMelt() {
        val vm = accountViewModel ?: return
        val quoted = _meltState.value as? CashuMeltFlowState.Quoted ?: return

        _meltState.value = CashuMeltFlowState.Paying
        vm.launchSigner {
            try {
                val result = state.meltToLightning(quoted.mintUrl, quoted.quote)
                _meltState.value = CashuMeltFlowState.Completed(result.paidAmount, result.fees, result.preimage)
            } catch (e: Exception) {
                _meltState.value = CashuMeltFlowState.Error(describeMintError(e))
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
        // sendAsToken on the state runs a NUT-07 scrub + balance check
        // before invoking the mint swap, so the viewmodel only needs to
        // surface failures.
        _sendTokenState.value = CashuSendTokenFlowState.Building
        vm.launchSigner {
            try {
                val result = state.sendAsToken(mintUrl, amountSats, memo)
                _sendTokenState.value = CashuSendTokenFlowState.Ready(result.cashuToken, result.amount)
            } catch (e: Exception) {
                _sendTokenState.value = CashuSendTokenFlowState.Error(describeMintError(e))
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
                            tok.mint to tok.proofs.map { CashuProof(it.id, it.amount.toLong(), it.secret, it.C) }
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
                            tok.mint to tok.proofs.map { CashuProof(it.id, it.amount.toLong(), it.secret, it.C) }
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

        if (mintUrl !in mints.value) {
            _redeemState.value =
                CashuRedeemFlowState.Error("Token mint ($mintUrl) is not in your wallet. Add it first.")
            return
        }

        _redeemState.value = CashuRedeemFlowState.Redeeming
        vm.launchSigner {
            try {
                val result = ops.redeemToken(trimmed, proofs, mintUrl)
                _redeemState.value = CashuRedeemFlowState.Completed(result.amount)
            } catch (e: Exception) {
                _redeemState.value = CashuRedeemFlowState.Error(describeMintError(e))
            }
        }
    }

    fun resetRedeemState() {
        _redeemState.value = CashuRedeemFlowState.Idle
    }
}

sealed class MintPingState {
    data object Idle : MintPingState()

    data object Pinging : MintPingState()

    data class Ok(
        val name: String?,
    ) : MintPingState()

    data class Failed(
        val message: String,
    ) : MintPingState()
}
