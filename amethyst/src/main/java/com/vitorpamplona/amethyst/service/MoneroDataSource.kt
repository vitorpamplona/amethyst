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
package com.vitorpamplona.amethyst.service

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

object MoneroDataSource {
    private lateinit var moneroService: WalletService

    fun status() =
        flow {
            emitAll(moneroService.statusStateFlow)
        }

    fun connectionStatus() =
        flow {
            emitAll(moneroService.connectionStatusStateFlow)
        }

    fun walletHeight() =
        flow {
            emitAll(moneroService.walletHeightStateFlow)
        }

    fun daemonHeight() =
        flow {
            emitAll(moneroService.daemonHeightStateFlow)
        }

    fun balance() =
        flow {
            emitAll(moneroService.balanceStateFlow)
        }

    fun lockedBalance() =
        flow {
            emitAll(moneroService.lockedBalanceStateFlow)
        }

    fun transactions() =
        flow {
            emitAll(moneroService.transactions)
        }

    fun setMoneroService(service: WalletService) {
        moneroService = service
    }
}
