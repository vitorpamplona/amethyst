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
package com.vitorpamplona.amethyst.model.zap

import androidx.compose.runtime.Immutable
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.model.Note
import com.vitorpamplona.amethyst.model.nip60Cashu.CashuWalletState
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetup
import com.vitorpamplona.quartz.nip57Zaps.splits.ZapSplitSetupLnAddress
import com.vitorpamplona.quartz.nip57Zaps.splits.zapSplitSetup

/**
 * What payment rails would actually reach the recipient(s) of a zap.
 *
 * Computed against the note author plus any NIP-57 `zap` split tags on the
 * event, so the popup can hide chips for rails that would silently no-op or
 * surface a "skipped recipient" error at send time.
 *
 * A flag is `true` when **at least one** recipient on the note can be paid
 * through that rail — matching the existing best-effort behaviour of the
 * actual send paths (Lightning skips pubkeys with no `lnAddress`; on-chain
 * separately warns about lnAddress-only splits that can't be paid on-chain).
 */
@Immutable
data class RailCapability(
    val hasCashu: Boolean,
    val hasLightning: Boolean,
    val hasOnchain: Boolean,
    /** Largest balance held in any single mint shared with the recipient. */
    val cashuBestSingleMintSats: Long = 0L,
    /** Total cashu balance across all our mints (reachable via reload/rebalance). */
    val cashuTotalWalletSats: Long = 0L,
) {
    /**
     * Classify a cashu nutzap of [amountSats] for the unified amount chip.
     * Cashu is the one rail whose balance is free to read synchronously, so
     * this is a precise per-amount status rather than the optimistic
     * recipient-only gating used for Lightning and on-chain.
     */
    fun cashuStatus(amountSats: Long): CashuRailStatus =
        when {
            !hasCashu -> CashuRailStatus.UNAVAILABLE
            amountSats <= cashuBestSingleMintSats -> CashuRailStatus.FUNDED
            amountSats <= cashuTotalWalletSats -> CashuRailStatus.NEEDS_RELOAD
            else -> CashuRailStatus.IMPOSSIBLE
        }

    companion object {
        val NONE = RailCapability(hasCashu = false, hasLightning = false, hasOnchain = false)
    }
}

/**
 * Per-amount cashu spendability for the unified zap chip.
 *  - [FUNDED]: a single shared mint already covers the amount — instant, no fee.
 *  - [NEEDS_RELOAD]: total wallet balance covers it, but no single shared mint
 *    does — needs a mint reload/rebalance first.
 *  - [IMPOSSIBLE]: not enough cashu anywhere — a reload can't help.
 *  - [UNAVAILABLE]: the recipient can't receive cashu at all (no kind:10019 /
 *    no shared mint).
 */
enum class CashuRailStatus { FUNDED, NEEDS_RELOAD, IMPOSSIBLE, UNAVAILABLE }

object RailCapabilityResolver {
    /**
     * Resolve [RailCapability] for [baseNote]. Reads are synchronous from
     * already-loaded cache state, so this is safe to call from a `remember {}`
     * inside a composable.
     *
     * Rules:
     *  - **Cashu**: the note **author** has a kind:10019 with a P2PK pubkey
     *    AND shares at least one mint with our wallet. Computed against the
     *    author only (not `zap` splits) because [CashuWalletState.sendNutzap]
     *    pays the author. Delegates to [CashuWalletState.peekNutzapFunding],
     *    which also reports per-mint balances for amount-level gating.
     *  - **Lightning**: any pubkey recipient has lud16/lud06 in their kind:0,
     *    OR the note has at least one direct `ZapSplitSetupLnAddress` split.
     *  - **On-chain**: at least one pubkey-based recipient exists. NIP-BC
     *    derives the Taproot address from the recipient pubkey, so any nostr
     *    pubkey is payable; lnAddress-only recipients are not (they have no
     *    pubkey to tweak). The sender's onchain wallet availability is a
     *    *sender* concern — handled elsewhere by the popup gating the chip
     *    on `onchainZapAmountChoices` and the dialog on `LocalCache.onchainBackend`.
     */
    fun peek(
        baseNote: Note,
        cashuState: CashuWalletState,
    ): RailCapability {
        val author = baseNote.author?.pubkeyHex
        val splits = baseNote.event?.zapSplitSetup().orEmpty()

        val pubKeySplits = splits.filterIsInstance<ZapSplitSetup>().map { it.pubKeyHex }
        val lnAddressOnlySplits = splits.filterIsInstance<ZapSplitSetupLnAddress>()

        val pubKeyRecipients: Set<HexKey> =
            buildSet {
                if (author != null) add(author)
                addAll(pubKeySplits)
            }

        if (pubKeyRecipients.isEmpty() && lnAddressOnlySplits.isEmpty()) {
            return RailCapability.NONE
        }

        // Cashu is computed against the author only: sendNutzap pays
        // baseNote.author (it does not fan a nutzap across `zap` splits), so
        // gating on a split recipient's wallet would offer a chip the send
        // path can't honor.
        val cashuFunding = author?.let { cashuState.peekNutzapFunding(it) }
        val hasCashu = cashuFunding != null

        val hasLightning =
            lnAddressOnlySplits.isNotEmpty() ||
                pubKeyRecipients.any { pk ->
                    LocalCache.getUserIfExists(pk)?.lnAddress() != null
                }

        // On-chain pays the pubkey directly; an event with only lnAddress
        // splits and no author pubkey has nothing to pay on-chain.
        val hasOnchain = pubKeyRecipients.isNotEmpty()

        return RailCapability(
            hasCashu = hasCashu,
            hasLightning = hasLightning,
            hasOnchain = hasOnchain,
            cashuBestSingleMintSats = cashuFunding?.bestSingleMintSats ?: 0L,
            cashuTotalWalletSats = cashuFunding?.totalWalletSats ?: 0L,
        )
    }
}
