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
package com.vitorpamplona.quartz.nip61Nutzaps.info

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.diff.EventDiff
import com.vitorpamplona.quartz.nip01Core.diff.ListDiff
import com.vitorpamplona.quartz.nip01Core.diff.ValueChange
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip61Nutzaps.info.tags.NutzapMintTag

/** Changes to the NIP-61 nutzap receiving settings: accepted mints (and their units), relays, and the P2PK key nutzaps are locked to. */
@Immutable
class NutzapInfoDiff(
    val mints: ListDiff<NutzapMintTag>,
    val relays: ListDiff<NormalizedRelayUrl>,
    val p2pkPubkey: ValueChange<HexKey>?,
) : EventDiff {
    override fun removesData() = mints.hasRemovals() || relays.hasRemovals() || p2pkPubkey?.isRemoval() == true

    override fun isEmpty() = mints.isEmpty() && relays.isEmpty() && p2pkPubkey == null
}
