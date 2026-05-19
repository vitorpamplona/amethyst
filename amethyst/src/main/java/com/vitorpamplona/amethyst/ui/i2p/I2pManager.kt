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
 * Mirror of TorManager, but EXTERNAL-only for now — no embedded I2P daemon ships
 * with this branch. When [I2pType.EXTERNAL] is selected, this manager surfaces the
 * configured SOCKS port; otherwise it emits [I2pServiceStatus.Off].
 *
 * There is intentionally no Connecting state, no bootstrap timeout, and no
 * "session bypass": with EXTERNAL the daemon's lifecycle is the user's
 * responsibility (i2pd / Java I2P running on the device), and the connection
 * either works or it doesn't — there's no in-app bootstrap to bypass.
 *
 * When INTERNAL is wired up in a follow-up, it will land here as a third branch
 * that starts an embedded daemon and emits Connecting / Active over its
 * lifecycle, mirroring the Tor flow.
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
                // Persisted INTERNAL is treated as Off until an embedded daemon ships.
                I2pType.INTERNAL -> I2pServiceStatus.Off
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
