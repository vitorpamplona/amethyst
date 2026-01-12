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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.subassembies

import com.vitorpamplona.amethyst.commons.model.ThreadAssembler
import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingAddressables
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingEvents
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.potentialRelaysToFindAddress
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.potentialRelaysToFindEvent
import com.vitorpamplona.quartz.nip01Core.relay.client.pool.RelayBasedFilter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.utils.mapOfSet

fun filterMissingEventsForThread(
    threadInfo: ThreadAssembler.ThreadInfo,
    defaultRelays: Set<NormalizedRelayUrl>,
): List<RelayBasedFilter> {
    val missingEvents =
        mapOfSet {
            if (threadInfo.root.event == null && threadInfo.root !is AddressableNote) {
                potentialRelaysToFindEvent(threadInfo.root).ifEmpty { defaultRelays }.forEach { relayUrl ->
                    add(relayUrl, threadInfo.root.idHex)
                }
            }

            threadInfo.allNotes.forEach {
                if (it !is AddressableNote && it.event == null) {
                    potentialRelaysToFindEvent(it).ifEmpty { defaultRelays }.forEach { relayUrl ->
                        add(relayUrl, it.idHex)
                    }
                }
            }
        }

    val missingAddresses =
        mapOfSet {
            val rootNote = threadInfo.root
            if (rootNote.event == null && rootNote is AddressableNote) {
                potentialRelaysToFindEvent(rootNote).ifEmpty { defaultRelays }.forEach { relayUrl ->
                    add(relayUrl, rootNote.address)
                }
            }

            threadInfo.allNotes.forEach {
                if (it is AddressableNote && it.event == null) {
                    potentialRelaysToFindAddress(it).ifEmpty { defaultRelays }.forEach { relayUrl ->
                        add(relayUrl, it.address)
                    }
                }
            }
        }

    val missingEventsFilter = filterMissingEvents(missingEvents)
    val missingAddressFilter = filterMissingAddressables(missingAddresses)

    return missingEventsFilter + missingAddressFilter
}
