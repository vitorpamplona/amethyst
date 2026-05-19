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
package com.vitorpamplona.amethyst.ui.i2p

import com.vitorpamplona.amethyst.commons.i2p.I2pServiceStatus
import com.vitorpamplona.amethyst.commons.i2p.I2pType
import com.vitorpamplona.amethyst.model.preferences.I2pSharedPreferences
import com.vitorpamplona.quartz.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * EXTERNAL-only I2P manager. Mirrors TorManager's status surface but never starts
 * a daemon itself: I2P bootstrap on Android is structurally minutes-long (no
 * equivalent of Tor's hardcoded directory authorities — the protocol requires
 * NetDB peer discovery), so the embedded route would ship a permanently-warming
 * experience. Users who want I2P run i2pd / Java I2P independently and point
 * Amethyst at its SOCKS port via the Privacy Options screen.
 *
 * There is intentionally no Connecting state, no bootstrap timeout, and no
 * "session bypass" — when EXTERNAL is selected the daemon's lifecycle is the
 * user's responsibility and the connection either works or it doesn't.
 */
class I2pManager(
    i2pPrefs: I2pSharedPreferences,
    scope: CoroutineScope,
) {
    val status: StateFlow<I2pServiceStatus> =
        combine(
            i2pPrefs.value.i2pType,
            i2pPrefs.value.externalSocksPort,
        ) { type, port ->
            when (type) {
                I2pType.OFF -> I2pServiceStatus.Off
                I2pType.EXTERNAL -> if (port > 0) I2pServiceStatus.Active(port) else I2pServiceStatus.Off
            }
        }.catch { e ->
            Log.e("I2pManager") { "I2P service error: ${e.message}" }
            emit(I2pServiceStatus.Off)
        }.flowOn(Dispatchers.IO)
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(30000),
                I2pServiceStatus.Off,
            )

    val activePortOrNull: StateFlow<Int?> =
        status
            .map { (it as? I2pServiceStatus.Active)?.port }
            .stateIn(
                scope,
                SharingStarted.WhileSubscribed(2000),
                (status.value as? I2pServiceStatus.Active)?.port,
            )

    fun isSocksReady() = status.value is I2pServiceStatus.Active

    fun socksPort(): Int? = (status.value as? I2pServiceStatus.Active)?.port
}
