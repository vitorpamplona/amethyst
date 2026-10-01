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

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlaying
import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSource
import com.vitorpamplona.quartz.utils.TimeUtils

/**
 * macOS: the system-wide "Now Playing" is a private framework apps can no longer read (macOS
 * 15.4+), so this asks the two players that matter most, Spotify and Apple Music, over
 * AppleScript. Each is only asked while it is running: addressing an app that is not running
 * would launch it. The first time, macOS asks the user to allow Amethyst to control the player.
 */
class MacNowPlayingReader(
    private val run: suspend (List<String>) -> String? = { runCommand(it) },
    private val nowSeconds: () -> Long = TimeUtils::now,
) : OsNowPlayingReader {
    private class Player(
        val bundleId: String,
        val label: String,
        val executable: String,
        // Spotify reports the track duration in milliseconds, Music in seconds.
        val durationInMs: Boolean,
    )

    override suspend fun read(): NowPlaying? {
        val running = run(listOf("ps", "-A", "-o", "comm=")) ?: return null

        for (player in PLAYERS) {
            if (!running.contains(player.executable)) continue
            val output = run(listOf("osascript", "-e", script(player))) ?: continue
            return parse(player.bundleId, player.label, output, player.durationInMs, nowSeconds()) ?: continue
        }

        return null
    }

    companion object {
        private val PLAYERS =
            listOf(
                Player("com.spotify.client", "Spotify", "Spotify.app/Contents/MacOS/Spotify", durationInMs = true),
                Player("com.apple.Music", "Music", "Music.app/Contents/MacOS/Music", durationInMs = false),
            )

        private fun script(player: Player) =
            """
            tell application id "${player.bundleId}"
                if player state is playing then
                    return (name of current track) & tab & (artist of current track) & tab & (duration of current track) & tab & (player position)
                end if
            end tell
            return ""
            """.trimIndent()

        /** Parses `title<TAB>artist<TAB>duration<TAB>position seconds`; numbers may use a decimal comma. */
        fun parse(
            id: String,
            label: String,
            output: String,
            durationInMs: Boolean,
            nowSeconds: Long,
        ): NowPlaying? {
            val fields = output.trimEnd('\n', '\r').split('\t')
            if (fields.size < 4) return null

            val title = fields[0].ifBlank { null } ?: return null
            val duration = fields[2].replace(',', '.').toDoubleOrNull()
            val durationMs = duration?.let { if (durationInMs) it.toLong() else (it * 1000).toLong() }
            val positionMs = fields[3].replace(',', '.').toDoubleOrNull()?.let { (it * 1000).toLong() } ?: 0L

            return NowPlaying(
                title = title,
                artist = fields[1].ifBlank { null },
                source = NowPlayingSource.OtherApp(id, label),
                endsAt = NowPlaying.endsAt(nowSeconds, durationMs, positionMs),
            )
        }
    }
}
