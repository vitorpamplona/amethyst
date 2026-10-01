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
package com.vitorpamplona.amethyst.desktop.nowPlaying

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettings
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.model.preferences.NowPlayingSettingsStore
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.prefs.Preferences

/**
 * Desktop's per-account [NowPlayingSettingsState]s, persisted with `java.util.prefs` like the
 * rest of the desktop preferences (the DataStore-backed `NowPlayingSettingsStore` is Android's).
 * One state per pubkey for the life of the process, so the settings card and the publisher share it.
 */
object DesktopNowPlayingSettings {
    private val prefs: Preferences = Preferences.userNodeForPackage(DesktopNowPlayingSettings::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val states = mutableMapOf<HexKey, NowPlayingSettingsState>()

    @Synchronized
    fun forAccount(pubKeyHex: HexKey): NowPlayingSettingsState =
        states.getOrPut(pubKeyHex) {
            NowPlayingSettingsState().also { state ->
                state.restore(load(pubKeyHex))
                scope.launch { state.flow.drop(1).collect { save(pubKeyHex, it) } }
            }
        }

    private fun key(
        pubKeyHex: HexKey,
        name: String,
    ) = "nowPlaying.${pubKeyHex.take(16)}.$name"

    private fun load(pubKeyHex: HexKey) =
        NowPlayingSettings(
            shareInApp = false,
            shareOtherApps = prefs.getBoolean(key(pubKeyHex, "shareOtherApps"), false),
            blockedApps = split(prefs.get(key(pubKeyHex, "blockedApps"), "")).toSet(),
            knownApps = NowPlayingSettingsStore.decodeApps(split(prefs.get(key(pubKeyHex, "knownApps"), "")).toSet()),
        )

    private fun save(
        pubKeyHex: HexKey,
        settings: NowPlayingSettings,
    ) {
        prefs.putBoolean(key(pubKeyHex, "shareOtherApps"), settings.shareOtherApps)
        prefs.put(key(pubKeyHex, "blockedApps"), settings.blockedApps.joinToString(LINE))
        prefs.put(key(pubKeyHex, "knownApps"), NowPlayingSettingsStore.encodeApps(settings.knownApps).joinToString(LINE))
    }

    private fun split(value: String) = value.split(LINE).filter { it.isNotEmpty() }

    private const val LINE = "\n"
}
