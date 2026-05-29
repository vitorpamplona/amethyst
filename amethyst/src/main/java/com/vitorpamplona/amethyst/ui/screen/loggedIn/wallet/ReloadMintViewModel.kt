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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.model.nip60Cashu.describeMintError
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Hand-off payload for the Reload Mint screen — the pending nutzap to fund. */
data class ReloadMintRequest(
    val baseNote: Note,
    val amountSats: Long,
)

/** Where the reload funds come from. */
@Immutable
sealed interface ReloadSource {
    /** Move existing cashu from another mint we hold (no new sats). */
    data class Mint(
        val mintUrl: String,
        val balanceSats: Long,
        val canCover: Boolean,
    ) : ReloadSource

    /** Pay a fresh Lightning invoice to mint at the destination. */
    data object Lightning : ReloadSource
}

/** A mint we hold a balance at, for the balances list. */
@Immutable
data class MintBalance(
    val mintUrl: String,
    val balanceSats: Long,
)

/** Progress of the reload + send. */
@Immutable
sealed interface ReloadStatus {
    data object Configuring : ReloadStatus

    data class Working(
        val step: String,
        val progress: Float,
    ) : ReloadStatus

    /** No NWC wallet — the user must pay [invoice] externally; we keep polling. */
    data class AwaitingInvoice(
        val invoice: String,
        val sats: Long,
    ) : ReloadStatus

    data object Done : ReloadStatus

    data class Failed(
        val message: String,
    ) : ReloadStatus
}

@Immutable
data class ReloadUiState(
    val recipient: String = "",
    val amountSats: Long = 0L,
    val balances: ImmutableList<MintBalance> = persistentListOf(),
    val targetOptions: ImmutableList<String> = persistentListOf(),
    val selectedTarget: String = "",
    val shortfallSats: Long = 0L,
    val estFeeSats: Long = 0L,
    val sources: ImmutableList<ReloadSource> = persistentListOf(),
    val selectedSource: ReloadSource? = null,
    val status: ReloadStatus = ReloadStatus.Configuring,
)

class ReloadMintViewModel : ViewModel() {
    private var accountViewModel: AccountViewModel? = null
    private var baseNote: Note? = null
    private val state: CashuWalletState? get() = accountViewModel?.account?.cashuWalletState

    private val _uiState = MutableStateFlow(ReloadUiState())
    val uiState: StateFlow<ReloadUiState> = _uiState.asStateFlow()

    fun init(
        accountViewModel: AccountViewModel,
        request: ReloadMintRequest,
    ) {
        this.accountViewModel = accountViewModel
        this.baseNote = request.baseNote
        val st = state ?: return
        val recipient = request.baseNote.author?.pubkeyHex ?: return

        val balances = st.peekMintBalances()
        val targets =
            st
                .recipientSharedMints(recipient)
                .sortedByDescending { balances[it] ?: 0L }
        val defaultTarget = targets.firstOrNull() ?: return

        _uiState.value =
            ReloadUiState(
                recipient = recipient,
                amountSats = request.amountSats,
                balances =
                    balances.entries
                        .sortedByDescending { it.value }
                        .map { MintBalance(it.key, it.value) }
                        .toImmutableList(),
                targetOptions = targets.toImmutableList(),
                selectedTarget = defaultTarget,
            ).let { recomputeFor(it, defaultTarget) }
    }

    /** Recompute shortfall, fee estimate, sources, and a default source. */
    private fun recomputeFor(
        base: ReloadUiState,
        target: String,
    ): ReloadUiState {
        val balances = base.balances.associate { it.mintUrl to it.balanceSats }
        val targetBalance = balances[target] ?: 0L
        val shortfall = (base.amountSats - targetBalance).coerceAtLeast(0L)
        // Rough fee cushion for enabling a source; the melt quote sets the real
        // fee at execution time. 1% (min 1 sat).
        val estFee = (shortfall / 100L).coerceAtLeast(1L)

        val mintSources =
            base.balances
                .filter { it.mintUrl != target }
                .map { ReloadSource.Mint(it.mintUrl, it.balanceSats, canCover = it.balanceSats >= shortfall + estFee) }

        val sources: List<ReloadSource> = mintSources + ReloadSource.Lightning
        // Prefer the richest mint that can cover (no new sats); else Lightning.
        val defaultSource: ReloadSource =
            mintSources.filter { it.canCover }.maxByOrNull { it.balanceSats } ?: ReloadSource.Lightning

        return base.copy(
            selectedTarget = target,
            shortfallSats = shortfall,
            estFeeSats = estFee,
            sources = sources.toImmutableList(),
            selectedSource = defaultSource,
        )
    }

