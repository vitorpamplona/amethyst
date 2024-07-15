/**
 * Copyright (c) 2024 Vitor Pamplona
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
package com.vitorpamplona.amethyst.ui.actions.relays

import androidx.compose.runtime.Immutable
import com.vitorpamplona.ammolite.relays.FeedType
import com.vitorpamplona.ammolite.relays.RelayBriefInfoCache
import com.vitorpamplona.ammolite.relays.RelayStat
import com.vitorpamplona.quartz.encoders.HexKey

@Immutable
data class BasicRelaySetupInfo(
    val url: String,
    val relayStat: RelayStat,
    val paidRelay: Boolean = false,
) {
    val briefInfo: RelayBriefInfoCache.RelayBriefInfo = RelayBriefInfoCache.RelayBriefInfo(url)
}

@Immutable
data class Kind3BasicRelaySetupInfo(
    val url: String,
    val read: Boolean,
    val write: Boolean,
    val feedTypes: Set<FeedType>,
    val relayStat: RelayStat,
    val paidRelay: Boolean = false,
) {
    val briefInfo: RelayBriefInfoCache.RelayBriefInfo = RelayBriefInfoCache.RelayBriefInfo(url)
}

@Immutable
data class Kind3RelayProposalSetupInfo(
    val url: String,
    val read: Boolean,
    val write: Boolean,
    val feedTypes: Set<FeedType>,
    val relayStat: RelayStat,
    val paidRelay: Boolean = false,
    val users: Set<HexKey>,
) {
    val briefInfo: RelayBriefInfoCache.RelayBriefInfo = RelayBriefInfoCache.RelayBriefInfo(url)
}
