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
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.amethyst.model.nip60Cashu.describeMintError
import com.vitorpamplona.amethyst.ui.screen.loggedIn.AccountViewModel
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceMethod
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    /** The in-flight reload pipeline, so a second tap can be rejected and the
     *  whole flow cancelled when the screen leaves (it runs on the long-lived
     *  AccountViewModel scope, not this VM's). */
    private var job: Job? = null

    private var recipient: String? = null

    private val _uiState = MutableStateFlow(ReloadUiState())
    val uiState: StateFlow<ReloadUiState> = _uiState.asStateFlow()

    fun init(
        accountViewModel: AccountViewModel,
        request: ReloadMintRequest,
    ) {
        if (this.accountViewModel != null) return // already initialized
        this.accountViewModel = accountViewModel
        this.baseNote = request.baseNote
        val st = state ?: return
        val recipient = request.baseNote.author?.pubkeyHex ?: return
        this.recipient = recipient
        _uiState.update { it.copy(recipient = recipient, amountSats = request.amountSats) }

        // Wallet proofs/mints arrive from relays and can land *after* the screen
        // opens — a one-time snapshot would show only the first-loaded mint. Keep
        // balances/targets in sync as the wallet fills in.
        viewModelScope.launch {
            combine(st.tokenEntries, st.mints) { _, _ -> Unit }.collect { rebuild() }
        }
    }

    /** Rebuild balances/targets/sources from the current wallet state, keeping
     *  the user's amount, chosen target, source pick, and status intact. */
    private fun rebuild() {
        val st = state ?: return
        val recipient = recipient ?: return
        val balances = st.peekMintBalances()
        val targets = st.recipientSharedMints(recipient).sortedByDescending { balances[it] ?: 0L }
        _uiState.update { cur ->
            val target = cur.selectedTarget.takeIf { it in targets } ?: targets.firstOrNull().orEmpty()
            recomputeFor(
                cur.copy(
                    balances =
                        balances.entries
                            .sortedByDescending { it.value }
                            .map { MintBalance(it.key, it.value) }
                            .toImmutableList(),
                    targetOptions = targets.toImmutableList(),
                ),
                target,
            )
        }
    }

    /** Recompute shortfall, fee estimate, sources, and a default source. */
    private fun recomputeFor(
        base: ReloadUiState,
        target: String,
    ): ReloadUiState {
        val balances = base.balances.associate { it.mintUrl to it.balanceSats }
        val targetBalance = balances[target] ?: 0L
        val shortfall = (base.amountSats - targetBalance).coerceAtLeast(0L)
        // Rough fee cushion for *enabling* a source — the real Lightning
        // feeReserve is only known once rebalance() fetches the melt quote, so
        // this is a heuristic (1%, min 2 sat). A source that clears the estimate
        // but not the real quote fails recoverably (Failed → pick another), it
        // doesn't move funds.
        val estFee = (shortfall / 100L).coerceAtLeast(2L)
        // A source must cover what we actually mint (shortfall + headroom buffer)
        // plus that melt fee.
        val needFromSource = shortfall + RELOAD_FEE_BUFFER_SATS + estFee

        val mintSources =
            base.balances
                .filter { it.mintUrl != target }
                .map { ReloadSource.Mint(it.mintUrl, it.balanceSats, canCover = it.balanceSats >= needFromSource) }

        val sources: List<ReloadSource> = mintSources + ReloadSource.Lightning
        val coverableMints = mintSources.filter { it.canCover }
        // Keep the user's current pick if it's still valid across the recompute
        // (target/amount edits), otherwise default to the richest mint that can
        // cover (no new sats), else Lightning.
        val keep =
            when (val prev = base.selectedSource) {
                is ReloadSource.Lightning -> ReloadSource.Lightning
                is ReloadSource.Mint -> coverableMints.firstOrNull { it.mintUrl == prev.mintUrl }
                null -> null
            }
        val selectedSource: ReloadSource = keep ?: coverableMints.maxByOrNull { it.balanceSats } ?: ReloadSource.Lightning

        return base.copy(
            selectedTarget = target,
            shortfallSats = shortfall,
            estFeeSats = estFee,
            sources = sources.toImmutableList(),
            selectedSource = selectedSource,
        )
    }

    fun selectTarget(mintUrl: String) {
        _uiState.update { recomputeFor(it, mintUrl) }
    }

    fun selectSource(source: ReloadSource) {
        _uiState.update { it.copy(selectedSource = source) }
    }

    /** Re-target the reload to a custom amount (the picker amount is the default). */
    fun setAmount(sats: Long) {
        _uiState.update { recomputeFor(it.copy(amountSats = sats.coerceAtLeast(0L)), it.selectedTarget) }
    }

    fun confirm() {
        val vm = accountViewModel ?: return
        val note = baseNote ?: return
        val s = _uiState.value
        val source = s.selectedSource ?: return
        if (s.amountSats <= 0L) return

        // In-flight guard: only start from a resting state. Without it a double
        // tap (or the Failed-state Retry) launches a second pipeline — two mint
        // quotes at the target and two melts at the source, double-spending /
        // double-minting. Flip to Working synchronously so the second call bails.
        if (s.status !is ReloadStatus.Configuring && s.status !is ReloadStatus.Failed) return
        setStatus(ReloadStatus.Working("Starting", 0.05f))

        // Mint a hair more than the bare shortfall so the follow-up nutzap's own
        // swap fee doesn't leave the target a sat short (see #RELOAD_FEE_BUFFER).
        val moveSats = s.shortfallSats + RELOAD_FEE_BUFFER_SATS

        job?.cancel()
        job =
            vm.launchSigner {
                try {
                    when {
                        // The chosen amount already fits in the target mint (e.g.
                        // the user lowered it) — no top-up needed, just send.
                        s.shortfallSats <= 0L -> sendNutzapAndFinish(note, s.amountSats)
                        source is ReloadSource.Mint -> rebalanceThenZap(source.mintUrl, s.selectedTarget, moveSats, note, s.amountSats)
                        source is ReloadSource.Lightning -> reloadFromLightningThenZap(s.selectedTarget, moveSats, note, s.amountSats)
                    }
                } catch (e: CancellationException) {
                    throw e // screen left mid-flow — don't mask as a Failed state
                } catch (e: Exception) {
                    setStatus(ReloadStatus.Failed(describeMintError(e)))
                }
            }
    }

    private suspend fun rebalanceThenZap(
        sourceMint: String,
        targetMint: String,
        moveSats: Long,
        note: Note,
        amount: Long,
    ) {
        val st = state ?: return
        setStatus(ReloadStatus.Working("Moving funds", 0.1f))
        st.rebalance(sourceMint, targetMint, moveSats) { p ->
            setStatus(ReloadStatus.Working("Moving funds", p.coerceIn(0.1f, 0.9f)))
        }
        sendNutzapAndFinish(note, amount)
    }

    private suspend fun reloadFromLightningThenZap(
        targetMint: String,
        moveSats: Long,
        note: Note,
        amount: Long,
    ) {
        val vm = accountViewModel ?: return
        val st = state ?: return
        val ops = st.ops

        setStatus(ReloadStatus.Working("Requesting invoice", 0.1f))
        val flow = ops.startMintFromLightning(targetMint, moveSats)

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
            setStatus(ReloadStatus.AwaitingInvoice(flow.invoice, moveSats))
        }

        // External payment can take a while; the poll runs on a job tied to the
        // screen (cancelled in onCleared), so leaving stops it instead of
        // hammering the mint for 3 minutes with nobody watching.
        val attempts = 90
        val delayMs = 2_000L
        var paid = false
        var attempt = 0
        while (!paid && attempt < attempts) {
            paid = ops.checkMintQuote(targetMint, flow.mintQuote.quote).isSettled()
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
        ops.completeMintFromLightning(targetMint, flow.quoteEvent, moveSats)
        sendNutzapAndFinish(note, amount)
    }

    /**
     * Send the nutzap and only report [ReloadStatus.Done] once it actually
     * succeeds. Suspends on the real send (throwing on failure) instead of the
     * fire-and-forget AccountViewModel.sendNutzap, so a send that fails after a
     * successful reload surfaces as Failed here rather than silently stranding
     * the just-moved funds while the screen pops on a premature "done".
     */
    private suspend fun sendNutzapAndFinish(
        note: Note,
        amount: Long,
    ) {
        val st = state ?: return
        val recipient = note.author?.pubkeyHex ?: throw IllegalStateException("Recipient has no pubkey")
        val zappedEvent = note.toEventHint<Event>() ?: throw IllegalStateException("Nothing to zap")
        setStatus(ReloadStatus.Working("Sending zap", 0.95f))
        st.sendNutzap(
            amountSats = amount,
            recipientPubKey = recipient,
            zappedEvent = zappedEvent,
            message = "",
        )
        setStatus(ReloadStatus.Done)
    }

    private fun setStatus(status: ReloadStatus) {
        _uiState.update { it.copy(status = status) }
    }

    override fun onCleared() {
        // The pipeline runs on the AccountViewModel scope, not this VM's, so it
        // would outlive the screen — cancel it when the screen goes away.
        job?.cancel()
        super.onCleared()
    }

    companion object {
        /**
         * Extra sats minted at the target beyond the bare shortfall, so the
         * follow-up nutzap's own swap fee doesn't leave the mint a hair short.
         * Most mints charge no input fee; this small cushion covers the ones
         * that do without meaningfully over-minting.
         */
        private const val RELOAD_FEE_BUFFER_SATS = 2L
    }
}
