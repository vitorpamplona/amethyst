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

import com.vitorpamplona.amethyst.commons.model.nip38UserStatuses.nowPlaying.NowPlayingSource
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsNowPlayingReaderTest {
    private val listNames =
        """
        method return time=1700000000.1 sender=org.freedesktop.DBus -> destination=:1.99 serial=3 reply_serial=2
           array [
              string "org.freedesktop.DBus"
              string ":1.7"
              string "org.mpris.MediaPlayer2.vlc.instance4242"
              string "org.mpris.MediaPlayer2.spotify"
           ]
        """.trimIndent()

    private val vlcPaused =
        """
        method return time=1700000000.2 sender=:1.50 -> destination=:1.99 serial=10 reply_serial=2
           array [
              dict entry(
                 string "PlaybackStatus"
                 variant             string "Paused"
              )
           ]
        """.trimIndent()

    private val spotifyPlaying =
        """
        method return time=1700000000.3 sender=:1.51 -> destination=:1.99 serial=11 reply_serial=2
           array [
              dict entry(
                 string "PlaybackStatus"
                 variant             string "Playing"
              )
              dict entry(
                 string "Metadata"
                 variant             array [
                       dict entry(
                          string "mpris:trackid"
                          variant                      object path "/com/spotify/track/0DiWol3AO6WpXZgp0goxAV"
                       )
                       dict entry(
                          string "mpris:length"
                          variant                      uint64 320000000
                       )
                       dict entry(
                          string "xesam:artist"
                          variant                      array [
                                string "Daft Punk"
                                string "Romanthony"
                             ]
                       )
                       dict entry(
                          string "xesam:title"
                          variant                      string "One More Time "Live" – Café"
                       )
                       dict entry(
                          string "xesam:url"
                          variant                      string "https://open.spotify.com/track/0DiWol3AO6WpXZgp0goxAV"
                       )
                    ]
              )
              dict entry(
                 string "Position"
                 variant             int64 20000000
              )
              dict entry(
                 string "Rate"
                 variant             double 1
              )
           ]
        """.trimIndent()

    private val spotifyIdentity =
        """
        method return time=1700000000.4 sender=:1.51 -> destination=:1.99 serial=12 reply_serial=2
           array [
              dict entry(
                 string "Identity"
                 variant             string "Spotify"
              )
              dict entry(
                 string "SupportedMimeTypes"
                 variant             array [
                    ]
              )
           ]
        """.trimIndent()

    @Test
    fun mprisFindsThePlayingPlayer() =
        runTest {
            val reader =
                MprisNowPlayingReader(
                    run = { command ->
                        val dest = command.firstOrNull { it.startsWith("--dest=") }?.removePrefix("--dest=")
                        when {
                            dest == "org.freedesktop.DBus" -> listNames
                            dest == "org.mpris.MediaPlayer2.vlc.instance4242" -> vlcPaused
                            command.last() == "string:org.mpris.MediaPlayer2.Player" -> spotifyPlaying
                            else -> spotifyIdentity
                        }
                    },
                    nowSeconds = { 1_000L },
                )

            val track = reader.read()!!

            assertEquals("One More Time \"Live\" – Café", track.title)
            assertEquals("Daft Punk, Romanthony", track.artist)
            assertEquals(NowPlayingSource.OtherApp("spotify", "Spotify"), track.source)
            // 320s long, 20s in.
            assertEquals(1_000L + 300 + 1, track.endsAt)
            assertEquals("https://open.spotify.com/track/0DiWol3AO6WpXZgp0goxAV", track.url)
        }

    /**
     * Runs the real reader, `dbus-send` included, against a live session bus. Opt-in: start an
     * MPRIS player (or a fake one) under `dbus-run-session` and set MPRIS_LIVE_TITLE to its title.
     */
    @Test
    fun mprisReadsALiveSessionBus() =
        runTest {
            val expectedTitle = System.getenv("MPRIS_LIVE_TITLE") ?: return@runTest
            assertEquals(expectedTitle, MprisNowPlayingReader().read()?.title)
        }

    @Test
    fun mprisReportsNothingWhenNoneIsPlaying() =
        runTest {
            val reader =
                MprisNowPlayingReader(
                    run = { command -> if (command.any { it == "--dest=org.freedesktop.DBus" }) listNames else vlcPaused },
                )

            assertNull(reader.read())
        }

    @Test
    fun mprisSharesTrackLinksButNeverPagesOrFiles() {
        assertEquals(
            "https://open.spotify.com/track/0DiWol3AO6WpXZgp0goxAV",
            MprisParser.shareableUrl("https://open.spotify.com/track/0DiWol3AO6WpXZgp0goxAV"),
        )
        assertEquals(
            "https://open.spotify.com/intl-pt/episode/4rOoJ6Egrf8K2IrywzwOMk?si=abc",
            MprisParser.shareableUrl("https://open.spotify.com/intl-pt/episode/4rOoJ6Egrf8K2IrywzwOMk?si=abc"),
        )
        // Chromium reports the playing tab's page; VLC the file it opened.
        assertNull(MprisParser.shareableUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(MprisParser.shareableUrl("https://mail.example.com/inbox/secret-thread"))
        assertNull(MprisParser.shareableUrl("file:///home/me/Music/song.mp3"))
        assertNull(MprisParser.shareableUrl("https://open.spotify.com.evil.example/track/abc"))
    }

    @Test
    fun mprisPlayerIdDropsTheInstanceSuffix() {
        assertEquals("vlc", MprisParser.playerId("org.mpris.MediaPlayer2.vlc.instance4242"))
        assertEquals("chromium", MprisParser.playerId("org.mpris.MediaPlayer2.chromium.instance_1_23"))
        assertEquals("spotify", MprisParser.playerId("org.mpris.MediaPlayer2.spotify"))
    }

    @Test
    fun macParsesSpotifyAndMusicUnits() {
        val spotify = MacNowPlayingReader.parse("com.spotify.client", "Spotify", "Song\tBand\t200000\t50,5\n", durationInMs = true, nowSeconds = 0)!!
        assertEquals("Song", spotify.title)
        assertEquals("Band", spotify.artist)
        assertEquals(149L + 1, spotify.endsAt)

        val music = MacNowPlayingReader.parse("com.apple.Music", "Music", "Song\tBand\t200.0\t50.0\n", durationInMs = false, nowSeconds = 0)!!
        assertEquals(150L + 1, music.endsAt)

        assertNull(MacNowPlayingReader.parse("com.apple.Music", "Music", "\n", durationInMs = false, nowSeconds = 0))
    }

    @Test
    fun windowsParsesTheScriptOutput() {
        val track = WindowsNowPlayingReader.parse("Spotify.exe\tSong\tBand\t200000\t50000\r\n", nowSeconds = 0)!!

        assertEquals("Song - Band", track.statusText())
        assertEquals(NowPlayingSource.OtherApp("Spotify.exe", "Spotify"), track.source)
        assertEquals(150L + 1, track.endsAt)

        assertNull(WindowsNowPlayingReader.parse("", nowSeconds = 0))
    }

    @Test
    fun windowsAppLabels() {
        assertEquals("Spotify", WindowsNowPlayingReader.appLabel("Spotify.exe"))
        assertEquals("ZuneMusic", WindowsNowPlayingReader.appLabel("Microsoft.ZuneMusic_8wekyb3d8bbwe!Microsoft.ZuneMusic"))
        assertEquals("308046B0AF4A39CB", WindowsNowPlayingReader.appLabel("308046B0AF4A39CB"))
    }

    private val hasShell = File("/bin/sh").canExecute()

    /** `ps -A -o comm=` prints ~100 KB on a busy Mac; reading only after the exit deadlocked on the pipe. */
    @Test
    fun runCommandReturnsOutputLargerThanThePipeBuffer() =
        runTest {
            if (!hasShell) return@runTest
            val output = runCommand(listOf("/bin/sh", "-c", "i=0; while [ \$i -lt 4000 ]; do echo 0123456789012345678901234567890123456789012345678; i=\$((i+1)); done"))
            assertEquals(4000 * 50, output?.length)
        }

    @Test
    fun runCommandGivesUpOnAHungCommand() =
        runTest {
            if (!hasShell) return@runTest
            val started = System.currentTimeMillis()
            // `; true` makes every shell fork `sleep` rather than exec it, as dash always does, so the
            // child that outlives a killed shell is exercised whichever shell /bin/sh is. Only Linux
            // JDKs then block on the pipe (macOS closes it on destroy): the check bites on Linux CI.
            assertNull(runCommand(listOf("/bin/sh", "-c", "sleep 30; true"), timeoutSeconds = 1))
            assertTrue(System.currentTimeMillis() - started < 10_000, "the timeout must end the call")
        }
}
