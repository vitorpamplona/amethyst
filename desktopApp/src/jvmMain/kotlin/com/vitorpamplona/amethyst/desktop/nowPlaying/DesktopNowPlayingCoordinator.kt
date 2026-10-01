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

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.UserStatusAction
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingPublisher
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettingsState
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingStatusService
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Desktop: shares what another app on this computer is playing as the account's NIP-38 music
 * status. The OS is polled every [pollMs] only while the account has
 * [com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSettings.shareOtherApps]
 * on, so nothing is spawned (and macOS never asks for Automation access) until the user opts in.
 *
 * Desktop has no music or podcast player of its own, so there is no in-app source here.
 */
class DesktopNowPlayingCoordinator(
    private val settings: NowPlayingSettingsState,
    private val signer: NostrSigner,
    private val publishEvent: (Event) -> Unit,
    private val reader: OsNowPlayingReader? = OsNowPlayingReader.forThisOs(),
    private val pollMs: Long = 15_000,
) {
    /** Runs until the calling coroutine (the logged-in account's composition) is cancelled. */
    suspend fun run() {
        val reader = reader ?: return

        coroutineScope {
            val otherApps = MutableStateFlow<NowPlaying?>(null)

            launch {
                settings.flow
                    .map { it.shareOtherApps }
                    .distinctUntilChanged()
                    .collectLatest { enabled ->
                        if (!enabled) {
                            otherApps.value = null
                            return@collectLatest
                        }
                        while (true) {
                            otherApps.value = reader.read()
                            delay(pollMs)
                        }
                    }
            }

            val publisher =
                NowPlayingPublisher(
                    publish = { track, expiration -> publishEvent(UserStatusAction.createMusic(track, expiration, signer)) },
                    clear = { publishEvent(UserStatusAction.clearMusic(signer)) },
                )

            NowPlayingStatusService(
                settings = settings.flow,
                sources = listOf(otherApps),
                onOtherAppSeen = { settings.rememberApp(it.id, it.label) },
                publisher = publisher,
            ).run()
        }
    }
}
