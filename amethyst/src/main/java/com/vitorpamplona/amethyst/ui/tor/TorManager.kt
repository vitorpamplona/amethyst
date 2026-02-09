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
package com.vitorpamplona.amethyst.ui.tor

import android.content.Context
import com.vitorpamplona.amethyst.model.preferences.TorSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest

/**
 * There should be only one instance of the Tor binding per app.
 *
 * Tor will connect as soon as status is listened to.
 */
class TorManager(
    torPrefs: TorSharedPreferences,
    app: Context,
    scope: CoroutineScope,
) {
    val service = TorService(app)

    @OptIn(ExperimentalCoroutinesApi::class)
    val status =
        combine(
            torPrefs.value.torType,
            torPrefs.value.externalSocksPort,
        ) { torType, externalSocksPort ->
            Pair(torType, externalSocksPort)
        }.transformLatest { (torType, externalSocksPort) ->
            when (torType) {
                TorType.INTERNAL -> {
                    emitAll(service.status)
                }

                TorType.OFF -> {
                    emit(TorServiceStatus.Off)
                }

                TorType.EXTERNAL -> {
                    if (externalSocksPort > 0) {
                        emit(TorServiceStatus.Active(externalSocksPort))
                    } else {
                        emitAll(service.status)
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(
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
