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
) {
    companion object {
        val NONE = RailCapability(hasCashu = false, hasLightning = false, hasOnchain = false)
    }
}

object RailCapabilityResolver {
    /**
     * Resolve [RailCapability] for [baseNote]. Reads are synchronous from
     * already-loaded cache state, so this is safe to call from a `remember {}`
     * inside a composable.
     *
     * Rules:
     *  - **Cashu**: any pubkey-based recipient (author + `ZapSplitSetup`
     *    splits) has a kind:10019 with a P2PK pubkey AND shares at least one
     *    mint with our wallet. Delegates to [CashuWalletState.peekNutzapTarget]
     *    which already enforces all three conditions.
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

        val hasCashu = pubKeyRecipients.any { cashuState.peekNutzapTarget(it) != null }

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
        )
    }
}