    fun selectTarget(mintUrl: String) {
        _uiState.update { recomputeFor(it, mintUrl) }
    }

    fun selectSource(source: ReloadSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    fun confirm() {
        val vm = accountViewModel ?: return
        val note = baseNote ?: return
        val s = _uiState.value
        val source = s.selectedSource ?: return

        vm.launchSigner {
            try {
                when (source) {
                    is ReloadSource.Mint -> rebalanceThenZap(source.mintUrl, s.selectedTarget, s.shortfallSats, note, s.amountSats)
                    ReloadSource.Lightning -> reloadFromLightningThenZap(s.selectedTarget, s.shortfallSats, note, s.amountSats)
                }
            } catch (e: Exception) {
                setStatus(ReloadStatus.Failed(describeMintError(e)))
            }
        }
    }

    private suspend fun rebalanceThenZap(
        sourceMint: String,
        targetMint: String,
        shortfall: Long,
        note: Note,
        amount: Long,
    ) {
        val st = state ?: return
        setStatus(ReloadStatus.Working("Moving funds", 0.1f))
        st.rebalance(sourceMint, targetMint, shortfall) { p ->
            setStatus(ReloadStatus.Working("Moving funds", p.coerceIn(0.1f, 0.9f)))
        }
        sendNutzapAndFinish(note, amount)
    }

    private suspend fun reloadFromLightningThenZap(
        targetMint: String,
        shortfall: Long,
        note: Note,
        amount: Long,
    ) {
        val vm = accountViewModel ?: return
        val st = state ?: return
        val ops = st.ops

        setStatus(ReloadStatus.Working("Requesting invoice", 0.1f))
        val flow = ops.startMintFromLightning(targetMint, shortfall)

        val walletUri = vm.account.settings.defaultZapPaymentRequest()
        if (walletUri != null) {
            setStatus(ReloadStatus.Working("Paying from your wallet", 0.35f))
            // Fire-and-forget: the mint-quote poll below is the source of truth
            // for whether the payment actually landed.
            runCatching {
                vm.account.sendNwcRequestToWallet(walletUri, PayInvoiceMethod.create(flow.invoice)) { }
            }
        } else {
            // No NWC — surface the invoice for an external wallet and keep polling.
            setStatus(ReloadStatus.AwaitingInvoice(flow.invoice, shortfall))
        }

        val attempts = 90
        val delayMs = 2_000L
        var paid = false
        var attempt = 0
        while (!paid && attempt < attempts) {
            val status = ops.checkMintQuote(targetMint, flow.mintQuote.quote)
            paid = status.paid == true || status.state == "PAID" || status.state == "ISSUED"
            if (!paid) {
                delay(delayMs)
                attempt++
            }
        }
        if (!paid) {
            setStatus(ReloadStatus.Failed("Invoice not paid yet — you can finish it later from the pending quote banner"))
            return
        }
        setStatus(ReloadStatus.Working("Issuing ecash", 0.85f))
        ops.completeMintFromLightning(targetMint, flow.quoteEvent, shortfall)
        sendNutzapAndFinish(note, amount)
    }

    private fun sendNutzapAndFinish(
        note: Note,
        amount: Long,
    ) {
        val vm = accountViewModel ?: return
        setStatus(ReloadStatus.Working("Sending zap", 0.95f))
        // The destination is now funded; fire the nutzap (its own error path
        // toasts) and report Done so the screen can pop.
        vm.sendNutzap(
            baseNote = note,
            amountSats = amount,
            message = "",
            onError = { _, msg, _ -> setStatus(ReloadStatus.Failed(msg)) },
        )
        setStatus(ReloadStatus.Done)
    }

    private fun setStatus(status: ReloadStatus) {
        _uiState.update { it.copy(status = status) }
    }
}
