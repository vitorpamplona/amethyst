/**
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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.connected

import com.vitorpamplona.amethyst.Amethyst
import com.vitorpamplona.amethyst.model.LocalCache
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfo
import com.vitorpamplona.amethyst.ui.screen.loggedIn.relays.common.BasicRelaySetupInfoModel
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl

class ConnectedRelayListViewModel : BasicRelaySetupInfoModel() {
    override fun relayListBuilder(): List<BasicRelaySetupInfo> {
        val relayList = getRelayList()

        return relayList
            .map {
                BasicRelaySetupInfo(
                    relay = it,
                    relayStat = Amethyst.instance.relayStats.get(it),
                    forcesTor =
                        Amethyst.instance.torEvaluatorFlow.flow.value
                            .useTor(it),
                    users =
                        account.declaredFollowsPerRelay.value[it]?.mapNotNull { hex ->
                            LocalCache.checkGetOrCreateUser(hex)
                        } ?: emptyList(),
                )
            }.distinctBy { it.relay }
            .sortedBy { it.relayStat.receivedBytes }
            .reversed()
    }

    override fun getRelayList(): List<NormalizedRelayUrl> =
        account.client
            .relayStatusFlow()
            .value.available
            .sorted()

    override suspend fun saveRelayList(urlList: List<NormalizedRelayUrl>) {
    }
}
