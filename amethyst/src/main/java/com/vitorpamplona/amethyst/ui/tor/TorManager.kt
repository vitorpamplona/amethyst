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
package com.vitorpamplona.amethyst.ui.tor

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 */
class TorManager(
    app: Application,
    scope: CoroutineScope,
) {
    val status: StateFlow<TorServiceStatus> =
        TorService(app).status.stateIn(
            scope,
            SharingStarted.WhileSubscribed(30000),
            TorServiceStatus.Off,
        )

    val activePortOrNull: StateFlow<Int?> =
        status
            .map {
                (status.value as? TorServiceStatus.Active)?.port
            }.stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                (status.value as? TorServiceStatus.Active)?.port,
            )

    fun isSocksReady() = status.value is TorServiceStatus.Active

    fun socksPort(): Int = (status.value as? TorServiceStatus.Active)?.port ?: 9050
}
