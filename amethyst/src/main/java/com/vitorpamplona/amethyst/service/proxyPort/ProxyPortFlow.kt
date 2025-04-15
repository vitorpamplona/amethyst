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
package com.vitorpamplona.amethyst.service.proxyPort

import com.vitorpamplona.amethyst.ui.tor.TorServiceStatus
import com.vitorpamplona.amethyst.ui.tor.TorType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ProxyPortFlow(
    torType: MutableStateFlow<TorType>,
    externalSocksPort: MutableStateFlow<Int>,
    torServiceStatus: StateFlow<TorServiceStatus>,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val status =
        torType
            .flatMapLatest { torType ->
                when (torType) {
                    TorType.INTERNAL -> {
                        // subscribing to status turns Tor service on
                        torServiceStatus.map {
                            if (it is TorServiceStatus.Active) {
                                it.port
                            } else {
                                null
                            }
                        }
                    }

                    TorType.EXTERNAL -> {
                        externalSocksPort.map { port ->
                            if (port > 0) {
                                port
                            } else {
                                null
                            }
                        }
                    }

                    else -> MutableStateFlow(null)
                }
            }.distinctUntilChanged()

    companion object {
        fun computePort(
            torType: TorType,
            externalPort: Int,
            status: TorServiceStatus,
        ): Int? =
            when (torType) {
                TorType.INTERNAL -> {
                    if (status is TorServiceStatus.Active && status.port > 0) {
                        status.port
                    } else {
                        null
                    }
                }

                TorType.EXTERNAL -> {
                    if (externalPort > 0) {
                        externalPort
                    } else {
                        null
                    }
                }

                else -> null
            }
    }
}
