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
package com.vitorpamplona.amethyst.service.relayClient.searchCommand.subassemblies

import com.vitorpamplona.ammolite.relays.ALL_FEED_TYPES
import com.vitorpamplona.ammolite.relays.TypedFilter
import com.vitorpamplona.ammolite.relays.filters.SincePerRelayFilter
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress

fun filterByAddress(address: NAddress) =
    listOf(
        TypedFilter(
            types = ALL_FEED_TYPES,
            filter =
                SincePerRelayFilter(
                    kinds = listOf(MetadataEvent.KIND),
                    authors = listOfNotNull(address.author),
                    limit = 1,
                ),
        ),
        if (address.kind < 25000 && address.dTag.isBlank()) {
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(address.kind),
                        authors = listOf(address.author),
                        limit = 5,
                    ),
            )
        } else {
            TypedFilter(
                types = ALL_FEED_TYPES,
                filter =
                    SincePerRelayFilter(
                        kinds = listOf(address.kind),
                        tags = mapOf("d" to listOf(address.dTag)),
                        authors = listOf(address.author),
                        limit = 5,
                    ),
            )
        },
    )
