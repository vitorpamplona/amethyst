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
package com.vitorpamplona.amethyst.ui.screen.loggedIn.threadview.datasources.subassembies

import com.vitorpamplona.amethyst.model.AddressableNote
import com.vitorpamplona.amethyst.model.ThreadAssembler
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingAddressables
import com.vitorpamplona.amethyst.service.relayClient.reqCommand.event.loaders.filterMissingEvents
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.EOSETime
import com.vitorpamplona.quartz.nip01Core.tags.addressables.Address

fun filterMissingEventsForThread(
    threadInfo: ThreadAssembler.ThreadInfo,
    since: Map<String, EOSETime>?,
): List<TypedFilter> {
    val missingEvents = mutableSetOf<String>()
    val missingAddresses = mutableSetOf<Address>()

    if (threadInfo.root.event == null) {
        if (threadInfo.root is AddressableNote) {
            missingAddresses.add(threadInfo.root.address)
        } else {
            missingEvents.add(threadInfo.root.idHex)
        }
    }

    threadInfo.allNotes.forEach {
        if (it.event == null) {
            if (it is AddressableNote) {
                missingAddresses.add(it.address)
            } else {
                missingEvents.add(it.idHex)
            }
        }
    }

    val missingEventsFilter = filterMissingEvents(missingEvents, since) ?: emptyList()
    val missingAddressFilter = filterMissingAddressables(missingAddresses, since) ?: emptyList()

    return missingEventsFilter + missingAddressFilter
}
